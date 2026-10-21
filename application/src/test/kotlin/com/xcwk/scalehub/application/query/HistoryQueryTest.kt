package com.xcwk.scalehub.application.query

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 历史查询行为测试（旧库 TrendPages 历史屏规则，DEC-022）：
 * 行序/previous 配对（toRows:542-545）、时段过滤（inSlot:547-554）、
 * 按日筛选（sameDay:291）、月汇总与趋势页同源。
 */
class HistoryQueryTest {

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        private fun ts(mo: Int, d: Int, h: Int): Long =
            LocalDate.of(2026, mo, d).atTime(h, 0).atZone(ZONE).toInstant().toEpochMilli()
        private fun rec(t: Long, weight: Double = 70.0, fat: Double = -1.0) =
            MeasureRecord(t, weight, 500.0, fat, "AA:BB", "builtin-1")
    }

    private class FakeRepo(private val rows: List<MeasureRecord>) : MeasurementRepository, MeasurementQueryPort {
        val deleted = mutableListOf<Long>()
        override suspend fun save(record: MeasureRecord, memberId: String) = throw UnsupportedOperationException()
        override suspend fun recent(limit: Int) = rows.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            rows.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
        override suspend fun delete(time: Long) {
            deleted += time
        }
    }

    @Test
    fun `行序倒序展示且previous配对升序前一条`() = runTest {
        val q = HistoryQuery(FakeRepo(listOf(rec(ts(10, 1, 8), 73.5), rec(ts(10, 15, 8), 72.0))), ZONE, { ts(10, 16, 15) })
        val rows = q.rows()
        assertThat(rows.map { it.record.weight }).containsExactly(72.0, 73.5).inOrder()   // 新→旧
        assertThat(rows[0].previous!!.weight).isEqualTo(73.5)      // 新行的 previous = 升序前一条
        assertThat(rows[1].previous).isNull()                      // 最老行无 previous
    }

    @Test
    fun `按日筛选_命中当天`() = runTest {
        val q = HistoryQuery(FakeRepo(listOf(rec(ts(10, 1, 8)), rec(ts(10, 15, 8)))), ZONE, { ts(10, 16, 15) })
        val rows = q.rows(day = LocalDate.of(2026, 10, 15))
        assertThat(rows).hasSize(1)
        assertThat(rows[0].record.time).isEqualTo(ts(10, 15, 8))
    }

    @Test
    fun `时段过滤与趋势同源`() = runTest {
        val q = HistoryQuery(FakeRepo(listOf(rec(ts(10, 1, 10)), rec(ts(10, 2, 20)))), ZONE, { ts(10, 16, 15) })
        assertThat(q.rows(TrendSegment.MORNING)).hasSize(1)
        assertThat(q.rows(TrendSegment.EVENING)).hasSize(1)
        assertThat(q.rows(TrendSegment.ALL_DAY)).hasSize(2)
    }

    @Test
    fun `月汇总与趋势页同源_降序`() = runTest {
        val q = HistoryQuery(
            FakeRepo(listOf(rec(ts(9, 5, 8), 80.0), rec(ts(10, 1, 8), 75.0), rec(ts(10, 15, 8), 72.0))),
            ZONE, { ts(10, 16, 15) },
        )
        val monthly = q.monthly()
        assertThat(monthly.map { it.yearMonth }).containsExactly("2026-10", "2026-09").inOrder()
        assertThat(monthly[0].weightLoss).isWithin(1e-9).of(3.0)
    }
}
