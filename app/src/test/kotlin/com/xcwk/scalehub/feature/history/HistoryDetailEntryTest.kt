package com.xcwk.scalehub.feature.history

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.query.AnalysisMetric
import com.xcwk.scalehub.application.query.BodyAnalysisResult
import com.xcwk.scalehub.application.query.MeasurementReport
import com.xcwk.scalehub.domain.model.MeasureRecord
import org.junit.Test

/**
 * 历史列表 → 测量详情 → 编辑 的接入 — 字段映射与记录同源。
 *
 * 钉的是 AC-HT3「详情与报告同源」的实现面：详情所有字段来自同一个 `recordId`
 * （= 测量时间戳），编辑页回传的 recordId 必须与详情一致；缺项按 `--` 呈现，不补零造假。
 */
class HistoryDetailEntryTest {

    private fun record(time: Long = 1_760_000_000_000L, weight: Double = 62.4, fat: Double = 28.6) =
        MeasureRecord(time, weight, 480.0, fat, "AA:BB:CC:DD:EE:FF", "builtin-1")

    private fun report(time: Long = 1_760_000_000_000L, score: Int? = 88) = MeasurementReport(
        record = record(time),
        analysis = BodyAnalysisResult(
            prevWeight = null,
            prevFat = null,
            bmi = 22.1,
            bmiLevel = 1,
            score = score,
            advice = "",
            metrics = listOf(
                AnalysisMetric("BMI", "22.1", "", "标准", 1),
                AnalysisMetric("体脂率", "28.6", "%", "标准", 1, delta = "-0.2"),
                AnalysisMetric("肌肉重量", "42.3", "公斤", "充足", 1, delta = "+0.1"),
            ),
            goodLines = emptyList(),
            warnLines = emptyList(),
            missing = emptyList(),
            bodyShape = null,
            fatControl = null,
            muscleControl = null,
            standardWeight = null,
            risks = emptyList(),
        ),
        prevScore = null,
    )

    @Test
    fun `详情字段取自同一条记录`() {
        val detail = historyDetailUiOf(report(), timeText = "10月21日 07:30")

        assertThat(detail.recordId).isEqualTo(1_760_000_000_000L)
        assertThat(detail.metaText).isEqualTo("10月21日 07:30")
        assertThat(detail.weightValue).isEqualTo("62.4")
        assertThat(detail.fatRateValue).isEqualTo("28.6")
        assertThat(detail.fatRateDelta).isEqualTo("-0.2")
        assertThat(detail.muscleValue).isEqualTo("42.3")
        assertThat(detail.scoreValue).isEqualTo("88")
    }

    @Test
    fun `缺项按占位呈现不补零`() {
        val detail = historyDetailUiOf(report(score = null), timeText = "10月21日 07:30")

        assertThat(detail.scoreValue).isEqualTo("--")
    }

    @Test
    fun `编辑页带上同一 recordId 且测量数值只读`() {
        val page = HistoryDetailPage(
            detail = historyDetailUiOf(report(), timeText = "10月21日 07:30"),
            bmiText = "22.1",
        )
        val model = recordEditModelOf(page, note = "晨起空腹")

        assertThat(model.recordId).isEqualTo(page.detail.recordId)
        assertThat(model.timeText).isEqualTo("10月21日 07:30")
        assertThat(model.weightText).isEqualTo("62.4 kg")
        assertThat(model.fatRateText).isEqualTo("28.6 %")
        assertThat(model.muscleText).isEqualTo("42.3 kg")
        assertThat(model.bmiText).isEqualTo("22.1")
        assertThat(model.note).isEqualTo("晨起空腹")
    }

    @Test
    fun `编辑页不篡改测量数值即无违规`() {
        val page = HistoryDetailPage(
            detail = historyDetailUiOf(report(), timeText = "10月21日 07:30"),
            bmiText = "22.1",
        )
        val original = recordEditModelOf(page, note = "")
        val edited = recordEditModelOf(page, note = "晨起空腹")

        assertThat(validateRecordEdit(original, edited)).isEmpty()
    }

    @Test
    fun `数据层无更新端口时给出显式说明而不是静默保存`() {
        assertThat(RECORD_EDIT_UNSUPPORTED_NOTICE).contains("不支持修改")
    }
}
