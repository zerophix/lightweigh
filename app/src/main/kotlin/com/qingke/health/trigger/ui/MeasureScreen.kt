package com.qingke.health.trigger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** trigger — 首屏：只渲染状态、转发意图。 */
@Composable
fun MeasureScreen(vm: MeasureViewModel = viewModel(factory = MeasureViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text("记录体重", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
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

        if (state.recent.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
            Text("最近测量", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            state.recent.forEach { item ->
                Text(item, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}
