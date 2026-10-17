package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * po — 健康记录持久化对象。表 health_logs 对应云表 public.health_logs 的本地列
 * （列名沿本地惯例 camelCase，同 measurements 对云表 measured_at 的处理）。
 * 复合主键 (loggedAt, type) = 云表 UNIQUE(owner, logged_at, type) 的本地形态。
 */
@Entity(tableName = "health_logs", primaryKeys = ["loggedAt", "type"])
data class HealthLogPo(
    val loggedAt: Long,
    val type: String,
    val value: Double,
    val value2: Double,
    val label: String,
    val note: String,
    @ColumnInfo(defaultValue = "1")
    val memberId: String,
)
