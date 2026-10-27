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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkScreenHeader
import com.xcwk.scalehub.ui.components.QkHeaderStyle
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkStroke
import com.xcwk.scalehub.ui.theme.QkComponentSize
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * password-login — 账号密码登录页（原型 v-password）。
 *
 * 表单：账号（用户名/手机号/邮箱）+ 密码（带显示/隐藏切换）
 * 导航：点击「登录」→ 引导/主页；点击「忘记密码？」→ 重置密码页；点击「其他方式登录」返回上一层
 * 文案：全部逐字取自 design/prototypes/interactive/01-login.html v-password
 */

// ---- 逐字文案（原型 v-password） ----

/** 账号密码登录页文案。 */
object PasswordLoginText {
    const val NavTitle = "账号密码登录"
    const val Headline = "登录你的账号"
    const val Subline = "支持用户名、手机号或邮箱"
    const val AccountLabel = "账号"
    const val AccountPlaceholder = "用户名 / 手机号 / 邮箱"
    const val PasswordLabel = "密码"
    const val PasswordPlaceholder = "请输入密码"
    const val ForgotPassword = "忘记密码？"
    const val Submit = "登录"
    const val OtherMethods = "其他方式登录 ›"
}

// ---- 表单状态与意图 ----

/** 账号密码登录表单的呈现状态。 */
data class PasswordLoginUiState(
    val account: String = "",
    val password: String = "",
    val submitAttempted: Boolean = false,
    val showPassword: Boolean = false,
) {
    /** 账号是否为空。 */
    val accountEmpty: Boolean get() = account.trim().isEmpty()
    /** 密码是否为空。 */
    val passwordEmpty: Boolean get() = password.isEmpty()
    /** 密码是否足够长（至少 6 位）。 */
    val passwordLongEnough: Boolean get() = password.length >= 6
    /** 表单整体是否可提交。 */
    val canSubmit: Boolean get() = accountEmpty.not() && passwordLongEnough
    /** 是否展示字段错误。 */
    val showErrors: Boolean get() = submitAttempted
}

/** 账号密码登录表单的意图。 */
sealed interface PasswordLoginIntent {
    data class AccountChanged(val value: String) : PasswordLoginIntent
    data class PasswordChanged(val value: String) : PasswordLoginIntent
    data object TogglePasswordVisibility : PasswordLoginIntent
    data object Submit : PasswordLoginIntent
    data object ForgotPassword : PasswordLoginIntent
    data object OtherMethods : PasswordLoginIntent
}

/** 纯呈现态转移。 */
fun passwordLoginReduce(state: PasswordLoginUiState, intent: PasswordLoginIntent): PasswordLoginUiState = when (intent) {
    is PasswordLoginIntent.AccountChanged -> state.copy(account = intent.value, submitAttempted = false)
    is PasswordLoginIntent.PasswordChanged -> state.copy(password = intent.value, submitAttempted = false)
    PasswordLoginIntent.TogglePasswordVisibility -> state.copy(showPassword = !state.showPassword)
    PasswordLoginIntent.Submit -> state.copy(submitAttempted = true)
    PasswordLoginIntent.ForgotPassword -> state
    PasswordLoginIntent.OtherMethods -> state
}

// ---- 密码输入框（带显示/隐藏切换） ----

/**
 * 密码输入框：与 QkTextField 同一套视觉，额外支持显示/隐藏切换。
 * 右侧显示眼睛图标，点击切换明文/密文。
 */
@Composable
private fun AuthPasswordFieldWithToggle(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    showPassword: Boolean,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(QkRadius.Field)
    val visualTransformation: VisualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
    val icon = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility

    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = QkColor.FieldLabel,
            modifier = Modifier.padding(bottom = QkSpacing.Xs + QkSpacing.Xs),
        )
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = QkColor.Fg),
            cursorBrush = SolidColor(QkColor.Primary),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                Box {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = QkColor.Hint,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = QkComponentSize.PasswordToggleButton), // 给眼睛图标留空间
                        )
                    }
                    inner()
                    // 右侧眼睛图标
                    androidx.compose.material3.IconButton(
                        onClick = onToggleVisibility,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = QkSpacing.Sm)
                            .size(QkComponentSize.PasswordToggleButton),
                    ) {
                        androidx.compose.material3.Icon(
                            icon,
                            contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                            tint = QkColor.Muted,
                            modifier = Modifier.size(QkComponentSize.PasswordToggleIcon),
                        )
                    }
                }
            },
        )
    }
}

