package com.xcwk.scalehub.feature.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.shell.WiringEntries
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * ai — 四件套之 Screen：问答对话（需求：AI 页返回回到刚才页面；游客与断网如实提示）。
 */
@Composable
fun AiScreen(
    state: AiUiState,
    onIntent: (AiIntent) -> Unit,
    onBack: () -> Unit = {},
) {
    // 接入波：AI 边界与降级示例（T-085）从本页进入，返回即回问答页
    var showBoundaries by rememberSaveable { mutableStateOf(false) }
    if (showBoundaries) {
        AiBoundariesRoute(onBack = { showBoundaries = false })
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("AI 问答", style = MaterialTheme.typography.headlineSmall)
        }
        QkCard(modifier = Modifier.padding(top = 8.dp)) {
            QkListRow(
                title = WiringEntries.AiBoundaries.title,
                subtitle = WiringEntries.AiBoundaries.hint,
                showDivider = false,
                onClick = { showBoundaries = true },
            )
        }
        if (!state.formal) {
            QkCard(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "AI 问答需要手机号登录；游客数据不送出本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            state.messages.forEach { msg ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        msg.text,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .padding(start = if (msg.mine) 48.dp else 0.dp, end = if (msg.mine) 0.dp else 48.dp),
                        color = if (msg.mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        state.error?.let {
            Text(it, modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            QkTextField(
                value = state.input,
                onValueChange = { onIntent(AiIntent.InputChanged(it)) },
                placeholder = "问点什么，如：减脂期怎么吃",
                enabled = !state.sending,
                modifier = Modifier.weight(1f),
            )
            QkButton(
                text = if (state.sending) "…" else "发送",
                enabled = !state.sending,
                modifier = Modifier.padding(start = 8.dp),
                onClick = { onIntent(AiIntent.Send) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AiScreenPreview() {
    QkTheme {
        AiScreen(
            state = AiUiState(
                loading = false,
                formal = true,
                messages = listOf(
                    AiMessage(true, "减脂期晚餐怎么吃？"),
                    AiMessage(false, "优先蛋白质与蔬菜，主食减半，睡前两小时不加餐。"),
                ),
            ),
            onIntent = {},
        ) {}
    }
}
