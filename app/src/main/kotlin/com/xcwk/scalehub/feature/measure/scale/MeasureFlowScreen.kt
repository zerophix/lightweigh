package com.xcwk.scalehub.feature.measure.scale

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.application.format.QkFormat
import com.xcwk.scalehub.domain.model.UserUnitPreferences
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkSkeletonLine
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkComponentSize
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import kotlin.math.absoluteValue

/**
 * v-result（原型 03-measure）：测量完成结果屏。
 *
 * 全部数值来自 MeasureFlowViewModel 的真实读取（本次记录 + 上次记录差值）；
 * 上次肌肉量在现有记录模型中不存在，对应格显示 BMI 而非编造肌肉数。
 */
@Composable
fun MeasureResultContent(
    snap: MeasureFlowState,
    unitPrefs: UserUnitPreferences,
    onAskAi: () -> Unit,
    onDone: () -> Unit,
    onOpenReport: () -> Unit,
    onKeepDup: () -> Unit,
    onReplaceDup: () -> Unit,
) {
    var showDupDialog by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Lg),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = QkSpacing.Xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QkIcon(QkIcons.Habit, tint = QkColor.Primary, size = 15.dp)
                Text(
                    "测量完成 · ${snap.memberName} · ${snap.timeText}",
                    modifier = Modifier.padding(start = QkSpacing.Xs),
                    style = MaterialTheme.typography.labelMedium,
                    color = QkColor.Primary,
                )
            }
            QkCard(modifier = Modifier.padding(top = QkSpacing.Md), padding = QkSpacing.Xl) {
                AiInterpretChip(onAskAi = onAskAi)
                Row(
                    modifier = Modifier.padding(top = QkSpacing.Lg),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        snap.weightText,
                        style = MaterialTheme.typography.displayLarge,
                        color = QkColor.Fg,
                    )
                    Text(
                        " ${snap.weightUnit}",
                        modifier = Modifier.padding(bottom = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = QkColor.Muted,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    val change = snap.weightChange
                    Text(
                        when {
                            change == null -> "首次记录"
                            else -> "${if (change < 0) "↓" else "↑"}${
                                QkFormat.weightValue(change.absoluteValue, unitPrefs)
                            } 较上次"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (change != null && change < 0) QkColor.Primary else QkColor.Muted,
                    )
                }
                Text(
                    resultSummary(snap),
                    modifier = Modifier.padding(top = QkSpacing.Md),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
            Row(modifier = Modifier.padding(top = QkSpacing.Md)) {
                FlowMetricCell("体脂率", fatText(snap.fatPercent), fatDeltaText(snap), Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(QkComponentSize.MetricCellDivider)
                        .background(QkColor.LineSoft),
                )
                FlowMetricCell("BMI", snap.bmiText ?: "--", null, Modifier.weight(1f))
            }
            Text(
                "身体评分及指标由体脂秤估算，仅供健康管理参考。",
                modifier = Modifier.padding(top = QkSpacing.Md),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Hint,
            )
        }
        Column(
            modifier = Modifier.padding(top = QkSpacing.Md, bottom = QkSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkButton(text = "完成，返回首页", modifier = Modifier.fillMaxWidth(), onClick = onDone)
            Row(horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm), modifier = Modifier.fillMaxWidth()) {
                QkButton(
                    text = "查看报告",
                    style = QkButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenReport,
                )
                QkButton(
                    text = "模拟重复测量",
                    style = QkButtonStyle.Ghost,
                    modifier = Modifier.weight(1f),
                    onClick = { showDupDialog = true },
                )
            }
        }
    }
    if (showDupDialog) {
        AlertDialog(
            onDismissRequest = { showDupDialog = false },
            icon = { QkIcon(QkIcons.History, tint = QkColor.AmberInk, size = 22.dp) },
            title = { Text("3 分钟内重复测量") },
            text = {
                Text("你刚刚已在 ${snap.dupPrevText} 测量过。请选择处理方式。")
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
                    if (snap.dupPrevTime != null) {
                        QkButton(
                            text = "替换上次记录",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showDupDialog = false
                                onReplaceDup()
                            },
                        )
                    }
                    QkButton(
                        text = "保留为新记录",
                        style = if (snap.dupPrevTime != null) QkButtonStyle.Secondary else QkButtonStyle.Primary,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showDupDialog = false
                            onKeepDup()
                        },
                    )
                    TextButton(
                        onClick = {
                            showDupDialog = false
                            onDone()
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text("放弃本次", color = QkColor.Muted) }
                }
            },
        )
    }
}

/** AI 解读入口条（原型 .card lg 内 iconbg 圆角行）。 */
@Composable
private fun AiInterpretChip(onAskAi: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Tile))
            .background(QkColor.MintSoft)
            .clickable(onClick = onAskAi)
            .padding(horizontal = QkSpacing.Md, vertical = QkSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkIcon(QkIcons.Ai, tint = QkColor.Primary, size = 18.dp)
        Text(
            "AI 解读这次变化 ›",
            modifier = Modifier.padding(start = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = QkColor.Fg,
        )
    }
}

