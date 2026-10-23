package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.Entity
import androidx.room.Index

/**
 * po — 非时间戳实体的行级待同步队列（DEC-S16）。
 * 测量用 measurements.syncStatus，不进本表。
 * 本阶段写入 profile / devices / goals；没有独立重试调度器。
 */
@Entity(
    tableName = "pending_sync",
    primaryKeys = ["entityType", "entityId"],
    indices = [Index(value = ["nextRetryAt"])],
)
data class PendingSyncPo(
    val entityType: String,
    val entityId: String,
    val op: String,
    val payloadJson: String,
    val attempts: Int,
    val nextRetryAt: Long,
    val updatedAt: Long,
)
