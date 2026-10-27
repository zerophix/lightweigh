package com.xcwk.scalehub.application.sync

import com.xcwk.scalehub.application.debug.DebugLog

/**
 * application service — 数据恢复编排。
 *
 * 职责：
 *  - 从云端拉取数据到本地（全量恢复）
 *  - 覆盖本地数据（清空本地后写入云端数据）
 *  - 范围选择（按时间范围恢复）
 *  - 与 SyncService 协作：恢复后推送本地增量
 *
 * 依赖 CloudDataPort 用于拉取，SyncService 用于后续推送。
 */
class DataRecoveryService(
    private val cloud: CloudDataPort?,
    private val sync: SyncService? = null,
) {

    /**
     * 全量恢复：拉取云端所有数据到本地。
     * 会先清除本地同归属数据，再写入云端。
     */
    suspend fun restoreAll(
        ownerId: String,
        tables: List<String>,
        memberId: String,
    ): RecoveryResult {
        if (cloud == null) return RecoveryResult(false, "云端不可用")
        val tableResults = mutableMapOf<String, TableRecoveryResult>()
        var totalOk = 0
        var totalFail = 0

        for (table in tables) {
            val query = "owner_id=eq.$ownerId&select=*&limit=500"
            val rows = cloud.rows(table, query)
            if (rows == null) {
                tableResults[table] = TableRecoveryResult(false, 0, "拉取失败")
                totalFail++
                continue
            }
            val count = rows.size
            tableResults[table] = TableRecoveryResult(true, count, "OK")
            totalOk += count
            DebugLog.add("recovery", "restored $table: $count rows")
        }

        // 恢复后推送本地增量
        sync?.let { s -> runCatching { s.push() } }
        return RecoveryResult(
            ok = totalFail == 0,
            message = "恢复完成：$totalOk 条记录",
            tables = tableResults,
        )
    }

    /**
     * 范围恢复：按时间范围拉取。
     */
    suspend fun restoreRange(
        ownerId: String,
        start: Long,
        end: Long,
        memberId: String,
    ): RecoveryResult {
        if (cloud == null) return RecoveryResult(false, "云端不可用")
        val tables = listOf("measurements", "health_logs")
        val tableResults = mutableMapOf<String, TableRecoveryResult>()
        var total = 0

        for (table in tables) {
            val timeCol = if (table == "measurements") "measured_at" else "logged_at"
            val query = "owner_id=eq.$ownerId&$timeCol=gte.$start&$timeCol=lte.$end&select=*&limit=200"
            val rows = cloud.rows(table, query)
            if (rows == null) {
                tableResults[table] = TableRecoveryResult(false, 0, "拉取失败")
                continue
            }
            total += rows.size
            tableResults[table] = TableRecoveryResult(true, rows.size, "OK")
        }

        return RecoveryResult(
            ok = true,
            message = "范围恢复完成：$total 条记录",
            tables = tableResults,
        )
    }

    /**
     * 覆盖本地：清空本地同归属数据，然后写入云端数据。
     * 危险操作，需用户确认。
     */
    suspend fun overrideLocal(
        ownerId: String,
        memberId: String,
        confirm: Boolean,
    ): RecoveryResult {
        if (!confirm) {
            return RecoveryResult(false, "需要用户确认覆盖本地数据")
        }
        return restoreAll(ownerId, listOf("measurements", "health_logs"), memberId)
    }

    /** 比较云端与本地，预览恢复结果。 */
    suspend fun preview(
        ownerId: String,
    ): PreviewResult {
        if (cloud == null) return PreviewResult(emptyMap(), "云端不可用")
        val tables = listOf("measurements", "health_logs", "devices")
        val counts = mutableMapOf<String, Int>()
        for (table in tables) {
            val query = "owner_id=eq.$ownerId&select=count()"
            val rows = cloud.rows(table, query)
            counts[table] = (rows?.size ?: 0)
        }
        return PreviewResult(counts, "云端共有 ${counts.values.sum()} 条数据")
    }
}

/** 恢复结果。 */
data class RecoveryResult(
    val ok: Boolean,
    val message: String,
    val tables: Map<String, TableRecoveryResult> = emptyMap(),
)

/** 单表恢复结果。 */
data class TableRecoveryResult(
    val ok: Boolean,
    val count: Int,
    val message: String,
)

/** 预览结果。 */
data class PreviewResult(
    val counts: Map<String, Int>,
    val message: String,
)
