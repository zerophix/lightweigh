package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.application.query.HabitQueryPort
import com.xcwk.scalehub.domain.model.Habit
import com.xcwk.scalehub.domain.model.HabitItem
import com.xcwk.scalehub.domain.model.HabitPeriod
import com.xcwk.scalehub.domain.repository.HabitRepository
import com.xcwk.scalehub.infrastructure.persistent.dao.HabitDao
import com.xcwk.scalehub.infrastructure.persistent.po.HabitPo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Room 实现：HabitRepository + HabitQueryPort（读写合一，多成员隔离）。
 * 周期进度计算：按 period 计算当期起止时间，doneCount 仅在当期内有效。
 * 使用 dao/HabitPo（camelCase 列名，与 codebase 现有 Po 规范一致）。
 */
class RoomHabitRepository(
    private val dao: HabitDao,
    private val activeMemberId: () -> String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : HabitRepository, HabitQueryPort {

    override fun all(): List<HabitItem> {
        val now = System.currentTimeMillis()
        return dao.allList(activeMemberId()).map { po ->
            val period = HabitPeriod.valueOf(po.period)
            val (start, end) = periodBounds(period)
            val done = if (now >= start && now <= end) po.doneCount else 0
            HabitItem(
                id = po.id, name = po.name, period = period,
                targetCount = po.targetCount, reminderEnabled = po.reminderEnabled,
                reminderHour = po.reminderHour, reminderMinute = po.reminderMinute,
                completedCount = done, isDone = done >= po.targetCount,
                periodStart = start, periodEnd = end,
            )
        }
    }

    override suspend fun byId(id: String): Habit? = get(id)

    override fun get(id: String): Habit? {
        val po = dao.byId(id, activeMemberId()) ?: return null
        return toHabit(po)
    }

    override suspend fun save(habit: Habit) {
        val po = toPo(habit)
        val existing = dao.byId(habit.id, habit.memberId)
        if (existing != null) {
            dao.upsert(po.copy(createdAt = existing.createdAt))
        } else {
            dao.upsert(po)
        }
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun tick(id: String): Boolean {
        val memberId = activeMemberId()
        val habit = dao.byId(id, memberId) ?: return false
        if (!habit.active) return false
        val now = System.currentTimeMillis()
        val (start, end) = periodBounds(HabitPeriod.valueOf(habit.period))
        if (now < start || now > end) return false
        dao.upsert(habit.copy(doneCount = habit.doneCount + 1))
        return true
    }

    /** HabitQueryPort 实现：当前成员所有习惯，创建时间正序。 */
    override fun allFlow(): Flow<List<HabitItem>> =
        dao.all(activeMemberId()).map { pos ->
            val now = System.currentTimeMillis()
            pos.filter { it.active }.map { po ->
                val period = HabitPeriod.valueOf(po.period)
                val (start, end) = periodBounds(period)
                val done = if (now >= start && now <= end) po.doneCount else 0
                HabitItem(
                    id = po.id,
                    name = po.name,
                    period = period,
                    targetCount = po.targetCount,
                    reminderEnabled = po.reminderEnabled,
                    reminderHour = po.reminderHour,
                    reminderMinute = po.reminderMinute,
                    completedCount = done,
                    isDone = done >= po.targetCount,
                    periodStart = start,
                    periodEnd = end,
                )
            }
        }

    private fun toPo(h: Habit): HabitPo = HabitPo(
        id = h.id,
        name = h.name,
        period = h.period.name,
        targetCount = h.targetCount,
        doneCount = h.completedCount,
        reminderEnabled = h.reminderEnabled,
        reminderHour = h.reminderHour,
        reminderMinute = h.reminderMinute,
        active = true,
        createdAt = h.createdAt,
        memberId = h.memberId,
    )

    private fun toHabit(e: HabitPo): Habit = Habit(
        id = e.id,
        memberId = e.memberId,
        name = e.name,
        period = HabitPeriod.valueOf(e.period),
        targetCount = e.targetCount,
        reminderEnabled = e.reminderEnabled,
        reminderHour = e.reminderHour,
        reminderMinute = e.reminderMinute,
        createdAt = e.createdAt,
        updatedAt = e.createdAt,
        completedCount = e.doneCount,
    )

    /** 计算周期起止时间戳（含当期内 completedCount 归零语义：跨期由定时任务重置，此处仅算边界）。 */
    private fun periodBounds(period: HabitPeriod): Pair<Long, Long> {
        val today = LocalDate.now(zone)
        return when (period) {
            HabitPeriod.DAILY -> {
                val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                start to end
            }
            HabitPeriod.WEEKLY -> {
                val start = today.`with`(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .atStartOfDay(zone).toInstant().toEpochMilli()
                val end = start + 7L * 24 * 3600 * 1000 - 1
                start to end
            }
            HabitPeriod.MONTHLY -> {
                val start = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                start to end
            }
        }
    }
}