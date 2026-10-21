package com.xcwk.scalehub.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.query.BMI_LABELS
import com.xcwk.scalehub.application.query.ReportQuery
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
    private val time: Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
    isFormalAccount: () -> Boolean = { true },
) : ViewModel() {

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        // AI 解读仅对正式账号开放（旧库口径：游客数据不送出本机）
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
        val bmiMetric = analysis.metrics.firstOrNull { it.name == "BMI" }
        _state.update {
            it.copy(
                loading = false,
                found = true,
                timeText = fmt("yyyy/MM/dd HH:mm").format(Instant.ofEpochMilli(report.record.time)),
                weightText = one(report.record.weight),
                deltaLine = deltaLineOf(analysis.prevWeight, analysis.prevFat, report.record),
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
            )
        }
    }

    /** Pages:663 —— 双端有效给带符号值，否则 "--"。 */
    private fun deltaLineOf(prevWeight: Double?, prevFat: Double?, record: com.xcwk.scalehub.domain.model.MeasureRecord): String {
        val weightDiff = prevWeight?.let { "%+.1f".format(Locale.US, record.weight - it) } ?: "--"
        val prevFatValue = prevFat
        val fatDiff = if (record.fat >= 0 && prevFatValue != null) {
            "%+.1f".format(Locale.US, record.fat - prevFatValue)
        } else {
            "--"
        }
        return "比上次  体重 $weightDiff 公斤  体脂率 $fatDiff %"
    }

    private fun fmt(pattern: String) = java.time.format.DateTimeFormatter.ofPattern(pattern).withZone(zone)
    private fun one(value: Double): String = "%.1f".format(Locale.US, value)
    private fun signedOne(value: Double): String = if (value > 0) "+${one(value)}" else one(value)

    companion object {
        fun Factory(time: Long, zone: ZoneId = ZoneId.systemDefault()) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                ReportViewModel(
                    ReportQuery(app.container.measurementQueryPort), time, zone,
                    isFormalAccount = app.container.isFormalAccount,
                )
            }
        }
    }
}
