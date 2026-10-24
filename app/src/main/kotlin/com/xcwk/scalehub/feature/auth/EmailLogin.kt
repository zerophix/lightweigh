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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkStroke

/**
 * email-login — 邮箱登录与微信登录（T-072 · 原型01 v-email / v-wechat）。
 *
 * ## 呈现式约束
 * 本文件只负责「长什么样 + 表单可判定逻辑」：
 * - 登录动作经 [EmailLoginScreen] 的 `onEmailLogin(email, password)` 回调交给既有 auth 流程；
 * - 微信授权经 [WechatLoginScreen] 的 `onWechatAuthorize()` 回调，本文件不接触任何 SDK；
 * - 表单校验与登录方式状态机是纯函数（[validateEmailLogin] / [emailLoginReduce] /
 *   [wechatLoginReduce]），由 JVM 单测钉死。
 *
 * ## 文案
 * 标题、提示、按钮全部逐字取自原型 `01-login.html`，集中在 [EmailLoginText] /
 * [WechatLoginText]，禁止在 Composable 里散落字面量。
 */

// ---- 邮箱表单 ----

/** 邮箱登录表单的字段级错误。 */
enum class EmailLoginError {
    EmailEmpty,
    EmailInvalid,
    PasswordEmpty,
    PasswordTooShort,
}

/** 字段错误对应的提示文案（原型未给错误态，这里只补最小必要提示）。 */
val EmailLoginError.message: String
    get() = when (this) {
        EmailLoginError.EmailEmpty -> "请输入邮箱地址"
        EmailLoginError.EmailInvalid -> "邮箱格式不正确"
        EmailLoginError.PasswordEmpty -> "请输入密码"
        EmailLoginError.PasswordTooShort -> "密码至少 6 位"
    }

/** 邮箱/密码的校验结果：两个字段各自独立，便于逐字段展示错误。 */
data class EmailLoginValidation(
    val emailError: EmailLoginError? = null,
    val passwordError: EmailLoginError? = null,
) {
    /** 两个字段都通过才算可提交。 */
    val valid: Boolean get() = emailError == null && passwordError == null
}

/** 最短密码长度（口径集中在常量里，UI 与单测共用）。 */
const val EMAIL_PASSWORD_MIN_LENGTH = 6

private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")

/**
 * 纯函数校验：邮箱非空且格式合法、密码非空且不短于 [EMAIL_PASSWORD_MIN_LENGTH]。
 *
 * 刻意不做「密码强度」判断：登录页只负责挡住明显无效的输入，强度规则属于注册流程。
 */
fun validateEmailLogin(email: String, password: String): EmailLoginValidation {
    val trimmedEmail = email.trim()
    val emailError = when {
        trimmedEmail.isEmpty() -> EmailLoginError.EmailEmpty
        !EMAIL_PATTERN.matches(trimmedEmail) -> EmailLoginError.EmailInvalid
        else -> null
    }
    val passwordError = when {
        password.isEmpty() -> EmailLoginError.PasswordEmpty
        password.length < EMAIL_PASSWORD_MIN_LENGTH -> EmailLoginError.PasswordTooShort
        else -> null
    }
    return EmailLoginValidation(emailError = emailError, passwordError = passwordError)
}

/** 邮箱登录表单的呈现状态（输入 + 是否已尝试提交）。 */
data class EmailLoginUiState(
    val email: String = "",
    val password: String = "",
    val submitAttempted: Boolean = false,
) {
    /** 当前校验结果（每次重组按需计算，无隐藏可变状态）。 */
    val validation: EmailLoginValidation get() = validateEmailLogin(email, password)

    /** 是否展示字段错误：只有尝试提交过才提示，避免一进页面就飘红。 */
    val showErrors: Boolean get() = submitAttempted
}

/** 邮箱登录表单的意图。 */
sealed interface EmailLoginIntent {
    data class EmailChanged(val value: String) : EmailLoginIntent
    data class PasswordChanged(val value: String) : EmailLoginIntent

    /** 点击「登录」：只标记尝试提交，真实登录由回调完成。 */
    data object Submit : EmailLoginIntent
}

/** 纯呈现态转移：输入改动会清掉本次提交标记之外的副作用，不发请求。 */
fun emailLoginReduce(state: EmailLoginUiState, intent: EmailLoginIntent): EmailLoginUiState = when (intent) {
    is EmailLoginIntent.EmailChanged -> state.copy(email = intent.value, submitAttempted = false)
    is EmailLoginIntent.PasswordChanged -> state.copy(password = intent.value, submitAttempted = false)
    EmailLoginIntent.Submit -> state.copy(submitAttempted = true)
}

