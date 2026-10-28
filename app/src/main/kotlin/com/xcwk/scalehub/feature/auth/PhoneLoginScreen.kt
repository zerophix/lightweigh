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
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkScreenHeader
import com.xcwk.scalehub.ui.components.QkHeaderStyle
import com.xcwk.scalehub.ui.components.QkFieldError
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * phone-login — 手机号登录页（原型 v-phone）。
 *
 * 表单：手机号（+86 前缀）+ 验证码输入框（带「获取验证码」动作）
 * 导航：点击「获取验证码」→ VerifyCodeScreen；点击「其他方式登录」返回上一层
 * 文案：全部逐字取自 design/prototypes/interactive/01-login.html v-phone
 */

// ---- 逐字文案（原型 v-phone） ----

/** 手机号登录页文案。 */
object PhoneLoginText {
    const val NavTitle = "手机号登录"
    const val Headline = "输入手机号"
    const val Subline = "未注册的号码将自动创建轻衡账号"
    const val PhoneLabel = "手机号"
    const val PhonePlaceholder = "请输入手机号"
    const val PhonePrefix = "+86"
    const val CodeLabel = "验证码"
    const val CodePlaceholder = "6 位验证码"
    const val GetCodeAction = "获取验证码"
    const val Submit = "登录 / 注册"
    const val OtherMethods = "其他方式登录 ›"
    const val AgreementPrefix = "登录即代表同意 "
    const val AgreementTerms = "《用户协议》"
    const val AgreementAnd = " 与 "
    const val AgreementPrivacy = "《隐私政策》"
}

// ---- 表单状态与意图 ----

/** 手机号登录表单的呈现状态。 */
data class PhoneLoginUiState(
    val phone: String = "",
    val code: String = "",
    val submitAttempted: Boolean = false,
    val sendingCode: Boolean = false,
    val countdown: Int = 0,
) {
    /** 手机号是否为空。 */
    val phoneEmpty: Boolean get() = phone.trim().isEmpty()
    /** 验证码是否为空。 */
    val codeEmpty: Boolean get() = code.trim().isEmpty()
    /** 手机号是否合法（11 位数字）。 */
    val phoneValid: Boolean get() = phone.trim().matches(Regex("^1\\d{10}$"))
    /** 验证码是否合法（6 位数字）。 */
    val codeValid: Boolean get() = code.trim().matches(Regex("^\\d{6}$"))
    /** 表单整体是否可提交。 */
    val canSubmit: Boolean get() = phoneValid && codeValid
    /** 是否展示字段错误：只有尝试提交过才提示。 */
    val showErrors: Boolean get() = submitAttempted
}

/** 手机号登录表单的意图。 */
sealed interface PhoneLoginIntent {
    data class PhoneChanged(val value: String) : PhoneLoginIntent
    data class CodeChanged(val value: String) : PhoneLoginIntent
    data object GetCode : PhoneLoginIntent
    data object Submit : PhoneLoginIntent
    data object OtherMethods : PhoneLoginIntent
}

/** 纯呈现态转移：输入改动会清掉提交标记，不发请求。 */
fun phoneLoginReduce(state: PhoneLoginUiState, intent: PhoneLoginIntent): PhoneLoginUiState = when (intent) {
    is PhoneLoginIntent.PhoneChanged -> state.copy(phone = intent.value, submitAttempted = false)
    is PhoneLoginIntent.CodeChanged -> state.copy(code = intent.value, submitAttempted = false)
    PhoneLoginIntent.GetCode -> state.copy(sendingCode = true, countdown = 60)
    PhoneLoginIntent.Submit -> state.copy(submitAttempted = true)
    PhoneLoginIntent.OtherMethods -> state
}

// ---- 倒计时副作用 ----

/**
 * 验证码倒计时：countdown > 0 时每秒 -1，归零后关闭 sendingCode。
 * 调用侧在 LaunchedEffect 中驱动，不在 reduce 里处理时间。
 */
@Composable
internal fun PhoneLoginCountdownEffect(
    state: PhoneLoginUiState,
    onTick: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(state.countdown) {
        if (state.countdown > 0) {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            kotlinx.coroutines.delay(1000)
            onTick(state.countdown - 1)
        } else if (state.sendingCode) {
            onFinish()
        }
    }
}

// ---- 页面 ----

/**
 * 手机号登录页（原型 v-phone）。
 *
 * @param onGetCode 点击「获取验证码」：进入验证码页
 * @param onPhoneLogin 表单校验通过点击「登录 / 注册」：进入引导或主页
 * @param onOtherMethods 点击「其他方式登录」：返回登录方式选择
 * @param onBack 返回键（默认同 onOtherMethods）
 */
