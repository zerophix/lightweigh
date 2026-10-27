package com.xcwk.scalehub.application.habit

import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.query.HabitQueryPort
import com.xcwk.scalehub.domain.model.Habit
import com.xcwk.scalehub.domain.model.HabitPeriod
import com.xcwk.scalehub.domain.model.HabitItem
import com.xcwk.scalehub.domain.repository.HabitRepository
import com.xcwk.scalehub.domain.types.Clock
import com.xcwk.scalehub.domain.types.SystemClock
import kotlinx.coroutines.flow.Flow

/**
 * application service — 习惪管理编排（新建/编辑/删除/打卡/查询/建议）。
 *
 * 职责：
 *  - 创建/编辑习惯（校验名称长度/目标次数范围）
 *  - 删除习惯
 *  - 打卡习惯（当期幂等）
 *  - 查询习惯列表/详情（响应式流）
 *  - 生成智能习惯建议
 *
 * 旧库 HabitStore 语义移植。写操作通过 HabitRepository，
 * 读操作通过 HabitQueryPort（依赖倒置，便于测试替换）。
 */
class HabitService(
    private val repo: HabitRepository,
    private val query: HabitQueryPort?,
    private val memberId: () -> String,
    private val clock: Clock = com.xcwk.scalehub.domain.types.SystemClock,
    private val scheduler: HabitReminderScheduler = com.xcwk.scalehub.application.habit.NoopHabitReminderScheduler,
    private val refreshBus: DataRefreshBus? = null,
) {

    /**
     * 创建新习惯。
     * @return 创建结果，ok=true 时返回习惯 ID
     */
    suspend fun create(
        name: String,
        period: HabitPeriod,
        targetCount: Int,
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int,
    ): HabitCreateResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return HabitCreateResult(false, "请输入习惯名称", null)
        }
        if (trimmed.length > 50) {
            return HabitCreateResult(false, "习惯名称最多 50 个字符", null)
        }
        if (targetCount < Habit.MIN_TARGET || targetCount > Habit.MAX_TARGET) {
            return HabitCreateResult(false, "目标次数需在 1-100 之间", null)
        }
        val id = "habit_${System.currentTimeMillis()}"
        val habit = Habit.create(
            name = trimmed,
            period = period,
            targetCount = targetCount,
            reminderEnabled = reminderEnabled,
            reminderHour = reminderHour,
            reminderMinute = reminderMinute,
            id = id,
            clock = clock,
        ).copy(memberId = memberId())
        return runCatching {
            repo.save(habit)
            if (reminderEnabled) scheduler.schedule(habit, memberId())
            refreshBus?.notifyChanged(DataChange.Habits)
            HabitCreateResult(true, "创建成功", id)
        }.onFailure {
            HabitCreateResult(false, "创建失败：${it.message ?: "未知错误"}}", null)
        }.getOrDefault(HabitCreateResult(false, "创建失败", null))
    }

    /**
     * 更新习惯。
     */
    suspend fun update(
        id: String,
        name: String,
        period: HabitPeriod,
        targetCount: Int,
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int,
    ): HabitUpdateResult {
        val existing = repo.get(id) ?: return HabitUpdateResult(false, "习惯不存在")
        val updated = existing.copy(
            name = name.trim(),
            period = period,
            targetCount = targetCount.coerceIn(Habit.MIN_TARGET, Habit.MAX_TARGET),
            reminderEnabled = reminderEnabled,
            reminderHour = reminderHour.coerceIn(0, 23),
            reminderMinute = reminderMinute.coerceIn(0, 59),
            updatedAt = clock.nowMillis(),
        )
        return runCatching {
            repo.save(updated)
            if (reminderEnabled) scheduler.schedule(updated, memberId())
            else scheduler.cancel(id, memberId())
            refreshBus?.notifyChanged(DataChange.Habits)
            HabitUpdateResult(true, "保存成功")
        }.onFailure {
            HabitUpdateResult(false, "保存失败：${it.message ?: "未知错误"}}")
        }.getOrDefault(HabitUpdateResult(false, "保存失败"))
    }

    /** 删除习惯。 */
    suspend fun delete(id: String): HabitDeleteResult {
        val existing = repo.get(id) ?: return HabitDeleteResult(false, "习惯不存在")
        return runCatching {
            repo.delete(id)
            scheduler.cancel(id, memberId())
            refreshBus?.notifyChanged(DataChange.Habits)
            HabitDeleteResult(true, "删除成功")
        }.onFailure {
            HabitDeleteResult(false, "删除失败：${it.message ?: "未知错误"}}")
        }.getOrDefault(HabitDeleteResult(false, "删除失败"))
    }

    /** 打卡习惯。 */
    suspend fun tick(id: String): HabitTickResult {
        val existing = repo.get(id) ?: return HabitTickResult(false, "习惯不存在", 0)
        val ok = repo.tick(id)
        if (ok) {
            refreshBus?.notifyChanged(DataChange.Habits)
        }
        return HabitTickResult(ok, if (ok) "打卡成功" else "打卡失败", existing.completedCount + if (ok) 1 else 0)
    }

    /** 获取所有习惯（响应式）。 */
    fun allFlow(): Flow<List<HabitItem>> = query?.allFlow() ?: kotlinx.coroutines.flow.emptyFlow()

    /** 获取习惯详情。 */
    suspend fun get(id: String): Habit? = repo.get(id)

    /** 生成智能习惯建议。 */
    suspend fun generateSuggestions(): List<HabitSuggestion> {
        val existing = repo.all()
        val suggestions = mutableListOf<HabitSuggestion>()
        if (existing.none { it.name.contains("水") || it.name.contains("喝") }) {
            suggestions.add(HabitSuggestion("每天喝 8 杯水", HabitPeriod.DAILY, 8, "帮助维持水分"))
        }
        if (existing.none { it.name.contains("睡眠") }) {
            suggestions.add(HabitSuggestion("保证 7-8 小时睡眠", HabitPeriod.DAILY, 1, "改善整体健康状态"))
        }
        if (existing.none { it.name.contains("步数") }) {
            suggestions.add(HabitSuggestion("每日 1 万步", HabitPeriod.DAILY, 1, "增加日常活动量"))
        }
        if (existing.none { it.name.contains("早餐") }) {
            suggestions.add(HabitSuggestion("吃营养早餐", HabitPeriod.DAILY, 1, "避免跳过早餐"))
        }
        return suggestions
    }
}

/** 创建结果。 */
data class HabitCreateResult(val ok: Boolean, val message: String, val id: String?)

/** 更新结果。 */
data class HabitUpdateResult(val ok: Boolean, val message: String)

/** 删除结果。 */
data class HabitDeleteResult(val ok: Boolean, val message: String)

/** 打卡结果。 */
data class HabitTickResult(val ok: Boolean, val message: String, val completedCount: Int)

/** 习惯建议。 */
data class HabitSuggestion(
    val name: String,
    val period: HabitPeriod,
    val targetCount: Int,
    val description: String,
)
