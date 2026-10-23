package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * po — 习惯持久化对象（DEC-043-D）。
 *
 * 习惯不是体重目标。@Index(memberId) 与 Migration 6→7 已创建的
 * index_habits_memberId 对齐，并进入导出 schema（DEC-S10）。
 * 停用走 active；物理删除才走 DELETE。本表不进主路径 Must。
 */
@Entity(
    tableName = "habits",
    indices = [Index(value = ["memberId"])],
)
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
