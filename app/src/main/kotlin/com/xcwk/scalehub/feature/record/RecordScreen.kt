package com.xcwk.scalehub.feature.record

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkChip
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * record — 四件套之 Screen：8 类记录表单，无状态渲染。
 * 结构随旧库 RecordScreen：标签预设行 → 主值字段 → 副值字段 → 快捷量 → 错误 → 保存。
 */
@Composable
fun RecordScreen(
    state: RecordUiState,
    onIntent: (RecordIntent) -> Unit,
    onBack: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(state.title, style = MaterialTheme.typography.headlineSmall)
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (state.presets.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    state.presets.forEach { preset ->
                        QkChip(preset, state.selectedPreset == preset) {
                            onIntent(RecordIntent.PresetPicked(preset))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.primaryValue,
                onValueChange = { onIntent(RecordIntent.ValueChanged(it)) },
                label = { Text(state.primaryLabel) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (state.extraLabel != null) {
                OutlinedTextField(
                    value = state.extraValue,
                    onValueChange = { onIntent(RecordIntent.ExtraChanged(it)) },
                    label = { Text(state.extraLabel) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            if (state.quickValues.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    state.quickValues.forEach { quick ->
                        QkChip("$quick", state.primaryValue == quick.toString()) {
                            onIntent(RecordIntent.QuickPicked(quick))
                        }
                    }
                }
            }
            state.error?.let {
                Text(it, modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { onIntent(RecordIntent.Save) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(if (state.saving) "保存中…" else "保存") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordScreenPreview() {
    QkTheme {
        RecordScreen(
            state = RecordUiState(
                type = "diet", title = "记饮食", primaryLabel = "摄入热量 千卡",
                presets = listOf("早餐", "午餐", "晚餐", "加餐"), selectedPreset = "午餐",
            ),
            onIntent = {},
        )
    }
}
