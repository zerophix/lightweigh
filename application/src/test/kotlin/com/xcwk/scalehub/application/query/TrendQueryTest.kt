package com.xcwk.scalehub.application.query

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 趋势规则行为测试（DEC-022 组 B）—— 每条断言排除一类移植事故，
 * 出处为旧库 qk-health-android TrendPages.kt 行号。
 */
class TrendQueryTest {

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        /** 固定"现在"：2026-10-16 15:00 +08:00。 */
        private val NOW: Long = LocalDate.of(2026, 10, 16).atTime(15, 0).atZone(ZONE).toInstant().toEpochMilli()

        private fun ts(mo: Int, d: Int, h: Int, mi: Int = 0): Long =
            LocalDate.of(2026, mo, d).atTime(h, mi).atZone(ZONE).toInstant().toEpochMilli()

        private fun rec(t: Long, weight: Double = 70.0, fat: Double = -1.0) =
            MeasureRecord(t, weight, 500.0, fat, "AA:BB", "builtin-1")
    }

    private class FakeRepo(private val rows: List<MeasureRecord>) : MeasurementRepository, MeasurementQueryPort {
        override suspend fun save(record: MeasureRecord, memberId: String) = throw UnsupportedOperationException()
        override suspend fun recent(limit: Int) = rows.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            rows.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
        override suspend fun delete(time: Long) { }
    }

    private fun query(vararg rows: MeasureRecord) =
        TrendQuery(FakeRepo(rows.toList()), ZONE, { NOW })

    // ---- 日末选取（dailySeries:530-540）----

    @Test
    fun `日末序列_同日多条取最后一条`() = runTest {
        val q = query(rec(ts(10, 16, 10), 70.0), rec(ts(10, 16, 20), 72.0))
        val points = q.dailySeries(TrendRange.ALL, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(points).hasSize(1)
        assertThat(points[0].time).isEqualTo(ts(10, 16, 20))
        assertThat(points[0].value).isEqualTo(72.0)
    }

    @Test
    fun `体脂序列_剔除未计算且取最后有效值`() = runTest {
        // 10:00 有效体脂，20:00 未计算(-1)：体脂口径应取 10:00；体重口径取 20:00
        val q = query(
            rec(ts(10, 16, 10), 70.0, 20.0),
            rec(ts(10, 16, 20), 72.0, -1.0),
        )
        val fat = q.dailySeries(TrendRange.ALL, TrendSegment.ALL_DAY, TrendSeriesKind.FAT)
        val weight = q.dailySeries(TrendRange.ALL, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(fat).hasSize(1)
        assertThat(fat[0].time).isEqualTo(ts(10, 16, 10))
        assertThat(fat[0].value).isEqualTo(20.0)
        assertThat(weight[0].time).isEqualTo(ts(10, 16, 20))
    }

    @Test
    fun `体脂序列_整日未计算则该日无点`() = runTest {
        val q = query(rec(ts(10, 15, 9), 70.0, -1.0))
        val fat = q.dailySeries(TrendRange.ALL, TrendSegment.ALL_DAY, TrendSeriesKind.FAT)
        val weight = q.dailySeries(TrendRange.ALL, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(fat).isEmpty()
        assertThat(weight).hasSize(1)
    }

    // ---- 范围（recordsInRange:519-527）----

    @Test
    fun `范围_今天从本地零点起`() = runTest {
        val q = query(rec(ts(10, 15, 23)), rec(ts(10, 16, 0, 30)))
        val points = q.dailySeries(TrendRange.TODAY, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(points.map { it.time }).containsExactly(ts(10, 16, 0, 30))
    }

    @Test
    fun `范围_近7天含第六天前零点边界`() = runTest {
        // now=10/16 → 起点 = 10/10 00:00（shiftDays(6)），>= 起点含，之前不含
        val q = query(rec(ts(10, 9, 23, 59)), rec(ts(10, 10, 0, 0)), rec(ts(10, 16, 8)))
        val points = q.dailySeries(TrendRange.LAST_7, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(points.map { it.time })
            .containsExactly(ts(10, 10, 0, 0), ts(10, 16, 8))
            .inOrder()
    }

    // ---- 时段（inSlot:547-554）----

    @Test
    fun `时段_整点分界归属`() = runTest {
        val q = query(
            rec(ts(10, 1, 11, 59)),   // 上午
            rec(ts(10, 2, 12, 0)),     // 下午起点
            rec(ts(10, 3, 17, 59)),    // 下午末点
            rec(ts(10, 4, 18, 0)),     // 晚上起点
            rec(ts(10, 5, 5, 0)),      // 上午
        )
        val morning = q.dailySeries(TrendRange.LAST_30, TrendSegment.MORNING, TrendSeriesKind.WEIGHT)
        val afternoon = q.dailySeries(TrendRange.LAST_30, TrendSegment.AFTERNOON, TrendSeriesKind.WEIGHT)
        val evening = q.dailySeries(TrendRange.LAST_30, TrendSegment.EVENING, TrendSeriesKind.WEIGHT)
        val all = q.dailySeries(TrendRange.LAST_30, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(morning.map { it.time }).containsExactly(ts(10, 1, 11, 59), ts(10, 5, 5, 0)).inOrder()
        assertThat(afternoon.map { it.time }).containsExactly(ts(10, 2, 12, 0), ts(10, 3, 17, 59)).inOrder()
        assertThat(evening.map { it.time }).containsExactly(ts(10, 4, 18, 0))
        assertThat(all).hasSize(5)
    }

    // ---- 统计（TrendBlock:199-201）----

    @Test
    fun `统计_变化为最新日减最早日_高低按值`() = runTest {
        val q = query(rec(ts(10, 1, 8), 70.0), rec(ts(10, 2, 8), 72.0), rec(ts(10, 3, 8), 71.0))
        val stats = q.stats(TrendRange.ALL, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(stats.change).isWithin(1e-9).of(1.0)   // 71 − 70
        assertThat(stats.high!!.value).isEqualTo(72.0)
        assertThat(stats.low!!.value).isEqualTo(70.0)
    }

    @Test
    fun `统计_单点变化为零_忠实旧库`() = runTest {
        val q = query(rec(ts(10, 1, 8), 70.0))
        val stats = q.stats(TrendRange.ALL, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(stats.change).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `统计_空集各项为空`() = runTest {
        val stats = query().stats(TrendRange.ALL, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(stats.points).isEmpty()
        assertThat(stats.high).isNull()
        assertThat(stats.low).isNull()
        assertThat(stats.change).isNull()
    }

    // ---- 月汇总（MonthBanner:455-458 + fatMonth:610-614）----

    @Test
    fun `月汇总_月内首末差与体脂哨兵`() {
        val q = query()
        val summaries = q.monthlySummaries(
            listOf(
                rec(ts(9, 5, 8), 80.0, 22.0),
                rec(ts(9, 20, 8), 78.0, -1.0),   // 末记录体脂未计算 → fatDelta null
                rec(ts(10, 1, 8), 75.0, 20.0),
                rec(ts(10, 15, 8), 72.0, 19.0),
            )
        )
        assertThat(summaries.map { it.yearMonth }).containsExactly("2026-10", "2026-09").inOrder()
        val oct = summaries[0]
        assertThat(oct.weightLoss).isWithin(1e-9).of(3.0)   // 75 − 72 共减重
        assertThat(oct.fatDelta).isWithin(1e-9).of(1.0)      // 20 − 19 减脂
        val sep = summaries[1]
        assertThat(sep.weightLoss).isWithin(1e-9).of(2.0)    // 80 − 78
        assertThat(sep.fatDelta).isNull()
    }

    // ---- 数据解读（原型 trends 屏 insight 卡）----

    @Test
    fun `解读_下降_给方向与幅度`() {
        val reading = trendReadingOf(
            listOf(TrendPoint(ts(10, 10, 8), 72.0), TrendPoint(ts(10, 16, 8), 70.5)),
        )!!
        assertThat(reading.direction).isEqualTo(TrendDirection.DOWN)
        assertThat(reading.magnitude).isWithin(1e-9).of(1.5)
        assertThat(reading.dayCount).isEqualTo(2)
    }

    @Test
    fun `解读_上升与平稳阈值`() {
        assertThat(
            trendReadingOf(listOf(TrendPoint(1L, 70.0), TrendPoint(2L, 71.2)))!!.direction,
        ).isEqualTo(TrendDirection.UP)
        // |变化| < 0.1 视为噪声级平稳
        assertThat(
            trendReadingOf(listOf(TrendPoint(1L, 70.0), TrendPoint(2L, 70.05)))!!.direction,
        ).isEqualTo(TrendDirection.FLAT)
        // 单点：首末同值，平稳
        assertThat(trendReadingOf(listOf(TrendPoint(1L, 70.0)))!!.direction).isEqualTo(TrendDirection.FLAT)
    }

    @Test
    fun `解读_空点为null`() {
        assertThat(trendReadingOf(emptyList())).isNull()
    }

    // ---- 周期对比（原型趋势对比卡 two-col）----

    @Test
    fun `对比_近7天取前一等长7天窗`() = runTest {
        // NOW=10-16 15:00；本窗 10/10..10/16（70,72），上窗 10/03..10/09（80,60）
        val q = query(
            rec(ts(10, 3, 8), 80.0), rec(ts(10, 9, 8), 60.0),
            rec(ts(10, 10, 8), 70.0), rec(ts(10, 16, 8), 72.0),
        )
        val c = q.comparison(TrendRange.LAST_7, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(c.current).isWithin(1e-9).of(71.0)
        assertThat(c.previous).isWithin(1e-9).of(70.0)
    }

    @Test
    fun `对比_今天取昨天为上一窗`() = runTest {
        val q = query(rec(ts(10, 15, 8), 69.0), rec(ts(10, 16, 9), 71.0))
        val c = q.comparison(TrendRange.TODAY, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(c.current).isWithin(1e-9).of(71.0)
        assertThat(c.previous).isWithin(1e-9).of(69.0)
    }

    @Test
    fun `对比_无更早数据上窗为null_全部范围无上周期`() = runTest {
        val q = query(rec(ts(10, 16, 8), 70.0))
        val c7 = q.comparison(TrendRange.LAST_7, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(c7.current).isWithin(1e-9).of(70.0)
        assertThat(c7.previous).isNull()
        val call = q.comparison(TrendRange.ALL, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(call.current).isNull()
        assertThat(call.previous).isNull()
    }

    @Test
    fun `block_一次取数三件齐`() = runTest {
        val q = query(rec(ts(10, 10, 8), 72.0), rec(ts(10, 16, 8), 70.5))
        val read = q.block(TrendRange.LAST_7, TrendSegment.ALL_DAY, TrendSeriesKind.WEIGHT)
        assertThat(read.stats.points).hasSize(2)
        assertThat(read.reading!!.direction).isEqualTo(TrendDirection.DOWN)
        assertThat(read.comparison.current).isWithin(1e-9).of(71.25)
        assertThat(read.comparison.previous).isNull()
    }
}
