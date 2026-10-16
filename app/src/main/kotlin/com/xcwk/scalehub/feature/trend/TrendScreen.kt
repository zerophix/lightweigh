package com.xcwk.scalehub.feature.trend

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.application.query.TrendRange
import com.xcwk.scalehub.application.query.TrendSegment
import com.xcwk.scalehub.ui.components.QkChip
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkSectionTitle
import com.xcwk.scalehub.ui.components.QkStatRow
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * feature.trend — 四件套之 Screen：无状态渲染 + 事件转发，不触任何数据层。
 * 样式只用 MaterialTheme 主题槽（设计 token 落地前禁止散落硬编码色值，DEC-021 支柱5）。
 */
@Composable
fun TrendScreen(
    state: TrendUiState,
    onIntent: (TrendIntent) -> Unit,
    onOpenHistory: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("趋势", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            Text(
                "历史记录",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenHistory).padding(4.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            TrendRange.entries.forEach { r ->
                QkChip(label(r), state.range == r) { onIntent(TrendIntent.RangeChanged(r)) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TrendSegment.entries.forEach { s ->
                QkChip(label(s), state.segment == s) { onIntent(TrendIntent.SegmentChanged(s)) }
            }
        }
        state.weight?.let { Block("体重趋势", it, Modifier.padding(top = 20.dp)) }
        state.fat?.let { Block("体脂率趋势", it, Modifier.padding(top = 20.dp)) }
    }
}

@Composable
private fun Block(title: String, block: TrendBlockUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        QkSectionTitle(title, block.caption)
        if (block.emptyText != null) {
            QkEmptyHint(block.emptyText)
        } else {
            QkStatRow(
                high = block.highValue ?: "--", highCaption = "(${block.highDay})最高",
                low = block.lowValue ?: "--", lowCaption = "(${block.lowDay})最低",
                change = block.changeValue ?: "--",
            )
            // TODO M4 视觉轮：面积图 Canvas（旧库 AreaTrendChart 等价物）
            Text(
                block.points.joinToString("  ") { "%.1f".format(it.value) },
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    QkTheme {
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
            onIntent = {},
        )
    }
}
