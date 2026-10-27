package com.xcwk.scalehub.application.device

/**
 * application service — 固件管理编排。
 *
 * 职责：
 *  - 检查设备固件是否有更新
 *  - 比较版本（语义化版本）
 *  - 触发 OTA 更新
 *  - 返回状态结果
 *
 * 依赖 FirmwareCheckPort（厂商固件服务），真实 OTA 能力需外部依赖。
 */
class FirmwareManagerService(
    private val port: FirmwareCheckPort?,
) {

    /** 检查是否有固件更新可用。 */
    suspend fun checkUpdate(deviceId: String): FirmwareCheckResult {
        val current = port?.currentVersion(deviceId) ?: ""
        val update = port?.checkUpdate(deviceId)
        return if (update != null) {
            FirmwareCheckResult(
                hasUpdate = true,
                currentVersion = current,
                latestVersion = update.version,
                changelog = update.changelog,
                force = update.force,
                sizeBytes = update.sizeBytes,
            )
        } else {
            FirmwareCheckResult(
                hasUpdate = false,
                currentVersion = current,
                latestVersion = current,
                changelog = "",
                force = false,
                sizeBytes = 0L,
            )
        }
    }

    /** 触发 OTA 更新。 */
    suspend fun update(deviceId: String, update: FirmwareUpdate): FirmwareUpdateResult {
        val p = port ?: return FirmwareUpdateResult(false, "固件服务不可用")
        return p.triggerUpdate(deviceId, update)
    }

    /** 批量检查多设备固件。 */
    suspend fun checkAll(deviceIds: List<String>): List<FirmwareCheckResult> {
        return deviceIds.map { checkUpdate(it) }
    }
}

/** 固件检查结果。 */
data class FirmwareCheckResult(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val changelog: String,
    val force: Boolean,
    val sizeBytes: Long,
)
