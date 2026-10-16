package com.xcwk.scalehub.infrastructure.persistent.repository

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.MeasureRecord
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 端口实现测试 — 最新在前、同时间戳覆盖。 */
class InMemoryMeasurementRepositoryTest {

    private fun record(at: Long) =
        MeasureRecord(at, 70.0, 500.0, 18.1, "AA:BB", "builtin-1")

    @Test
    fun `recent按时间倒序且限数`() = runTest {
        val repo = InMemoryMeasurementRepository()
        repo.save(record(1000L)); repo.save(record(3000L)); repo.save(record(2000L))
        assertThat(repo.recent(2).map { it.time }).containsExactly(3000L, 2000L).inOrder()
    }

    @Test
    fun `同时间戳save幂等覆盖`() = runTest {
        val repo = InMemoryMeasurementRepository()
        repo.save(record(1000L))
        repo.save(record(1000L).let { MeasureRecord(it.time, 75.0, 0.0, -1.0, "", "builtin-1") })
        assertThat(repo.recent(10)).hasSize(1)
        assertThat(repo.recent(10)[0].weight).isEqualTo(75.0)
    }
}
