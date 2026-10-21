package com.xcwk.scalehub.feature.trend

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.query.TrendQuery
import com.xcwk.scalehub.application.query.TrendRange
import com.xcwk.scalehub.application.query.TrendSegment
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * trend ViewModel 行为测试（T-006 首切片三关之一：ViewModel JVM 测试）。
 * 覆盖：初始化拉取、意图触发重查、空态文案（旧库 TrendPages 口径）。
 */
class TrendViewModelTest {

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        private val NOW: Long = LocalDate.of(2026, 10, 16).atTime(15, 0).atZone(ZONE).toInstant().toEpochMilli()

        @BeforeClass
        @JvmStatic
        fun setupMain() {
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }

        @AfterClass
        @JvmStatic
        fun tearDownMain() {
            Dispatchers.resetMain()
        }

        private fun ts(mo: Int, d: Int, h: Int): Long =
            LocalDate.of(2026, mo, d).atTime(h, 0).atZone(ZONE).toInstant().toEpochMilli()

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

    private fun vm(vararg rows: MeasureRecord) =
        TrendViewModel(TrendQuery(FakeRepo(rows.toList()), ZONE, { NOW }), ZONE)

    @Test
    fun `初始化按默认近7天全天拉取并完成格式化`() {
        val vm = vm(rec(ts(10, 16, 8), 71.5, 22.0))
        val s = vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.range).isEqualTo(TrendRange.LAST_7)
        assertThat(s.segment).isEqualTo(TrendSegment.ALL_DAY)
        assertThat(s.weight!!.points).hasSize(1)
        assertThat(s.weight!!.highValue).isEqualTo("71.5")
        assertThat(s.weight!!.caption).isEqualTo("2026/10/16 ~ 2026/10/16")
        assertThat(s.weight!!.changeValue).isEqualTo("0.0公斤")   // 单点=0，旧库口径
        assertThat(s.fat!!.highValue).isEqualTo("22.0")
        assertThat(s.fat!!.changeValue).isEqualTo("0.0%")
    }

    @Test
    fun `切换范围意图触发重查`() {
        val vm = vm(rec(ts(10, 15, 8), 70.0), rec(ts(10, 16, 8), 72.0))
        assertThat(vm.state.value.weight!!.points).hasSize(2)   // 近7天含昨天
        vm.onIntent(TrendIntent.RangeChanged(TrendRange.TODAY))
        val s = vm.state.value
        assertThat(s.range).isEqualTo(TrendRange.TODAY)
        assertThat(s.weight!!.points).hasSize(1)
        assertThat(s.weight!!.points[0].time).isEqualTo(ts(10, 16, 8))
    }

    @Test
    fun `体脂全未计算显示空态文案`() {
        val vm = vm(rec(ts(10, 16, 8), 70.0, -1.0))
        val s = vm.state.value
        assertThat(s.weight!!.emptyText).isNull()
        assertThat(s.fat!!.emptyText).isEqualTo("这段时间还没有体脂数据")
        assertThat(s.fat!!.points).isEmpty()
    }
}
