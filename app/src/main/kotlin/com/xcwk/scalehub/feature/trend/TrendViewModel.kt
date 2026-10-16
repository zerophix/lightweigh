package com.xcwk.scalehub.feature.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.query.TrendQuery
import com.xcwk.scalehub.application.query.TrendRange
import com.xcwk.scalehub.application.query.TrendSegment
import com.xcwk.scalehub.application.query.TrendSeriesKind
import com.xcwk.scalehub.application.query.TrendStats
import kotlinx.coroutines.flow.MutableStateFlow
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
) : ViewModel() {

    private val _state = MutableStateFlow(TrendUiState())
    val state: StateFlow<TrendUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
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
        val weight = query.stats(s.range, s.segment, TrendSeriesKind.WEIGHT)
        val fat = query.stats(s.range, s.segment, TrendSeriesKind.FAT)
        _state.update {
            it.copy(
                loading = false,
                weight = toUi(weight, "体重", "公斤"),
                fat = toUi(fat, "体脂", "%"),
            )
        }
    }

    private fun toUi(stats: TrendStats, label: String, unit: String): TrendBlockUi {
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
        )
    }

    /** 旧库格式口径（TrendPages: one/signed）。 */
    private fun one(value: Double): String = "%.1f".format(Locale.US, value)
    private fun signed(value: Double): String = if (value > 0) "+${one(value)}" else one(value)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                TrendViewModel(app.container.trendQuery)
            }
        }
    }
}
