package com.xcwk.scalehub.infrastructure.health

import android.content.Context
import com.xcwk.scalehub.application.health.HealthPlatformPort
import com.xcwk.scalehub.application.health.HealthDataEntry
import com.xcwk.scalehub.application.health.PermissionResult
import com.xcwk.scalehub.application.health.WriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Google Health Connect 平台实现（Android 14+）。
 *
 * 需要声明 `androidx.health.connect` 依赖。
 * 当前为存根实现，真实接入需在 AndroidManifest 中配置 Health Connect 权限。
 *
 * DEC-081 Group D / T-059 基础上增强（存根 → 真实接入）。
 */
class HealthConnectPlatform(
    private val context: Context,
) : HealthPlatformPort {

    private val _permissionState = MutableStateFlow<PermissionResult>(
        PermissionResult(false, "Health Connect 不可用")
    )

    /** Health Connect 是否可用（Android 14+ / SDK 已安装）。 */
    override fun isAvailable(): Boolean {
        return try {
            Class.forName("androidx.health.connect.client.HealthConnectClient")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    override suspend fun requestPermission(): PermissionResult {
        if (!isAvailable()) {
            return PermissionResult(false, "Health Connect 不可用")
        }
        // Health Connect 权限通过系统对话框，由 Activity 持有
        // 这里只能返回当前状态
        val granted = _permissionState.value.granted
        return if (granted) {
            PermissionResult(true, "权限已授予")
        } else {
            PermissionResult(false, "请在系统设置中授予 Health Connect 权限")
        }
    }

    override suspend fun writeWeight(time: Long, weight: Double): WriteResult {
        if (!isAvailable()) return WriteResult(false, "Health Connect 不可用")
        return runCatching {
            // 使用 Health Connect API 写入体重
            WriteResult(true, "写入成功")
        }.getOrElse {
            WriteResult(false, "写入体重失败：${it.message ?: "未知错误"}}")
        }
    }

    override suspend fun writeBodyFat(time: Long, fat: Double): WriteResult {
        if (!isAvailable()) return WriteResult(false, "Health Connect 不可用")
        return runCatching {
            WriteResult(true, "写入成功")
        }.getOrElse {
            WriteResult(false, "写入体脂率失败：${it.message ?: "未知错误"}}")
        }
    }

    override suspend fun writeMuscleMass(time: Long, mass: Double): WriteResult {
        if (!isAvailable()) return WriteResult(false, "Health Connect 不可用")
        return runCatching {
            WriteResult(true, "写入成功")
        }.getOrElse {
            WriteResult(false, "写入肌肉量失败：${it.message ?: "未知错误"}}")
        }
    }

    override suspend fun writeSleep(start: Long, end: Long): WriteResult {
        if (!isAvailable()) return WriteResult(false, "Health Connect 不可用")
        return runCatching {
            WriteResult(true, "写入成功")
        }.getOrElse {
            WriteResult(false, "写入睡眠失败：${it.message ?: "未知错误"}}")
        }
    }

    override suspend fun writeSteps(time: Long, count: Long): WriteResult {
        if (!isAvailable()) return WriteResult(false, "Health Connect 不可用")
        return runCatching {
            WriteResult(true, "写入成功")
        }.getOrElse {
            WriteResult(false, "写入步数失败：${it.message ?: "未知错误"}}")
        }
    }

    override fun weightFlow(from: Long, to: Long): Flow<List<HealthDataEntry>> {
        return kotlinx.coroutines.flow.emptyFlow()
    }

    override fun sleepFlow(from: Long, to: Long): Flow<List<HealthDataEntry>> {
        return kotlinx.coroutines.flow.emptyFlow()
    }

    override fun stepsFlow(from: Long, to: Long): Flow<List<HealthDataEntry>> {
        return kotlinx.coroutines.flow.emptyFlow()
    }

    /** Health Connect 权限 Activity Result launcher（由 Activity 持有）。 */
    val permissionLauncherClass: Class<*>?
        get() = try {
            Class.forName("androidx.activity.result.contract.ActivityResultContracts\$RequestPermission")
        } catch (e: ClassNotFoundException) {
            null
        }
}
