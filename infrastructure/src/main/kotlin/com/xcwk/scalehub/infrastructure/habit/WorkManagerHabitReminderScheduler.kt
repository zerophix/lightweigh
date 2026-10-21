package com.xcwk.scalehub.infrastructure.habit

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.xcwk.scalehub.application.habit.HabitReminderScheduler
import com.xcwk.scalehub.domain.model.Habit
import java.util.concurrent.TimeUnit

/**
 * WorkManager 实现：习惯每日提醒调度。
 * 工作名：habit_reminder_{memberId}_{habitId}（唯一键，更新即替换）。
 * 周期：每日一次，在 reminderHour:reminderMinute 触发（用 PeriodicWorkRequest + flexInterval 近似）。
 * 说明：WorkManager 不支持精确时刻，flexInterval 最近 15 分钟窗口；若需精确用 AlarmManager，
 *     但需 REQUEST_SCHEDULE_EXACT_ALARM 权限（Android 12+），本项目暂用 WorkManager 近似。
 */
class WorkManagerHabitReminderScheduler(context: Context) : HabitReminderScheduler {

    private val workManager = WorkManager.getInstance(context)

    override fun schedule(habit: Habit, memberId: String) {
        val workName = workName(memberId, habit.id)
        // 先取消旧任务
        workManager.cancelUniqueWork(workName)

        if (!habit.reminderEnabled) return

        // 计算首次执行延迟（从现在到今天/明天的 reminderHour:reminderMinute）
        val now = System.currentTimeMillis()
        val todayTarget = java.time.LocalDate.now().atTime(habit.reminderHour, habit.reminderMinute)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val initialDelayMs = if (todayTarget > now) todayTarget - now else todayTarget + 24 * 3600 * 1000 - now

        // PeriodicWorkRequest：每日重复，flexInterval 15 分钟（最近一次执行窗口）
        val request = PeriodicWorkRequest.Builder(
            HabitReminderWorker::class.java,
            24, TimeUnit.HOURS,
            15, TimeUnit.MINUTES,
        )
            .setInputData(
                androidx.work.Data.Builder()
                    .putString("habit_id", habit.id)
                    .putString("member_id", memberId)
                    .putString("habit_name", habit.name)
                    .build()
            )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.REPLACE, request)
    }

    override fun cancel(habitId: String, memberId: String) {
        workManager.cancelUniqueWork(workName(memberId, habitId))
    }

    private fun workName(memberId: String, habitId: String) = "habit_reminder_${memberId}_${habitId}"

    companion object {
        /** 启动时恢复所有启用提醒的习惯（App 启动/登录后调用）。 */
        fun restoreAll(context: Context, habits: List<Habit>, memberId: String) {
            val scheduler = WorkManagerHabitReminderScheduler(context)
            habits.filter { it.reminderEnabled }.forEach { scheduler.schedule(it, memberId) }
        }
    }
}