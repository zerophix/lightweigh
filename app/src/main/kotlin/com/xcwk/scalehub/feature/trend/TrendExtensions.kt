package com.xcwk.scalehub.feature.trend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkColor

// ---- 趋势扩展（DEC-043-E，参考 TrendPages.kt:92-140）----

/** 扩展指标定义。 */
data class TrendMetric(
    val id: String,
    val name: String,
    val unit: String,
    val color: Color,
)

/** 参考项目 7 指标（TrendPages.kt:92-100）。 */
val TREND_METRICS = listOf(
    TrendMetric("weight", "体重", "公斤", QkColor.Primary),
    TrendMetric("fat", "体脂率", "%", Color(0xFF3DB7FF)),
    TrendMetric("skeletal", "骨骼肌", "%", Color(0xFF8B5CF6)),
    TrendMetric("water", "水分", "%", Color(0xFF06B6D4)),
    TrendMetric("bmr", "基础代谢", "千卡", Color(0xFFF59E0B)),
    TrendMetric("muscle", "肌肉", "%", Color(0xFF10B981)),
    TrendMetric("protein", "蛋白质", "%", Color(0xFFEC4899)),
)

/** 聚合模式（TrendPages.kt:108,140）。 */
enum class AggMode(val label: String) {
    DAILY_AVG("日均"),
    EARLIEST("最早"),
    LATEST("最晚"),
    MIN("最小"),
    MAX("最大"),
}

/**
 * trend-metric-selector — 指标选择器（DEC-043-E）。
 * 7 个指标胶囊，单选。
 */
@Composable
fun TrendMetricSelector(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TREND_METRICS.forEach { metric ->
            val isSelected = metric.id == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) metric.color else QkColor.Mint)
                    .clickable { onSelect(metric.id) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    metric.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color.White else QkColor.Muted,
                )
            }
        }
    }
}

/**
 * trend-agg-mode — 聚合模式选择器（DEC-043-E）。
 */
@Composable
fun TrendAggModeSelector(
    selected: AggMode,
    onSelect: (AggMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AggMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) QkColor.Primary else QkColor.Mint)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    mode.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color.White else QkColor.Muted,
                )
            }
        }
    }
}

/**
 * trend-line-chart — 趋势折线图（DEC-043-E，支持多指标）。
 */
@Composable
fun TrendLineChart(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = QkColor.Hint)
        }
        return
    }
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        if (values.size == 1) {
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
            return@Canvas
        }
        val min = values.min()
        val max = values.max()
        val span = if (max - min < 0.05) 1.0 else max - min
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * (1 - ((v - min) / span).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        // 最后一点高亮
        val lastX = size.width
        val lastY = size.height * (1 - ((values.last() - min) / span).toFloat())
        drawCircle(color, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
    }
}

/**
 * trend-summary — 趋势汇总卡（均值/最高/最低）。
 */
@Composable
fun TrendSummary(
    values: List<Double>,
    unit: String,
) {
    if (values.isEmpty()) return
    QkCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            TrendSummaryCell("均值", "%.1f".format(values.average()), unit, Modifier.weight(1f))
            TrendSummaryCell("最高", "%.1f".format(values.max()), unit, Modifier.weight(1f))
            TrendSummaryCell("最低", "%.1f".format(values.min()), unit, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TrendSummaryCell(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = QkColor.Muted)
        Text(
            "$value $unit",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            color = QkColor.Fg,
        )
    }
}