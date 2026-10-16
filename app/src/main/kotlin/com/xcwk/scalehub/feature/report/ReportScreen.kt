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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkSectionTitle
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * report — 四件套之 Screen：该次测量的体测报告（需求：报告对应该次测量，
 * 免责语两态随阻抗有无，AI 解读留在报告流程）。
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
                Text(
                    state.weightText,
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text("公斤", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.deltaLine, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
            QkCard(modifier = Modifier.padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QkSectionTitle("BMI 指数").let { }
                    Text(state.bmiLabel ?: "", modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.primary)
                    Text(state.bmiText, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }
            }
            state.missingText?.let { text ->
                QkCard(modifier = Modifier.padding(top = 10.dp)) { QkEmptyHint(text) }
            }
            QkCard(modifier = Modifier.padding(top = 10.dp)) {
                state.metrics.chunked(2).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        row.forEach { m ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${m.valueText} ${m.unit}".trim(), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        if (row.size == 1) {
                            Column(modifier = Modifier.weight(1f)) {}
                        }
                    }
                    HorizontalDivider()
                }
                Text(
                    state.disclaimer,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // AI 解读留在报告流程（需求 3.x）；云接入前不可用，如实置灰
                OutlinedButton(
                    onClick = { onIntent(ReportIntent.AiExplain) },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("AI 解读（需手机号登录与联网，M2 接入）") }
            }
        }
    }
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
                bmiText = "23.5",
                bmiLabel = "标准",
                metrics = listOf(
                    ReportMetricUi("体重", "72.0", "公斤"),
                    ReportMetricUi("体脂率", "19.1", "%"),
                    ReportMetricUi("水分率", "55.2", "%"),
                    ReportMetricUi("肌肉率", "38.0", "%"),
                    ReportMetricUi("骨量", "2.8", "kg"),
                    ReportMetricUi("内脏脂肪", "7.0", ""),
                ),
                disclaimer = "体重、体脂率为实测记录；其余指标基于公认公式估算，仅供参考，不构成医疗建议",
            ),
        )
    }
}
