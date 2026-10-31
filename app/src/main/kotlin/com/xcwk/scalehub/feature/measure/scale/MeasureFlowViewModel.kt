package com.xcwk.scalehub.feature.measure.scale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.ai.AiService
import com.xcwk.scalehub.application.format.QkFormat
import com.xcwk.scalehub.application.query.MeasurementQuery
import com.xcwk.scalehub.application.service.DeleteMeasurementService
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 测量完成后的全屏覆盖步骤（原型 03-measure：v-result / v-ai-report）。 */
enum class MeasureFlowStep { Result, Report }

/**
 * v-result / v-ai-report 的呈现状态。
 *
 * 差值口径：`*Change` 为「本次 − 上次」，null 表示没有可对比的上次记录或该指标上次未测出；
 * 界面据此显示「较上次 …」或「暂无对比」，不造数。
 */
data class MeasureFlowState(
    val step: MeasureFlowStep = MeasureFlowStep.Result,
    val loading: Boolean = true,
    val timeMillis: Long = 0L,
    val timeText: String = "",
    val memberName: String = "",
    val weightText: String = "",
    val weightUnit: String = "kg",
    val weightChange: Double? = null,
    val fatPercent: Double? = null,
    val fatChange: Double? = null,
    val bmiText: String? = null,
    val hasPrev: Boolean = false,
    val dupPrevTime: Long? = null,
    val dupPrevText: String = "",
    val reportRunning: Boolean = false,
    val reportText: String? = null,
    val reportError: String? = null,
)

/**
 * T-118 测量流程覆盖层的数据宿主：SAVED 后读上次记录算真实差值、
 * 判定 3 分钟重复测量、按需生成 AI 解读。
 *
 * 与 ScaleViewModel 分工：ScaleViewModel 只宿主 BLE 会话三相六态；
 * 本 ViewModel 只负责「保存之后」的结果呈现与报告，两者状态互不写入。
 */
class MeasureFlowViewModel(
    private val measurementQuery: MeasurementQuery,
    private val deleteService: DeleteMeasurementService,
    private val aiService: AiService,
    private val profileReader: MemberProfileReader,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _flow = MutableStateFlow(MeasureFlowState())
    val flow: StateFlow<MeasureFlowState> = _flow.asStateFlow()

    private var capturedTime: Long? = null
    private var reportJob: Job? = null

    /** 会话 SAVED 终态由 ScaleRoute 回调注入（只喂事件，不反向持有会话）。 */
    fun onSaved(timeMillis: Long, weightKg: Double) {
        if (capturedTime == timeMillis) return
        capturedTime = timeMillis
        viewModelScope.launch { captureResult(timeMillis, weightKg) }
    }

    private suspend fun captureResult(time: Long, weightKg: Double) {
        val current = measurementQuery.recent(1).firstOrNull { it.time == time }
        val prev = prevOf(time)
        val unitPrefs = profileReader.unitPreferences()
        val curFat = current?.fat?.takeIf { it >= 0 }
        _flow.update {
            it.copy(
                loading = false,
                step = MeasureFlowStep.Result,
                timeMillis = time,
                timeText = formatTime(time),
                memberName = profileReader.nickname().ifBlank { "本人" },
                weightText = QkFormat.weightStr(weightKg, unitPrefs),
                weightUnit = QkFormat.weightUnitLabel(unitPrefs),
                weightChange = prev?.let { p -> weightKg - p.weight },
                fatPercent = curFat,
                fatChange = curFat?.let { c -> prev?.fat?.takeIf { it >= 0 }?.let { c - it } },
                bmiText = current?.let { bmiText(it) },
                hasPrev = prev != null,
                dupPrevTime = prev?.time?.takeIf { p -> time - p in 1 until DUP_WINDOW_MS },
                dupPrevText = prev?.let { formatTime(it.time) } ?: "",
            )
        }
    }

    /** 上次记录：最近两条中除本次外的最早一条。 */
    private suspend fun prevOf(time: Long): MeasureRecord? =
        measurementQuery.recent(2).filter { it.time != time }.minByOrNull { it.time }

    private fun bmiText(record: MeasureRecord): String? {
        val heightM = record.heightCm?.div(100.0) ?: profileReader.heightCm().div(100.0)
        if (heightM <= 0.0 || record.weight <= 0.0) return null
        return String.format(java.util.Locale.US, "%.1f", record.weight / (heightM * heightM))
    }

    private fun formatTime(time: Long) =
        DateTimeFormatter.ofPattern(FLOW_TIME_PATTERN).withZone(zone).format(Instant.ofEpochMilli(time))

    fun openReport() {
        _flow.update { it.copy(step = MeasureFlowStep.Report) }
        if (!_flow.value.reportRunning && _flow.value.reportText == null && _flow.value.reportError == null) {
            generateReport()
        }
    }

    fun backToResult() {
        _flow.update { it.copy(step = MeasureFlowStep.Result) }
    }

    fun retryReport() = generateReport()

    private fun generateReport() {
        val snap = _flow.value
        reportJob?.cancel()
        reportJob = viewModelScope.launch {
            _flow.update { it.copy(reportRunning = true, reportError = null) }
            val outcome = aiService.ask(listOf(buildReportQuestion(snap) to true))
            _flow.update {
                if (outcome.ok) {
                    it.copy(reportRunning = false, reportText = outcome.message)
                } else {
                    it.copy(reportRunning = false, reportError = outcome.message)
                }
            }
        }
    }

    private fun buildReportQuestion(snap: MeasureFlowState): String = buildString {
        append("请解读这次身体成分测量")
        if (snap.hasPrev) {
            append("与上次的变化")
            snap.weightChange?.let { append("。体重较上次${if (it < 0) "下降" else "上升"} ${it.absoluteValue} 公斤") }
            snap.fatChange?.let { append("，体脂率较上次${if (it < 0) "下降" else "上升"} ${it.absoluteValue} 个百分点") }
        } else {
            append("（首次记录，没有可对比的历史数据）")
        }
        append("。本次体重 ${snap.weightText} ${snap.weightUnit}")
        snap.fatPercent?.let { append("，体脂率 ${String.format(java.util.Locale.US, "%.1f", it)}%") }
    }

    /** 「替换上次记录」= 删除上一条：本次已是新记录，删旧即达成替换语义。 */
    fun replacePrev() {
        val prev = _flow.value.dupPrevTime ?: return
        viewModelScope.launch {
            runCatching { deleteService.execute(prev) }
            _flow.update { it.copy(dupPrevTime = null) }
        }
    }

    /** 「放弃本次」= 删除刚保存的记录并关闭覆盖层。 */
    fun discardCurrent(onClosed: () -> Unit) {
        val time = _flow.value.timeMillis
        viewModelScope.launch {
            if (time > 0L) runCatching { deleteService.execute(time) }
            reset()
            onClosed()
        }
    }

    fun closeFlow() {
        reset()
    }

    private fun reset() {
        capturedTime = null
        _flow.update { MeasureFlowState(loading = false) }
    }

    companion object {
        /** 3 分钟内重复测量判定窗口（原型 dlg-dup 同值）。 */
        const val DUP_WINDOW_MS = 3 * 60 * 1000L

        private const val FLOW_TIME_PATTERN = "M月d日 HH:mm"

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                val container = app.container
                MeasureFlowViewModel(
                    measurementQuery = container.measurementQuery,
                    deleteService = container.deleteMeasurementService,
                    aiService = container.aiService,
                    profileReader = container.profileReader,
                )
            }
        }
    }
}
