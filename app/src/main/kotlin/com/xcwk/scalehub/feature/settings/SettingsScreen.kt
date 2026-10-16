package com.xcwk.scalehub.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.legal.AGREEMENT_SECTIONS
import com.xcwk.scalehub.ui.legal.LegalDoc
import com.xcwk.scalehub.ui.legal.PRIVACY_SECTIONS
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * settings — 四件套之 Screen：账号三流程 + 法务入口（需求：恢复/退出/注销口径）。
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.headlineSmall)
        }
        QkCard(modifier = Modifier.padding(top = 16.dp)) {
            Text(
                if (state.formal) "已登录：${state.phone}" else "游客使用中（数据只存本机）",
                style = MaterialTheme.typography.titleSmall,
            )
            state.message?.let {
                Text(it, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (state.formal) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ActionRow("从云端恢复当前用户记录", enabled = !state.busy) { onIntent(SettingsIntent.Restore) }
                ActionRow("退出登录（本机与云端记录保留）", enabled = !state.busy) { onIntent(SettingsIntent.SignOutAsk) }
                ActionRow("注销账号（删除云端记录与登录身份）", enabled = !state.busy, danger = true) { onIntent(SettingsIntent.DeleteAsk) }
            }
        }
        QkCard(modifier = Modifier.padding(top = 10.dp)) {
            ActionRow("用户协议", enabled = true) { onIntent(SettingsIntent.OpenLegal(LegalDoc.AGREEMENT)) }
            ActionRow("隐私政策", enabled = true) { onIntent(SettingsIntent.OpenLegal(LegalDoc.PRIVACY)) }
            Text(
                "轻刻健康 v0.1（本地数据版）",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.SignOutCancel) },
            title = { Text("退出登录？") },
            text = { Text("只清除本机登录状态；本机测量与云端数据都保留。") },
            confirmButton = { TextButton(onClick = { onIntent(SettingsIntent.SignOutConfirm) }) { Text("退出登录") } },
            dismissButton = { TextButton(onClick = { onIntent(SettingsIntent.SignOutCancel) }) { Text("取消") } },
        )
    }
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.DeleteCancel) },
            title = { Text("注销账号") },
            text = {
                Column {
                    Text("将删除云端档案、体脂秤、测量与健康记录，且不可恢复；本机测量保留。")
                    OutlinedTextField(
                        value = state.deleteCode,
                        onValueChange = { onIntent(SettingsIntent.DeleteCodeChanged(it)) },
                        label = { Text("短信验证码") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (state.deleteCodeSent) onIntent(SettingsIntent.DeleteConfirm) else onIntent(SettingsIntent.DeleteSendCode)
                    },
                    enabled = !state.busy,
                ) {
                    Text(
                        when {
                            state.busy -> "处理中…"
                            state.deleteCodeSent -> "确认注销"
                            else -> "发送验证码"
                        },
                    )
                }
            },
            dismissButton = { TextButton(onClick = { onIntent(SettingsIntent.DeleteCancel) }) { Text("取消") } },
        )
    }
    state.legalDoc.let { doc ->
        if (doc != LegalDoc.NONE) {
            val sections = if (doc == LegalDoc.AGREEMENT) AGREEMENT_SECTIONS else PRIVACY_SECTIONS
            AlertDialog(
                onDismissRequest = { onIntent(SettingsIntent.CloseLegal) },
                title = { Text(if (doc == LegalDoc.AGREEMENT) "用户协议" else "隐私政策") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        sections.forEach { (heading, body) ->
                            Text(heading, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                            Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { onIntent(SettingsIntent.CloseLegal) }) { Text("知道了") } },
            )
        }
    }
}

@Composable
private fun ActionRow(title: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        title,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant
        else if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    QkTheme {
        SettingsScreen(state = SettingsUiState(loading = false, formal = true, phone = "13800138000"), onIntent = {}) {}
    }
}
