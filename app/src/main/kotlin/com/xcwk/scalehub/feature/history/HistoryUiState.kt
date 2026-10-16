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
)

sealed interface HistoryIntent {
    data class SegmentChanged(val segment: TrendSegment) : HistoryIntent
    data class ToggleMonth(val month: String) : HistoryIntent
    data class AskDelete(val time: Long) : HistoryIntent
    data object CancelDelete : HistoryIntent
    data object ConfirmDelete : HistoryIntent
}