@Composable
fun PhoneLoginScreen(
    state: PhoneLoginUiState,
    onIntent: (PhoneLoginIntent) -> Unit,
    onGetCode: () -> Unit,
    onPhoneLogin: (phone: String, code: String) -> Unit,
    onOtherMethods: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = onOtherMethods,
) {
    // 倒计时副作用
    var countdown by remember { mutableStateOf(state.countdown) }
    var sendingCode by remember { mutableStateOf(state.sendingCode) }
    if (countdown != state.countdown) countdown = state.countdown
    if (sendingCode != state.sendingCode) sendingCode = state.sendingCode

    PhoneLoginCountdownEffect(
        state = state.copy(countdown = countdown, sendingCode = sendingCode),
        onTick = { countdown = it },
        onFinish = { sendingCode = false },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Lg),
    ) {
        // 导航栏：返回 + 居中标题
        QkScreenHeader(
            title = PhoneLoginText.NavTitle,
            onBack = onBack,
            style = QkHeaderStyle.Centered,
            modifier = Modifier.padding(bottom = QkSpacing.Xs),
        )

        // Hero 区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Xl),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                PhoneLoginText.Headline,
                style = MaterialTheme.typography.headlineSmall,
                color = QkColor.Fg,
            )
            Text(
                PhoneLoginText.Subline,
                modifier = Modifier.padding(top = QkSpacing.Xs + QkSpacing.Xs),
                style = MaterialTheme.typography.bodyMedium,
                color = QkColor.Muted,
            )
        }

        // 表单区
        Column(
            modifier = Modifier.padding(top = QkSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Lg),
        ) {
            // 手机号行：前缀 + 输入框
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // +86 前缀
                    Text(
                        PhoneLoginText.PhonePrefix,
                        style = MaterialTheme.typography.bodyLarge,
                        color = QkColor.Fg,
                        modifier = Modifier.padding(end = QkSpacing.Md),
                    )
                    // 输入框
                    QkTextField(
                        value = state.phone,
                        onValueChange = { onIntent(PhoneLoginIntent.PhoneChanged(it)) },
                        modifier = Modifier.weight(1f),
                        label = PhoneLoginText.PhoneLabel,
                        placeholder = PhoneLoginText.PhonePlaceholder,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }
                if (state.showErrors && state.phoneEmpty) {
                    QkFieldError("请输入手机号")
                } else if (state.showErrors && !state.phoneValid && state.phone.isNotEmpty()) {
                    QkFieldError("手机号格式不正确")
                }
            }

            // 验证码行：输入框 + 获取验证码按钮
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
                ) {
                    QkTextField(
                        value = state.code,
                        onValueChange = { onIntent(PhoneLoginIntent.CodeChanged(it)) },
                        modifier = Modifier.weight(1f),
                        label = PhoneLoginText.CodeLabel,
                        placeholder = PhoneLoginText.CodePlaceholder,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    // 获取验证码按钮（文本按钮样式）
                    Text(
                        text = when {
                            state.countdown > 0 -> "${state.countdown}s 后可重发"
                            state.sendingCode -> "发送中…"
                            else -> PhoneLoginText.GetCodeAction
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = !state.sendingCode && state.countdown == 0,
                                onClick = { onIntent(PhoneLoginIntent.GetCode); onGetCode() },
                            )
                            .padding(horizontal = QkSpacing.Md, vertical = QkSpacing.Md),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (!state.sendingCode && state.countdown == 0)
                            QkColor.Primary
                        else
                            QkColor.Muted,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
                if (state.showErrors && state.codeEmpty) {
                    QkFieldError("请输入验证码")
                } else if (state.showErrors && !state.codeValid && state.code.isNotEmpty()) {
                    QkFieldError("验证码为 6 位数字")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 底部动作区
        Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            QkButton(
                text = PhoneLoginText.Submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSubmit,
                onClick = {
                    onIntent(PhoneLoginIntent.Submit)
                    if (state.canSubmit) {
                        onPhoneLogin(state.phone.trim(), state.code.trim())
                    }
                },
            )
            Text(
                PhoneLoginText.OtherMethods,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onIntent(PhoneLoginIntent.OtherMethods); onOtherMethods() })
                    .padding(vertical = QkSpacing.Sm),
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
            // 协议文案
            Text(
                buildString {
                    append(PhoneLoginText.AgreementPrefix)
                    append(PhoneLoginText.AgreementTerms)
                    append(PhoneLoginText.AgreementAnd)
                    append(PhoneLoginText.AgreementPrivacy)
                },
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, name = "PhoneLoginScreen")
@Composable
private fun PhoneLoginScreenPreview() {
    QkTheme {
        PhoneLoginScreen(
            state = PhoneLoginUiState(phone = "13800138000"),
            onIntent = {},
            onGetCode = {},
            onPhoneLogin = { _, _ -> },
            onOtherMethods = {},
        )
    }
}

@Preview(showBackground = true, name = "PhoneLoginScreen - With Errors")
@Composable
private fun PhoneLoginScreenErrorPreview() {
    QkTheme {
        PhoneLoginScreen(
            state = PhoneLoginUiState(phone = "138", code = "123", submitAttempted = true),
            onIntent = {},
            onGetCode = {},
            onPhoneLogin = { _, _ -> },
            onOtherMethods = {},
        )
    }
}
