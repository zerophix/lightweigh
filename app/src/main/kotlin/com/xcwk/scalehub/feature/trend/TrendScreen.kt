package com.xcwk.scalehub.feature.trend

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.application.query.TrendRange
import com.xcwk.scalehub.application.query.TrendSegment
import com.xcwk.scalehub.ui.components.QkAreaChart
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkChip
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkSegmented
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme
import java.util.Locale

/**
 * feature.trend — 四件套之 Screen：无状态渲染 + 事件转发，不触任何数据层。
 *
 * 版式按 v2 原型「趋势分析」屏：单卡内 指标（体重/体脂率）× 范围（今天/近7天/近30天/全部）
 * 两级分段控件，加本应用特有的时段筛选；随后大数字 + 增减 + 面积图 + 四格汇总
 * （平均值/最高/最低/变化）。两个指标块由 ViewModel 一并算好，这里按分段切换展示——
 * 原型同一时刻只画一条线，切换即换线，不堆叠。
 */
@Composable
fun TrendScreen(
    state: TrendUiState,
    onIntent: (TrendIntent) -> Unit,
    onOpenHistory: () -> Unit = {},
) {
    // 指标切换是控件态（切走不丢另一个块的加载结果），不进 UiState/VM
    var fatMetric by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
            // 指标：原型 metricTabs（BMI 无连续序列数据，本版只交替体重/体脂率）
            QkSegmented(
                items = listOf("体重", "体脂率"),
                selectedIndex = if (fatMetric) 1 else 0,
                onSelect = { fatMetric = it == 1 },
            )
            // 范围：原型 rangeTabs（今天为应用特有档，旧库默认近7天）
            QkSegmented(
                items = TrendRange.entries.map { rangeLabel(it) },
                selectedIndex = state.range.ordinal,
                onSelect = { onIntent(TrendIntent.RangeChanged(TrendRange.entries[it])) },
                modifier = Modifier.padding(top = 8.dp),
            )
            // 时段：应用特有筛选（旧库 segment），以胶囊行承接，不与原型两级分段混排
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                TrendSegment.entries.forEach { s ->
                    QkChip(segmentLabel(s), state.segment == s) {
                        onIntent(TrendIntent.SegmentChanged(s))
                    }
                }
            }

            val block = if (fatMetric) state.fat else state.weight
            val unit = if (fatMetric) "%" else "kg"
            if (block == null || block.emptyText != null) {
                QkEmptyHint(
                    block?.emptyText ?: "正在加载…",
                    modifier = Modifier.padding(top = QkSpacing.Md),
                )
            } else {
                TrendBlockBody(block = block, unit = unit)
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}
@Composable
private fun TrendBlockBody(block: TrendBlockUi, unit: String) {
    if (block.caption.isNotBlank()) {
        Text(
            block.caption,
            modifier = Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            one(block.points.last().value),
            style = MaterialTheme.typography.displayMedium,
            color = QkColor.Fg,
        )
        Text(
            unit,
            modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
        if (block.changeValue != null) {
            Column(
                modifier = Modifier.weight(1f).padding(bottom = 6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    "较上期",
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Muted,
                )
                Text(
                    block.changeValue,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = QkColor.Primary,
                )
            }
        }
    }
    QkAreaChart(
        points = block.points,
        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
    )
    // 四格汇总（原型 chart-summary）：平均值为本页从点序计算，其余取块内极值
    TrendSummary(
        average = one(block.points.map { it.value }.average()),
        high = block.highValue ?: "--",
        highDay = block.highDay,
        low = block.lowValue ?: "--",
        lowDay = block.lowDay,
        change = block.changeValue ?: "--",
        modifier = Modifier.padding(top = 14.dp),
    )
}

/** 汇总行（原型 .chart-summary）：四等分、上标签下数值、顶部分隔线。 */
@Composable
private fun TrendSummary(
    average: String,
    high: String,
    highDay: String?,
    low: String,
    lowDay: String?,
    change: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 11.dp)) {
        SummaryCell("平均值", average, Modifier.weight(1f))
        SummaryCell(highDay?.let { "$it 最高" } ?: "最高", high, Modifier.weight(1f))
        SummaryCell(lowDay?.let { "$it 最低" } ?: "最低", low, Modifier.weight(1f))
        SummaryCell("变化", change, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = QkColor.Muted)
        Text(value, modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
    }
}

private fun one(value: Double): String = "%.1f".format(Locale.US, value)

private fun rangeLabel(range: TrendRange): String = when (range) {
    TrendRange.TODAY -> "今天"
    TrendRange.LAST_7 -> "近7天"
    TrendRange.LAST_30 -> "近30天"
    TrendRange.ALL -> "全部"
}

private fun segmentLabel(segment: TrendSegment): String = when (segment) {
    TrendSegment.ALL_DAY -> "全天"
    TrendSegment.MORNING -> "上午"
    TrendSegment.AFTERNOON -> "下午"
    TrendSegment.EVENING -> "晚上"
}

@Preview(showBackground = true)
@Composable
private fun TrendScreenPreview() {
    QkTheme {
        TrendScreen(
            state = TrendUiState(
                loading = false,
                weight = TrendBlockUi(
                    points = listOf(TrendPointUi(0, 70.0), TrendPointUi(1, 71.5), TrendPointUi(2, 70.8)),
                    caption = "2026/10/10 ~ 2026/10/16",
                    highValue = "71.5", highDay = "10/16",
                    lowValue = "70.0", lowDay = "10/10",
                    changeValue = "-0.7公斤", emptyText = null,
                ),
                fat = TrendBlockUi(
                    points = emptyList(), caption = "",
                    highValue = null, highDay = null, lowValue = null, lowDay = null,
                    changeValue = null, emptyText = "这段时间还没有体脂数据",
                ),
            ),
            onIntent = {},
        )
    }
}
