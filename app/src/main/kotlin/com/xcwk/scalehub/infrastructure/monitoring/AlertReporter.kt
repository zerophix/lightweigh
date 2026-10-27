package com.xcwk.scalehub.infrastructure.monitoring

import android.util.Log
import com.xcwk.scalehub.application.debug.DebugLog
import com.xcwk.scalehub.application.sync.CloudDataPort
import com.xcwk.scalehub.application.sync.CloudRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 客户端侧告警上报（T-1009）。
 *
 * 职责：
 *  - 在客户端检测到需要告警的事件，上报到 CloudBase /client_alerts 表
 *  - 告警类型：本地错误率过高、采样失败、同步连续失败等
 *  - 去重 + 冷却：相同告警 ID 在 COOLDOWN_MS 内不重复上报
 *  - 采样：高频告警按采样率上报
 *
 * 使用方式：
 *  - AlertReporter.report(AlertType.MEASURE_FAIL_RATE_HIGH, "success rate 92%")
 *  - AlertReporter.report(AlertType.SYNC_CONSECUTIVE_FAIL, "5 failures in 2 min")
 */
class AlertReporter(
    private val cloud: CloudDataPort? = null,
    private val samplingRate: Double = DEFAULT_SAMPLING_RATE,
) {

    companion object {
        private const val TAG = "AlertReporter"
        private const val DEFAULT_SAMPLING_RATE = 1.0
        private const val CLIENT_ALERTS_TABLE = "client_alerts"

        /** 告警冷却（毫秒）：相同告警 ID 在此时间内不重复上报。 */
        private const val COOLDOWN_MS = 300_000L  // 5 分钟

        private const val PROP_ALERT_ID = "alert_id"
        private const val PROP_ALERT_TYPE = "alert_type"
        private const val PROP_MESSAGE = "message"
        private const val PROP_SEVERITY = "severity"
        private const val PROP_DEVICE_MODEL = "device_model"
        private const val PROP_SDK_INT = "sdk_int"
        private const val PROP_TIMESTAMP = "timestamp"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastReportTime = mutableMapOf<String, Long>()
    private val lock = Any()

    /** 告警类型枚举。 */
    enum class AlertType(
        val severity: String,
        val defaultMessage: String,
    ) {
        MEASURE_FAIL_RATE_HIGH("P1", "测量失败率升高"),
        MEASURE_TIMEOUT_HIGH("P1", "测量超时率升高"),
        SYNC_CONSECUTIVE_FAIL("P1", "同步连续失败"),
        BLE_CONNECT_FAIL_HIGH("P1", "BLE 连接失败率升高"),
        BLE_DISCONNECT_HIGH("P1", "BLE 掉线率升高"),
        MEMORY_PRESSURE("P2", "内存压力"),
        DISK_FULL("P2", "存储空间不足"),
        NETWORK_UNSTABLE("P2", "网络不稳定"),
        ;
    }

    /**
     * 上报客户端告警。
     * @param type 告警类型
     * @param message 告警详情
     * @param extra 额外信息
     */
    fun report(
        type: AlertType,
        message: String = type.defaultMessage,
        extra: Map<String, Any?> = emptyMap(),
    ) {
        // 冷却检查
        val now = System.currentTimeMillis()
        val alertId = "${type.name}_${type.severity}"
        synchronized(lock) {
            val lastTime = lastReportTime[alertId]
            if (lastTime != null && now - lastTime < COOLDOWN_MS) {
                Log.d(TAG, "alert $alertId in cooldown, skip")
                return
            }
            lastReportTime[alertId] = now
        }

        // 采样
        if (Math.random() >= samplingRate) {
            Log.d(TAG, "alert $alertId sampled out")
            return
        }

        val fields = HashMap<String, Any?>().apply {
            put(PROP_ALERT_ID, alertId)
            put(PROP_ALERT_TYPE, type.name)
            put(PROP_MESSAGE, sanitizeMessage(message))
            put(PROP_SEVERITY, type.severity)
            put(PROP_DEVICE_MODEL, android.os.Build.MODEL)
            put(PROP_SDK_INT, android.os.Build.VERSION.SDK_INT)
            put(PROP_TIMESTAMP, now)
            putAll(extra)
        }

        DebugLog.add("monitoring", "alert: ${type.name} msg=$message")

        scope.launch {
            val result = cloud?.upsert(
                CLIENT_ALERTS_TABLE,
                "alert_id",
                listOf(CloudRow(fields)),
            )
            if (result == true) {
                Log.i(TAG, "alert uploaded: ${type.name}")
            } else {
                Log.w(TAG, "alert upload failed (offline?)")
            }
        }
    }

    /**
     * 过滤敏感信息。
     */
    private fun sanitizeMessage(msg: String): String {
        var sanitized = msg
        val patterns = listOf(
            "[0-9]{11}".toRegex(),  // 手机号
            "token=[^,}\\s]+".toRegex(),
            "access_token=[^,}\\s]+".toRegex(),
        )
        for (p in patterns) {
            sanitized = sanitized.replace(p, "[REDACTED]")
        }
        // 限制长度
        return if (sanitized.length > 500) sanitized.take(500) + "..." else sanitized
    }
}
