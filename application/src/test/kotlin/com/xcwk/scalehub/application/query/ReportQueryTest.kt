package com.xcwk.scalehub.application.query

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 体测报告读模型行为（BodyMetrics/Pages 规则出处见 ReportQuery 注释）。
 */
class ReportQueryTest {

    private class FakeRepo(private val rows: List<MeasureRecord>) : MeasurementRepository {
        override suspend fun save(record: MeasureRecord) = throw UnsupportedOperationException()
        override suspend fun recent(limit: Int) = rows.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            rows.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
        override suspend fun delete(time: Long) = throw UnsupportedOperationException()
    }

    /** 快照齐备 + okok 算法 + 有阻抗。 */
    private fun fullRecord(time: Long = 1000L, weight: Double = 70.0) = MeasureRecord(
        time, weight, 500.0, 18.4, "AA:BB", "okok-bia-1", true, 30, 175,
    )

    @Test
    fun `BMI与分级_阈值边界逐位`() = runTest {
        assertThat(bmiLevelOf(18.4)).isEqualTo(0)
        assertThat(bmiLevelOf(18.5)).isEqualTo(1)
        assertThat(bmiLevelOf(23.9)).isEqualTo(1)
        assertThat(bmiLevelOf(24.0)).isEqualTo(2)
        assertThat(bmiLevelOf(27.9)).isEqualTo(2)
        assertThat(bmiLevelOf(28.0)).isEqualTo(3)
        assertThat(bmiLevelOf(31.9)).isEqualTo(3)
        assertThat(bmiLevelOf(32.0)).isEqualTo(4)
        // BMI = 70 / 1.75^2 = 22.9（标准）
        val report = ReportQuery(FakeRepo(listOf(fullRecord()))).report(1000L)!!
        assertThat(report.bmi).isWithin(0.05).of(22.9)
        assertThat(report.bmiLabel).isEqualTo("标准")
    }

    @Test
    fun `上次差值_双端有效才给prev`() = runTest {
        val q = ReportQuery(
            FakeRepo(
                listOf(
                    MeasureRecord(500L, 73.0, 500.0, 20.0, "", "builtin-1", true, 30, 175),
                    fullRecord(time = 1000L, weight = 70.0),
                ),
            ),
        )
        val report = q.report(1000L)!!
        assertThat(report.prevWeight).isWithin(1e-9).of(73.0)
        assertThat(report.prevFat).isWithin(1e-9).of(20.0)
    }

    @Test
    fun `快照缺失_诚实给缺档与空指标`() = runTest {
        // 旧库式行：无快照字段（6 参构造）
        val legacy = MeasureRecord(1000L, 70.0, 500.0, 18.4, "", "okok-bia-1")
        val report = ReportQuery(FakeRepo(listOf(legacy))).report(1000L)!!
        assertThat(report.bmi).isNull()
        assertThat(report.missingProfile).containsExactly("身高", "出生年")
        // 体成分四项依赖快照 → null（自解释语义：不借当前档案补算）
        val byName = report.metrics.associate { it.name to it.value }
        assertThat(byName["水分率"]).isNull()
        assertThat(byName["肌肉率"]).isNull()
        assertThat(byName["骨量"]).isNull()
        assertThat(byName["内脏脂肪"]).isNull()
        assertThat(byName["体重"]).isNotNull()
        assertThat(byName["体脂率"]).isNotNull()
    }

    @Test
    fun `无阻抗_okok分析为空但体重体脂在`() = runTest {
        val noZ = MeasureRecord(1000L, 70.0, 0.0, -1.0, "", "okok-bia-1", true, 30, 175)
        val report = ReportQuery(FakeRepo(listOf(noZ))).report(1000L)!!
        val byName = report.metrics.associate { it.name to it.value }
        assertThat(byName["体脂率"]).isNull()          // fat=-1 未计算
        assertThat(byName["内脏脂肪"]).isNull()
        assertThat(byName["体重"]).isNotNull()
        assertThat(report.missingProfile).isEmpty()     // 快照齐备，缺档与阻抗无关
    }

    @Test
    fun `builtin算法_不产体成分分析`() = runTest {
        val builtin = MeasureRecord(1000L, 70.0, 500.0, 18.4, "", "builtin-1", true, 30, 175)
        val report = ReportQuery(FakeRepo(listOf(builtin))).report(1000L)!!
        val byName = report.metrics.associate { it.name to it.value }
        assertThat(byName["内脏脂肪"]).isNull()
        assertThat(byName["水分率"]).isNull()
        assertThat(byName["体脂率"]).isNotNull()
    }

    @Test
    fun `okok全输入_体成分指标产出`() = runTest {
        val report = ReportQuery(FakeRepo(listOf(fullRecord()))).report(1000L)!!
        val byName = report.metrics.associate { it.name to it.value }
        assertThat(byName["内脏脂肪"]).isNotNull()
        assertThat(byName["肌肉率"]).isNotNull()
        assertThat(byName["水分率"]).isNotNull()
        assertThat(byName["骨量"]).isNotNull()
    }

    @Test
    fun `不存在的时间_返回null`() = runTest {
        assertThat(ReportQuery(FakeRepo(listOf(fullRecord()))).report(999L)).isNull()
    }
}
