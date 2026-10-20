package com.xcwk.scalehub.ui.components

import com.xcwk.scalehub.application.query.TrendSegment

/**
 * 时段枚举的展示文案。趋势页与历史页原本各有一份逐字相同的 when（gov-code-check
 * GOV-CODE-002 抓获），因 feature 之间不可互相依赖，故上提到共享 UI 层。
 */
fun segmentLabel(segment: TrendSegment): String = when (segment) {
    TrendSegment.ALL_DAY -> "全天"
    TrendSegment.MORNING -> "上午"
    TrendSegment.AFTERNOON -> "下午"
    TrendSegment.EVENING -> "晚上"
}
