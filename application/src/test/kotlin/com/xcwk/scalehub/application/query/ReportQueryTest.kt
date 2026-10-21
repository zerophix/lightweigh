package com.xcwk.scalehub.application.query

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 体测报告读模型（BodyMetrics 全套规则入 BodyAnalysis 后的组装口径）。
 */
class ReportQueryTest {

    private class FakeRepo(private val rows: List<MeasureRecord>) : MeasurementRepository {
        override suspend fun save(record: MeasureRecord, memberId: String) = throw UnsupportedOperationException()
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
        val report = ReportQuery(FakeRepo(listOf(fullRecord()))).report(1000L)!!
        assertThat(report.analysis.bmi).isWithin(0.05).of(22.9)
        assertThat(report.analysis.bmiLevel).isEqualTo(1)
        assertThat(BMI_LABELS[report.analysis.bmiLevel!!]).isEqualTo("标准")
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
        val analysis = q.report(1000L)!!.analysis
        assertThat(analysis.prevWeight).isWithin(1e-9).of(73.0)
        assertThat(analysis.prevFat).isWithin(1e-9).of(20.0)
    }

    @Test
    fun `快照缺失_诚实缺档与未录入指标`() = runTest {
        val legacy = MeasureRecord(1000L, 70.0, 500.0, 18.4, "", "okok-bia-1")
        val analysis = ReportQuery(FakeRepo(listOf(legacy))).report(1000L)!!.analysis
        assertThat(analysis.missing).containsExactly("身高", "出生年")
        val byName = analysis.metrics.associate { it.name to it }
        assertThat(byName["BMI"]!!.valueText).isEqualTo("--")
        assertThat(byName["内脏脂肪"]!!.valueText).isEqualTo("--")
        assertThat(byName["肌肉率"]!!.status).isEqualTo("未录入")
        // 自解释语义：okok 路径需年龄 → 跳过，不借当前档案
        assertThat(analysis.score).isNull()
        assertThat(analysis.bodyShape).isNull()
    }

    @Test
    fun `无阻抗_只保留体重和BMI口径`() = runTest {
        val noZ = MeasureRecord(1000L, 70.0, 0.0, -1.0, "", "okok-bia-1", true, 30, 175)
        val report = ReportQuery(FakeRepo(listOf(noZ))).report(1000L)!!
        val names = report.analysis.metrics.map { it.name }
        assertThat(names).containsExactly("BMI", "肥胖度", "身高", "实际年龄").inOrder()
        assertThat(report.disclaimer).contains("没有阻抗")
        assertThat(report.analysis.missing).isEmpty()
    }

    @Test
    fun `builtin算法_通用估算路径产出体成分`() = runTest {
        val builtin = MeasureRecord(1000L, 70.0, 500.0, 18.4, "", "builtin-1", true, 30, 175)
        val analysis = ReportQuery(FakeRepo(listOf(builtin))).report(1000L)!!.analysis
        val byName = analysis.metrics.associate { it.name to it }
        // 通用估算：内脏脂肪 = 体脂率*0.35（BodyMetrics:106）
        assertThat(byName["内脏脂肪"]!!.valueText).isNotEqualTo("--")
        assertThat(byName["水分率"]!!.valueText).isNotEqualTo("--")
        assertThat(byName["去脂体重"]!!.valueText).isNotEqualTo("--")
    }

    @Test
    fun `okok全输入_指标与评分产出`() = runTest {
        val analysis = ReportQuery(FakeRepo(listOf(fullRecord()))).report(1000L)!!.analysis
        val byName = analysis.metrics.associate { it.name to it }
        assertThat(byName["内脏脂肪"]!!.valueText).isNotEqualTo("--")
        assertThat(byName["肌肉率"]!!.valueText).isNotEqualTo("--")
        assertThat(byName["水分率"]!!.valueText).isNotEqualTo("--")
        assertThat(byName["骨量"]!!.valueText).isNotEqualTo("--")
        assertThat(analysis.score).isNotNull()
        assertThat(analysis.risks.map { it.name }).contains("脂肪肝")   // 有阻抗才有
        assertThat(analysis.advice).isNotEmpty()
    }

    @Test
    fun `不存在的时间_返回null`() = runTest {
        assertThat(ReportQuery(FakeRepo(listOf(fullRecord()))).report(999L)).isNull()
    }

    @Test
    fun `上期评分_对prev复跑同规则并给差值`() = runTest {
        // 两条同快照 okok 有阻抗记录：BMI 22.9(70kg) vs 24.8(76kg) → 评分一高一低
        val q = ReportQuery(
            FakeRepo(
                listOf(
                    fullRecord(time = 500L, weight = 70.0),
                    fullRecord(time = 1000L, weight = 76.0),
                ),
            ),
        )
        val report = q.report(1000L)!!
        assertThat(report.prevScore).isNotNull()
        assertThat(report.analysis.score).isNotNull()
        assertThat(report.scoreDelta).isEqualTo(report.analysis.score!! - report.prevScore!!)
        assertThat(report.scoreDelta).isLessThan(0)   // 体重涨到 BMI 偏胖，评分应下降
    }

    @Test
    fun `上期评分_prev无评分时不给差值`() = runTest {
        // prev 无阻抗（手动记录）→ 无评分；当前有评分 → scoreDelta 仍为 null
        val q = ReportQuery(
            FakeRepo(
                listOf(
                    MeasureRecord(500L, 73.0, 0.0, -1.0, "", "okok-bia-1", true, 30, 175),
                    fullRecord(time = 1000L, weight = 70.0),
                ),
            ),
        )
        val report = q.report(1000L)!!
        assertThat(report.prevScore).isNull()
        assertThat(report.scoreDelta).isNull()
    }

    @Test
    fun `上期评分_当前无评分时不给差值`() = runTest {
        val q = ReportQuery(
            FakeRepo(
                listOf(
                    fullRecord(time = 500L, weight = 70.0),
                    MeasureRecord(1000L, 70.0, 0.0, -1.0, "", "okok-bia-1", true, 30, 175),
                ),
            ),
        )
        val report = q.report(1000L)!!
        assertThat(report.prevScore).isNotNull()
        assertThat(report.scoreDelta).isNull()
    }
}