/** 结果卡摘要句：只在有真实对比时给「平稳」判断，否则保持原型提示口径。 */
private fun resultSummary(snap: MeasureFlowState): String = when {
    !snap.hasPrev -> "首次记录已保存，积累连续记录后可看变化。"
    snap.weightChange?.absoluteValue?.let { it > 1.0 } == true -> "本次与上次相差较大，建议确认测量条件一致。"
    else -> "整体状态平稳，继续保持你的健康节奏。"
}

private fun fatText(fat: Double?): String =
    fat?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "--"

private fun fatDeltaText(snap: MeasureFlowState): String? {
    val change = snap.fatChange ?: return null
    val dir = if (change < 0) "↓" else "↑"
    return "$dir${String.format(java.util.Locale.US, "%.1f", change.absoluteValue)}"
}

@Composable
private fun FlowMetricCell(label: String, value: String, delta: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = QkSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = QkColor.Muted)
        Row(
            modifier = Modifier.padding(top = QkSpacing.Xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
            )
            if (label == "体脂率" && value != "--") {
                Text("%", style = MaterialTheme.typography.labelSmall, color = QkColor.Muted)
            }
        }
        if (delta != null) {
            Text(
                delta,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.labelSmall,
                color = if (delta.startsWith("↓")) QkColor.Primary else QkColor.AmberInk,
            )
        }
    }
}

/**
 * v-ai-report（原型 03-measure）：AI 报告解读屏。
 *
 * 标题行由本次与上次的真实差值推导（见 [reportHeadline]）；
 * 正文调 AiService 生成，未登录 / 失败走服务端统一文案并可重试。
 */
@Composable
fun MeasureReportContent(
    snap: MeasureFlowState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Lg),
    ) {
        Row(modifier = Modifier.padding(top = QkSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = onBack)
            Text(
                "AI 报告解读",
                modifier = Modifier.padding(start = QkSpacing.Sm),
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = QkSpacing.Lg),
        ) {
            QkCard(padding = QkSpacing.Lg) {
                Text(
                    reportHeadline(snap),
                    style = MaterialTheme.typography.headlineSmall,
                    color = QkColor.Fg,
                )
                Text(
                    snap.timeText + if (snap.hasPrev) " · 对比上次" else " · 首次记录",
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
            QkCard(modifier = Modifier.padding(top = QkSpacing.Md), padding = QkSpacing.Lg) {
                when {
                    snap.reportRunning -> Column(
                        verticalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
                    ) {
                        QkSkeletonLine(widthPercent = 92f)
                        QkSkeletonLine(widthPercent = 88f)
                        QkSkeletonLine(widthPercent = 60f)
                    }
                    snap.reportError != null -> Column {
                        Text(snap.reportError!!, style = MaterialTheme.typography.bodyMedium, color = QkColor.Danger)
                        Text(
                            "AI 解读不影响本次记录，已安全保存。",
                            modifier = Modifier.padding(top = QkSpacing.Sm),
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.Muted,
                        )
                    }
                    snap.reportText != null -> Text(
                        snap.reportText!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = QkColor.Sub,
                    )
                    else -> Text(
                        "正在准备解读…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = QkColor.Muted,
                    )
                }
                if (snap.reportError != null) {
                    QkButton(
                        text = "重试",
                        style = QkButtonStyle.Secondary,
                        modifier = Modifier.padding(top = QkSpacing.Md),
                        onClick = onRetry,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(top = QkSpacing.Md, bottom = QkSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
        ) {
            QkButton(text = "返回首页", modifier = Modifier.fillMaxWidth(), onClick = onDone)
            QkButton(
                text = "返回结果",
                style = QkButtonStyle.Ghost,
                modifier = Modifier.fillMaxWidth(),
                onClick = onBack,
            )
        }
    }
}

/**
 * 解读标题：只陈述真实差值，不对未测出的指标下结论。
 *
 * 无历史（首次）→ 起步文案；两项同向下降 → 原型「不等于脂肪减少」口径；
 * 同向上升 → 水分/进食提示；其余 → 小幅波动口径。
 */
internal fun reportHeadline(snap: MeasureFlowState): String {
    val down = snap.weightChange?.let { it <= 0 } ?: false
    val fatDown = snap.fatChange?.let { it <= 0 }
    val up = snap.weightChange?.let { it > 0 } ?: false
    val fatUp = snap.fatChange?.let { it > 0 }
    return when {
        !snap.hasPrev -> "首次记录已保存，积累连续记录后再看变化。"
        down && (fatDown ?: true) -> "两项读数小幅下降，不等于脂肪减少。"
        up && (fatUp ?: true) -> "读数受测量时段影响，先保持正常饮水再观察。"
        else -> "单次测量有波动，看长期趋势更可靠。"
    }
}
