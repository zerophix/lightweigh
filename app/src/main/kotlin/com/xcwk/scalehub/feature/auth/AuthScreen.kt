package com.xcwk.scalehub.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.legal.AGREEMENT_SECTIONS
import com.xcwk.scalehub.ui.legal.PRIVACY_SECTIONS
import com.xcwk.scalehub.ui.legal.LegalDoc
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * auth — 游客门 Screen（需求 3.1：协议勾选先行，验证码/登录/游客三路）。
 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onIntent: (AuthIntent) -> Unit,
    onOpenAuthFlow: () -> Unit = {},
) {
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
            // 登录方式切换
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    LoginMode.CODE to "验证码",
                    LoginMode.PASSWORD to "密码",
                    LoginMode.EMAIL to "邮箱",
                ).forEach { (mode, label) ->
                    val selected = state.loginMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onIntent(AuthIntent.LoginModeChanged(mode)) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.loginMode == LoginMode.EMAIL) {
                QkTextField(
                    value = state.email,
                    onValueChange = { onIntent(AuthIntent.EmailChanged(it)) },
                    label = "邮箱",
                    modifier = Modifier.fillMaxWidth(),
                )
                QkTextField(
                    value = state.password,
                    onValueChange = { onIntent(AuthIntent.PasswordChanged(it)) },
                    label = "密码",
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            } else {
                QkTextField(
                    value = state.phone,
                    onValueChange = { onIntent(AuthIntent.PhoneChanged(it)) },
                    label = "手机号",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.loginMode == LoginMode.CODE) {
                    QkTextField(
                        value = state.code,
                        onValueChange = { onIntent(AuthIntent.CodeChanged(it)) },
                        label = "短信验证码",
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    QkTextField(
                        value = state.password,
                        onValueChange = { onIntent(AuthIntent.PasswordChanged(it)) },
                        label = "密码",
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            if (state.loginMode == LoginMode.CODE) {
                QkButton(
                    text = when {
                        state.countdown > 0 -> "${state.countdown}s 后可重发"
                        state.sendingCode -> "发送中…"
                        else -> "获取验证码"
                    },
                    enabled = !state.sendingCode && state.countdown == 0,
                    style = QkButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    onClick = { onIntent(AuthIntent.SendCode) },
                )
            }
            QkButton(
                text = when {
                    state.signingIn -> "登录中…"
                    state.loginMode == LoginMode.PASSWORD -> "密码登录"
                    state.loginMode == LoginMode.EMAIL -> "邮箱登录"
                    else -> "登录 / 注册"
                },
                enabled = !state.signingIn,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = { onIntent(AuthIntent.SignIn) },
            )
            state.message?.let {
                Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            // 微信登录
            QkButton(
                text = if (state.signingIn && state.loginMode == LoginMode.WECHAT) "微信登录中…" else "微信登录",
                style = QkButtonStyle.Secondary,
                enabled = !state.signingIn,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onIntent(AuthIntent.LoginModeChanged(LoginMode.WECHAT)); onIntent(AuthIntent.SignIn) },
            )
            QkButton(
                text = "游客进入（数据只存本机）",
                style = QkButtonStyle.Ghost,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = { onIntent(AuthIntent.EnterGuest) },
            )
            // 接入层入口：邮箱 / 访客 / 找回等呈现式屏由 AuthFlowRoute 串成链
            QkButton(
                text = "其他方式登录",
                style = QkButtonStyle.Ghost,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = onOpenAuthFlow,
            )
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
        AuthScreen(
            state = AuthUiState(loading = false, agreed = true, phone = "13800138000"),
            onIntent = {},
        )
    }
}
