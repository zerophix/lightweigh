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
 * 同步成功率/冲突率上报（T-1005）。
 *
 * 职责：
 *  - 记录每次同步操作：开始时间、方向（push/pull/both）、结果、耗时、冲突数量
 *  - 聚合统计：同步成功率、平均耗时、冲突率、冲突解决策略分布
 *  - 采样上报（默认 100%）
 *  - 上报到 CloudBase /sync_metrics 表
 *
 * 使用方式（在 SyncService 中调用）：
 *  - 开始：SyncMetricsReporter.onSyncStart(direction)
 *  - 结束：SyncMetricsReporter.onSyncEnd(sessionId, result, durationMs, conflictCount, strategy)
 */
class SyncMetricsReporter(
    private val cloud: CloudDataPort? = null,
    private val samplingRate: Double = DEFAULT_SAMPLING_RATE,
) {

    companion object {
        private const val TAG = "SyncMetricsReporter"
        private const val DEFAULT_SAMPLING_RATE = 1.0
        private const val SYNC_METRICS_TABLE = "sync_metrics"

        private const val PROP_SESSION_ID = "session_id"
        private const val PROP_DIRECTION = "direction"
        private const val PROP_RESULT = "result"
        private const val PROP_DURATION_MS = "duration_ms"
        private const val PROP_CONFLICT_COUNT = "conflict_count"
        private const val PROP_STRATEGY = "strategy"
        private const val PROP_DEVICE_MODEL = "device_model"
        private const val PROP_TIMESTAMP = "timestamp"

        private const val FLUSH_INTERVAL_MS = 60_000L
        private const val FLUSH_BATCH_SIZE = 50
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeSessions = ConcurrentHashMap<String, SyncSession>()
    private val pending = mutableListOf<Map<String, Any?>>()
    private val pendingLock = Any()
    private var lastFlush = System.currentTimeMillis()

    /** 同步方向。 */
    enum class SyncDirection { PUSH, PULL, BOTH }

    /** 同步结果。 */
    enum class SyncResult {
        SUCCESS,
        FAIL_NETWORK,
        FAIL_AUTH,
        FAIL_CONFLICT,
        FAIL_SERVER,
        FAIL_TIMEOUT,
    }

    /** 冲突解决策略（与 ConflictResolutionStrategy 对应）。 */
    enum class ConflictStrategy {
        LOCAL_WINS, CLOUD_WINS, MERGE, MANUAL, NONE
    }

    private data class SyncSession(
        val id: String,
        val direction: SyncDirection,
        val startTime: Long,
        val startNanos: Long,
    )

    /**
     * 同步开始。
     * @return session ID
     */
    fun onSyncStart(direction: SyncDirection): String {
        val sessionId = "s_${System.currentTimeMillis()}"
        activeSessions[sessionId] = SyncSession(sessionId, direction, System.currentTimeMillis(), System.nanoTime())
        Log.d(TAG, "sync start: $sessionId dir=${direction.name}")
        return sessionId
    }

    /**
     * 同步结束。
     */
    fun onSyncEnd(
        sessionId: String,
        result: SyncResult,
        durationMs: Long,
        conflictCount: Int = 0,
        strategy: ConflictStrategy = ConflictStrategy.NONE,
    ) {
        val session = activeSessions.remove(sessionId) ?: run {
            Log.w(TAG, "sync end: unknown session $sessionId")
            return
        }

        if (Math.random() >= samplingRate) {
            return
        }

        val fields = mapOf(
            PROP_SESSION_ID to sessionId,
            PROP_DIRECTION to session.direction.name,
            PROP_RESULT to result.name,
            PROP_DURATION_MS to durationMs,
            PROP_CONFLICT_COUNT to conflictCount,
            PROP_STRATEGY to strategy.name,
            PROP_DEVICE_MODEL to android.os.Build.MODEL,
            PROP_TIMESTAMP to session.startTime,
        )

        DebugLog.add("monitoring", "sync: ${result.name} ${durationMs}ms conflicts=$conflictCount")

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
                    SYNC_METRICS_TABLE,
                    "session_id",
                    rows,
                )
                if (ok == true) {
                    Log.i(TAG, "sync metrics uploaded: ${toSend.size}")
                } else {
                    Log.w(TAG, "sync metrics upload failed, ${toSend.size} records lost")
                }
            }
        }
    }

    /** 获取当前待上报数量。 */
    fun pendingCount(): Int = synchronized(pendingLock) { pending.size }
}
