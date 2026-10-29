package com.xcwk.scalehub.domain.repository

import com.xcwk.scalehub.domain.model.ScaleDevice

/**
 * 已绑定体脂秤仓储（旧库 AppStore.devices 段语义，:82-180）。
 * 协议与算法编号在绑定时写下；normalizeMac 由实现持有（旧库 AppStore:208-227）。
 */
interface DeviceRepository {
    fun devices(): List<ScaleDevice>

    fun contains(mac: String): Boolean

    fun device(mac: String): ScaleDevice?

    /** 新增或更新一台秤，并把它设为当前测量设备；协议缺省 okok-1（旧库 bind :119-145）。 */
    fun bind(name: String, mac: String, protocolId: String): ScaleDevice?

    fun activate(mac: String)

    fun unbind(mac: String)

    /** 云端恢复合并的基线写入：整集替换（旧库 writeDevices 语义，CloudSync 恢复路径）。 */
    fun restoreAll(devices: List<ScaleDevice>)

    // ---- T-133 接入波 Batch2 新增能力 ----

    /** 重命名设备：仅更新显示名称，不触碰连接与测量数据。 */
    fun renameDevice(mac: String, newName: String): ScaleDevice?

    /** 更新成员设备关联关系。 */
    fun updateMemberLinks(mac: String, memberIds: Set<String>)

    /** 固件版本检查结果。 */
    data class FirmwareInfo(
        val currentVersion: String,
        val latestVersion: String,
        val hasUpdate: Boolean,
    )

    /** 检查固件版本（返回当前版本、最新版本、是否有更新）。 */
    fun checkFirmware(mac: String): FirmwareInfo?

    /** 启动 OTA 升级（真实实现由 BLE 层承担，仓储只记录意图/状态）。 */
    fun startOta(mac: String)
}
