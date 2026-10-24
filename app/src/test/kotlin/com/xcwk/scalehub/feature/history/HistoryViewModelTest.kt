package com.xcwk.scalehub.feature.history

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.query.HistoryQuery
import com.xcwk.scalehub.application.query.TrendSegment
import com.xcwk.scalehub.application.service.DeleteMeasurementService
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

/** history ViewModel 行为（加载分组/时段切换/确认删除流）。 */
class HistoryViewModelTest {

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        private fun ts(mo: Int, d: Int, h: Int): Long =
            LocalDate.of(2026, mo, d).atTime(h, 0).atZone(ZONE).toInstant().toEpochMilli()

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
    }

    private class FakeRepo(rows: List<MeasureRecord>) : MeasurementRepository, MeasurementQueryPort {
        val data = rows.toMutableList()
        override suspend fun save(record: MeasureRecord, memberId: String) { data += record }
        override suspend fun recent(limit: Int) = data.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            data.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
        override suspend fun delete(time: Long) { data.removeAll { it.time == time } }
    }

    private fun rec(t: Long, weight: Double, fat: Double = -1.0) =
        MeasureRecord(t, weight, 500.0, fat, "AA:BB", "builtin-1")

    private fun vm(repo: FakeRepo) = HistoryViewModel(
        HistoryQuery(repo, ZONE, { ts(10, 16, 15) }),
        DeleteMeasurementService(repo),
        ZONE,
    )

    @Test
    fun `加载分组_月标题与行格式`() {
        val vm = vm(FakeRepo(listOf(rec(ts(10, 1, 8), 75.0, 20.0), rec(ts(10, 15, 8), 72.0, 19.0))))
        val s = vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.months).hasSize(1)
        val m = s.months[0]
        assertThat(m.month).isEqualTo("2026-10")
        assertThat(m.titleText).contains("共减重3.0公斤")
        assertThat(m.titleText).contains("减脂1.0%")
        assertThat(m.expanded).isFalse()                      // 默认收起，同旧库
        assertThat(m.rows).hasSize(2)
        assertThat(m.rows[0].weightText).isEqualTo("72.0")     // 新→旧
        assertThat(m.rows[0].deltaText).isEqualTo("-3.0")      // 72−75
    }

    @Test
    fun `时段切换触发重查`() {
        val vm = vm(FakeRepo(listOf(rec(ts(10, 1, 10), 70.0), rec(ts(10, 2, 20), 71.0))))
        assertThat(vm.state.value.months[0].rows).hasSize(2)
        vm.onIntent(HistoryIntent.SegmentChanged(TrendSegment.EVENING))
        assertThat(vm.state.value.segment).isEqualTo(TrendSegment.EVENING)
        assertThat(vm.state.value.months[0].rows).hasSize(1)
    }

    @Test
    fun `确认删除流_取消不执行_确认后刷新`() = runTest {
        val repo = FakeRepo(listOf(rec(ts(10, 1, 8), 75.0), rec(ts(10, 15, 8), 72.0)))
        val vm = vm(repo)
        val target = vm.state.value.months[0].rows[0].time

        vm.onIntent(HistoryIntent.AskDelete(target))
        assertThat(vm.state.value.confirmDeleteTime).isEqualTo(target)
        vm.onIntent(HistoryIntent.CancelDelete)
        assertThat(vm.state.value.confirmDeleteTime).isNull()
        assertThat(repo.data).hasSize(2)                     // 取消不执行

        vm.onIntent(HistoryIntent.AskDelete(target))
        vm.onIntent(HistoryIntent.ConfirmDelete)
        assertThat(vm.state.value.confirmDeleteTime).isNull()
        assertThat(repo.data).hasSize(1)
        assertThat(vm.state.value.months[0].rows).hasSize(1)
    }

    @Test
    fun `按日筛选_选中平铺当日行_清除回月视图`() {
        val vm = vm(FakeRepo(listOf(rec(ts(10, 1, 8), 75.0), rec(ts(10, 15, 8), 72.0), rec(ts(10, 15, 20), 71.5))))
        vm.onIntent(HistoryIntent.PickDay("2026-10-15"))
        var s = vm.state.value
        assertThat(s.selectedDay).isEqualTo("2026-10-15")
        assertThat(s.dayRows).hasSize(2)                      // 当日两条
        assertThat(s.dayHeader).isEqualTo("2026-10-15 · 共 2 条")
        assertThat(s.emptyText).isNull()

        vm.onIntent(HistoryIntent.ClearDay)
        s = vm.state.value
        assertThat(s.selectedDay).isNull()
        assertThat(s.dayRows).isEmpty()
        assertThat(s.months).hasSize(1)                       // 月视图恢复
    }

    @Test
    fun `按日筛选_空日给出日期级提示`() {
        val vm = vm(FakeRepo(listOf(rec(ts(10, 1, 8), 75.0))))
        vm.onIntent(HistoryIntent.PickDay("2026-10-15"))
        val s = vm.state.value
        assertThat(s.dayRows).isEmpty()
        assertThat(s.emptyText).isEqualTo("2026-10-15 没有测量记录")
    }

    @Test
    fun `导出文本_月视图含标题与行_按日只导当日`() {
        val vm = vm(FakeRepo(listOf(rec(ts(10, 1, 8), 75.0, 20.0), rec(ts(10, 15, 8), 72.0, 19.0))))
        val monthText = buildExportText(vm.state.value)
        assertThat(monthText).contains("轻衡健康 · 测量记录导出")
        assertThat(monthText).contains("2026年10月: 共减重3.0公斤")
        assertThat(monthText).contains("72.0 kg")
        assertThat(monthText).contains("体脂 19.0%")
        assertThat(monthText).contains("较上条 -3.0 kg")

        vm.onIntent(HistoryIntent.PickDay("2026-10-15"))
        val dayText = buildExportText(vm.state.value)
        assertThat(dayText).contains("2026-10-15 · 共 1 条")
        assertThat(dayText).contains("72.0 kg")
        assertThat(dayText).doesNotContain("75.0")            // 非当日不导出
    }
}
