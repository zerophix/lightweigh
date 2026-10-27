package com.xcwk.scalehub.application.health

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * application service — Google Health Connect 集成编排。
 *
 * 职责：
 *  - 向 Google Health Connect 写入体重/体脂/肌肉量/睡眠/步数
 *  - 从 Google Health Connect 读取数据（响应式流）
 *  - 管理权限请求
 *  - 处理写入/读取错误
 *
 * 依赖 HealthPlatformPort 接口实现；真实接入需 Android 权限。
 * 仅 Android 14+ 设备可用。
 */
class HealthConnectService(
    private val port: HealthPlatformPort?,
) {

    /** 是否可用。 */
    fun isAvailable(): Boolean = port?.isAvailable() ?: false

    /** 请求 Health Connect 权限。 */
    suspend fun requestPermission(): PermissionResult {
        val p = port ?: return PermissionResult(false, "Health Connect 不可用")
        return p.requestPermission()
    }

    /** 写入测量结果到 Health Connect。 */
    suspend fun writeMeasurement(
        time: Long,
        weight: Double,
        fatRate: Double?,
        muscleMass: Double?,
    ): HealthWriteResult {
        if (port == null) return HealthWriteResult(false, "Health Connect 不可用")
        val results = mutableListOf<WriteResult>()

        // 体重
        val weightResult = port.writeWeight(time, weight)
        results.add(weightResult)
        if (!weightResult.ok) {
            return HealthWriteResult(false, "写入体重失败：${weightResult.message}")
        }

        // 体脂率
        if (fatRate != null) {
            val fatResult = port.writeBodyFat(time, fatRate)
            results.add(fatResult)
            if (!fatResult.ok) {
                return HealthWriteResult(false, "写入体脂率失败：${fatResult.message}")
            }
        }

        // 肌肉量
        if (muscleMass != null) {
            val muscleResult = port.writeMuscleMass(time, muscleMass)
            results.add(muscleResult)
            if (!muscleResult.ok) {
                return HealthWriteResult(false, "写入肌肉量失败：${muscleResult.message}")
            }
        }

        return HealthWriteResult(true, "写入健康平台成功", results)
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

    /** 同步历史到 Health Connect。 */
    suspend fun syncHistory(entries: List<MeasurementForHealth>): HealthSyncResult {
        val p = port ?: return HealthSyncResult(false, "Health Connect 不可用", 0)
        var success = 0
        val failures = mutableListOf<String>()
        for (entry in entries) {
            val result = writeMeasurement(entry.time, entry.weight, entry.fatRate, entry.muscleMass)
            if (result.ok) success++ else failures.add("time=${entry.time}: ${result.message}")
        }
        return HealthSyncResult(
            ok = success > 0,
            message = "同步 $success/${entries.size} 条记录",
            successCount = success,
            failures = failures,
        )
    }
}

/** 写入结果汇总。 */
data class HealthWriteResult(
    val ok: Boolean,
    val message: String,
    val details: List<WriteResult> = emptyList(),
)

/** 健康平台同步结果。 */
data class HealthSyncResult(
    val ok: Boolean,
    val message: String,
    val successCount: Int,
    val failures: List<String> = emptyList(),
)

/** Health Connect 测量条目。 */
data class MeasurementForHealth(
    val time: Long,
    val weight: Double,
    val fatRate: Double?,
    val muscleMass: Double?,
)
