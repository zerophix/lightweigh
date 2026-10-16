package com.xcwk.scalehub.feature.trend

import com.xcwk.scalehub.application.query.TrendRange
import com.xcwk.scalehub.application.query.TrendSegment

/** trend 点（展示模型，与查询读模型解耦——DEC-021 支柱 4）。 */
data class TrendPointUi(val time: Long, val value: Double)

/** 一个趋势块（体重或体脂）的可渲染状态；格式化在 ViewModel 完成。 */
data class TrendBlockUi(
    val points: List<TrendPointUi>,
    val caption: String,        // "yyyy/MM/dd ~ yyyy/MM/dd"
    val highValue: String?,     // "%.1f"，空集为 null
    val highDay: String?,       // "MM/dd"
    val lowValue: String?,
    val lowDay: String?,
    val changeValue: String?,   // 带符号+单位（如 +1.0公斤），空集为 null
    val emptyText: String?,     // 非 null 时渲染空态（旧库文案）
)

data class TrendUiState(
    val range: TrendRange = TrendRange.LAST_7,      // 旧库默认（TrendsScreen:90-91）
    val segment: TrendSegment = TrendSegment.ALL_DAY,
    val loading: Boolean = true,
    val weight: TrendBlockUi? = null,
    val fat: TrendBlockUi? = null,
)

sealed interface TrendIntent {
    data class RangeChanged(val range: TrendRange) : TrendIntent
    data class SegmentChanged(val segment: TrendSegment) : TrendIntent
}
