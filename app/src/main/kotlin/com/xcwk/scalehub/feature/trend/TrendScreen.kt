package com.xcwk.scalehub.feature.trend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.application.query.TrendRange
import com.xcwk.scalehub.application.query.TrendSegment

/**
 * feature.trend — 四件套之 Screen：无状态渲染 + 事件转发，不触任何数据层。
 * 样式只用 MaterialTheme 主题槽（设计 token 落地前禁止散落硬编码色值，DEC-021 支柱5）。
 */
@Composable
fun TrendScreen(state: TrendUiState, onIntent: (TrendIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("趋势", style = MaterialTheme.typography.headlineSmall)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            TrendRange.entries.forEach { r ->
                Chip(label(r), state.range == r) { onIntent(TrendIntent.RangeChanged(r)) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TrendSegment.entries.forEach { s ->
                Chip(label(s), state.segment == s) { onIntent(TrendIntent.SegmentChanged(s)) }
            }
        }
        state.weight?.let { Block("体重趋势", it, Modifier.padding(top = 20.dp)) }
        state.fat?.let { Block("体脂率趋势", it, Modifier.padding(top = 20.dp)) }
    }
}

@Composable
private fun Block(title: String, block: TrendBlockUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(block.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (block.emptyText != null) {
            Text(block.emptyText, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // TODO M4 视觉轮：面积图 Canvas（旧库 AreaTrendChart 等价物）
            Text(
                "最高 ${block.highValue} (${block.highDay}) · 最低 ${block.lowValue} (${block.lowDay}) · 变化 ${block.changeValue}",
                modifier = Modifier.padding(top = 8.dp),
            )
            // 图形化：待 M4 图表组件（依赖 DEC-021 task B 设计组件库）
            Text(
                block.points.joinToString("  ") { "%.1f".format(it.value) },
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(if (selected) 2.dp else 1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

private fun label(range: TrendRange): String = when (range) {
    TrendRange.TODAY -> "今天"
    TrendRange.LAST_7 -> "近7天"
    TrendRange.LAST_30 -> "近30天"
    TrendRange.ALL -> "全部"
}

private fun label(segment: TrendSegment): String = when (segment) {
    TrendSegment.ALL_DAY -> "全天"
    TrendSegment.MORNING -> "上午"
    TrendSegment.AFTERNOON -> "下午"
    TrendSegment.EVENING -> "晚上"
}

@Preview(showBackground = true)
@Composable
private fun TrendScreenPreview() {
    MaterialTheme {
        TrendScreen(
            state = TrendUiState(
                loading = false,
                weight = TrendBlockUi(
                    points = listOf(TrendPointUi(0, 70.0), TrendPointUi(0, 71.5)),
                    caption = "2026/10/10 ~ 2026/10/16",
                    highValue = "71.5", highDay = "10/16",
                    lowValue = "70.0", lowDay = "10/10",
                    changeValue = "+1.5公斤", emptyText = null,
                ),
                fat = TrendBlockUi(
                    points = emptyList(), caption = "",
                    highValue = null, highDay = null, lowValue = null, lowDay = null,
                    changeValue = null, emptyText = "这段时间还没有体脂数据",
                ),
            ),
        ) {}
    }
}
