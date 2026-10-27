package com.xcwk.scalehub.infrastructure.health

import com.xcwk.scalehub.application.health.HealthPlatformPort
import com.xcwk.scalehub.application.health.HealthDataEntry
import com.xcwk.scalehub.application.health.PermissionResult
import com.xcwk.scalehub.application.health.WriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Apple HealthKit 平台实现（iOS 存根）。
 *
 * iOS 真实接入需要 Swift/Kotlin 多平台框架或 Kotlin/Native。
 * 当前为存根，标记为「暂不支持」。
 */
class AppleHealthPlatform : HealthPlatformPort {

    override fun isAvailable(): Boolean = false

    override suspend fun requestPermission(): PermissionResult {
        return PermissionResult(false, "Apple Health 仅在 iOS 设备上可用")
    }

    override suspend fun writeWeight(time: Long, weight: Double): WriteResult {
        return WriteResult(false, "Apple Health 仅在 iOS 设备上可用")
    }

    override suspend fun writeBodyFat(time: Long, fat: Double): WriteResult {
        return WriteResult(false, "Apple Health 仅在 iOS 设备上可用")
    }

    override suspend fun writeMuscleMass(time: Long, mass: Double): WriteResult {
        return WriteResult(false, "Apple Health 仅在 iOS 设备上可用")
    }

    override suspend fun writeSleep(start: Long, end: Long): WriteResult {
        return WriteResult(false, "Apple Health 仅在 iOS 设备上可用")
    }

    override suspend fun writeSteps(time: Long, count: Long): WriteResult {
        return WriteResult(false, "Apple Health 仅在 iOS 设备上可用")
    }

    override fun weightFlow(from: Long, to: Long): Flow<List<HealthDataEntry>> = emptyFlow()
    override fun sleepFlow(from: Long, to: Long): Flow<List<HealthDataEntry>> = emptyFlow()
    override fun stepsFlow(from: Long, to: Long): Flow<List<HealthDataEntry>> = emptyFlow()
}