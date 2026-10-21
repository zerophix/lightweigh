package com.xcwk.scalehub.application.habit

import com.xcwk.scalehub.domain.model.Habit

/**
 * 习惯提醒调度端口（WorkManager 封装）。
 * 容器侧实现：保存/删除习惯后由 RecordHabitService 通过此端口调度/取消每日通知。
 * 非空实现由容器注入；测试/无需提醒场景可传空实现（no-op）。
 */
interface HabitReminderScheduler {
    /** 调度/更新某习惯的每日提醒（若 reminderEnabled=false 则取消）。 */
    fun schedule(habit: Habit, memberId: String)

    /** 取消某习惯的提醒。 */
    fun cancel(habitId: String, memberId: String)
}

/** 空实现（测试/禁用提醒时用）。 */
object NoopHabitReminderScheduler : HabitReminderScheduler {
    override fun schedule(habit: Habit, memberId: String) {}
    override fun cancel(habitId: String, memberId: String) {}
}