package com.xcwk.scalehub.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkScreenHeader
import com.xcwk.scalehub.ui.components.QkHeaderStyle
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * verify-code — 验证码输入页（原型 v-verify）。
 *
 * 表单：验证码输入框（6 位数字，显示已发送手机号）
 * 导航：点击「登录」→ 引导/主页；倒计时结束可重发
 * 文案：全部逐字取自 design/prototypes/interactive/01-login.html v-verify
 */

// ---- 逐字文案（原型 v-verify） ----

/** 验证码页文案。 */
object VerifyCodeText {
    const val NavTitle = "输入验证码"
    const val Headline = "输入验证码"
    const val SublinePrefix = "验证码已发送至 "
    const val CodeLabel = "验证码"
    const val CodePlaceholder = "6 位验证码"
    const val ResendPrefix = "重新发送（"
    const val ResendSuffix = "s）"
    const val CantReceive = "收不到验证码？"
    const val Submit = "登录"
}

// ---- 表单状态与意图 ----

/** 验证码页的呈现状态。 */
data class VerifyCodeUiState(
    val phone: String = "",           // 已脱敏的手机号（如 +86 138****0000）
    val code: String = "",
    val submitAttempted: Boolean = false,
    val countdown: Int = 0,
) {
    /** 验证码是否为空。 */
    val codeEmpty: Boolean get() = code.trim().isEmpty()
    /** 验证码是否合法（6 位数字）。 */
    val codeValid: Boolean get() = code.trim().matches(Regex("^\\d{6}$"))
    /** 是否展示字段错误。 */
    val showErrors: Boolean get() = submitAttempted
}

/** 验证码页的意图。 */
sealed interface VerifyCodeIntent {
    data class CodeChanged(val value: String) : VerifyCodeIntent
    data object Resend : VerifyCodeIntent
    data object Submit : VerifyCodeIntent
    data object CantReceive : VerifyCodeIntent
}

/** 纯呈现态转移。 */
fun verifyCodeReduce(state: VerifyCodeUiState, intent: VerifyCodeIntent): VerifyCodeUiState = when (intent) {
    is VerifyCodeIntent.CodeChanged -> state.copy(code = intent.value, submitAttempted = false)
    VerifyCodeIntent.Resend -> state.copy(countdown = 60)
    VerifyCodeIntent.Submit -> state.copy(submitAttempted = true)
    VerifyCodeIntent.CantReceive -> state
}

// ---- 倒计时副作用 ----

/**
 * 重发倒计时：countdown > 0 时每秒 -1。
 */
@Composable
internal fun VerifyCodeCountdownEffect(
    state: VerifyCodeUiState,
    onTick: (Int) -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(state.countdown) {
        if (state.countdown > 0) {
            kotlinx.coroutines.delay(1000)
            onTick(state.countdown - 1)
        }
    }
}

// ---- 页面 ----

/**
 * 验证码输入页（原型 v-verify）。
 *
 * @param onVerifyLogin 点击「登录」且校验通过：进入引导或主页
 * @param onResend 点击「重新发送」：重新发送验证码
 * @param onCantReceive 点击「收不到验证码？」：进入找回流程或提示
 * @param onBack 返回键：返回手机号登录页
 */
@Composable
fun VerifyCodeScreen(
    state: VerifyCodeUiState,
    onIntent: (VerifyCodeIntent) -> Unit,
    onVerifyLogin: (code: String) -> Unit,
    onResend: () -> Unit,
    onCantReceive: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    // 倒计时副作用
    var countdown by remember { mutableStateOf(state.countdown) }
    if (countdown != state.countdown) countdown = state.countdown

    VerifyCodeCountdownEffect(
        state = state.copy(countdown = countdown),
        onTick = { countdown = it },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Lg),
    ) {
        // 导航栏：返回 + 居中标题
        QkScreenHeader(
            title = VerifyCodeText.NavTitle,
            onBack = onBack,
            style = QkHeaderStyle.Centered,
            modifier = Modifier.padding(bottom = QkSpacing.Xs),
        )

        // Hero 区：显示已发送手机号
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Xl),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                VerifyCodeText.Headline,
                style = MaterialTheme.typography.headlineSmall,
                color = QkColor.Fg,
            )
            Text(
                "${VerifyCodeText.SublinePrefix}$state.phone",
                modifier = Modifier.padding(top = QkSpacing.Xs + QkSpacing.Xs),
                style = MaterialTheme.typography.bodyMedium,
                color = QkColor.Muted,
            )
        }

        // 表单区：验证码输入框
        Column(
            modifier = Modifier.padding(top = QkSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Lg),
        ) {
            QkTextField(
                value = state.code,
                onValueChange = { onIntent(VerifyCodeIntent.CodeChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = VerifyCodeText.CodeLabel,
                placeholder = VerifyCodeText.CodePlaceholder,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (state.showErrors && state.codeEmpty) {
                FieldError("请输入验证码")
            } else if (state.showErrors && !state.codeValid && state.code.isNotEmpty()) {
                FieldError("验证码为 6 位数字")
            }
        }

        // 重发 / 收不到验证码
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (countdown > 0)
                        "${VerifyCodeText.ResendPrefix}$countdown${VerifyCodeText.ResendSuffix}"
                    else
                        "可重新发送验证码",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (countdown > 0) QkColor.Muted else QkColor.Primary,
                )
                Text(
                    VerifyCodeText.CantReceive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onIntent(VerifyCodeIntent.CantReceive); onCantReceive() })
                        .padding(end = QkSpacing.Md),
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Primary,
                    textAlign = TextAlign.End,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 底部登录按钮
        QkButton(
            text = VerifyCodeText.Submit,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.codeValid,
            onClick = {
                onIntent(VerifyCodeIntent.Submit)
                if (state.codeValid) {
                    onVerifyLogin(state.code.trim())
                }
            },
        )
    }
}

/** 字段级错误提示（红色小字）。 */
@Composable
private fun FieldError(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = QkSpacing.Xs),
        style = MaterialTheme.typography.labelSmall,
        color = QkColor.Danger,
    )
}

@Preview(showBackground = true, name = "VerifyCodeScreen")
@Composable
private fun VerifyCodeScreenPreview() {
    QkTheme {
        VerifyCodeScreen(
            state = VerifyCodeUiState(phone = "+86 138****0000"),
            onIntent = {},
            onVerifyLogin = { _ -> },
            onResend = {},
            onCantReceive = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, name = "VerifyCodeScreen - With Error")
@Composable
private fun VerifyCodeScreenErrorPreview() {
    QkTheme {
        VerifyCodeScreen(
            state = VerifyCodeUiState(phone = "+86 138****0000", code = "123", submitAttempted = true),
            onIntent = {},
            onVerifyLogin = { _ -> },
            onResend = {},
            onCantReceive = {},
            onBack = {},
        )
    }
}
