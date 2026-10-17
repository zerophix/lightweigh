package com.xcwk.scalehub.feature.measure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.theme.QkSpacing

/** feature.measure — 首屏（四件套之 Screen，DEC-021）：只渲染状态、转发事件。 */
@Composable
fun MeasureScreen(
    vm: MeasureViewModel = viewModel(factory = MeasureViewModel.Factory),
    onOpenScale: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("记录体重", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Button(
            onClick = onOpenScale,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("蓝牙称重（连体脂秤）") }
        OutlinedTextField(
            value = state.weightInput,
            onValueChange = { vm.onIntent(MeasureIntent.WeightChanged(it)) },
            label = { Text("体重 (kg)") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Button(
            onClick = { vm.onIntent(MeasureIntent.Save) },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text(if (state.saving) "保存中…" else "保存") }

        state.lastRecord?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
        state.error?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }

        // 最近测量（卡片化：DEC-021 视觉轮）
        QkCard(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("最近测量", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            if (state.recent.isEmpty()) {
                QkEmptyHint("还没有测量记录")
            } else {
                state.recent.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = QkSpacing.Sm))
                    Text(item, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // 健康记录（卡片化：DEC-021 视觉轮）
        if (state.recentLogs.isNotEmpty()) {
            QkCard(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    "健康记录",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                state.recentLogs.forEachIndexed { index, log ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = QkSpacing.Sm))
                    Text(
                        "${log.timeText}  ${log.typeLabel} · ${log.valueText}",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

    }
}