// ---- 页面 ----

/**
 * 账号密码登录页（原型 v-password）。
 *
 * @param onPasswordLogin 表单校验通过点击「登录」：进入引导或主页
 * @param onForgotPassword 点击「忘记密码？」：进入重置密码页
 * @param onOtherMethods 点击「其他方式登录」：返回登录方式选择
 * @param onBack 返回键（默认同 onOtherMethods）
 */
@Composable
fun PasswordLoginScreen(
    state: PasswordLoginUiState,
    onIntent: (PasswordLoginIntent) -> Unit,
    onPasswordLogin: (account: String, password: String) -> Unit,
    onForgotPassword: () -> Unit,
    onOtherMethods: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = onOtherMethods,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Lg),
    ) {
        // 导航栏：返回 + 居中标题
        QkScreenHeader(
            title = PasswordLoginText.NavTitle,
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
                PasswordLoginText.Headline,
                style = MaterialTheme.typography.headlineSmall,
                color = QkColor.Fg,
            )
            Text(
                PasswordLoginText.Subline,
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
            // 账号输入框
            Column {
                QkTextField(
                    value = state.account,
                    onValueChange = { onIntent(PasswordLoginIntent.AccountChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = PasswordLoginText.AccountLabel,
                    placeholder = PasswordLoginText.AccountPlaceholder,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                if (state.showErrors && state.accountEmpty) {
                    FieldError("请输入账号")
                }
            }

            // 密码输入框（带显示/隐藏）
            Column {
                AuthPasswordFieldWithToggle(
                    value = state.password,
                    onValueChange = { onIntent(PasswordLoginIntent.PasswordChanged(it)) },
                    label = PasswordLoginText.PasswordLabel,
                    placeholder = PasswordLoginText.PasswordPlaceholder,
                    showPassword = state.showPassword,
                    onToggleVisibility = { onIntent(PasswordLoginIntent.TogglePasswordVisibility) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.showErrors && state.passwordEmpty) {
                    FieldError("请输入密码")
                } else if (state.showErrors && !state.passwordLongEnough && state.password.isNotEmpty()) {
                    FieldError("密码至少 6 位")
                }
            }

            // 忘记密码链接
            Text(
                PasswordLoginText.ForgotPassword,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onIntent(PasswordLoginIntent.ForgotPassword); onForgotPassword() })
                    .padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.Primary,
                textAlign = TextAlign.End,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 底部动作区
        Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            QkButton(
                text = PasswordLoginText.Submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSubmit,
                onClick = {
                    onIntent(PasswordLoginIntent.Submit)
                    if (state.canSubmit) {
                        onPasswordLogin(state.account.trim(), state.password)
                    }
                },
            )
            Text(
                PasswordLoginText.OtherMethods,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onIntent(PasswordLoginIntent.OtherMethods); onOtherMethods() })
                    .padding(vertical = QkSpacing.Sm),
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
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

@Preview(showBackground = true, name = "PasswordLoginScreen")
@Composable
private fun PasswordLoginScreenPreview() {
    QkTheme {
        PasswordLoginScreen(
            state = PasswordLoginUiState(account = "user@example.com"),
            onIntent = {},
            onPasswordLogin = { _, _ -> },
            onForgotPassword = {},
            onOtherMethods = {},
        )
    }
}

@Preview(showBackground = true, name = "PasswordLoginScreen - With Errors")
@Composable
private fun PasswordLoginScreenErrorPreview() {
    QkTheme {
        PasswordLoginScreen(
            state = PasswordLoginUiState(account = "", password = "123", submitAttempted = true),
            onIntent = {},
            onPasswordLogin = { _, _ -> },
            onForgotPassword = {},
            onOtherMethods = {},
        )
    }
}
