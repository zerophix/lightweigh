package com.xcwk.scalehub.application.device

import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.domain.repository.DeviceRepository

/**
 * application service — 设备重命名 + 成员关联编排。
 *
 * 职责：
 *  - 重命名设备（校验名称长度/非空）
 *  - 关联设备到成员（设备激活）
 *  - 返回操作结果文案
 *
 * 旧库 DeviceManager 语义移植。
 */
class DeviceRenameService(
    private val devices: DeviceRepository,
) {

    /** 昵称最大长度（旧库 20 字符）。 */
    companion object {
        const val MAX_NICKNAME_LENGTH = 20
    }

    /**
     * 重命名设备。
     * @param mac 设备 MAC 地址
     * @param newName 新名称
     * @return 重命名结果
     */
    suspend fun rename(mac: String, newName: String): DeviceRenameResult {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return DeviceRenameResult(false, "设备名称不能为空")
        }
        if (trimmed.length > MAX_NICKNAME_LENGTH) {
            return DeviceRenameResult(false, "设备名称最多 $MAX_NICKNAME_LENGTH 个字符")
        }
        val device = devices.device(mac) ?: return DeviceRenameResult(false, "设备不存在")
        return runCatching {
            // 重新绑定新名称
            devices.bind(trimmed, mac, device.protocolId ?: "")
            DeviceRenameResult(true, "设备名称已修改为：$trimmed")
        }.onFailure {
            DeviceRenameResult(false, "修改失败：${it.message ?: "未知错误"}}")
        }.getOrDefault(DeviceRenameResult(false, "修改失败"))
    }

    /**
     * 关联设备到成员（激活）。
     * @param mac 设备 MAC 地址
     * @param memberId 成员 ID
     * @return 关联结果
     */
    suspend fun associate(mac: String, memberId: String): DeviceAssociateResult {
        val device = devices.device(mac) ?: return DeviceAssociateResult(false, "设备不存在", null)
        // 激活设备
        devices.activate(mac)
        return DeviceAssociateResult(true, "设备已关联到成员", device)
    }

    /** 绑定设备到成员。 */
    suspend fun bind(mac: String, memberId: String, name: String): DeviceBindResult {
        if (name.isBlank()) return DeviceBindResult(false, "请输入设备名称", null)
        val result = devices.bind(name, mac, ScaleDevice.ALGORITHM_BUILTIN_1)
        return if (result != null) {
            DeviceBindResult(true, "绑定成功", result)
        } else {
            DeviceBindResult(false, "绑定失败，设备可能已绑定", null)
        }
    }
}

/** 重命名结果。 */
data class DeviceRenameResult(
    val ok: Boolean,
    val message: String,
)

/** 关联结果。 */
data class DeviceAssociateResult(
    val ok: Boolean,
    val message: String,
    val device: ScaleDevice?,
)

/** 绑定结果。 */
data class DeviceBindResult(
    val ok: Boolean,
    val message: String,
    val device: ScaleDevice?,
)
