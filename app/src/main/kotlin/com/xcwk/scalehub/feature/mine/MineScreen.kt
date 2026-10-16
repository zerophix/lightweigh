package com.xcwk.scalehub.feature.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkChip
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * mine — 四件套之 Screen：我的资料表单，无状态渲染 + 事件转发。
 * 视觉只用主题槽与 Qk 组件（DEC-021 支柱5）；滚轮选择器属 M4 视觉轮 TODO。
 */
@Composable
fun MineScreen(state: MineUiState, onIntent: (MineIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("我的资料", style = MaterialTheme.typography.headlineSmall)
        if (state.unsetHint) {
            QkEmptyHint("未设身高，体脂估算会不准确——请先完善资料", modifier = Modifier.padding(top = 4.dp))
        }
        QkCard(modifier = Modifier.padding(top = 16.dp)) {
            OutlinedTextField(
                value = state.nickname,
                onValueChange = { onIntent(MineIntent.NicknameChanged(it)) },
                label = { Text("昵称（≤20 字，可空）") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                QkChip("男", state.male) { onIntent(MineIntent.MaleChanged(true)) }
                QkChip("女", !state.male) { onIntent(MineIntent.MaleChanged(false)) }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(
                    value = state.birthYear,
                    onValueChange = { onIntent(MineIntent.YearChanged(it)) },
                    label = { Text("年") },
                    modifier = Modifier.weight(1.4f).padding(end = 6.dp),
                )
                OutlinedTextField(
                    value = state.birthMonth,
                    onValueChange = { onIntent(MineIntent.MonthChanged(it)) },
                    label = { Text("月") },
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                )
                OutlinedTextField(
                    value = state.birthDay,
                    onValueChange = { onIntent(MineIntent.DayChanged(it)) },
                    label = { Text("日") },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = state.heightCm,
                onValueChange = { onIntent(MineIntent.HeightChanged(it)) },
                label = { Text("身高 cm（100..230）") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        if (state.summary.isNotBlank()) {
            Text(state.summary, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
        }
        if (state.saved) {
            Text("已保存", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary)
        }
        Button(
            onClick = { onIntent(MineIntent.Save) },
            enabled = !state.saving && !state.loading,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text(if (state.saving) "保存中…" else "保存") }
    }
}

@Preview(showBackground = true)
@Composable
private fun MineScreenPreview() {
    QkTheme {
        MineScreen(
            state = MineUiState(
                loading = false,
                male = true,
                birthYear = "1996", birthMonth = "5", birthDay = "20",
                heightCm = "175", nickname = "测试",
                summary = "男 | 30岁 | 175cm",
                unsetHint = false,
            ),
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun MineScreenUnsetPreview() {
    QkTheme {
        MineScreen(
            state = MineUiState(loading = false, summary = "男 | --岁 | --cm", unsetHint = true),
        ) {}
    }
}
