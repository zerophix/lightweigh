package com.xcwk.scalehub.feature.measure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import kotlin.math.roundToInt

/**
 * 目标进度曲线（T-066）。
 *
 * 口径（可判定，不依赖图表库）：
 * - 起点 = 序列第一个体重，终点目标 = [targetWeight]；
 * - 单点进度 = (起点 - 当前) / (起点 - 目标)，夹在 0..100；
 * - 起点不高于目标（没有下降空间）→ 不产出曲线，避免画出一条"永远 100%"的假线；
 * - 少于 2 个点 → 不产出（一个点画不成曲线）。
 *
 * 只做"相对目标"的百分比，不做体重预测：预测需要模型与置信区间，本仓库没有。
 */
fun goalCurvePercents(weights: List<Double>, targetWeight: Float): List<Int> {
    if (weights.size < 2) return emptyList()
    if (targetWeight <= 0f) return emptyList()
    val start = weights.first()
    val target = targetWeight.toDouble()
    val span = start - target
    if (span <= 0.0) return emptyList()
    return weights.map { w ->
        (((start - w) / span) * 100).roundToInt().coerceIn(0, 100)
    }
}

/** 曲线的变化量（末点 - 首点），用于文案「本周 +12%」。 */
fun goalCurveDeltaPercents(percents: List<Int>): Int? =
    if (percents.size < 2) null else percents.last() - percents.first()

/**
 * 迷你柱状曲线：高度按百分比等比，最低 2dp 保证可见。
 * 无数据时调用方不应渲染本组件。
 */
@Composable
fun GoalCurveBars(percents: List<Int>, modifier: Modifier = Modifier) {
    if (percents.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        percents.forEach { pct ->
            val h = (2 + (26 * pct / 100)).dp
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(h)
                    .background(QkColor.Mint, androidx.compose.foundation.shape.RoundedCornerShape(QkRadius.SegmentItem)),
            )
        }
    }
}
