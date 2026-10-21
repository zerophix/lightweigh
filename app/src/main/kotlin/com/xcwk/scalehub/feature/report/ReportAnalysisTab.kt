package com.xcwk.scalehub.feature.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

// ---- BMI 色带（唯一真源，旧库 Pages.kt:608-614）----
private val bmiColors = listOf(
    Color(0xFF38BDF8), // 偏瘦
    Color(0xFF2D8B56), // 标准
    Color(0xFFF59E0B), // 偏胖
    Color(0xFFE85D04), // 肥胖
    Color(0xFFDC2626), // 重度肥胖
)
private val bmiLabels = listOf("偏瘦", "标准", "偏胖", "肥胖", "重度肥胖")

/** 体型小人插画（旧库 Pages.kt:637-657）：等级越高身宽越大，颜色随 BMI 分档。 */
@Composable
fun BodyFigure(level: Int?, modifier: Modifier = Modifier) {
    val idx = (level ?: 1).coerceIn(0, 4)
    val color = bmiColors[idx]
    val bodyW = listOf(26f, 32f, 42f, 54f, 66f)[idx]
    Canvas(modifier = modifier.size(width = 90.dp, height = 120.dp)) {
        val w = size.width
        val h = size.height
        // 头
        drawCircle(color, radius = 12.dp.toPx(), center = Offset(w / 2, 14.dp.toPx()))
        // 躯干
        drawRoundRect(
            color = color,
            topLeft = Offset((w - bodyW.dp.toPx()) / 2, 30.dp.toPx()),
            size = Size(bodyW.dp.toPx(), 46.dp.toPx()),
            cornerRadius = CornerRadius(8.dp.toPx()),
        )
        // 左腿
        drawRoundRect(
            color = color,
            topLeft = Offset((w - bodyW.dp.toPx()) / 2 + 3.dp.toPx(), 78.dp.toPx()),
            size = Size((bodyW.dp.toPx() - 8.dp.toPx()) / 2, 34.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        // 右腿
        drawRoundRect(
            color = color,
            topLeft = Offset(w / 2 + 2.dp.toPx(), 78.dp.toPx()),
            size = Size((bodyW.dp.toPx() - 8.dp.toPx()) / 2, 34.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
    }
}

/** BMI 色带 + 游标（旧库 Pages.kt:659-691）：BMI 14~38 映射到 2%~98%。 */
@Composable
fun BmiScaleBar(bmi: Double?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 5 段等宽色条
        Row(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            bmiColors.forEachIndexed { i, c ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(c),
                )
            }
        }
        // 标签行
        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            bmiLabels.forEachIndexed { i, label ->
                Text(
                    label,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = (i * 20).dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Muted,
                )
            }
        }
        // 游标
        if (bmi != null && bmi > 0) {
            val pct = ((bmi - 14.0) / 24.0).coerceIn(0.02, 0.98)
            Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                val cx = size.width * pct.toFloat()
                drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(cx, 8.dp.toPx()))
                drawCircle(QkColor.Fg, radius = 4.dp.toPx(), center = Offset(cx, 8.dp.toPx()))
            }
        }
    }
}

/** 迷你折线图（旧库 Pages.kt:881-911）：最新值大字 + 120dp Canvas 折线。 */
@Composable
fun HistoryLine(
    values: List<Double>,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return
    val latest = values.last()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "%.1f".format(latest),
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
            Text(
                " $unit",
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp)) {
            if (values.size == 1) {
                drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
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
            val lastY = size.height * (1 - ((latest - min) / span).toFloat())
            drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(lastX, lastY))
        }
    }
}

