package com.xcwk.scalehub.feature.report

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkSectionTitle
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * report — 四件套之 Screen：体测报告（评分/建议/体型/风险/指标全表/控制建议）。
 * tone 上色：0=弱化 1=主色绿 2=注意橙 3=超标红。
 */
@Composable
fun ReportScreen(
    state: ReportUiState,
    onIntent: (ReportIntent) -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("体测报告", style = MaterialTheme.typography.headlineSmall)
        }
        if (!state.loading && !state.found) {
            QkEmptyHint("找不到这次测量记录")
            return
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            QkCard(modifier = Modifier.padding(top = 12.dp)) {
                Text(state.timeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.weightText, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 4.dp))
                Text("公斤", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.deltaLine, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
            // 评分 + 建议（BodyMetrics score/adviceOfBmi）
            if (state.score != null) {
                QkCard(modifier = Modifier.padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("健康评分", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${state.score}", style = MaterialTheme.typography.displaySmall, color = toneColor(state.scoreTone()))
                    }
                    state.advice?.let {
                        Text(it, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            QkCard(modifier = Modifier.padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("BMI 指数", style = MaterialTheme.typography.titleMedium)
                    state.bmiLabel?.let {
                        Text(it, modifier = Modifier.padding(start = 8.dp), color = toneColor(bmiTone(state)))
                    }
                    Text(state.bmiText, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }
                state.bodyShape?.let {
                    Text("体型：$it", modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.missingText?.let { text -> QkCard(modifier = Modifier.padding(top = 10.dp)) { QkEmptyHint(text) } }
            // 风险评估（risksOf 移植件）
            if (state.risks.isNotEmpty()) {
                QkCard(modifier = Modifier.padding(top = 10.dp)) {
                    QkSectionTitle("风险评估")
                    state.risks.chunked(3).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            row.forEach { risk ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(risk.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(risk.levelText, style = MaterialTheme.typography.bodyMedium, color = toneColor(risk.tone))
                                }
                            }
                        }
                    }
                }
            }
            // 指标全表（十八项随阻抗与算法而定）
            QkCard(modifier = Modifier.padding(top = 10.dp)) {
                state.metrics.chunked(2).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        row.forEach { m ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${m.valueText} ${m.unit}".trim(), style = MaterialTheme.typography.titleMedium, color = toneColor(m.tone))
                                val sub = listOfNotNull(m.status.takeIf { it != "--" }, m.delta).joinToString(" · ")
                                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (row.size == 1) Column(modifier = Modifier.weight(1f)) {}
                    }
                    HorizontalDivider()
                }
            }
            // 控制建议（fatControl/muscleControl/standardWeight）
            if (state.fatControlText != null || state.muscleControlText != null || state.standardWeightText != null) {
                QkCard(modifier = Modifier.padding(top = 10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ControlCell("脂肪控制", state.fatControlText, Modifier.weight(1f))
                        ControlCell("肌肉控制", state.muscleControlText, Modifier.weight(1f))
                        ControlCell("标准体重", state.standardWeightText?.let { "$it 公斤" }, Modifier.weight(1f))
                    }
                }
            }
            Text(
                state.disclaimer,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { onIntent(ReportIntent.AiExplain) },
                enabled = false,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("AI 解读（需手机号登录与联网，M2 接入）") }
        }
    }
}

@Composable
private fun ControlCell(title: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "--", style = MaterialTheme.typography.titleMedium)
    }
}

/** 评分档位着色：86+ 绿 / 70-85 橙 / <70 红（展示口径，规则分值本身随 BodyMetrics）。 */
private fun ReportUiState.scoreTone(): Int = score?.let { if (it >= 86) 1 else if (it >= 70) 2 else 3 } ?: 0

private fun bmiTone(state: ReportUiState): Int = state.bmiLabel?.let {
    when (it) {
        "标准" -> 1
        "偏瘦", "偏胖" -> 2
        else -> 3
    }
} ?: 0

@Composable
private fun toneColor(tone: Int): Color = when (tone) {
    1 -> MaterialTheme.colorScheme.primary
    2 -> QkColor.Accent
    3 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Preview(showBackground = true)
@Composable
private fun ReportScreenPreview() {
    QkTheme {
        ReportScreen(
            state = ReportUiState(
                loading = false,
                found = true,
                timeText = "2026/10/15 08:00",
                weightText = "72.0",
                deltaLine = "比上次  体重 -1.5 公斤  体脂率 -0.8 %",
                score = 87,
                advice = "身体数据处于标准区间，继续保持当前饮食与运动习惯。",
                bodyShape = "苗条型",
                risks = listOf(
                    ReportRiskUi("肥胖症", "低风险", 1),
                    ReportRiskUi("脂肪肝", "中风险", 2),
                    ReportRiskUi("高血压", "低风险", 1),
                ),
                bmiText = "23.5",
                bmiLabel = "标准",
                metrics = listOf(
                    ReportMetricUi("BMI", "23.5", "", "标准", 1, "+0.3"),
                    ReportMetricUi("体脂率", "19.1", "%", "标准", 1, null),
                    ReportMetricUi("肌肉率", "33.0", "%", "不足", 2, null),
                    ReportMetricUi("内脏脂肪", "7", "级", "标准", 1, null),
                ),
                fatControlText = "-1.2",
                muscleControlText = "+0.8",
                standardWeightText = "68.0",
                disclaimer = "体重、体脂率为实测记录；其余指标基于公认公式估算，仅供参考，不构成医疗建议",
            ),
        )
    }
}
