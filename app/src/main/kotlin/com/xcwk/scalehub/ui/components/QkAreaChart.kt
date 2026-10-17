package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcwk.scalehub.feature.trend.TrendPointUi
import com.xcwk.scalehub.ui.theme.QkColor
import kotlin.math.max

/**
 * 面积趋势图（对齐原型 .chart：渐变面积 + 三条网格 + 空心端点 + 可选轴标签）。
 *
 * 无状态纯渲染组件：只接收已排序的点，自行归一化到画布高度。
 * 颜色经 QkColor token（设计系统，页面不散落硬编码）。
 * 单点渲染为圆点；多点渲染为折线 + 渐变面积 + 端点圆点。
 *
 * 向后兼容：既有调用（首页 48dp 迷你图、趋势页 160dp 主图）不传新参数即保持原布局，
 * 不画网格与标签。
 */
@Composable
fun QkAreaChart(
    points: List<TrendPointUi>,
    modifier: Modifier = Modifier,
    lineColor: Color = QkColor.Primary,
    height: Int = 160,
    padY: Int = 18,
    showGrid: Boolean = false,
    showDots: Boolean = true,
    xLabels: List<String> = emptyList(),
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = QkColor.Muted)

    Canvas(modifier = modifier.height(height.dp)) {
        if (points.isEmpty()) return@Canvas

        val labelBand = if (xLabels.isEmpty()) 0f else 18.dp.toPx()
        val baseline = size.height - padY.toFloat() - labelBand

        if (points.size == 1) {
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(size.width / 2f, baseline / 2f))
            return@Canvas
        }

        val maxV = points.maxOf { it.value }
        val minV = points.minOf { it.value }
        val span = max(1e-9, maxV - minV).toFloat()   // 防除零：全同值时归一化到中带
        val padTop = padY.toFloat()
        val plotHeight = baseline - padTop
        val scaleY = plotHeight / span

        fun yOf(v: Double): Float = baseline - (v - minV).toFloat() * scaleY
        fun xOf(i: Int): Float = size.width * i / (points.size - 1).toFloat()

        if (showGrid) {
            for (i in 0..2) {
                val y = padTop + plotHeight * i / 2f
                drawLine(
                    color = QkColor.Border,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        val linePath = Path().apply {
            moveTo(0f, yOf(points.first().value))
            points.forEachIndexed { i, p ->
                if (i > 0) lineTo(xOf(i), yOf(p.value))
            }
        }

        val areaPath = Path().apply {
            moveTo(0f, yOf(points.first().value))
            points.forEachIndexed { i, p ->
                if (i > 0) lineTo(xOf(i), yOf(p.value))
            }
            lineTo(size.width, baseline)
            lineTo(0f, baseline)
            close()
        }
        drawPath(
            areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.18f), lineColor.copy(alpha = 0f)),
                startY = padTop,
                endY = baseline,
            ),
        )
        drawPath(linePath, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))

        if (showDots) {
            val r = 3.dp.toPx()
            val stroke = 2.dp.toPx()
            points.forEachIndexed { i, p ->
                if (i == 0 || i == points.lastIndex || points.size <= 8) {
                    val c = Offset(xOf(i), yOf(p.value))
                    drawCircle(QkColor.Card, radius = r, center = c)
                    drawCircle(lineColor, radius = r, center = c, style = Stroke(width = stroke))
                }
            }
        }

        if (xLabels.isNotEmpty()) {
            xLabels.forEachIndexed { i, label ->
                val layout = measurer.measure(label, labelStyle)
                val raw = size.width * i / (xLabels.size - 1).coerceAtLeast(1).toFloat()
                val x = raw.coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, baseline + 4.dp.toPx()))
            }
        }
    }
}
