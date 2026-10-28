package com.xcwk.scalehub.application.health

import kotlinx.coroutines.flow.Flow

/**
 * 健康平台端口（M3）—— 抽象 Google Health Connect / Apple HealthKit 读写。
 *
 * 统一接口让 HealthConnectService 与 AppleHealthService 共享编排逻辑；
 * 应用层只编排语义，平台接入由实现层决定。
 */
interface HealthPlatformPort {
    /** 是否可用（Google Health Connect 需 Android 14+ / Apple HealthKit 需 iOS）。 */
    fun isAvailable(): Boolean

    /** 请求权限。 */
    suspend fun requestPermission(): PermissionResult

    /** 写入体重数据。 */
    suspend fun writeWeight(time: Long, weight: Double): WriteResult

    /** 写入体脂率数据。 */
    suspend fun writeBodyFat(time: Long, fat: Double): WriteResult

    /** 写入肌肉量数据。 */
    suspend fun writeMuscleMass(time: Long, mass: Double): WriteResult

    /** 写入睡眠数据。 */
    suspend fun writeSleep(start: Long, end: Long): WriteResult

    /** 写入步骤数据。 */
    suspend fun writeSteps(time: Long, count: Long): WriteResult

    /** 读取体重数据流。 */
    fun weightFlow(from: Long, to: Long): Flow<List<HealthDataEntry>>

    /** 读取睡眠数据流。 */
    fun sleepFlow(from: Long, to: Long): Flow<List<HealthDataEntry>>

    /** 读取步骤数据流。 */
    fun stepsFlow(from: Long, to: Long): Flow<List<HealthDataEntry>>
}

/** 权限请求结果。 */
data class PermissionResult(
    val granted: Boolean,
    val message: String,
)

/** 写入结果。 */
data class WriteResult(
    val ok: Boolean,
    val message: String,
    val id: String? = null,
)

/** 健康数据条目。 */
data class HealthDataEntry(
    val time: Long,
    val value: Double,
    val unit: String,
    val source: String? = null,
)
