package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * po — 每人一行的体重/热量/喝水目标（DEC-S02）。
 * targetWeightKg = 0 表示未设置。deletedAt 预留软删；清本机/注销走硬删。
 */
@Entity(tableName = "goals")
data class GoalPo(
    @PrimaryKey val memberId: String,
    @ColumnInfo(defaultValue = "0")
    val targetWeightKg: Double,
    @ColumnInfo(defaultValue = "2049")
    val calorieGoal: Int,
    @ColumnInfo(defaultValue = "2000")
    val waterGoal: Int,
    val updatedAt: Long,
    val deletedAt: Long?,
)
