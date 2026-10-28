package com.xcwk.scalehub.application.habit

import com.xcwk.scalehub.domain.model.Habit
import com.xcwk.scalehub.domain.model.HabitPeriod
import com.xcwk.scalehub.domain.model.HabitItem
import com.xcwk.scalehub.domain.repository.HabitRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 习惯服务行为测试。
 */
class HabitServiceTest {

    private class FakeHabitRepo : HabitRepository {
        private val habits = mutableMapOf<String, Habit>()

        override fun all(): List<HabitItem> = habits.values.map { h ->
            HabitItem(h.id, h.name, h.period, h.targetCount, h.reminderEnabled,
                h.reminderHour, h.reminderMinute, h.completedCount, true, h.createdAt, h.createdAt + 86400000)
        }
        override fun get(id: String): Habit? = habits[id]
        override suspend fun save(habit: Habit) { habits[habit.id] = habit }
        override suspend fun delete(id: String) { habits.remove(id) }
        override suspend fun tick(id: String): Boolean = true
    }

    @Test
    fun create_with_empty_name_returns_error() = runTest {
        val repo = FakeHabitRepo()
        val service = HabitService(repo, null, { "1" }, com.xcwk.scalehub.domain.types.FixedClock(1000L))

        val result = service.create("", HabitPeriod.DAILY, 1, false, 8, 0)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("名称")
    }

    @Test
    fun create_with_valid_data_succeeds() = runTest {
        val repo = FakeHabitRepo()
        val service = HabitService(repo, null, { "1" }, com.xcwk.scalehub.domain.types.FixedClock(1000L))

        val result = service.create("每天喝水", HabitPeriod.DAILY, 8, true, 8, 0)

        assertThat(result.ok).isTrue()
        assertThat(result.id).isNotNull()
    }

    @Test
    fun create_with_target_out_of_range_returns_error() = runTest {
        val repo = FakeHabitRepo()
        val service = HabitService(repo, null, { "1" }, com.xcwk.scalehub.domain.types.FixedClock(1000L))

        val result = service.create("测试", HabitPeriod.DAILY, 0, false, 8, 0)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("1-100")
    }

    @Test
    fun delete_nonexistent_returns_error() = runTest {
        val repo = FakeHabitRepo()
        val service = HabitService(repo, null, { "1" }, com.xcwk.scalehub.domain.types.FixedClock(1000L))

        val result = service.delete("nonexistent")

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("不存在")
    }

    @Test
    fun generateSuggestions_returns_default_habits() = runTest {
        val repo = FakeHabitRepo()
        val service = HabitService(repo, null, { "1" }, com.xcwk.scalehub.domain.types.FixedClock(1000L))

        val suggestions = service.generateSuggestions()

        assertThat(suggestions).isNotEmpty()
        assertThat(suggestions.any { it.name.contains("水") }).isTrue()
    }

    @Test
    fun generateSuggestions_filters_existing_habits() = runTest {
        val repo = FakeHabitRepo()
        val service = HabitService(repo, null, { "1" }, com.xcwk.scalehub.domain.types.FixedClock(1000L))

        val createResult = service.create("每天喝 8 杯水", HabitPeriod.DAILY, 8, false, 8, 0)
        assertThat(createResult.ok).isTrue()

        val existing = repo.all()
        assertThat(existing).isNotEmpty()
        assertThat(existing.any { it.name.contains("水") }).isTrue()

        val suggestions = service.generateSuggestions()

        // 已有"喝水"习惯，不能再建议"喝水"
        assertThat(suggestions.none { it.name == "每天喝 8 杯水" }).isTrue()
    }
}
