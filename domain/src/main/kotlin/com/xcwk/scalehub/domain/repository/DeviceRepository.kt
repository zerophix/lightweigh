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
}