/** 报告分析 tab（旧库 Pages.kt:740-848 ReportAnalysis 全套）。 */
@Composable
fun ReportAnalysisTab(
    state: ReportUiState,
    male: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 1. 身体成分分析
        QkSectionHead(title = "身体成分分析", modifier = Modifier.padding(top = QkSpacing.Lg))
        QkCard(modifier = Modifier.padding(top = 2.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BodyFigure(state.bmiLevel)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    listOf("体重", "体脂率", "水分率", "肌肉率", "骨量", "内脏脂肪").forEach { name ->
                        val m = state.metrics.firstOrNull { it.name == name }
                        if (m != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
                                Text("${m.valueText} ${m.unit}".trim(), style = MaterialTheme.typography.bodySmall, color = QkColor.Fg)
                            }
                        }
                    }
                }
            }
        }

        // 2. 体型分析（3×3 矩阵）
        QkSectionHead(title = "体型分析", modifier = Modifier.padding(top = QkSpacing.Lg))
        QkCard(modifier = Modifier.padding(top = 2.dp)) {
            val shapes = listOf(
                listOf("消瘦型", "低脂肪型", "运动员型"),
                listOf("苗条肌肉型", "苗条型", "健康型"),
                listOf("隐形肥胖型", "偏胖型", "肥胖型"),
            )
            shapes.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    row.forEach { shape ->
                        val hit = shape == state.bodyShape
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (hit) QkColor.Primary else QkColor.Mint)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                shape,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hit) Color.White else QkColor.Muted,
                            )
                        }
                    }
                }
            }
        }

        // 3. 体型评估
        state.bodyShape?.let { shape ->
            QkSectionHead(title = "体型评估", modifier = Modifier.padding(top = QkSpacing.Lg))
            QkCard(modifier = Modifier.padding(top = 2.dp)) {
                Text(shape, style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
                state.advice?.let {
                    Text(it, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
                }
            }
        }

        // 4. 体重控制建议
        if (state.fatControlText != null || state.muscleControlText != null) {
            QkSectionHead(title = "体重控制建议", modifier = Modifier.padding(top = QkSpacing.Lg))
            QkCard(modifier = Modifier.padding(top = 2.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    ControlSuggestion("脂肪控制", state.fatControlText, Modifier.weight(1f))
                    ControlSuggestion("肌肉控制", state.muscleControlText, Modifier.weight(1f))
                }
            }
        }

        // 5. 目标达成情况
        if (state.goalRows.isNotEmpty()) {
            QkSectionHead(title = "目标达成情况", modifier = Modifier.padding(top = QkSpacing.Lg))
            QkCard(modifier = Modifier.padding(top = 2.dp)) {
                state.goalRows.forEachIndexed { i, row ->
                    if (i > 0) QkDivider()
                    GoalAchieveRow(row)
                }
            }
        }

        // 6. 近期体重折线
        if (state.recentWeights.isNotEmpty()) {
            QkSectionHead(title = "近期体重", modifier = Modifier.padding(top = QkSpacing.Lg))
            QkCard(modifier = Modifier.padding(top = 2.dp)) {
                HistoryLine(state.recentWeights, "公斤", QkColor.Primary)
            }
        }

        // 7. 近期体脂率折线
        if (state.recentFats.isNotEmpty()) {
            QkSectionHead(title = "近期体脂率", modifier = Modifier.padding(top = QkSpacing.Lg))
            QkCard(modifier = Modifier.padding(top = 2.dp)) {
                HistoryLine(state.recentFats, "%", Color(0xFF3DB7FF))
            }
        }
    }
}

@Composable
private fun ControlSuggestion(title: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        Text(
            value?.let { if (it.startsWith("-")) "建议减少 $it 公斤" else if (it.startsWith("+")) "建议增加 $it 公斤" else "已接近标准" } ?: "--",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Fg,
        )
    }
}

@Composable
private fun GoalAchieveRow(row: ReportGoalRow, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        Text(row.valueText, style = MaterialTheme.typography.bodySmall, color = QkColor.Fg)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (row.achieved) "达标" else "未达标",
            style = MaterialTheme.typography.labelSmall,
            color = if (row.achieved) QkColor.Primary else QkColor.Accent,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(row.rangeText, style = MaterialTheme.typography.labelSmall, color = QkColor.Hint)
    }
}

@Composable
private fun QkDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(QkColor.Border),
    )
}