package com.xcwk.scalehub.application.query

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 健康记录读模型（类型中文名与单位随旧库 RecordScreen 口径 Pages.kt:466-530）。
 */
class HealthLogQueryTest {

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        private fun ts(mo: Int, d: Int, h: Int, mi: Int = 0): Long =
            LocalDate.of(2026, mo, d).atTime(h, mi).atZone(ZONE).toInstant().toEpochMilli()
    }

    private class FakeRepo(private val rows: List<HealthLog>) : HealthLogRepository {
        override suspend fun save(log: HealthLog, memberId: String) = throw UnsupportedOperationException()
        override suspend fun recent(limit: Int) = rows.sortedByDescending { it.time }.take(limit)


        override suspend fun between(fromInc: Long, toExc: Long) =
            rows.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
    }

    private fun log(t: Long, type: String, value: Double, value2: Double = 0.0, label: String = "") =
        HealthLog(t, type, value, value2, label, "")

    @Test
    fun `七类数值与单位格式`() = runTest {
        val rows = listOf(
            log(ts(10, 1, 8), "diet", 1500.0, label = "午餐"),
            log(ts(10, 2, 8), "exercise", 30.0, 320.0, "跑步"),
            log(ts(10, 3, 8), "water", 250.0),
            log(ts(10, 4, 8), "waist", 78.0, 88.0),
            log(ts(10, 5, 8), "glucose", 5.2),
            log(ts(10, 6, 8), "blood_pressure", 120.0, 80.0),
            log(ts(10, 7, 8), "habit", 1.0, label = "早睡"),
        )
        val items = HealthLogQuery(FakeRepo(rows), ZONE).recent(10)
        val byLabel = items.associate { it.typeLabel to it.valueText }
        assertThat(byLabel["饮食"]).isEqualTo("1500 千卡")
        assertThat(byLabel["运动"]).isEqualTo("30 分钟 · 320 千卡")
        assertThat(byLabel["喝水"]).isEqualTo("250 ml")
        assertThat(byLabel["体围"]).isEqualTo("78 cm · 臀围 88 cm")
        assertThat(byLabel["血糖"]).isEqualTo("5.2 mmol/L")
        assertThat(byLabel["血压"]).isEqualTo("120/80 mmHg")
        assertThat(byLabel["习惯"]).isEqualTo("早睡")
    }

    @Test
    fun `倒序与限数_时间文本`() = runTest {
        val rows = listOf(log(ts(10, 1, 8), "water", 200.0), log(ts(10, 2, 9, 30), "water", 300.0))
        val items = HealthLogQuery(FakeRepo(rows), ZONE).recent(1)
        assertThat(items).hasSize(1)
        assertThat(items[0].valueText).isEqualTo("300 ml")
        assertThat(items[0].timeText).isEqualTo("10-02 09:30")
    }
}
