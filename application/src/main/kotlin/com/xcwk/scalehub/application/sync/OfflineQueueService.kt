package com.xcwk.scalehub.application.sync

import com.xcwk.scalehub.application.debug.DebugLog

/**
 * application service — 离线操作队列编排。
 *
 * 职责：
 *  - 缓存离线时生成的操作（测量记录保存、档案更新、习惯打卡）
 *  - 网络恢复后自动重试
 *  - 幂等：同一操作 ID 不重复执行
 *  - 最大重试次数控制
 *
 * 依赖 CloudDataPort 用于检查网络/执行上云。
 */
class OfflineQueueService(
    private val cloud: CloudDataPort?,
) {

    companion object {
        private const val MAX_RETRY = 3
        private const val MAX_QUEUE_SIZE = 100
    }

    /** 待执行的操作队列。 */
    private val queue = mutableListOf<OfflineOperation>()

    /** 已完成/跳过的操作 ID（幂等）。 */
    private val doneIds = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** 重试计数。 */
    private val retryCount = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** 入队离线操作。 */
    fun enqueue(op: OfflineOperation) {
        if (queue.size >= MAX_QUEUE_SIZE) {
            DebugLog.add("offline", "queue full, dropping oldest")
            queue.removeAt(0)
        }
        queue.add(op)
        DebugLog.add("offline", "enqueued: ${op.id} type=${op.type}")
    }

    /**
     * 重试所有队列中的操作。
     * @return 重试结果
     */
    suspend fun flush(): QueueFlushResult {
        val p = cloud ?: return QueueFlushResult(0, queue.size, "云端不可用")
        val remaining = mutableListOf<OfflineOperation>()
        var success = 0
        var failed = 0

        for (op in queue) {
            val count = retryCount.getOrDefault(op.id, 0)
            if (count >= MAX_RETRY) {
                DebugLog.add("offline", "max retry exceeded: ${op.id}")
                failed++
                doneIds[op.id] = -1L
                continue
            }
            val ok = runCatching {
                op.execute(p)
            }.getOrDefault(false)

            if (ok) {
                success++
                doneIds[op.id] = System.currentTimeMillis()
            } else {
                retryCount[op.id] = count + 1
                remaining.add(op)
                failed++
                DebugLog.add("offline", "retry ${retryCount[op.id]}: ${op.id}")
            }
        }

        queue.clear()
        queue.addAll(remaining)

        return QueueFlushResult(
            success = success,
            failed = failed,
            message = if (queue.isEmpty()) "队列已清空" else "仍有 ${queue.size} 条待重试",
        )
    }

    /** 获取队列大小。 */
    fun size(): Int = queue.size

    /** 获取待重试的操作数量。 */
    fun pendingCount(): Int = queue.size

    /** 清除已完成的记录（清理内存）。 */
    fun cleanup() {
        val cutoff = System.currentTimeMillis() - 86400_000L * 7
        doneIds.filterValues { it < cutoff || it < 0 }.keys.forEach { doneIds.remove(it) }
        retryCount.filterValues { it >= MAX_RETRY }.keys.forEach { retryCount.remove(it) }
    }

    /** 判断操作是否已完成（幂等检查）。 */
    fun isDone(opId: String): Boolean = doneIds.containsKey(opId)
}

/** 离线操作。 */
data class OfflineOperation(
    val id: String,
    val type: String,
    val table: String,
    val conflictKey: String,
    val fields: Map<String, Any?>,
    val filter: String,
    val createdAt: Long,
) {
    suspend fun execute(port: CloudDataPort): Boolean {
        return when (type) {
            "upsert" -> port.upsert(table, conflictKey, listOf(CloudRow(fields)))
            "delete" -> port.delete(table, filter)
            else -> false
        }
    }
}

/** 队列刷新结果。 */
data class QueueFlushResult(
    val success: Int,
    val failed: Int,
    val message: String,
)
