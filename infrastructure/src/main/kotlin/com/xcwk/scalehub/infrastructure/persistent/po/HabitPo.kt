package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * po — 习惯持久化对象（DEC-043-D 习惯管理页）。
 *
 * 表 habits 每行一条习惯，按成员隔离（memberId 维，同 measurements/health_logs）。
 * 字段与领域模型 [Habit] 一一对应；提醒时间用 (reminderHour, reminderMinute) 24 小时制。
 * 软删除语义：停用的习惯用 active 标记（0/1），物理删除才走 DELETE（delete 用例）。
 */
@Entity(tableName = "habits")
data class HabitPo(
    @PrimaryKey val id: String,
    val name: String,
    val period: String,
    val targetCount: Int,
    val doneCount: Int,
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val active: Boolean,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "1")
    val memberId: String,
)