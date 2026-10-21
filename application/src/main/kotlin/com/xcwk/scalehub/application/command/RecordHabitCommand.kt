package com.xcwk.scalehub.application.command

/**
 * command — 新建/编辑一条习惯（由 trigger 层组装，只带事实）。
 * period：Habit.PERIOD_DAILY("每天") / Habit.PERIOD_WEEKLY("每周")。
 * targetCount 编辑器钳制在 1..20（Habit.MIN_TARGET..Habit.MAX_TARGET）。
 * reminderEnabled 为 true 时 reminderHour/reminderMinute 必须给出（24 小时制）。
 * id：新建传 null（由用例生成）；编辑传已有 id。
 */
data class RecordHabitCommand(
    val id: String? = null,
    val name: String,
    val period: String,
    val targetCount: Int,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
)