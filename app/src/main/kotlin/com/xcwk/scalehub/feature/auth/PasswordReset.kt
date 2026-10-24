package com.xcwk.scalehub.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkStroke

/**
 * auth — 密码重置页与账号恢复共享原语（T-073 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/01-login.html` 的 `v-reset`：
 * 导航「重置密码」→ 标题「设置新密码」+「为账号 138****0000 重置密码」
 * → 验证码（含「58s 后重发」倒计时）→ 新密码 → 确认新密码 →「确认重置」。
 *
 * ## 验收（AC-J1-02）
 * - 页面文案逐字对齐原型（常量为唯一真源，JVM 单测钉死）
 * - 倒计时秒数由外部传入，本文件不跑计时器；结束前重发不可用
 * - 密码规则三条（8 位以上 / 含字母和数字 / 两次一致）与「不与旧密码相同」为纯函数
 *
 * ## 边界（呈现式组件）
 * 本文件不发验证码、不提交密码：验证与重置请求经 [PasswordResetView.onSubmit] /
 * [PasswordResetView.onResend] 回调交由既有 auth 流程。密码明文不进日志、不入展示态。
 *
 * ## 文件分工
 * - `PasswordReset.kt`（本文件）：v-reset 页 + 密码校验/掩码/倒计时纯函数 + 跨两页共享原语
 * - `AccountRecovery.kt`：找回方式→验证→设密→成功的五态状态机与五屏
 */

// ---- 静态文案（v-reset，逐字取自原型） ----

const val TITLE_RESET_PASSWORD = "重置密码"
const val HEADLINE_SET_PASSWORD = "设置新密码"
const val RESET_ACCOUNT_TEMPLATE = "为账号 %s 重置密码"
const val LABEL_VERIFICATION_CODE = "验证码"
const val PLACEHOLDER_VERIFICATION_CODE = "6 位验证码"
const val LABEL_NEW_PASSWORD = "新密码"
const val LABEL_CONFIRM_PASSWORD = "确认新密码"
const val PLACEHOLDER_NEW_PASSWORD = "8–20 位，含字母和数字"
const val PLACEHOLDER_CONFIRM_PASSWORD = "再次输入新密码"
const val ACTION_CONFIRM_RESET = "确认重置"
const val HINT_NEW_PASSWORD_DIFFERS = "新密码不能与旧密码相同"
const val HINT_OTHER_SESSIONS_INVALID = "修改密码后，其他设备的登录状态将失效。"
const val RESEND_COUNTDOWN_TEMPLATE = "%ds 后重发"
const val ACTION_RESEND = "重新发送"

/** 密码规则三条（原型 v-set-password 规则行）。 */
const val RULE_MIN_LENGTH = "8 位以上"
const val RULE_LETTER_AND_DIGIT = "含字母和数字"
const val RULE_MATCH = "两次一致"
val PASSWORD_RULES = listOf(RULE_MIN_LENGTH, RULE_LETTER_AND_DIGIT, RULE_MATCH)

/** 原型演示账号的掩码形态（真实掩码由 [maskedPhone] / [maskedEmail] 从绑定值算出）。 */
const val DEFAULT_PHONE_MASKED = "138****0000"
const val DEFAULT_EMAIL_MASKED = "l***@example.com"

/** 验证码位数（原型 v-phone-verify / v-email-verify 均为 6 位）。 */
const val VERIFICATION_CODE_LENGTH = 6

// ---- 纯函数：掩码 / 倒计时 / 验证码 / 密码规则（JVM 单测钉死） ----

/** 手机号掩码：保留前 3 后 4（`13800000000` → `138****0000`）。 */
fun maskedPhone(phone: String): String =
    if (phone.length >= 7) phone.take(3) + "****" + phone.takeLast(4) else phone

/** 邮箱掩码：保留首字符与域名（`linyue@example.com` → `l***@example.com`）。 */
fun maskedEmail(email: String): String {
    val at = email.indexOf('@')
    return if (at > 0) email.first() + "***" + email.substring(at) else email
}

/** v-reset 副标题：「为账号 138****0000 重置密码」。 */
fun resetAccountSubtitle(masked: String): String = RESET_ACCOUNT_TEMPLATE.format(masked)

/**
 * 倒计时按钮文案：剩余秒数 > 0 时「58s 后重发」，归零后「重新发送」。
 *
 * 秒数由外部计时逻辑注入——组件只渲染，不自己跑 [kotlinx.coroutines.delay]。
 */
fun resendLabel(secondsLeft: Int): String =
    if (secondsLeft > 0) RESEND_COUNTDOWN_TEMPLATE.format(secondsLeft) else ACTION_RESEND

