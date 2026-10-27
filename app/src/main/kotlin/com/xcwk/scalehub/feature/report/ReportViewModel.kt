package com.xcwk.scalehub.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.query.BMI_LABELS
import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.application.query.ReportQuery
import com.xcwk.scalehub.domain.service.BodyCompositionService
import com.xcwk.scalehub.domain.service.BodyShapeClassificationService
import com.xcwk.scalehub.domain.service.HealthAdviceService
import com.xcwk.scalehub.domain.service.HealthRiskAssessmentService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * report — UDF 四件套之 ViewModel：规则全部来自 BodyAnalysis（旧库 BodyMetrics
 * 全套：评分/建议/体型/风险/控制/十八项指标），本层只做展示格式化与状态编排。
 */
class ReportViewModel(
    private val reportQuery: ReportQuery,
    private val queryPort: MeasurementQueryPort,
    private val time: Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
    isFormalAccount: () -> Boolean = { true },
) : ViewModel() {

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(aiEnabled = isFormalAccount()) }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val report = reportQuery.report(time)
        if (report == null) {
            _state.update { it.copy(loading = false, found = false) }
            return
        }
        val analysis = report.analysis
        val record = report.record
        val bmiMetric = analysis.metrics.firstOrNull { it.name == "BMI" }
        val muscleRateMetric = analysis.metrics.firstOrNull { it.name == "肌肉率" }

        // 近期序列（最多 11 条，含当前记录，时间正序）
        val recent = queryPort.between(0L, time + 1).takeLast(11)
        val recentWeights = recent.map { it.weight }
        val recentFats = recent.mapNotNull { it.fat.takeIf { f -> f >= 0 } }

        _state.update {
            it.copy(
                loading = false,
                found = true,
                timeText = fmt("yyyy/MM/dd HH:mm").format(Instant.ofEpochMilli(record.time)),
                weightText = one(record.weight),
                deltaLine = deltaLineOf(analysis.prevWeight, analysis.prevFat, record),
                score = analysis.score,
                scoreDelta = report.scoreDelta,
                advice = analysis.advice,
                bodyShape = analysis.bodyShape,
                risks = analysis.risks.map { r -> ReportRiskUi(r.name, r.levelText, r.tone) },
                bmiText = bmiMetric?.valueText ?: "--",
                bmiLabel = analysis.bmiLevel?.let { BMI_LABELS[it] },
                metrics = analysis.metrics.map { m ->
                    ReportMetricUi(m.name, m.valueText, m.unit, m.status, m.tone, m.delta)
                },
                fatControlText = analysis.fatControl?.let { signedOne(it) },
                muscleControlText = analysis.muscleControl?.let { signedOne(it) },
                standardWeightText = analysis.standardWeight?.let { one(it) },
                missingText = analysis.missing
                    .takeIf { list -> list.isNotEmpty() }
                    ?.let { list -> "完善「${list.joinToString("、")}」档案，可解锁更完整的指标分析" },
                disclaimer = report.disclaimer,
                bmiLevel = analysis.bmiLevel ?: 1,
                recentWeights = recentWeights,
                recentFats = recentFats,
                goalRows = goalRowsOf(record.male ?: true, analysis.bmi, record.fat, muscleRateMetric?.valueText?.toDoubleOrNull(), analysis.standardWeight, record.weight),
            )
        }
    }

    /** 目标达成行（旧库 Pages.kt:802-816 口径）。 */
    private fun goalRowsOf(
        male: Boolean,
        bmi: Double?,
        fat: Double?,
        muscleRate: Double?,
        stdWeight: Double?,
        weight: Double,
    ): List<ReportGoalRow> {
        val rows = mutableListOf<ReportGoalRow>()
        // 体重：目标 = 标准体重 × 0.9 ~ × 1.1
        if (stdWeight != null && stdWeight > 0) {
            val low = stdWeight * 0.9
            val high = stdWeight * 1.1
            rows += ReportGoalRow("体重", one(weight), weight in low..high, "${one(low)}–${one(high)} 公斤")
        }
        // BMI：18.5–24.0
        if (bmi != null && bmi > 0) {
            rows += ReportGoalRow("BMI", one(bmi), bmi in 18.5..24.0, "18.5–24.0")
        }
        // 体脂率：男 10–20 / 女 18–28
        if (fat != null && fat >= 0) {
            val (low, high) = if (male) 10.0 to 20.0 else 18.0 to 28.0
            rows += ReportGoalRow("体脂率", one(fat), fat in low..high, "${one(low)}–${one(high)} %")
        }
        // 肌肉率：男 32–56 / 女 28–52
        if (muscleRate != null && muscleRate > 0) {
            val (low, high) = if (male) 32.0 to 56.0 else 28.0 to 52.0
            rows += ReportGoalRow("肌肉率", one(muscleRate), muscleRate in low..high, "${one(low)}–${one(high)} %")
        }
        return rows
    }

    /** Pages:663 —— 双端有效给带符号值，否则 "--"。 */
    private fun deltaLineOf(prevWeight: Double?, prevFat: Double?, record: com.xcwk.scalehub.domain.model.MeasureRecord): String {
        val weightDiff = prevWeight?.let { "%+.1f".format(Locale.US, record.weight - it) }
        val fatDiff = if (record.fat >= 0 && prevFat != null) {
            "%+.1f".format(Locale.US, record.fat - prevFat)
        } else {
            null
        }
        if (weightDiff == null && fatDiff == null) return "首次记录，暂无对比"
        val parts = buildList {
            weightDiff?.let { add("体重 $it 公斤") }
            fatDiff?.let { add("体脂率 $it %") }
        }
        return "比上次  " + parts.joinToString("  ")
    }

    private fun fmt(pattern: String) = java.time.format.DateTimeFormatter.ofPattern(pattern).withZone(zone)
    private fun one(value: Double): String = "%.1f".format(Locale.US, value)
    private fun signedOne(value: Double): String = if (value > 0) "+${one(value)}" else one(value)

    companion object {
        fun Factory(time: Long, zone: ZoneId = ZoneId.systemDefault()) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                val bodyCompService = BodyCompositionService()
                val riskService = HealthRiskAssessmentService()
                val shapeService = BodyShapeClassificationService()
                val adviceService = HealthAdviceService()
                ReportViewModel(
                    ReportQuery(
                        app.container.measurementQueryPort,
                        bodyCompService, riskService, shapeService, adviceService
                    ),
                    app.container.measurementQueryPort,
                    time, zone,
                    isFormalAccount = app.container.isFormalAccount,
                )
            }
        }
    }
}