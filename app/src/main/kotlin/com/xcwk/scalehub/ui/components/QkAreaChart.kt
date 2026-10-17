package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.feature.trend.TrendPointUi
import com.xcwk.scalehub.ui.theme.QkColor
import kotlin.math.max

/**
 * 面积趋势图（DEC-021 视觉轮：旧库 AreaTrendChart 等价物，TrendPages TODO 的实体化）。
 *
 * 无状态纯渲染组件：只接收已排序的点，自行归一化到画布高度。
 * 颜色经 QkColor token（设计系统，页面不散落硬编码）。
 * 单点渲染为圆点；多点渲染为折线 + 面积填充 + 端点圆点。
 */
@Composable
fun QkAreaChart(
    points: List<TrendPointUi>,
    modifier: Modifier = Modifier,
    lineColor: Color = QkColor.Primary,
    height: Int = 160,
    padY: Int = 18,
) {
    Canvas(modifier = modifier.height(height.dp)) {
        if (points.isEmpty()) return@Canvas

        if (points.size == 1) {
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(size.width / 2f, size.height / 2f))
            return@Canvas
        }

        val maxV = points.maxOf { it.value }
        val minV = points.minOf { it.value }
        val span = max(1e-9, maxV - minV).toFloat()   // 防除零：全同值时归一化到中带
        val padTop = padY.toFloat()
        val padBottom = padY.toFloat()
        val baseline = size.height - padBottom
        val scaleY = (size.height - padTop - padBottom) / span

        fun yOf(v: Double): Float = baseline - (v - minV).toFloat() * scaleY
        fun xOf(i: Int): Float = size.width * i / (points.size - 1).toFloat()

        // 折线路径
        val linePath = Path().apply {
            moveTo(0f, yOf(points.first().value))
            points.forEachIndexed { i, p ->
                if (i > 0) lineTo(xOf(i), yOf(p.value))
            }
        }

        // 面积填充：折线 → 底部基线闭合
        val areaPath = Path().apply {
            moveTo(0f, yOf(points.first().value))
            points.forEachIndexed { i, p ->
                if (i > 0) lineTo(xOf(i), yOf(p.value))
            }
            lineTo(size.width, baseline)
            lineTo(0f, baseline)
            close()
        }
        drawPath(areaPath, color = lineColor.copy(alpha = 0.12f))
        drawPath(linePath, color = lineColor, style = Stroke(width = 2.dp.toPx()))

        // 端点圆点
        drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(0f, yOf(points.first().value)))
        drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(xOf(points.size - 1), yOf(points.last().value)))
    }
}