/** 提交判定结果：无效时带回字段错误，有效时带回规范化后的凭据。 */
sealed interface EmailLoginSubmission {
    data class Invalid(val validation: EmailLoginValidation) : EmailLoginSubmission
    data class Valid(val email: String, val password: String) : EmailLoginSubmission
}

/** 由当前表单状态得出提交判定（UI 与单测共用，避免两处各写一遍规则）。 */
fun emailLoginSubmissionOf(state: EmailLoginUiState): EmailLoginSubmission {
    val validation = state.validation
    return if (validation.valid) {
        EmailLoginSubmission.Valid(email = state.email.trim(), password = state.password)
    } else {
        EmailLoginSubmission.Invalid(validation)
    }
}

// ---- 微信授权 ----

/** 微信授权页的两项授权（昵称 / 头像），默认都勾选（原型两行均为选中态）。 */
data class WechatLoginUiState(
    val nicknameGranted: Boolean = true,
    val avatarGranted: Boolean = true,
)

/** 微信授权页的意图。 */
sealed interface WechatLoginIntent {
    data object ToggleNickname : WechatLoginIntent
    data object ToggleAvatar : WechatLoginIntent
}

/** 纯呈现态转移：只翻转勾选，不发起授权。 */
fun wechatLoginReduce(state: WechatLoginUiState, intent: WechatLoginIntent): WechatLoginUiState = when (intent) {
    WechatLoginIntent.ToggleNickname -> state.copy(nicknameGranted = !state.nicknameGranted)
    WechatLoginIntent.ToggleAvatar -> state.copy(avatarGranted = !state.avatarGranted)
}

// ---- 逐字文案（原型 01-login.html） ----

/** 邮箱登录页文案（v-email）。 */
object EmailLoginText {
    const val NavTitle = "邮箱登录"
    const val Headline = "用邮箱登录"
    const val Subline = "未注册的邮箱将自动创建账号"
    const val EmailLabel = "邮箱地址"
    const val EmailPlaceholder = "name@example.com"
    const val PasswordLabel = "密码"
    const val PasswordPlaceholder = "请输入密码"
    const val Submit = "登录"
    const val OtherMethods = "其他方式登录 ›"
}

/** 微信登录页文案（v-wechat）。 */
object WechatLoginText {
    const val NavTitle = "微信登录"
    const val Headline = "使用微信账号登录"
    const val Subline = "轻衡将获得你的微信昵称与头像"
    const val NicknameTitle = "微信昵称"
    const val NicknameDesc = "用于在轻衡中显示你的名字"
    const val AvatarTitle = "微信头像"
    const val AvatarDesc = "作为你在轻衡中的默认头像"
    const val Authorize = "微信授权登录"
    const val OtherMethods = "使用其他方式"
}

// ---- 页面 ----

/** 登录子页导航条：返回圆钮 + 居中标题（原型 .navbar）。 */
@Composable
internal fun AuthSubNavBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkBackButton(onClick = onBack)
        Text(
            title,
            modifier = Modifier.weight(1f).padding(end = QkSpacing.Md),
            style = MaterialTheme.typography.titleMedium,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        // 右侧留出与返回钮等宽的占位，保证标题视觉居中
        Spacer(modifier = Modifier.width(QkSpacing.Xl))
    }
}