/** 倒计时结束（或外部给的秒数已归零）才允许重发。 */
fun canResend(secondsLeft: Int): Boolean = secondsLeft <= 0

/** 验证码是否填满：恰好 6 位且全为数字。 */
fun isVerificationCodeComplete(code: String): Boolean =
    code.length == VERIFICATION_CODE_LENGTH && code.all { it.isDigit() }

/** 密码规则校验结果（每条对应原型规则行的一个勾选位）。 */
data class PasswordValidation(
    val lengthOk: Boolean,
    val letterDigitOk: Boolean,
    val matchOk: Boolean,
    val differsFromOld: Boolean,
) {
    /** 四项全通过才允许提交。 */
    val isValid: Boolean get() = lengthOk && letterDigitOk && matchOk && differsFromOld
}

/** 规则行的一项：[satisfied] 决定勾选色，文案取 [PASSWORD_RULES]。 */
data class PasswordRule(val text: String, val satisfied: Boolean)

/**
 * 密码规则纯校验。
 *
 * 口径（来自原型与验收）：
 * - 「8 位以上」：长度 ≥ 8（原型 placeholder 写 8–20 位，但验收规则只约束下限，不额外造上限）
 * - 「含字母和数字」：至少一个字母且至少一个数字
 * - 「两次一致」：确认密码非空且与新密码相同
 * - 「新密码不能与旧密码相同」：[oldPassword] 未知/为空时视为通过（旧值不落展示态）
 */
fun validateNewPassword(
    newPassword: String,
    confirmPassword: String,
    oldPassword: String? = null,
): PasswordValidation = PasswordValidation(
    lengthOk = newPassword.length >= 8,
    letterDigitOk = newPassword.any { it.isLetter() } && newPassword.any { it.isDigit() },
    matchOk = newPassword.isNotEmpty() && newPassword == confirmPassword,
    differsFromOld = oldPassword.isNullOrEmpty() || newPassword != oldPassword,
)

/** 把校验结果映射成三行规则（顺序与原型一致）。 */
fun passwordRules(validation: PasswordValidation): List<PasswordRule> = listOf(
    PasswordRule(RULE_MIN_LENGTH, validation.lengthOk),
    PasswordRule(RULE_LETTER_AND_DIGIT, validation.letterDigitOk),
    PasswordRule(RULE_MATCH, validation.matchOk),
)

// ---- v-reset 呈现态 ----

/**
 * 重置密码页状态（全部由调用方持有）。
 *
 * [codeSecondsLeft] 是外部倒计时秒数；[oldPassword] 只用于「不与旧密码相同」比较，
 * 不参与渲染，也不出现在任何展示文案里。
 */
data class PasswordResetUiState(
    val accountMasked: String = DEFAULT_PHONE_MASKED,
    val code: String = "",
    val codeSecondsLeft: Int = 58,
    val newPassword: String = "",
    val confirmPassword: String = "",
    val oldPassword: String? = null,
) {
    val validation: PasswordValidation
        get() = validateNewPassword(newPassword, confirmPassword, oldPassword)

    /** 验证码填满且密码四条规则全过才允许「确认重置」。 */
    val canSubmit: Boolean get() = isVerificationCodeComplete(code) && validation.isValid
}

/**
 * v-reset 重置密码页。
 *
 * 回调即外部交接点（本组件不发请求、不落库）：
 * - [onSubmit] 确认重置（参数为新密码，明文只经此回调）
 * - [onResend] 倒计时结束后的重发
 * - [onCodeChanged] / [onNewPasswordChanged] / [onConfirmPasswordChanged] 受控输入
 */
@Composable
fun PasswordResetView(
    state: PasswordResetUiState,
    onCodeChanged: (String) -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    onResend: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Page),
    ) {
        AuthNavBar(TITLE_RESET_PASSWORD, onBack)
        AuthHero(
            headline = HEADLINE_SET_PASSWORD,
            subline = resetAccountSubtitle(state.accountMasked),
            modifier = Modifier.padding(top = QkSpacing.Xl),
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))

        AuthCodeField(
            value = state.code,
            onValueChange = onCodeChanged,
            secondsLeft = state.codeSecondsLeft,
            onResend = onResend,
        )
        Spacer(modifier = Modifier.height(QkSpacing.Lg))
        AuthPasswordSection(
            newPassword = state.newPassword,
            confirmPassword = state.confirmPassword,
            validation = state.validation,
            onNewPasswordChanged = onNewPasswordChanged,
            onConfirmPasswordChanged = onConfirmPasswordChanged,
        )

        Spacer(modifier = Modifier.weight(1f))
        QkButton(
            text = ACTION_CONFIRM_RESET,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
            onClick = { onSubmit(state.newPassword) },
        )
    }
}

