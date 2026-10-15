package com.qingke.health.application.service

import com.google.common.truth.Truth.assertThat
import com.qingke.health.application.command.RecordMeasurementCommand
import com.qingke.health.domain.model.MeasureRecord
import com.qingke.health.domain.repository.MeasurementRepository
import com.qingke.health.domain.repository.MemberProfileReader
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 用例测试 — 内联 Fake 端口，纯 JVM 秒级（旧库此逻辑在 Activity 里不可测）。 */
class RecordMeasurementServiceTest {

    private class FakeRepo : MeasurementRepository {
        val saved = mutableListOf<MeasureRecord>()
        override suspend fun save(record: MeasureRecord) { saved += record }
        override suspend fun recent(limit: Int) = saved.sortedByDescending { it.time }.take(limit)
    }

    private val repo = FakeRepo()
    private val profile = object : MemberProfileReader {
        override fun male() = true
        override fun ageYears() = 30
        override fun heightCm() = 175
    }
    private val service = RecordMeasurementService(repo, profile)

    @Test
    fun `正常测量算体脂并落库`() = runTest {
        val r = service.execute(RecordMeasurementCommand(70.0, 500.0, "AA:BB", "builtin-1"))
        assertThat(r.fat).isWithin(0.05).of(18.1)   // 黄金向量：男30/175/70/500
        assertThat(repo.saved).hasSize(1)
        assertThat(repo.saved[0].algorithm).isEqualTo("builtin-1")
    }

    @Test
    fun `无阻抗体脂记-1不硬算`() = runTest {
        val r = service.execute(RecordMeasurementCommand(70.0, 0.0, "AA:BB", "builtin-1"))
        assertThat(r.fat).isEqualTo(-1.0)
    }

    @Test
    fun `未知算法编号计算回落builtin但落库保留原编号`() = runTest {
        val r = service.execute(RecordMeasurementCommand(70.0, 500.0, "", "garbage"))
        assertThat(r.fat).isWithin(0.05).of(18.1)
        assertThat(r.algorithm).isEqualTo("garbage")
    }
}
