package com.xcwk.scalehub.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.query.HistoryQuery
import com.xcwk.scalehub.application.query.TrendSegment
import com.xcwk.scalehub.application.service.DeleteMeasurementService
import com.xcwk.scalehub.domain.types.DomainException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * history — UDF 四件套之 ViewModel：只消费 HistoryQuery/DeleteMeasurementService
 * 与做展示格式化；月汇总文案口径 = 旧库 MonthBanner（TrendPages:455-458,610-614）。
 * 日期渲染注入时区保证 JVM 测试确定性（同 TrendViewModel）。
 */
class HistoryViewModel(
    private val historyQuery: HistoryQuery,
    private val deleteMeasurement: DeleteMeasurementService,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val refreshBus: DataRefreshBus? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
        refreshBus?.changed?.let { signal ->
            viewModelScope.launch { signal.collect { refresh() } }
        }
    }

    fun onIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.SegmentChanged -> {
                _state.update { it.copy(segment = intent.segment) }
                reload()
            }
            is HistoryIntent.ToggleMonth -> _state.update { s ->
                s.copy(months = s.months.map { m ->
                    if (m.month == intent.month) m.copy(expanded = !m.expanded) else m
                })
            }
            is HistoryIntent.PickDay -> {
                _state.update { it.copy(selectedDay = intent.day) }
                reload()
            }
            HistoryIntent.ClearDay -> {
                _state.update { it.copy(selectedDay = null, dayRows = emptyList(), dayHeader = null) }
                reload()
            }
            is HistoryIntent.AskDelete -> _state.update { it.copy(confirmDeleteTime = intent.time) }
            HistoryIntent.CancelDelete -> _state.update { it.copy(confirmDeleteTime = null) }
            HistoryIntent.ConfirmDelete -> confirmDelete()
        }
    }

    private fun reload() {
        viewModelScope.launch { refresh() }
    }

    private fun confirmDelete() {
        val time = _state.value.confirmDeleteTime ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true, error = null) }
            try {
                deleteMeasurement.execute(time)
                refresh()
                _state.update { it.copy(deleting = false, confirmDeleteTime = null) }
            } catch (e: DomainException) {
                _state.update { it.copy(deleting = false, error = e.message ?: "删除失败") }
            } catch (e: Exception) {
                _state.update { it.copy(deleting = false, error = e.message ?: "删除失败") }
            }
        }
    }

    private suspend fun refresh() {
        // 按日视图（M4）：选定日期即平铺当日行，忽略时段与月分组
        val day = _state.value.selectedDay
        if (day != null) {
            val dayRows = historyQuery.rows(TrendSegment.ALL_DAY)
                .filter { dayOf(it.record.time) == day }
                .map { row -> rowUi(row) }
            _state.update {
                it.copy(
                    loading = false,
                    dayRows = dayRows,
                    dayHeader = "$day · 共 ${dayRows.size} 条",
                    emptyText = if (dayRows.isEmpty()) "$day 没有测量记录" else null,
                )
            }
            return
        }
        val segment = _state.value.segment
        val rows = historyQuery.rows(segment)
        val monthly = historyQuery.monthly(segment)
        // 月在前（降序），组内行已按展示序（新→旧）；展开态默认全部收起（同旧库 collapsed 默认）
        val expandedMap = _state.value.months.associate { it.month to it.expanded }
        val months = monthly.map { summary ->
            HistoryMonthUi(
                month = summary.yearMonth,
                titleText = monthTitle(summary.yearMonth, summary),
                expanded = expandedMap[summary.yearMonth] ?: false,
                rows = rows.filter { monthOf(it.record.time) == summary.yearMonth }
                    .map { row -> rowUi(row) },
            )
        }
        _state.update {
            it.copy(
                loading = false,
                months = months,
                emptyText = if (months.isEmpty()) "这段时间还没有测量记录" else null,
            )
        }
    }

    /** 旧库 MonthBanner 文案（TrendPages:457-458 + fatMonth:610-614）。 */
    private fun monthTitle(month: String, s: com.xcwk.scalehub.application.query.MonthSummary): String {
        val (y, m) = month.split("-")
        val weightText = if (s.weightLoss >= 0) "共减重${one(s.weightLoss)}公斤" else "共增重${one(-s.weightLoss)}公斤"
        val fatPart = s.fatDelta?.let {
            if (it >= 0) "减脂${one(it)}%" else "增脂${one(-it)}%"
        } ?: "减脂--"
        return "${y}年${m}月: $weightText  $fatPart"
    }

    private fun rowUi(row: com.xcwk.scalehub.application.query.HistoryRow): HistoryRowUi =
        HistoryRowUi(
            time = row.record.time,
            timeText = fmt("MM-dd HH:mm").format(Instant.ofEpochMilli(row.record.time)),
            weightText = one(row.record.weight),
            fatText = if (row.record.fat >= 0) "${one(row.record.fat)}%" else "--",
            deltaText = row.previous?.let { p -> signed(row.record.weight - p.weight) },
        )

    private fun dayOf(time: Long): String = fmt("yyyy-MM-dd").format(Instant.ofEpochMilli(time))

    private fun monthOf(time: Long): String {
        val d = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
        return "%04d-%02d".format(d.year, d.monthValue)
    }

    private fun fmt(pattern: String) = java.time.format.DateTimeFormatter.ofPattern(pattern).withZone(zone)
    private fun one(value: Double): String = "%.1f".format(Locale.US, value)
    private fun signed(value: Double): String = if (value > 0) "+${one(value)}" else one(value)

    companion object {
        fun Factory(zone: ZoneId = ZoneId.systemDefault()) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                HistoryViewModel(
                    com.xcwk.scalehub.application.query.HistoryQuery(app.container.measurementRepository, zone),
                    app.container.deleteMeasurementService,
                    zone,
                    app.container.dataRefreshBus,
                )
            }
        }
    }
}
