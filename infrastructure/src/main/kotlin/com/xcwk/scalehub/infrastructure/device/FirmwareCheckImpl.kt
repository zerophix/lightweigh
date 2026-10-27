package com.xcwk.scalehub.infrastructure.device

import com.xcwk.scalehub.application.device.FirmwareCheckPort
import com.xcwk.scalehub.application.device.FirmwareUpdate
import com.xcwk.scalehub.application.device.FirmwareUpdateResult

/**
 * 固件检查实现（Stub）。
 *
 * 真实 OTA 能力依赖设备厂商服务，当前为存根实现。
 * 生产环境替换为厂商 SDK。
 */
class FirmwareCheckImpl : FirmwareCheckPort {

    override suspend fun currentVersion(deviceId: String): String {
        // 当前仅返回固定版本（存根）
        return "1.0.0"
    }

    override suspend fun checkUpdate(deviceId: String): FirmwareUpdate? {
        // 当前无 OTA 服务，返回 null
        return null
    }

    override suspend fun triggerUpdate(deviceId: String, update: FirmwareUpdate): FirmwareUpdateResult {
        return FirmwareUpdateResult(false, "固件OTA服务暂未开放，敬请期待")
    }
}
