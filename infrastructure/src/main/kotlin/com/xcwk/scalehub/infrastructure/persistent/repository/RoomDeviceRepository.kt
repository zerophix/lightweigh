package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.domain.repository.DeviceRepository
import com.xcwk.scalehub.domain.repository.DeviceRepository.FirmwareInfo
import com.xcwk.scalehub.infrastructure.persistent.dao.DeviceDao
import com.xcwk.scalehub.infrastructure.persistent.po.ScaleDevicePo
import java.util.Locale

/**
 * repository — 已绑定秤仓储（旧库 AppStore devices 段语义移植，:82-180,208-227）。
 * 内存缓存 + 写穿刷新：devices() 为高频同步读（测量会话扫描回调路径）。
 */
class RoomDeviceRepository(private val dao: DeviceDao) : DeviceRepository {

    private var cache: List<ScaleDevicePo>? = null

    private fun rows(): List<ScaleDevicePo> = cache ?: dao.all().also { cache = it }

    override fun devices(): List<ScaleDevice> = rows().map { it.toModel() }

    override fun contains(mac: String): Boolean = device(mac) != null

    override fun device(mac: String): ScaleDevice? {
        val normalized = normalizeMac(mac)
        if (normalized.isEmpty()) return null
        return rows().firstOrNull { it.mac == normalized }?.toModel()
    }

    /** 新增或更新一台秤，并把它设为当前测量设备（旧库 bind :119-145）。 */
    override fun bind(name: String, mac: String, protocolId: String): ScaleDevice? {
        val normalized = normalizeMac(mac)
        if (normalized.isEmpty()) return null
        val protocol = protocolId.ifEmpty { ScaleDevice.PROTOCOL_OKOK_1 }
        val displayName = name.ifBlank { "智能体脂秤" }
        // 协议→默认算法的映射是领域规则，仓储只负责存（DEC-034 任务组 B / F-004）
        val algorithm = ScaleDevice.algorithmFor(protocol, ScaleDevice.ALGORITHM_BUILTIN_1)
        val existing = rows().firstOrNull { it.mac == normalized }
        val boundAt = if (existing == null || existing.boundAt.isEmpty()) today() else existing.boundAt
        val firmwareVersion = existing?.firmwareVersion ?: "v1.1.0"
        val updated = ScaleDevicePo(normalized, displayName, boundAt, true, protocol, algorithm, firmwareVersion)
        for (row in rows()) {
            if (row.mac == normalized) {
                dao.upsert(updated)
            } else if (row.active) {
                dao.upsert(row.copy(active = false))
            }
        }
        if (existing == null) dao.upsert(updated)
        cache = null
        return device(normalized)
    }

    override fun activate(mac: String) {
        val normalized = normalizeMac(mac)
        if (normalized.isEmpty()) return
        for (row in rows()) {
            val on = row.mac == normalized
            if (row.active != on) dao.upsert(row.copy(active = on))
        }
        cache = null
    }

    override fun unbind(mac: String) {
        val normalized = normalizeMac(mac)
        if (normalized.isEmpty()) return
        dao.delete(normalized)
        cache = null
    }

    override fun restoreAll(devices: List<ScaleDevice>) {
        for (row in rows()) dao.delete(row.mac)
        for (device in devices) {
            dao.upsert(device.toPo())
        }
        cache = null
    }

    // ---- T-133 接入波 Batch2 新增能力 ----

    /** 重命名设备：仅更新显示名称，不触碰连接与测量数据。 */
    override fun renameDevice(mac: String, newName: String): ScaleDevice? {
        val normalized = normalizeMac(mac)
        if (normalized.isEmpty()) return null
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) return null
        val existing = rows().firstOrNull { it.mac == normalized }
        if (existing == null) return null
        val updated = existing.copy(name = trimmedName)
        dao.upsert(updated)
        cache = null
        return device(normalized)
    }

    /** 更新成员设备关联关系（存储在 SharedPreferences，与设备档案分离）。 */
    override fun updateMemberLinks(mac: String, memberIds: Set<String>) {
        val normalized = normalizeMac(mac)
        if (normalized.isEmpty()) return
        // 存储格式：mac -> JSON 字符串，实际实现可用 SharedPreferences 或单独表
        // 此处仅作仓储层口径预留，真实持久化由上层决定存储介质
        // TODO: 接入 MemberDeviceLinkStore 持久化
    }

    /** 检查固件版本（返回当前版本、最新版本、是否有更新）。 */
    override fun checkFirmware(mac: String): FirmwareInfo? {
        val normalized = normalizeMac(mac)
        if (normalized.isEmpty()) return null
        val device = rows().firstOrNull { it.mac == normalized } ?: return null
        // 真实实现应调用云端/设备固件接口；此处返回模拟数据占位
        // 版本比较逻辑：语义化版本号 v1.1.0 < v1.2.0
        val current = device.firmwareVersion?.ifBlank { "v1.1.0" } ?: "v1.1.0"
        val latest = "v1.2.0" // 模拟云端最新版本
        val hasUpdate = compareVersion(current, latest) < 0
        return FirmwareInfo(current, latest, hasUpdate)
    }

    /** 启动 OTA 升级（真实实现由 BLE 层承担，仓储只记录意图/状态）。 */
    override fun startOta(mac: String) {
        val normalized = normalizeMac(mac)
        if (normalized.isEmpty()) return
        // 真实 OTA 由 BlePort 层发起；此处可记录升级意图、更新本地版本号等
        // TODO: 接入 BlePort.startOta 并更新 firmwareVersion 字段
    }

    /** 语义化版本比较（简单实现：v1.2.0 > v1.1.0）。 */
    private fun compareVersion(v1: String, v2: String): Int {
        val parts1 = v1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    private fun ScaleDevicePo.toModel() =
        ScaleDevice(name, mac, boundAt, active, protocolId, algorithmId, firmwareVersion)

    private fun ScaleDevice.toPo(): ScaleDevicePo =
        ScaleDevicePo(mac, name, boundAt, active, protocolId, algorithmId, firmwareVersion)

    private fun today(): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return format.format(java.util.Date())
    }

    companion object {
        /** MAC 规范化归 domain 纯函数（MacFormat），此别名保留调用点语义。 */
        fun normalizeMac(raw: String?): String = com.xcwk.scalehub.domain.model.MacFormat.normalize(raw)
    }
}
