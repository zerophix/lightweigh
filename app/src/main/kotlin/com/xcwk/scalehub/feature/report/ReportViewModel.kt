package com.xcwk.scalehub.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.query.MeasurementReport
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
 * report — UDF 四件套之 ViewModel：消费 ReportQuery，格式化口径随旧库
 * （one=%.1f、delta=%+.1f）。评分/建议/体型属 BodyMetrics 规则下沉轮（披露延后）。
 */
class ReportViewModel(
    private val reportQuery: ReportQuery,
    private val time: Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onIntent(intent: ReportIntent) {
        when (intent) {
            // M2 云接入前占位：需求文档要求 AI 解读留在报告流程，按钮点亮待登录+联网
            ReportIntent.AiExplain -> Unit
        }
    }

    private suspend fun load() {
        val report = reportQuery.report(time)
        if (report == null) {
            _state.update { it.copy(loading = false, found = false) }
            return
        }
        _state.update {
            it.copy(
                loading = false,
                found = true,
                timeText = fmt("yyyy/MM/dd HH:mm").format(Instant.ofEpochMilli(report.record.time)),
                weightText = one(report.record.weight),
                deltaLine = deltaLineOf(report),
                bmiText = report.bmi?.let(::one) ?: "--",
                bmiLabel = report.bmiLabel,
                metrics = report.metrics.map { m ->
                    ReportMetricUi(m.name, m.value?.let(::one) ?: "--", m.unit)
                },
                missingText = report.missingProfile
                    .takeIf { list -> list.isNotEmpty() }
                    ?.let { list -> "完善「${list.joinToString("、")}」档案，可解锁更完整的指标分析" },
                disclaimer = if (report.record.impedance <= 0) {
                    "这次测量没有阻抗，只保留体重和 BMI，不生成体成分分析"
                } else {
                    "体重、体脂率为实测记录；其余指标基于公认公式估算，仅供参考，不构成医疗建议"
                },
            )
        }
    }

    /** Pages:663 —— 双端有效给带符号值，否则 "--"。 */
    private fun deltaLineOf(report: MeasurementReport): String {
        val weightDiff = report.prevWeight?.let { "%+.1f".format(Locale.US, report.record.weight - it) } ?: "--"
        val prevFat = report.prevFat
        val fatDiff = if (report.record.fat >= 0 && prevFat != null) {
            "%+.1f".format(Locale.US, report.record.fat - prevFat)
        } else {
            "--"
        }
        return "比上次  体重 $weightDiff 公斤  体脂率 $fatDiff %"
    }

    private fun fmt(pattern: String) = java.time.format.DateTimeFormatter.ofPattern(pattern).withZone(zone)
    private fun one(value: Double): String = "%.1f".format(Locale.US, value)

    companion object {
        fun Factory(time: Long, zone: ZoneId = ZoneId.systemDefault()) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                ReportViewModel(ReportQuery(app.container.measurementRepository), time, zone)
            }
        }
    }
}