/** 页面主标题 + 副标题（原型 .hero）。 */
@Composable
private fun AuthHero(headline: String, subline: String, centered: Boolean = false, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            headline,
            style = MaterialTheme.typography.headlineSmall,
            color = QkColor.Fg,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Text(
            subline,
            modifier = Modifier.padding(top = QkSpacing.Xs + QkSpacing.Xs),
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Muted,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
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

/**
 * 密码输入框：与 QkTextField 同一套视觉（标签在框外、11 圆角、1px 细边），
 * 额外加 [PasswordVisualTransformation]。QkTextField 不支持密文，故在此就地实现。
 */
@Composable
private fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(QkRadius.Field)
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = QkColor.FieldLabel,
            modifier = Modifier.padding(bottom = QkSpacing.Xs + QkSpacing.Xs),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = QkColor.Fg),
            cursorBrush = SolidColor(QkColor.Primary),
            visualTransformation = PasswordVisualTransformation(),
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
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/**
 * 邮箱登录页（原型 v-email）。
 *
 * @param onEmailLogin 点击「登录」且表单校验通过时触发，凭据交给既有 auth 流程
 * @param onOtherMethods 点击「其他方式登录 ›」回到登录方式选择
 */
@Composable
fun EmailLoginScreen(
    state: EmailLoginUiState,
    onIntent: (EmailLoginIntent) -> Unit,
    onEmailLogin: (email: String, password: String) -> Unit,
    onOtherMethods: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = onOtherMethods,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Lg),
    ) {
        AuthSubNavBar(title = EmailLoginText.NavTitle, onBack = onBack)
        Column(modifier = Modifier.padding(top = QkSpacing.Xl)) {
            AuthHero(headline = EmailLoginText.Headline, subline = EmailLoginText.Subline)
        }
        Column(
            modifier = Modifier.padding(top = QkSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Lg),
        ) {
            Column {
                QkTextField(
                    value = state.email,
                    onValueChange = { onIntent(EmailLoginIntent.EmailChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = EmailLoginText.EmailLabel,
                    placeholder = EmailLoginText.EmailPlaceholder,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                if (state.showErrors) {
                    state.validation.emailError?.let { FieldError(it.message) }
                }
            }
            Column {
                AuthPasswordField(
                    value = state.password,
                    onValueChange = { onIntent(EmailLoginIntent.PasswordChanged(it)) },
                    label = EmailLoginText.PasswordLabel,
                    placeholder = EmailLoginText.PasswordPlaceholder,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.showErrors) {
                    state.validation.passwordError?.let { FieldError(it.message) }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            QkButton(
                text = EmailLoginText.Submit,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onIntent(EmailLoginIntent.Submit)
                    val submission = emailLoginSubmissionOf(state)
                    if (submission is EmailLoginSubmission.Valid) {
                        onEmailLogin(submission.email, submission.password)
                    }
                },
            )
            Text(
                EmailLoginText.OtherMethods,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOtherMethods)
                    .padding(vertical = QkSpacing.Sm),
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 授权行右侧的选中勾（原型 .chk.on）。 */
@Composable
private fun WechatConsentRow(
    title: String,
    desc: String,
    checked: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    QkListRow(
        title = title,
        subtitle = desc,
        trailing = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(QkRadius.Field))
                    .background(if (checked) QkColor.Primary else QkColor.Bg)
                    .clickable(onClick = onClick)
                    .padding(horizontal = QkSpacing.Xs + QkSpacing.Xs, vertical = QkSpacing.Xs),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    QkIcon(Icons.Outlined.Check, tint = QkColor.Card, size = QkSpacing.Lg)
                }
            }
        },
        showDivider = showDivider,
        onClick = onClick,
    )
}

/**
 * 微信登录页（原型 v-wechat）。
 *
 * 授权动作只走 [onWechatAuthorize]：真实微信 SDK 授权、换取用户信息由既有 auth 流程处理。
 */
@Composable
fun WechatLoginScreen(
    state: WechatLoginUiState,
    onIntent: (WechatLoginIntent) -> Unit,
    onWechatAuthorize: () -> Unit,
    onOtherMethods: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = onOtherMethods,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Lg),
    ) {
        AuthSubNavBar(title = WechatLoginText.NavTitle, onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(QkRadius.Tile))
                    .background(QkColor.Mint)
                    .padding(QkSpacing.Lg),
                contentAlignment = Alignment.Center,
            ) {
                QkIcon(Icons.Outlined.AccountCircle, tint = QkColor.Primary, size = QkSpacing.Xl + QkSpacing.Md)
            }
            AuthHero(
                headline = WechatLoginText.Headline,
                subline = WechatLoginText.Subline,
                centered = true,
                modifier = Modifier.padding(top = QkSpacing.Xl),
            )
        }
        QkCard(modifier = Modifier.padding(top = QkSpacing.Xl), padding = QkSpacing.Sm) {
            WechatConsentRow(
                title = WechatLoginText.NicknameTitle,
                desc = WechatLoginText.NicknameDesc,
                checked = state.nicknameGranted,
                onClick = { onIntent(WechatLoginIntent.ToggleNickname) },
                showDivider = true,
            )
            WechatConsentRow(
                title = WechatLoginText.AvatarTitle,
                desc = WechatLoginText.AvatarDesc,
                checked = state.avatarGranted,
                onClick = { onIntent(WechatLoginIntent.ToggleAvatar) },
                showDivider = false,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            QkButton(
                text = WechatLoginText.Authorize,
                modifier = Modifier.fillMaxWidth(),
                onClick = onWechatAuthorize,
            )
            QkButton(
                text = WechatLoginText.OtherMethods,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Secondary,
                onClick = onOtherMethods,
            )
        }
    }
}
