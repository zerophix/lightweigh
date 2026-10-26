package com.xcwk.scalehub.feature.sync

/**
 * 健康平台写入端口（T-059）。
 *
 * 本仓库没有 Health Connect / HealthKit 依赖，因此这里只定义**端口与可用性**，
 * 不做假实现：不可用时必须显式返回不可用，绝不允许"调用成功但什么都没写"。
 * 接入真实 SDK 时只需提供一个新的 [HealthPlatformPort] 实现并在组合根替换。
 */
enum class HealthPlatformAvailability {
    /** 设备或系统不支持（Android 无 Health Connect、iOS 无 HealthKit）。 */
    Unavailable,

    /** 支持但用户尚未授权。 */
    PermissionRequired,

    /** 已授权，可写入。 */
    Ready,
}

data class HealthSample(
    val epochMillis: Long,
    val weightKg: Double,
    val fatPercent: Double? = null,
)

sealed interface HealthWriteResult {
    data class Success(val written: Int) : HealthWriteResult
    data class Failure(val reason: String) : HealthWriteResult
    data object NotAvailable : HealthWriteResult
}

interface HealthPlatformPort {
    fun availability(): HealthPlatformAvailability
    suspend fun write(samples: List<HealthSample>): HealthWriteResult
}

/** 未接入任何健康平台时的默认实现：如实报告不可用。 */
class UnavailableHealthPlatform(
    private val reason: String = "当前版本未接入健康平台",
) : HealthPlatformPort {
    override fun availability(): HealthPlatformAvailability = HealthPlatformAvailability.Unavailable
    override suspend fun write(samples: List<HealthSample>): HealthWriteResult =
        HealthWriteResult.NotAvailable
}

/**
 * 是否应该向用户展示"同步到健康平台"入口。
 * 未接入时连入口都不出现，避免点进去只有一个失败提示。
 */
fun shouldShowHealthSync(availability: HealthPlatformAvailability): Boolean =
    availability != HealthPlatformAvailability.Unavailable

/** 是否可以立即写入：只有 Ready 才允许，PermissionRequired 必须先走授权。 */
fun canWriteNow(availability: HealthPlatformAvailability, userOptIn: Boolean): Boolean =
    availability == HealthPlatformAvailability.Ready && userOptIn
