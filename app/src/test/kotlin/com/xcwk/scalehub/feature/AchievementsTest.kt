package com.xcwk.scalehub.feature

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.feature.achievements.Milestone
import com.xcwk.scalehub.feature.achievements.currentStreakDays
import com.xcwk.scalehub.feature.achievements.lostWeightKg
import com.xcwk.scalehub.feature.achievements.nextMilestoneGap
import com.xcwk.scalehub.feature.achievements.reachedMilestone
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * T-069 成就系统纯逻辑。
 *
 * 钉住三条：同一天多次只算一天；断一天即归零；没减重不产出成就。
 */
class AchievementsTest {

    private val zone = ZoneOffset.UTC
    private fun millisOf(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `今天测一次连续一天`() {
        val today = 20_000L
        assertThat(currentStreakDays(listOf(millisOf(today)), today, zone)).isEqualTo(1)
    }

    @Test
    fun `连续三天`() {
        val today = 20_000L
        val days = listOf(today, today - 1, today - 2).map { millisOf(it) }
        assertThat(currentStreakDays(days, today, zone)).isEqualTo(3)
    }

    @Test
    fun `断档从断点前停止`() {
        val today = 20_000L
        val days = listOf(today, today - 1, today - 3).map { millisOf(it) }
        assertThat(currentStreakDays(days, today, zone)).isEqualTo(2)
    }

    @Test
    fun `同一天多次测量只算一天`() {
        val today = 20_000L
        val days = listOf(millisOf(today), millisOf(today) + 3_600_000, millisOf(today) + 7_200_000)
        assertThat(currentStreakDays(days, today, zone)).isEqualTo(1)
    }

    @Test
    fun `今天没测但昨天测了仍算连续`() {
        val today = 20_000L
        val days = listOf(today - 1, today - 2).map { millisOf(it) }
        assertThat(currentStreakDays(days, today, zone)).isEqualTo(2)
    }

    @Test
    fun `今天和昨天都没测归零`() {
        val today = 20_000L
        val days = listOf(today - 2, today - 3).map { millisOf(it) }
        assertThat(currentStreakDays(days, today, zone)).isEqualTo(0)
    }

    @Test
    fun `没有数据归零`() {
        assertThat(currentStreakDays(emptyList(), 20_000L, zone)).isEqualTo(0)
    }

    @Test
    fun `减重计算与阈值`() {
        assertThat(lostWeightKg(65.2, 62.4)).isWithin(0.001).of(2.8)
        assertThat(lostWeightKg(65.0, 64.5)).isNull()
        assertThat(lostWeightKg(65.0, 66.0)).isNull()
        assertThat(lostWeightKg(null, 62.0)).isNull()
    }

    @Test
    fun `里程碑按已减重量取最高一档`() {
        assertThat(reachedMilestone(0.9)).isNull()
        assertThat(reachedMilestone(1.0)).isEqualTo(Milestone.First)
        assertThat(reachedMilestone(3.4)).isEqualTo(Milestone.Three)
        assertThat(reachedMilestone(21.0)).isEqualTo(Milestone.Twenty)
    }

    @Test
    fun `下一档距离`() {
        val next = nextMilestoneGap(1.5)
        assertThat(next!!.first).isEqualTo(Milestone.Three)
        assertThat(next.second).isWithin(0.001).of(1.5)
        assertThat(nextMilestoneGap(25.0)).isNull()
    }
}
