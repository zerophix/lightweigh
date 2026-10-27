package com.xcwk.scalehub.application.device

/**
 * 固件检查端口（M3）—— 抽象固件版本查询与 OTA 更新触发。
 *
 * 实现者负责与设备厂商固件服务交互；应用层只编排语义。
 * 真实 OTA 能力依赖外部服务，如未可用则返回 Stub 结果并标记。
 */
interface FirmwareCheckPort {
    /** 当前设备固件版本（设备返回）；空串=设备不返回版本。 */
    suspend fun currentVersion(deviceId: String): String

    /**
     * 查询最新固件。
     * @return 可用更新时返回 FirmwareUpdate，否则 null
     */
    suspend fun checkUpdate(deviceId: String): FirmwareUpdate?

    /**
     * 触发 OTA 更新。
     * @return 是否已下发；失败时 message 解释原因
     */
    suspend fun triggerUpdate(deviceId: String, update: FirmwareUpdate): FirmwareUpdateResult
}

/** 可用的固件更新。 */
data class FirmwareUpdate(
    val version: String,
    val oldVersion: String,
    val changelog: String,
    val url: String,           // OTA 下载地址
    val force: Boolean,        // 是否强制更新
    val sizeBytes: Long = 0L,
)

/** 更新触发结果。 */
data class FirmwareUpdateResult(
    val ok: Boolean,
    val message: String,
)
