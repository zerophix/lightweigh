package com.xcwk.scalehub.feature.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.query.TrendBlockRead
import com.xcwk.scalehub.application.query.TrendDirection
import com.xcwk.scalehub.application.query.TrendQuery
import com.xcwk.scalehub.application.query.TrendSeriesKind
import com.xcwk.scalehub.application.query.TrendReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * feature.trend — UDF 四件套之 ViewModel：只调 TrendQuery 与做展示格式化，
 * 业务规则（日末/范围/时段/统计/月汇总）全部在 application 层（DEC-021/DEC-022）。
 * 日期渲染使用注入时区，保证 JVM 测试确定性。
 */
class TrendViewModel(
    private val query: TrendQuery,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val refreshBus: DataRefreshBus? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(TrendUiState())
    val state: StateFlow<TrendUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
        // 只关心测量数据：本页读 TrendQuery → MeasurementRepository，
        // 健康日志/档案变更与它无关（DEC-034 组 A，原来收到任何变更都全量重查）
        refreshBus?.changed
            ?.filter { it in TREND_RELEVANT }
            ?.let { signal ->
                viewModelScope.launch { signal.collect { refresh() } }
            }
    }

    fun onIntent(intent: TrendIntent) {
        when (intent) {
            is TrendIntent.RangeChanged -> {
                _state.update { it.copy(range = intent.range) }
                reload()
            }
            is TrendIntent.SegmentChanged -> {
                _state.update { it.copy(segment = intent.segment) }
                reload()
            }
        }
    }

    private fun reload() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val s = _state.value
        val weight = query.block(s.range, s.segment, TrendSeriesKind.WEIGHT)
        val fat = query.block(s.range, s.segment, TrendSeriesKind.FAT)
        _state.update {
            it.copy(
                loading = false,
                weight = toUi(weight, "体重", "公斤"),
                fat = toUi(fat, "体脂", "%"),
            )
        }
    }

    private fun toUi(read: TrendBlockRead, label: String, unit: String): TrendBlockUi {
        val stats = read.stats
        if (stats.points.isEmpty()) {
            return TrendBlockUi(
                points = emptyList(), caption = "",
                highValue = null, highDay = null, lowValue = null, lowDay = null,
                changeValue = null, emptyText = "这段时间还没有${label}数据",
            )
        }
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(zone)
        val day = java.time.format.DateTimeFormatter.ofPattern("MM/dd").withZone(zone)
        val first = stats.points.first()
        val last = stats.points.last()
        val high = stats.high!!
        val low = stats.low!!
        return TrendBlockUi(
            points = stats.points.map { TrendPointUi(it.time, it.value) },
            caption = "${fmt.format(Instant.ofEpochMilli(first.time))} ~ ${fmt.format(Instant.ofEpochMilli(last.time))}",
            highValue = one(high.value),
            highDay = day.format(Instant.ofEpochMilli(high.time)),
            lowValue = one(low.value),
            lowDay = day.format(Instant.ofEpochMilli(low.time)),
            changeValue = stats.change?.let { signed(it) + unit },
            emptyText = null,
            insightDirection = read.reading?.direction,
            insightTitle = read.reading?.let { insightTitleOf(it) },
            insightText = read.reading?.let { insightOf(it, label, unit) },
            periodAvgText = read.comparison.current?.let { "${one(it)} $unit" },
            prevPeriodAvgText = read.comparison.previous?.let { "${one(it)} $unit" },
        )
    }

    /** 数据解读行标题（原型 insight 首行口径）：单点不宣称趋势。 */
    private fun insightTitleOf(reading: TrendReading): String =
        if (reading.dayCount <= 1) {
            "记录还太少"
        } else {
            when (reading.direction) {
                TrendDirection.DOWN -> "整体呈下降趋势"
                TrendDirection.UP -> "整体呈上升趋势"
                TrendDirection.FLAT -> "整体趋势平稳"
            }
        }

    /** 数据解读句子（原型 insight 口径）：平稳只说天数，升降带幅度；单点不硬读趋势。 */
    private fun insightOf(reading: TrendReading, label: String, unit: String): String =
        if (reading.dayCount <= 1) {
            "最近 1 次测量，多记录几天才能看出趋势"
        } else {
            when (reading.direction) {
                TrendDirection.FLAT -> "近 ${reading.dayCount} 天${label}基本持平"
                TrendDirection.DOWN -> "近 ${reading.dayCount} 天${label}下降 ${one(reading.magnitude)} $unit"
                TrendDirection.UP -> "近 ${reading.dayCount} 天${label}上升 ${one(reading.magnitude)} $unit"
            }
        }

    /** 旧库格式口径（TrendPages: one/signed）。 */
    private fun one(value: Double): String = "%.1f".format(Locale.US, value)
    private fun signed(value: Double): String = if (value > 0) "+${one(value)}" else one(value)

    companion object {
        /** 本页关心的数据变更：趋势图只画测量记录。 */
        private val TREND_RELEVANT = setOf(DataChange.Measurements, DataChange.MemberScopeChanged)

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                TrendViewModel(app.container.trendQuery, refreshBus = app.container.dataRefreshBus)
            }
        }
    }
}
