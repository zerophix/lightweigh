package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * po — 健康记录。本地主键 (memberId, loggedAt, type)，消除跨成员同秒碰撞（DEC-058）。
 * updatedAt 为冲突/水位时刻；deletedAt 为软删墓碑。
 */
@Entity(
    tableName = "health_logs",
    primaryKeys = ["memberId", "loggedAt", "type"],
    indices = [Index(value = ["updatedAt"])],
)
data class HealthLogPo(
    val loggedAt: Long,
    val type: String,
    val value: Double,
    val value2: Double,
    val label: String,
    val note: String,
    @ColumnInfo(defaultValue = "1")
    val memberId: String,
    val updatedAt: Long = loggedAt,
    val deletedAt: Long? = null,
)
