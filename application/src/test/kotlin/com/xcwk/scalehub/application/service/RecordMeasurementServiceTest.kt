package com.xcwk.scalehub.application.service

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.command.RecordMeasurementCommand
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 用例测试 — 内联 Fake 端口，纯 JVM 秒级（旧库此逻辑在 Activity 里不可测）。 */
class RecordMeasurementServiceTest {

    private class FakeRepo : MeasurementRepository {
        val saved = mutableListOf<MeasureRecord>()
        override suspend fun save(record: MeasureRecord) { saved += record }
        override suspend fun recent(limit: Int) = saved.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            saved.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
        override suspend fun delete(time: Long) { saved.removeAll { it.time == time } }
    }

    private val repo = FakeRepo()
    private class MutableProfileFake : MemberProfileReader {
        var heightCmValue = 175
        override fun male() = true
        override fun ageYears() = 30
        override fun heightCm() = heightCmValue
        override fun birthYear() = 1996
        override fun birthMonth() = 5
        override fun birthDay() = 20
        override fun nickname() = ""
    }
    private val profile = MutableProfileFake()
    private val service = RecordMeasurementService(repo, profile)

    @Test
    fun `正常测量算体脂并落库`() = runTest {
        val r = service.execute(RecordMeasurementCommand(70.0, 500.0, "AA:BB", "builtin-1"))
        assertThat(r.fat).isWithin(0.05).of(18.1)   // 黄金向量：男30/175/70/500
        assertThat(repo.saved).hasSize(1)
        assertThat(repo.saved[0].algorithm).isEqualTo("builtin-1")
    }

    @Test
    fun `计算输入随记录持久化`() = runTest {
        val r = service.execute(RecordMeasurementCommand(70.0, 500.0, "AA:BB", "builtin-1"))
        assertThat(r.male).isTrue()
        assertThat(r.ageYears).isEqualTo(30)
        assertThat(r.heightCm).isEqualTo(175)
    }

    @Test
    fun `存档后修改档案不改变已存记录的输入快照`() = runTest {
        val saved = service.execute(RecordMeasurementCommand(70.0, 500.0, "AA:BB", "builtin-1"))
        profile.heightCmValue = 180   // 模拟用户随后改档案
        val stored = repo.saved.first { it.time == saved.time }
        assertThat(stored.heightCm).isEqualTo(175)   // 快照仍是计算时的输入
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
