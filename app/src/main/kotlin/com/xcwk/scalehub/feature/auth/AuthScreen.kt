package com.xcwk.scalehub.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import com.xcwk.scalehub.ui.legal.PRIVACY_SECTIONS
import com.xcwk.scalehub.ui.legal.LegalDoc
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * auth — 游客门 Screen（需求 3.1：协议勾选先行，验证码/登录/游客三路）。
 */
@Composable
fun AuthScreen(state: AuthUiState, onIntent: (AuthIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("轻刻健康", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 32.dp))
        Text(
            "配合蓝牙体脂秤的个人健康记录。可游客使用，也可手机号登录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        QkCard(modifier = Modifier.padding(top = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.agreed, onCheckedChange = { onIntent(AuthIntent.AgreedChanged(it)) })
                Text(
                    buildString {
                        append("我已阅读并同意 ")
                        append("《用户协议》")
                        append(" 与 ")
                        append("《隐私政策》")
                    },
                    modifier = Modifier.clickable { onIntent(AuthIntent.AgreedChanged(!state.agreed)) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                TextButton(onClick = { onIntent(AuthIntent.OpenLegal(LegalDoc.AGREEMENT)) }) {
                    Text("查看用户协议", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { onIntent(AuthIntent.OpenLegal(LegalDoc.PRIVACY)) }) {
                    Text("查看隐私政策", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            OutlinedTextField(
                value = state.phone,
                onValueChange = { onIntent(AuthIntent.PhoneChanged(it)) },
                label = { Text("手机号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.code,
                onValueChange = { onIntent(AuthIntent.CodeChanged(it)) },
                label = { Text("短信验证码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(
                onClick = { onIntent(AuthIntent.SendCode) },
                enabled = !state.sendingCode && state.countdown == 0,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(
                    when {
                        state.countdown > 0 -> "${state.countdown}s 后可重发"
                        state.sendingCode -> "发送中…"
                        else -> "获取验证码"
                    },
                )
            }
            Button(
                onClick = { onIntent(AuthIntent.SignIn) },
                enabled = !state.signingIn,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(if (state.signingIn) "登录中…" else "登录 / 注册") }
            state.message?.let {
                Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            OutlinedButton(
                onClick = { onIntent(AuthIntent.EnterGuest) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("游客进入（数据只存本机）") }
        }
    }

    state.legalDoc.let { doc ->
        if (doc != LegalDoc.NONE) {
            val sections = if (doc == LegalDoc.AGREEMENT) AGREEMENT_SECTIONS else PRIVACY_SECTIONS
            val title = if (doc == LegalDoc.AGREEMENT) "用户协议" else "隐私政策"
            AlertDialog(
                onDismissRequest = { onIntent(AuthIntent.CloseLegal) },
                title = { Text(title) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        sections.forEach { (heading, body) ->
                            Text(heading, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                            Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onIntent(AuthIntent.CloseLegal) }) { Text("知道了") }
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    QkTheme {
        AuthScreen(state = AuthUiState(loading = false, agreed = true, phone = "13800138000")) {}
    }
}
