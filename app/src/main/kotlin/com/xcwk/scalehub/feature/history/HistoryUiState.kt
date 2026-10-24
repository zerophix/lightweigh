package com.xcwk.scalehub.feature.history

import com.xcwk.scalehub.application.query.TrendSegment

/** 历史行（展示模型）。 */
data class HistoryRowUi(
    val time: Long,
    val timeText: String,      // "MM-dd HH:mm"
    val weightText: String,    // "%.1f"
    val fatText: String,       // "18.4%"，未计算 "--"
    val deltaText: String?,    // 较升序前一条的体重差（带符号），首条 null
)

/** 月分组（旧库 MonthBanner：标题含月内增减，行可展开）。 */
data class HistoryMonthUi(
    val month: String,         // 分组键 "2026-10"
    val titleText: String,     // "2026年10月: 共减重3.0公斤  减脂1.0%"
    val expanded: Boolean,
    val rows: List<HistoryRowUi>,
)

data class HistoryUiState(
    val loading: Boolean = true,
    val segment: TrendSegment = TrendSegment.ALL_DAY,
    val months: List<HistoryMonthUi> = emptyList(),
    val emptyText: String? = null,
    /** 非 null 时显示删除确认对话框（需求：删除前确认）。 */
    val confirmDeleteTime: Long? = null,
    val deleting: Boolean = false,
    val error: String? = null,
    /** 按日筛选（M4）："yyyy-MM-dd"；null=按月视图。 */
    val selectedDay: String? = null,
    val dayRows: List<HistoryRowUi> = emptyList(),
    val dayHeader: String? = null,
)

sealed interface HistoryIntent {
    data class SegmentChanged(val segment: TrendSegment) : HistoryIntent
    data class ToggleMonth(val month: String) : HistoryIntent
    data class AskDelete(val time: Long) : HistoryIntent
    data object CancelDelete : HistoryIntent
    data object ConfirmDelete : HistoryIntent
    data class PickDay(val day: String) : HistoryIntent
    data object ClearDay : HistoryIntent
}

/**
 * 导出文本（M4，需求：历史记录可导出）——纯展示格式化，零业务。
 * 按日视图导出当日；按月视图导出全部月份与行（含收起的）。
 */
fun buildExportText(state: HistoryUiState): String = buildString {
    appendLine("轻衡健康 · 测量记录导出")
    if (state.selectedDay != null) {
        appendLine(state.dayHeader ?: state.selectedDay)
        state.dayRows.forEach { appendLine(rowLine(it)) }
    } else {
        state.months.forEach { month ->
            appendLine(month.titleText)
            month.rows.forEach { appendLine(rowLine(it)) }
        }
    }
}.trimEnd()

private fun rowLine(row: HistoryRowUi): String =
    "  ${row.timeText}  ${row.weightText} kg  体脂 ${row.fatText}" +
        (row.deltaText?.let { "  较上条 $it kg" } ?: "")
