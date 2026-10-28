package com.xcwk.scalehub.infrastructure.monitoring

import android.util.Log
import com.xcwk.scalehub.application.debug.DebugLog
import com.xcwk.scalehub.application.sync.CloudDataPort
import com.xcwk.scalehub.application.sync.CloudRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 测量成功率/延迟上报（T-1004）。
 *
 * 职责：
 *  - 记录每次测量操作的：开始时间、结果（成功/失败/超时/无效）、耗时
 *  - 聚合统计：成功率、平均耗时、P95 耗时、失败原因分布
 *  - 采样上报（默认 100%），批量上报（每累积 N 条或 N 秒上报一次）
 *  - 上报到 CloudBase /measure_metrics 表
 *
 * 使用方式：
 *  - 测量开始：MeasureMetricsReporter.onMeasureStart()
 *  - 测量结束：MeasureMetricsReporter.onMeasureEnd(result, durationMs)
 *  - 定时上报（或应用切后台时）：MeasureMetricsReporter.flush()
 */
class MeasureMetricsReporter(
    private val cloud: CloudDataPort? = null,
    private val samplingRate: Double = DEFAULT_SAMPLING_RATE,
) {

    companion object {
        private const val TAG = "MeasureMetricsReporter"
        private const val DEFAULT_SAMPLING_RATE = 1.0
        private const val MEASURE_METRICS_TABLE = "measure_metrics"

        private const val FLUSH_INTERVAL_MS = 60_000L
        private const val FLUSH_BATCH_SIZE = 50

        private const val PROP_SESSION_ID = "session_id"
        private const val PROP_RESULT = "result"
        private const val PROP_DURATION_MS = "duration_ms"
        private const val PROP_DEVICE_MAC = "device_mac"
        private const val PROP_ERROR_CODE = "error_code"
        private const val PROP_TIMESTAMP = "timestamp"
        private const val PROP_DEVICE_MODEL = "device_model"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ConcurrentHashMap<String, Any>()
    private val pending = mutableListOf<Map<String, Any?>>()
    private val pendingLock = Any()
    private var lastFlush = System.currentTimeMillis()

    /** 测量结果枚举。 */
    enum class MeasureResult {
        SUCCESS,
        FAIL_TIMEOUT,     // 测量超时
        FAIL_INVALID,     // 数据无效（阻抗异常等）
        FAIL_CONNECTION,  // 设备连接失败
        FAIL_SAVE,        // 保存失败
    }

    /**
     * 测量开始（记录 session ID）。
     * @return session ID，用于 end 匹配
     */
    fun onMeasureStart(deviceMac: String = ""): String {
        val sessionId = "m_${System.currentTimeMillis()}"
        queue[sessionId] = mapOf(
            PROP_SESSION_ID to sessionId,
            PROP_DEVICE_MAC to deviceMac,
            PROP_TIMESTAMP to System.currentTimeMillis(),
        )
        Log.d(TAG, "measure start: $sessionId mac=$deviceMac")
        return sessionId
    }

    /**
     * 测量结束（记录结果 + 耗时）。
     */
    fun onMeasureEnd(
        sessionId: String,
        result: MeasureResult,
        durationMs: Long,
        errorCode: String = "",
    ) {
        val startInfo = queue.remove(sessionId) as? Map<String, Any?> ?: run {
            Log.w(TAG, "measure end: unknown session $sessionId")
            return
        }

        if (Math.random() >= samplingRate) {
            return
        }

        val fields = HashMap<String, Any?>(startInfo as Map<String, Any?>)
        fields[PROP_RESULT] = result.name
        fields[PROP_DURATION_MS] = durationMs
        fields[PROP_ERROR_CODE] = errorCode
        fields[PROP_DEVICE_MODEL] = android.os.Build.MODEL

        DebugLog.add("monitoring", "measure: ${result.name} ${durationMs}ms")

        synchronized(pendingLock) {
            pending.add(fields)
        }

        // 自动 flush
        val now = System.currentTimeMillis()
        if (pending.size >= FLUSH_BATCH_SIZE || now - lastFlush > FLUSH_INTERVAL_MS) {
            flush()
        }
    }

    /**
     * 上报待发送的指标。
     */
    fun flush() {
        synchronized(pendingLock) {
            if (pending.isEmpty()) return
            val toSend = pending.toList()
            pending.clear()
            lastFlush = System.currentTimeMillis()

            scope.launch {
                val rows = toSend.map { CloudRow(it) }
                val ok = cloud?.upsert(
                    MEASURE_METRICS_TABLE,
                    "session_id",
                    rows,
                )
                if (ok == true) {
                    Log.i(TAG, "measure metrics uploaded: ${toSend.size}")
                } else {
                    Log.w(TAG, "measure metrics upload failed, ${toSend.size} records lost")
                    // TODO: 保存到本地，下次重试
                }
            }
        }
    }

    /** 获取当前待上报数量。 */
    fun pendingCount(): Int = synchronized(pendingLock) { pending.size }
}
