package com.xcwk.scalehub.domain.model

import java.time.LocalDate

/**
 * 习惯实体（DEC-043-D：习惯管理页）。
 *
 * 业务规则：
 * - 周期：daily/weekly/monthly（旧库 period 枚举）
 * - 目标次数：1..100（MIN_TARGET=1, MAX_TARGET=100）
 * - 提醒：可选，时/分（24h 制），启用后由 WorkManager 调度每日通知
 * - 打卡进度：当期内已完成次数（completedCount），由 RecordHabitCommand.tick 推进
 * - 归属成员：memberId 显式隔离（多成员横切维度，同测量/档案口径）
 */
data class Habit(
    val id: String,
    val memberId: String,
    val name: String,
    val period: HabitPeriod,
    val targetCount: Int,
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /** 当期内已打卡次数（周期内重置，不持久化跨期历史——历史由 HealthLog 记录承载）。 */
    val completedCount: Int = 0,
) {
    companion object {
        const val MIN_TARGET = 1
        const val MAX_TARGET = 100

        fun create(
            name: String,
            period: HabitPeriod,
            targetCount: Int,
            reminderEnabled: Boolean,
            reminderHour: Int,
            reminderMinute: Int,
            id: String,
            clock: com.xcwk.scalehub.domain.types.Clock,
        ): Habit {
            val now = clock.nowMillis()
            return Habit(
                id = id,
                memberId = "", // 由仓储层按当前成员填充
                name = name.trim(),
                period = period,
                targetCount = targetCount.coerceIn(MIN_TARGET, MAX_TARGET),
                reminderEnabled = reminderEnabled,
                reminderHour = reminderHour.coerceIn(0, 23),
                reminderMinute = reminderMinute.coerceIn(0, 59),
                createdAt = now,
                updatedAt = now,
                completedCount = 0,
            )
        }
    }
}

enum class HabitPeriod(val label: String) {
    DAILY("每日"),
    WEEKLY("每周"),
    MONTHLY("每月"),
}

/** 习惯查询项（列表展示）。 */
data class HabitItem(
    val id: String,
    val name: String,
    val period: HabitPeriod,
    val targetCount: Int,
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val completedCount: Int,
    /** 是否已达标（当期完成次数 >= 目标）。 */
    val isDone: Boolean,
    /** 当期起始时间戳（用于倒计时/进度条）。 */
    val periodStart: Long,
    /** 当期结束时间戳。 */
    val periodEnd: Long,
)