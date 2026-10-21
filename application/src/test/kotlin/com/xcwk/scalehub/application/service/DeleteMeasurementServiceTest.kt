package com.xcwk.scalehub.application.service

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 删除用例（薄封装，行为在仓储——此处钉编排与目标选择）。 */
class DeleteMeasurementServiceTest {

    private class FakeRepo : MeasurementRepository, MeasurementQueryPort {
        val rows = mutableListOf(
            MeasureRecord(1000L, 70.0, 0.0, -1.0, "", "builtin-1"),
            MeasureRecord(2000L, 71.0, 0.0, -1.0, "", "builtin-1"),
        )
        override suspend fun save(record: MeasureRecord, memberId: String) { rows += record }
        override suspend fun recent(limit: Int) = rows.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            rows.filter { it.time >= fromInc && it.time < toExc }
        override suspend fun delete(time: Long) { rows.removeAll { it.time == time } }
    }

    @Test
    fun `删除后发测量变更事件`() = runTest {
        val repo = FakeRepo()
        val bus = com.xcwk.scalehub.application.DataRefreshBus()
        val svc = DeleteMeasurementService(repo, bus)
        val got = mutableListOf<com.xcwk.scalehub.application.DataChange>()
        val job = launch { bus.changed.collect { got += it } }
        runCurrent()
        svc.execute(1000L)
        runCurrent()
        // 增删同类型：订阅方关心「测量数据变了」，不必区分方向
        assertThat(got).containsExactly(com.xcwk.scalehub.application.DataChange.Measurements)
        job.cancel()
    }

    @Test
    fun `删除目标行其他保留`() = runTest {
        val repo = FakeRepo()
        DeleteMeasurementService(repo).execute(1000L)
        assertThat(repo.rows.map { it.time }).containsExactly(2000L)
    }

    @Test
    fun `删除不存在的时间戳无副作用`() = runTest {
        val repo = FakeRepo()
        DeleteMeasurementService(repo).execute(9999L)
        assertThat(repo.rows).hasSize(2)
    }
}