// ---- 跨两页共享的呈现原语（供 AccountRecovery.kt 复用） ----

/** 压栈页导航条：返回圆钮 + 页标题（原型 .navbar）。 */
@Composable
internal fun AuthNavBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkBackButton(onClick = onBack)
        Text(
            title,
            modifier = Modifier.weight(1f).padding(start = QkSpacing.Sm),
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
        )
    }
}

/** 页头：大标题 + 一句说明（原型 .hero 的 .h1.sm / .h2）。 */
@Composable
internal fun AuthHero(headline: String, subline: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(headline, style = MaterialTheme.typography.headlineSmall, color = QkColor.Fg)
        Text(
            subline,
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Muted,
        )
    }
}

/**
 * 提示横幅（原型 .banner）：薄荷底 + 图标 + 小字提示。
 * [warn] 为 true 时用琥珀色（原型 .banner.warn，如验证码安全提示）。
 */
@Composable
internal fun AuthBanner(text: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(if (warn) QkColor.AccentSoft else QkColor.Mint)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(
            QkIcons.About,
            tint = if (warn) QkColor.Accent else QkColor.Primary,
            size = QkSpacing.Lg,
        )
        Text(
            text,
            modifier = Modifier.padding(start = QkSpacing.Sm).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
        )
    }
}

/**
 * 输入框（原型 .field）：标签在框外，框内可挂右侧尾随内容（如倒计时文案）。
 *
 * 与 [com.xcwk.scalehub.ui.components.QkTextField] 的差别只有两点：密码遮罩与尾随插槽。
 * 组件库的 QkTextField 目前不开放 visualTransformation / trailingContent，本流程又有
 * 「倒计时在框内右侧」的原型要求，故在流程内做一层薄壳，视觉口径与 QkTextField 一致。
 */
@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(QkRadius.Field)
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.FieldLabel,
                modifier = Modifier.padding(bottom = QkSpacing.Sm),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = QkColor.Fg),
            cursorBrush = SolidColor(QkColor.Primary),
            keyboardOptions = keyboardOptions,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(QkColor.FieldBg)
                .border(
                    QkStroke.Thin,
                    if (focused) QkColor.Primary else QkColor.FieldBorder,
                    shape,
                )
                .padding(horizontal = QkSpacing.Md, vertical = QkSpacing.Md),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = QkColor.Hint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
    }
}

/**
 * 验证码输入框：右侧尾随倒计时（「58s 后重发」/「重新发送」）。
 * 倒计时未结束时点击无效，且不会触发 [onResend]。
 */
@Composable
internal fun AuthCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    secondsLeft: Int,
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = LABEL_VERIFICATION_CODE,
    placeholder: String = PLACEHOLDER_VERIFICATION_CODE,
) {
    AuthTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() }) },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailing = {
            val ready = canResend(secondsLeft)
            Text(
                resendLabel(secondsLeft),
                modifier = Modifier
                    .clip(RoundedCornerShape(QkRadius.Button))
                    .clickable(enabled = ready, onClick = onResend)
                    .padding(horizontal = QkSpacing.Sm, vertical = QkSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = if (ready) QkColor.Primary else QkColor.Muted,
            )
        },
    )
}

/** 新密码 + 确认新密码 + 三行规则（v-reset 与 v-set-password 共用）。 */
@Composable
internal fun AuthPasswordSection(
    newPassword: String,
    confirmPassword: String,
    validation: PasswordValidation,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AuthTextField(
            value = newPassword,
            onValueChange = onNewPasswordChanged,
            label = LABEL_NEW_PASSWORD,
            placeholder = PLACEHOLDER_NEW_PASSWORD,
            password = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(modifier = Modifier.height(QkSpacing.Lg))
        AuthTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChanged,
            label = LABEL_CONFIRM_PASSWORD,
            placeholder = PLACEHOLDER_CONFIRM_PASSWORD,
            password = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(modifier = Modifier.height(QkSpacing.Sm))
        Row(horizontalArrangement = Arrangement.spacedBy(QkSpacing.Lg)) {
            passwordRules(validation).forEach { rule ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "✓",
                        modifier = Modifier.padding(end = QkSpacing.Xs),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (rule.satisfied) QkColor.Primary else QkColor.Hint,
                    )
                    Text(
                        rule.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (rule.satisfied) QkColor.Primary else QkColor.Muted,
                    )
                }
            }
        }
    }
}
