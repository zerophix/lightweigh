package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.domain.repository.DeviceRepository
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
        val updated = ScaleDevicePo(normalized, displayName, boundAt, true, protocol, algorithm)
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
            dao.upsert(
                ScaleDevicePo(
                    device.mac, device.name, device.boundAt, device.active,
                    device.protocolId, device.algorithmId,
                ),
            )
        }
        cache = null
    }

    private fun ScaleDevicePo.toModel() =
        ScaleDevice(name, mac, boundAt, active, protocolId, algorithmId)

    private fun today(): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return format.format(java.util.Date())
    }

    companion object {
        /** MAC 规范化归 domain 纯函数（MacFormat），此别名保留调用点语义。 */
        fun normalizeMac(raw: String?): String = com.xcwk.scalehub.domain.model.MacFormat.normalize(raw)
    }
}
