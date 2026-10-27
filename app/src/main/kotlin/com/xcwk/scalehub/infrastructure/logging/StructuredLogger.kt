package com.xcwk.scalehub.infrastructure.logging

import android.util.Log
import com.xcwk.scalehub.application.debug.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 结构化日志（T-1015）。
 *
 * 职责：
 *  - 提供结构化日志接口（键值对格式），便于 CLS 采集与查询
 *  - 采样控制：高频日志按采样率上报，避免流量爆炸
 *  - 敏感信息过滤：手机号/验证码/token 自动脱敏
 *  - 与 [DebugLog] 共享缓冲，UI 悬浮面板可查看
 *
 * 使用方式：
 *  - StructuredLogger.info("measure_start", mapOf("mac" to "AA:BB"))
 *  - StructuredLogger.error("sync_fail", mapOf("error" to "network"), exception)
 */
class StructuredLogger(
    private val cloud: com.xcwk.scalehub.application.sync.CloudDataPort? = null,
) {

    companion object {
        private const val TAG = "StructuredLogger"
        private const val LOG_TABLE = "structured_logs"

        /** 敏感信息正则。 */
        private val SENSITIVE_PATTERNS = listOf(
            "token=[^,&\\s]+".toRegex(),
            "access_token=[^,&\\s]+".toRegex(),
            "refresh_token=[^,&\\s]+".toRegex(),
            "[0-9]{11}".toRegex(),
            "code=[^,&\\s]+".toRegex(),
            "password=[^,&\\s]+".toRegex(),
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 日志等级。 */
    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    /** 配置。 */
    data class Config(
        val samplingRate: Double = 1.0,
        val minLevel: LogLevel = LogLevel.DEBUG,
        val enableCloud: Boolean = true,
    )

    private var config = Config()

    /** 更新配置。 */
    fun updateConfig(newConfig: Config) {
        config = newConfig
    }

    /** 记录日志。 */
    fun log(
        level: LogLevel,
        tag: String,
        fields: Map<String, Any?> = emptyMap(),
        error: Throwable? = null,
    ) {
        if (Math.random() >= config.samplingRate) return

        val sanitized = sanitize(fields)
        val message = buildString {
            append(tag)
            sanitized.forEach { (k, v) -> append(" $k=$v") }
        }

        DebugLog.add(tag, message)

        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message, error)
        }

        if (config.enableCloud && cloud != null) {
            val fullFields = HashMap<String, Any?>(sanitized).apply {
                put("level", level.name)
                put("tag", tag)
                put("error_stack", error?.stackTraceToString()?.take(50_000))
                put("timestamp", System.currentTimeMillis())
                put("device_model", android.os.Build.MODEL)
                put("sdk_int", android.os.Build.VERSION.SDK_INT)
            }

            scope.launch {
                cloud.upsert(
                    LOG_TABLE,
                    "timestamp",
                    listOf(com.xcwk.scalehub.application.sync.CloudRow(fullFields)),
                )
            }
        }
    }

    fun debug(tag: String, fields: Map<String, Any?> = emptyMap()) =
        log(LogLevel.DEBUG, tag, fields)

    fun info(tag: String, fields: Map<String, Any?> = emptyMap()) =
        log(LogLevel.INFO, tag, fields)

    fun warn(tag: String, fields: Map<String, Any?> = emptyMap()) =
        log(LogLevel.WARN, tag, fields)

    fun error(tag: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) =
        log(LogLevel.ERROR, tag, fields, error)

    private fun sanitize(fields: Map<String, Any?>): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        for ((key, value) in fields) {
            if (value is String) {
                var sanitized: String = value
                for (pattern in SENSITIVE_PATTERNS) {
                    sanitized = sanitized.replace(pattern, "[REDACTED]")
                }
                result[key] = sanitized
            } else {
                result[key] = value
            }
        }
        return result
    }
}
