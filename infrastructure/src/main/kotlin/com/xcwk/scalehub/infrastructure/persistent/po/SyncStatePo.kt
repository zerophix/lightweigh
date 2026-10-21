package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.Entity

/**
 * po — 同步状态持久化对象（M2b 水位线/对账/幂等）。
 *
 * 设计要点：
 * - **主键**：`(tableName, memberId)` 复合——每表每成员独立水位线，多成员隔离的基础
 * - **pushedWatermark**：本机已成功推送到云端的最大时间戳（measurements 是 measuredAt，
 *   health_logs 是 logged_at）。推送时只推 `> pushedWatermark` 的行，实现增量。
 * - **pulledWatermark**：云端已成功拉回本机的最大时间戳。拉取时只拉 `> pulledWatermark`
 *   的行，实现增量。
 * - **幂等语义**：push/pull 都以 `watermark` 为闸门——重复触发时，若无新数据则
 *   `watermark` 不变，`CloudDataPort.upsert` 即使收到相同行也因 PostgREST
 *   `on_conflict=merge-duplicates` 语义而幂等（本地先查再推亦幂等）。
 * - **崩溃恢复**：先推/拉，成功后再更新 watermark（顺序保证——若在更新前崩溃，
 *   重启后会重推/重拉，但结果等价，不会漏数据）。
 */
@Entity(tableName = "sync_state", primaryKeys = ["tableName", "memberId"])
data class SyncStatePo(
    /** 表名：measurements / health_logs / profile / devices */
    val tableName: String,
    /** 成员 id：多成员隔离 */
    val memberId: String,
    /** 本机已成功推送到云端的最大时间戳（0 = 尚未推送） */
    val pushedWatermark: Long,
    /** 云端已成功拉回本机的最大时间戳（0 = 尚未拉取） */
    val pulledWatermark: Long,
    /** 最近一次同步时间（毫秒），用于过期检测/对账 */
    val updatedAt: Long,
)
