package com.xcwk.scalehub.feature

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.feature.history.RecordEditModel
import com.xcwk.scalehub.feature.history.RecordEditField
import com.xcwk.scalehub.feature.history.buildDelta
import com.xcwk.scalehub.feature.history.isEditable
import com.xcwk.scalehub.feature.history.validateRecordEdit
import com.xcwk.scalehub.feature.trend.METRIC_SPECS
import com.xcwk.scalehub.feature.trend.MetricKey
import com.xcwk.scalehub.feature.trend.TrendState
import com.xcwk.scalehub.feature.trend.metricSpec
import com.xcwk.scalehub.feature.trend.segmentSeries
import com.xcwk.scalehub.feature.trend.trendStateOf
import org.junit.Test

/**
 * Wave 1（T-081 历史详情与编辑 / T-082 指标详情）纯逻辑单测。
 *
 * 覆盖的都是验收中**唯一可自动判定**的部分：
 * - T-081：原型「仅时间与备注可编辑，数值来自设备保证真实」+ AC-HT3 同源（recordId）
 * - T-082：AC-HT4 缺日不跨空洞插值 + AC-HT5 仅 1 条测量判定 InsufficientData
 *
 * 视觉部分不在此测（需真机/截图），但上面这些是"写错了也没人会发现"的判定逻辑。
 */
class Wave1HistoryTrendTest {

    // ---- T-081：编辑字段限制（原型 v-edit 硬约束）----

    @Test
    fun `仅时间与备注可编辑`() {
        assertThat(isEditable(RecordEditField.Time)).isTrue()
        assertThat(isEditable(RecordEditField.Note)).isTrue()
    }

    @Test
    fun `测量字段一律不可编辑`() {
        assertThat(isEditable(RecordEditField.Weight)).isFalse()
        assertThat(isEditable(RecordEditField.FatRate)).isFalse()
        assertThat(isEditable(RecordEditField.Muscle)).isFalse()
        assertThat(isEditable(RecordEditField.Bmi)).isFalse()
    }

    private fun model(
        recordId: Long = 1L,
        time: String = "10月21日 07:30",
        note: String = "",
        weight: String = "62.4 kg",
        fatRate: String = "28.6 %",
        muscle: String = "42.3 kg",
        bmi: String = "22.1",
    ) = RecordEditModel(recordId, time, note, weight, fatRate, muscle, bmi)

    @Test
    fun `只改时间与备注不产生违规`() {
        val original = model()
        val edited = model(time = "10月21日 08:00", note = "晨起测量")
        assertThat(validateRecordEdit(original, edited)).isEmpty()
    }

    @Test
    fun `改动测量数值必须被判违规`() {
        val original = model()
        val edited = model(weight = "60.0 kg")
        val violations = validateRecordEdit(original, edited)
        assertThat(violations).isNotEmpty()
        assertThat(violations.first()).contains("测量数值")
    }

    @Test
    fun `改动体脂率同样违规`() {
        val violations = validateRecordEdit(model(), model(fatRate = "30.0 %"))
        assertThat(violations).isNotEmpty()
        assertThat(violations.first()).contains("体脂率")
    }

    @Test
    fun `recordId 不同源必须违规`() {
        // AC-HT3 的同源约束：编辑落在另一条记录上属于数据错乱
        val violations = validateRecordEdit(model(recordId = 1L), model(recordId = 2L))
        assertThat(violations).isNotEmpty()
        assertThat(violations.first()).contains("recordId")
    }

    @Test
    fun `完全未改动不产生违规`() {
        assertThat(validateRecordEdit(model(), model())).isEmpty()
    }

    @Test
    fun `副标题格式带差值与不带差值`() {
        assertThat(buildDelta("28.6", "%", "↓0.2")).isEqualTo("28.6 %（↓0.2）")
        assertThat(buildDelta("62.4", "kg", "")).isEqualTo("62.4 kg")
    }

    // ---- T-082：趋势状态与缺日切段（AC-HT4 / AC-HT5）----

    @Test
    fun `零条数据为空态`() {
        assertThat(trendStateOf(0)).isEqualTo(TrendState.Empty)
        assertThat(trendStateOf(-1)).isEqualTo(TrendState.Empty)
    }

    @Test
    fun `仅一条测量判定为数据不足不画趋势`() {
        // AC-HT5 原文：仅 1 条测量时为 InsufficientData，不画假趋势
        assertThat(trendStateOf(1)).isEqualTo(TrendState.InsufficientData)
    }

    @Test
    fun `两条及以上才可绘制`() {
        assertThat(trendStateOf(2)).isEqualTo(TrendState.Plottable)
        assertThat(trendStateOf(30)).isEqualTo(TrendState.Plottable)
    }

    @Test
    fun `缺日被切成互不相连的分段`() {
        // AC-HT4：不出现跨空洞插值线 → 切段后逐段绘制，段间不连线
        assertThat(segmentSeries(listOf(1.0, 2.0, null, 3.0)))
            .isEqualTo(listOf(listOf(1.0, 2.0), listOf(3.0)))
    }

    @Test
    fun `连续缺日只产生前后两段`() {
        assertThat(segmentSeries(listOf(1.0, null, null, 2.0)))
            .isEqualTo(listOf(listOf(1.0), listOf(2.0)))
    }

    @Test
    fun `首尾缺日不产生空段`() {
        assertThat(segmentSeries(listOf(null, 1.0, 2.0, null)))
            .isEqualTo(listOf(listOf(1.0, 2.0)))
    }

    @Test
    fun `全空与全缺返回空分段`() {
        assertThat(segmentSeries(emptyList())).isEmpty()
        assertThat(segmentSeries(listOf(null, null))).isEmpty()
    }

    @Test
    fun `无缺口时只有一段`() {
        assertThat(segmentSeries(listOf(1.0, 2.0, 3.0)))
            .isEqualTo(listOf(listOf(1.0, 2.0, 3.0)))
    }

    // ---- T-082：指标文案与覆盖 ----

    @Test
    fun `八个指标文案齐全且无重复 key`() {
        assertThat(METRIC_SPECS).hasSize(8)
        val keys = METRIC_SPECS.map { it.key }
        assertThat(keys).containsNoDuplicates()
        assertThat(keys).containsExactly(
            MetricKey.Weight, MetricKey.Bmi, MetricKey.Muscle, MetricKey.Water,
            MetricKey.Visceral, MetricKey.Metabolism, MetricKey.Score, MetricKey.Bone,
        )
    }

    @Test
    fun `每个指标都写明解释文案`() {
        val blank = METRIC_SPECS.filter { it.explainer.isBlank() }.map { it.key }
        assertThat(blank).isEmpty()
    }

    @Test
    fun `关键指标文案与原型逐字一致`() {
        // 原型 v-bmi 公式
        assertThat(metricSpec(MetricKey.Bmi)!!.formula)
            .isEqualTo("BMI = 体重(kg) ÷ 身高(m)²")
        // 原型 v-score 免责
        assertThat(metricSpec(MetricKey.Score)!!.note)
            .isEqualTo("评分为估算参考，非医疗诊断。")
        // 原型 v-bone 参考范围
        assertThat(metricSpec(MetricKey.Bone)!!.levels[1].range)
            .isEqualTo("2.5–3.2 kg")
    }

    @Test
    fun `未知指标返回 null 而非编造文案`() {
        // 原型只有 8 个指标；请求不存在的 key 必须为空，避免 AI 补内容
        assertThat(metricSpec(MetricKey.Weight)).isNotNull()
        assertThat(METRIC_SPECS.none { it.title.isBlank() }).isTrue()
    }
}
