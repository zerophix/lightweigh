package com.xcwk.scalehub.application.health

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * application service — Apple HealthKit 集成编排。
 *
 * 职责：
 *  - 向 Apple HealthKit 写入体重/体脂/肌肉量/睡眠/步数
 *  - 从 Apple HealthKit 读取数据（响应式流）
 *  - 管理权限请求
 *
 * 依赖 HealthPlatformPort 接口实现；仅 iOS 设备可用。
 * 与 HealthConnectService 共享同一端口接口。
 */
class AppleHealthService(
    private val port: HealthPlatformPort?,
) {

    /** 是否可用（iOS 设备）。 */
    fun isAvailable(): Boolean = port?.isAvailable() ?: false

    /** 请求 HealthKit 权限。 */
    suspend fun requestPermission(): PermissionResult {
        val p = port ?: return PermissionResult(false, "Apple Health 不可用")
        return p.requestPermission()
    }

    /** 写入测量结果到 Apple HealthKit。 */
    suspend fun writeMeasurement(
        time: Long,
        weight: Double,
        fatRate: Double?,
        muscleMass: Double?,
    ): HealthWriteResult {
        if (port == null) return HealthWriteResult(false, "Apple Health 不可用")
        val results = mutableListOf<WriteResult>()

        val weightResult = port.writeWeight(time, weight)
        results.add(weightResult)
        if (!weightResult.ok) {
            return HealthWriteResult(false, "写入体重失败：${weightResult.message}")
        }

        if (fatRate != null) {
            val fatResult = port.writeBodyFat(time, fatRate)
            results.add(fatResult)
            if (!fatResult.ok) {
                return HealthWriteResult(false, "写入体脂率失败：${fatResult.message}")
            }
        }

        if (muscleMass != null) {
            val muscleResult = port.writeMuscleMass(time, muscleMass)
            results.add(muscleResult)
            if (!muscleResult.ok) {
                return HealthWriteResult(false, "写入肌肉量失败：${muscleResult.message}")
            }
        }

        return HealthWriteResult(true, "写入 Apple Health 成功", results)
    }

    /** 读取体重数据流。 */
    fun weightFlow(from: Long, to: Long): Flow<List<HealthDataEntry>> {
        return port?.weightFlow(from, to) ?: emptyFlow()
    }

    /** 读取睡眠数据流。 */
    fun sleepFlow(from: Long, to: Long): Flow<List<HealthDataEntry>> {
        return port?.sleepFlow(from, to) ?: emptyFlow()
    }

    /** 读取步数数据流。 */
    fun stepsFlow(from: Long, to: Long): Flow<List<HealthDataEntry>> {
        return port?.stepsFlow(from, to) ?: emptyFlow()
    }
}
