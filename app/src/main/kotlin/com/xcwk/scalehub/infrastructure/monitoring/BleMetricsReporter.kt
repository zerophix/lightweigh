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
 * BLE 连接成功率/掉线率上报（T-1006）。
 *
 * 职责：
 *  - 记录 BLE 连接尝试、连接结果、连接时长、掉线事件
 *  - 聚合统计：连接成功率、平均连接耗时、掉线率、断开原因分布
 *  - 采样上报（默认 100%）
 *  - 上报到 CloudBase /ble_metrics 表
 *
 * 使用方式（在 AndroidBleLink / BleGate 中调用）：
 *  - 开始：BleMetricsReporter.onConnectStart(mac)
 *  - 结束：BleMetricsReporter.onConnectEnd(sessionId, success, durationMs, errorCode)
 *  - 掉线：BleMetricsReporter.onDisconnect(sessionId, disconnectReason)
 */
class BleMetricsReporter(
    private val cloud: CloudDataPort? = null,
    private val samplingRate: Double = DEFAULT_SAMPLING_RATE,
) {

    companion object {
        private const val TAG = "BleMetricsReporter"
        private const val DEFAULT_SAMPLING_RATE = 1.0
        private const val BLE_METRICS_TABLE = "ble_metrics"

        private const val FLUSH_INTERVAL_MS = 60_000L
        private const val FLUSH_BATCH_SIZE = 50

        private const val PROP_SESSION_ID = "session_id"
        private const val PROP_DEVICE_MAC = "device_mac"
        private const val PROP_EVENT = "event"          // CONNECT_START / CONNECT_END / DISCONNECT
        private const val PROP_SUCCESS = "success"
        private const val PROP_DURATION_MS = "duration_ms"
        private const val PROP_ERROR_CODE = "error_code"
        private const val PROP_DISCONNECT_REASON = "disconnect_reason"
        private const val PROP_DEVICE_MODEL = "device_model"
        private const val PROP_TIMESTAMP = "timestamp"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeSessions = ConcurrentHashMap<String, BleSession>()
    private val pending = mutableListOf<Map<String, Any?>>()
    private val pendingLock = Any()

    private var lastFlush = System.currentTimeMillis()

    /** 断开原因枚举。 */
    enum class DisconnectReason {
        REMOTE_USER_REQUESTED,   // 设备主动断开
        LOCAL_USER_REQUESTED,    // 用户主动断开
        CONNECTION_TIMEOUT,       // 连接超时
        CONNECTION_LOST,          // 信号丢失
        SERVICE_DISCOVERY_FAILED, // 服务发现失败
        UNEXPECTED,              // 其他
    }

    private data class BleSession(
        val id: String,
        val mac: String,
        val startTime: Long,
    )

    /**
     * 连接开始。
     * @return session ID
     */
    fun onConnectStart(mac: String): String {
        val sessionId = "ble_${System.currentTimeMillis()}"
        activeSessions[sessionId] = BleSession(sessionId, mac, System.currentTimeMillis())
        Log.d(TAG, "ble connect start: $sessionId mac=$mac")

        val fields = mapOf(
            PROP_SESSION_ID to sessionId,
            PROP_DEVICE_MAC to mac,
            PROP_EVENT to "CONNECT_START",
            PROP_TIMESTAMP to System.currentTimeMillis(),
            PROP_DEVICE_MODEL to android.os.Build.MODEL,
        )
        record(fields)
        return sessionId
    }

    /**
     * 连接结束（成功或失败）。
     */
    fun onConnectEnd(
        sessionId: String,
        success: Boolean,
        durationMs: Long,
        errorCode: String = "",
    ) {
        val session = activeSessions.remove(sessionId) ?: run {
            Log.w(TAG, "ble connect end: unknown session $sessionId")
            return
        }

        val fields = mapOf(
            PROP_SESSION_ID to sessionId,
            PROP_DEVICE_MAC to session.mac,
            PROP_EVENT to "CONNECT_END",
            PROP_SUCCESS to success,
            PROP_DURATION_MS to durationMs,
            PROP_ERROR_CODE to errorCode,
            PROP_TIMESTAMP to session.startTime,
            PROP_DEVICE_MODEL to android.os.Build.MODEL,
        )

        DebugLog.add("monitoring", "ble: ${if (success) "connected" else "failed"} ${durationMs}ms")
        record(fields)
    }

    /**
     * 断开连接。
     */
    fun onDisconnect(
        sessionId: String,
        reason: DisconnectReason,
        durationMs: Long = 0,
    ) {
        val session = activeSessions[sessionId]
        val mac = session?.mac ?: "unknown"

        val fields = mapOf(
            PROP_SESSION_ID to sessionId,
            PROP_DEVICE_MAC to mac,
            PROP_EVENT to "DISCONNECT",
            PROP_SUCCESS to false,
            PROP_DURATION_MS to durationMs,
            PROP_DISCONNECT_REASON to reason.name,
            PROP_TIMESTAMP to System.currentTimeMillis(),
            PROP_DEVICE_MODEL to android.os.Build.MODEL,
        )

        DebugLog.add("monitoring", "ble: disconnected reason=${reason.name}")
        record(fields)
    }

    /**
     * 记录一条事件到待上报队列。
     */
    private fun record(fields: Map<String, Any?>) {
        if (Math.random() >= samplingRate) return

        synchronized(pendingLock) {
            pending.add(fields)
        }

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
                    BLE_METRICS_TABLE,
                    "session_id",
                    rows,
                )
                if (ok == true) {
                    Log.i(TAG, "ble metrics uploaded: ${toSend.size}")
                } else {
                    Log.w(TAG, "ble metrics upload failed, ${toSend.size} records lost")
                }
            }
        }
    }

    /** 获取当前待上报数量。 */
    fun pendingCount(): Int = synchronized(pendingLock) { pending.size }
}
