package com.xcwk.scalehub.application.service

import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.command.RecordHabitCommand
import com.xcwk.scalehub.application.habit.HabitReminderScheduler
import com.xcwk.scalehub.application.habit.NoopHabitReminderScheduler
import com.xcwk.scalehub.domain.model.Habit
import com.xcwk.scalehub.domain.model.HabitPeriod
import com.xcwk.scalehub.domain.repository.HabitRepository
import com.xcwk.scalehub.domain.types.Clock
import com.xcwk.scalehub.domain.types.SystemClock

/**
 * application service — 记录习惯用例（新建/编辑/删除/打卡）。
 *
 * 语义（DEC-043-D 习惯管理页）：
 *  - 新建：id 由用例生成（时间戳），时间由 [Habit.create] 经 [Clock] 盖章（DEC-034 组 C）
 *  - 编辑：保留既有 id 与打卡进度，只改名称/周期/目标/提醒字段
 *  - 删除：物理删（HabitRepository.delete）
 *  - 打卡：HabitRepository.tick（当期内幂等，防重复点击）
 *
 * 保存后广播 [DataChange.Habits]，刷 UI 与推云由容器的事件订阅承担（组 A）。
 * 提醒调度：`reminderScheduler` 是非空端口——保存/删除后由容器侧实现（WorkManager）
 * 负责调度/取消每日提醒，本层只发通知语义（防打卡与提醒只挂调度器，不参与领域规则）。
 *
 * period 归一：command 层传字符串（"每天"/"每周"/"每月"），此处映射为 [HabitPeriod] 枚举。
 */
class RecordHabitService(
    private val repository: HabitRepository,
    private val refreshBus: DataRefreshBus? = null,
    private val reminderScheduler: HabitReminderScheduler = NoopHabitReminderScheduler,
    private val clock: Clock = SystemClock,
    /** 当前成员归属（DEC-034 组 D / F-005）。 */
    private val memberId: () -> String,
) {

    suspend fun save(cmd: RecordHabitCommand): Habit {
        val period = periodOf(cmd.period)
        val habit = if (cmd.id == null) {
            Habit.create(
                name = cmd.name,
                period = period,
                targetCount = cmd.targetCount,
                reminderEnabled = cmd.reminderEnabled,
                reminderHour = cmd.reminderHour,
                reminderMinute = cmd.reminderMinute,
                id = clock.nowMillis().toString(),
                clock = clock,
            ).copy(memberId = memberId())
        } else {
            val existing = repository.get(cmd.id)
                ?: throw IllegalArgumentException("习惯不存在: ${cmd.id}")
            existing.copy(
                name = cmd.name.trim(),
                period = period,
                targetCount = cmd.targetCount.coerceIn(Habit.MIN_TARGET, Habit.MAX_TARGET),
                reminderEnabled = cmd.reminderEnabled,
                reminderHour = cmd.reminderHour,
                reminderMinute = cmd.reminderMinute,
            )
        }
        repository.save(habit)
        reminderScheduler.schedule(habit, memberId())
        refreshBus?.notifyChanged(DataChange.Habits)
        return habit
    }

    suspend fun delete(habitId: String) {
        repository.delete(habitId)
        reminderScheduler.cancel(habitId, memberId())
        refreshBus?.notifyChanged(DataChange.Habits)
    }

    /** 打卡：当期内 doneCount +1（幂等防重）。 */
    suspend fun tick(habitId: String): Boolean {
        val ok = repository.tick(habitId)
        if (ok) refreshBus?.notifyChanged(DataChange.Habits)
        return ok
    }

    /** command 层字符串周期 → 枚举（"每天"/"每周"/"每月"，兜底默认 DAILY）。 */
    private fun periodOf(raw: String): HabitPeriod = when (raw.trim()) {
        HabitPeriod.WEEKLY.label -> HabitPeriod.WEEKLY
        HabitPeriod.MONTHLY.label -> HabitPeriod.MONTHLY
        else -> HabitPeriod.DAILY
    }
}