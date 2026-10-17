package com.xcwk.scalehub.application.query

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.GoalRepository
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 首页目标进度查询（旧库 HomeScreen 计算口径 :102-131 移植）。 */
class HomeGoalQueryTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun todayAt(h: Int): Long =
        LocalDate.now(zone).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun daysAgo(n: Int, h: Int = 8): Long =
        LocalDate.now(zone).minusDays(n.toLong()).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    private class FakeMeasureRepo(rows: List<MeasureRecord>) : MeasurementRepository {
        val data = rows.toMutableList()
        override suspend fun save(record: MeasureRecord) { data += record }
        override suspend fun recent(limit: Int) = data.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            data.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
        override suspend fun delete(time: Long) { data.removeAll { it.time == time } }
    }

    private class FakeLogRepo(rows: List<HealthLog>) : HealthLogRepository {
        val data = rows.toMutableList()
        override suspend fun save(log: HealthLog) { data += log }
        override suspend fun recent(limit: Int) = data.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            data.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
    }

    private class FakeGoals(
        override var targetWeight: Float = 0f,
        override var calorieGoal: Int = 2049,
        override var waterGoal: Int = 2000,
    ) : GoalRepository

    private class FakeProfile(private val height: Int = 175) : MemberProfileReader {
        override fun male() = true
        override fun ageYears() = 30
        override fun heightCm() = height
        override fun birthYear() = 1996
        override fun birthMonth() = 5
        override fun birthDay() = 20
        override fun nickname() = ""
    }

    private fun query(
        weights: List<MeasureRecord> = emptyList(),
        logs: List<HealthLog> = emptyList(),
        goals: FakeGoals = FakeGoals(),
        profile: FakeProfile = FakeProfile(),
    ) = HomeGoalQuery(FakeMeasureRepo(weights), FakeLogRepo(logs), goals, profile, zone)

    private fun rec(t: Long, weight: Double, fat: Double = -1.0, impedance: Double = 0.0) =
        MeasureRecord(t, weight, impedance, fat, "AA", "builtin-1")

    private fun log(t: Long, type: String, value: Double, value2: Double = 0.0, label: String = "") =
        HealthLog(t, type, value, value2, label, "")

    @Test
    fun `今日饮食运动与剩余千卡`() = runTest {
        val q = query(
            logs = listOf(
                log(todayAt(8), "diet", 300.0, label = "早餐"),
                log(todayAt(12), "diet", 600.0, label = "午餐"),
                log(todayAt(13), "exercise", 100.0, value2 = 250.0),
                log(daysAgo(1), "diet", 999.0, label = "昨天"),   // 不计今日
            ),
            goals = FakeGoals(calorieGoal = 1500),
        )
        val s = q.state()
        assertThat(s.dietByMeal["早餐"]).isEqualTo(300)
        assertThat(s.dietByMeal["午餐"]).isEqualTo(600)
        assertThat(s.exerciseKcal).isEqualTo(250)
        assertThat(s.calorieRemaining).isEqualTo(1500 - 900 + 250)   // 850
    }

    @Test
    fun `喝水进度百分比封顶`() = runTest {
        val q = query(
            logs = listOf(log(todayAt(9), "water", 2500.0)),
            goals = FakeGoals(waterGoal = 2000),
        )
        val s = q.state()
        assertThat(s.waterTodayMl).isEqualTo(2500)
        assertThat(s.waterPercent).isEqualTo(100)
    }

    @Test
    fun `BMI段映射与身高缺失`() = runTest {
        val over = query(weights = listOf(rec(daysAgo(1), 90.0)), profile = FakeProfile(170)).state()
        assertThat(over.bmiSegment).isEqualTo("肥胖")
        val normal = query(weights = listOf(rec(daysAgo(1), 65.0)), profile = FakeProfile(175)).state()
        assertThat(normal.bmiSegment).isEqualTo("标准")
        val noHeight = query(weights = listOf(rec(daysAgo(1), 65.0)), profile = FakeProfile(0)).state()
        assertThat(noHeight.bmiSegment).isNull()
    }

    @Test
    fun `近7天变化首末差_点数不足为null`() = runTest {
        val q = query(weights = listOf(rec(daysAgo(6), 70.0), rec(todayAt(7), 68.5)))
        val s = q.state()
        assertThat(s.weekChange).isWithin(0.01).of(-1.5)
        val single = query(weights = listOf(rec(todayAt(7), 68.5))).state()
        assertThat(single.weekChange).isNull()
    }

    @Test
    fun `比上次与最新体脂可见性`() = runTest {
        val q = query(
            weights = listOf(
                rec(todayAt(7), 68.5, fat = 18.4, impedance = 500.0),
                rec(daysAgo(1), 69.0),
            ),
        )
        val s = q.state()
        assertThat(s.latestWeight).isWithin(0.01).of(68.5)
        assertThat(s.diffText).isEqualTo("-0.5")
        assertThat(s.latestFatShown).isTrue()
        // 手动体重（无阻抗）不显示体脂
        val manual = query(weights = listOf(rec(todayAt(7), 68.5, fat = 30.0))).state()
        assertThat(manual.latestFatShown).isFalse()
    }

    @Test
    fun `习惯计数与腰围跳绳`() = runTest {
        val q = query(
            logs = listOf(
                log(todayAt(7), "habit", 1.0, label = "早睡"),
                log(todayAt(7), "habit", 1.0, label = "喝水"),
                log(todayAt(7), "waist", 82.5),
                log(todayAt(10), "exercise", 120.0, label = "跳绳"),
                log(todayAt(10), "exercise", 50.0, label = "跳绳"),
            ),
        )
        val s = q.state()
        assertThat(s.habitCount).isEqualTo(2)
        assertThat(s.waistCm).isWithin(0.01).of(82.5)
        assertThat(s.ropeToday).isEqualTo(170)
    }
}
