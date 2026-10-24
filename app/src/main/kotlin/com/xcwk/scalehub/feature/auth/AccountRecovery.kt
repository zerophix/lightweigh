package com.xcwk.scalehub.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * auth — 账号恢复五屏与状态机（T-073 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/21-account-recovery.html`：
 * - `v-choose` 找回方式选择：手机号 / 邮箱两个掩码入口 + 客服兜底
 * - `v-phone-verify` 手机验证：「验证码将发送至 +86 138****0000」+ 倒计时 + 安全提示
 * - `v-email-verify` 邮箱验证：验证链接说明 + 或输入邮件验证码 + 重新发送
 * - `v-set-password` 设置新密码：规则三条 +「新密码不能与旧密码相同」+ 其他设备失效提示
 * - `v-success` 重置成功：请用新密码重新登录 + 其他设备已失效 + 返回登录
 *
 * ## 验收（AC-J1-02）
 * - 五屏文案逐字对齐原型（常量为唯一真源，JVM 单测钉死）
 * - 状态机「找回方式 → 验证 → 设密 → 成功」与密码规则为纯函数
 * - 倒计时秒数外部注入；结束前重发不可用（[AccountRecoveryIntent.ResendCode] 被忽略）
 *
 * ## 边界（呈现式组件）
 * 本流程不实现真实验证与改密：验证码发送/校验、密码提交全部经回调交给既有 auth 流程。
 * 状态里只保留用户输入；密码明文只经 [AccountRecoveryFlow.onSubmitNewPassword] 出去，
 * 成功转移时清空（不留在呈现态里）。
 */

// ---- 静态文案（逐字取自原型 21-account-recovery.html） ----

// v-choose
const val TITLE_RECOVERY = "找回密码"
const val HEADLINE_CHOOSE_RECOVERY = "选择找回方式"
const val SUBLINE_CHOOSE_RECOVERY = "通过绑定的手机号或邮箱验证身份"
const val ENTRY_RECOVER_BY_PHONE = "通过手机号找回"
const val ENTRY_RECOVER_BY_EMAIL = "通过邮箱找回"
const val BANNER_CONTACT_SUPPORT = "两种方式都不可用？请联系客服协助找回。"
const val ACTION_CONTACT_SUPPORT = "联系客服"

// v-phone-verify
const val TITLE_PHONE_VERIFY = "手机验证"
const val HEADLINE_PHONE_VERIFY = "验证手机号"
const val PHONE_VERIFY_TEMPLATE = "验证码将发送至 +86 %s"
const val BANNER_CODE_SECURITY = "验证成功后才能设置新密码，请勿泄露验证码。"
const val ACTION_VERIFY_AND_CONTINUE = "验证并继续"

// v-email-verify
const val TITLE_EMAIL_VERIFY = "邮箱验证"
const val HEADLINE_EMAIL_VERIFY = "验证邮箱"
const val EMAIL_SENT_TEMPLATE = "验证链接已发送至 %s"
const val EMAIL_LINK_HINT = "请打开邮箱点击验证链接，\n验证成功后自动返回设置新密码。"
const val LABEL_EMAIL_CODE = "或输入邮件中的验证码"
const val BANNER_EMAIL_NOT_RECEIVED = "没收到邮件？请检查垃圾箱，或重新发送。"
const val ACTION_RESEND_EMAIL = "重新发送验证邮件"

// v-success
const val HEADLINE_RESET_SUCCESS = "密码已重置"
const val SUCCESS_LINE_RELOGIN = "请用新密码重新登录。"
const val SUCCESS_LINE_OTHER_SESSIONS = "其他设备的登录状态已失效。"
const val ACTION_BACK_TO_LOGIN = "返回登录"

/** 原型 21 单次使用的图标（QkIcons 只收全局复用图标，改 QkIcons 属跨任务改动，故留在本文件）。 */
private val IconPhone: ImageVector = Icons.Outlined.Smartphone
private val IconMail: ImageVector = Icons.Outlined.Email

// ---- 五态状态机 ----

/** 找回方式（决定验证码发往哪里，也决定验证页形态）。 */
enum class RecoveryChannel { Phone, Email }

/** 找回流程五态（与原型五个 view 一一对应）。 */
enum class RecoveryStep { Choose, PhoneVerify, EmailVerify, SetPassword, Success }

/**
 * 呈现式 UI 状态。
 *
 * [codeSecondsLeft] 由外部计时逻辑注入；[oldPassword] 只参与「不与旧密码相同」比较。
 */
data class AccountRecoveryUiState(
    val step: RecoveryStep = RecoveryStep.Choose,
    val channel: RecoveryChannel? = null,
    val phoneMasked: String = DEFAULT_PHONE_MASKED,
    val emailMasked: String = DEFAULT_EMAIL_MASKED,
    val code: String = "",
    val codeSecondsLeft: Int = 0,
    val codeError: Boolean = false,
    val newPassword: String = "",
    val confirmPassword: String = "",
    val oldPassword: String? = null,
) {
    val validation: PasswordValidation
        get() = validateNewPassword(newPassword, confirmPassword, oldPassword)

    /** 当前渠道的验证码是否已填满（决定「验证并继续」可用性）。 */
    val canVerify: Boolean get() = isVerificationCodeComplete(code)

    /** 设密页「确认重置」可用性（含验证码渠道已通过的语义，由步骤保证）。 */
    val canSubmitPassword: Boolean get() = validation.isValid
}

/** 流程内的呈现态转移。外部副作用（发码、校验、改密）走回调，不进 reduce。 */
sealed interface AccountRecoveryIntent {
    /** 选择手机找回（同时意味着发码）。 */
    data object ChoosePhone : AccountRecoveryIntent

    /** 选择邮箱找回（同时意味着发验证邮件）。 */
    data object ChooseEmail : AccountRecoveryIntent

    /** 验证码输入变化（只保留数字）。 */
    data class CodeChanged(val code: String) : AccountRecoveryIntent

    /** 手机验证页重发验证码：倒计时未结束时被忽略。 */
    data object ResendCode : AccountRecoveryIntent

    /** 邮箱验证页重新发送验证邮件（原型无倒计时，始终可用）。 */
    data object ResendEmail : AccountRecoveryIntent

    /** 提交当前验证页的验证码。 */
    data object Verify : AccountRecoveryIntent

    data class NewPasswordChanged(val password: String) : AccountRecoveryIntent

    data class ConfirmPasswordChanged(val password: String) : AccountRecoveryIntent

    /** 提交新密码。 */
    data object SubmitPassword : AccountRecoveryIntent

    /** 验证页 / 设密页返回找回方式选择。 */
    data object BackToChoose : AccountRecoveryIntent

    /** 成功页返回登录（导航由调用方执行）。 */
    data object BackToLogin : AccountRecoveryIntent
}

/**
 * 纯呈现态转移 —— JVM 单测钉死的部分。
 *
 * 口径：
 * - 选渠道即进入对应验证页，并清空上一轮验证码/密码（换渠道不继承旧输入）
 * - [AccountRecoveryIntent.Verify] 只有 6 位数字验证码才推进到设密页；否则置
 *   [AccountRecoveryUiState.codeError]，停在原页
 * - [AccountRecoveryIntent.ResendCode] 在倒计时结束前是**空操作**（不重发、不清码）
 * - [AccountRecoveryIntent.SubmitPassword] 只有四条密码规则全过才到成功页；
 *   成功转移时清空两个密码字段（不把明文留在呈现态）
 */
fun accountRecoveryReduce(
    state: AccountRecoveryUiState,
    intent: AccountRecoveryIntent,
): AccountRecoveryUiState = when (intent) {
    AccountRecoveryIntent.ChoosePhone -> state.copy(
        step = RecoveryStep.PhoneVerify,
        channel = RecoveryChannel.Phone,
        code = "",
        codeError = false,
        newPassword = "",
        confirmPassword = "",
    )

    AccountRecoveryIntent.ChooseEmail -> state.copy(
        step = RecoveryStep.EmailVerify,
        channel = RecoveryChannel.Email,
        code = "",
        codeError = false,
        newPassword = "",
        confirmPassword = "",
    )

    is AccountRecoveryIntent.CodeChanged -> state.copy(
        code = intent.code.filter { it.isDigit() },
        codeError = false,
    )

    AccountRecoveryIntent.ResendCode ->
        if (canResend(state.codeSecondsLeft)) state.copy(code = "", codeError = false) else state

    AccountRecoveryIntent.ResendEmail -> state.copy(codeError = false)

    AccountRecoveryIntent.Verify ->
        if (state.canVerify) {
            state.copy(step = RecoveryStep.SetPassword, codeError = false)
        } else {
            state.copy(codeError = true)
        }

    is AccountRecoveryIntent.NewPasswordChanged -> state.copy(newPassword = intent.password)

    is AccountRecoveryIntent.ConfirmPasswordChanged -> state.copy(confirmPassword = intent.password)

    AccountRecoveryIntent.SubmitPassword ->
        if (state.canSubmitPassword) {
            state.copy(
                step = RecoveryStep.Success,
                newPassword = "",
                confirmPassword = "",
                code = "",
            )
        } else {
            state
        }

    AccountRecoveryIntent.BackToChoose -> state.copy(
        step = RecoveryStep.Choose,
        channel = null,
        code = "",
        codeError = false,
        newPassword = "",
        confirmPassword = "",
    )

    AccountRecoveryIntent.BackToLogin -> state.copy(
        step = RecoveryStep.Choose,
        channel = null,
        code = "",
        codeError = false,
        newPassword = "",
        confirmPassword = "",
    )
}

// ---- 文案拼装（掩码账号由外部注入） ----

/** v-phone-verify 副标题：「验证码将发送至 +86 138****0000」。 */
fun phoneVerifySubtitle(masked: String): String = PHONE_VERIFY_TEMPLATE.format(masked)

/** v-email-verify 副标题：「验证链接已发送至 l***@example.com」。 */
fun emailSentSubtitle(masked: String): String = EMAIL_SENT_TEMPLATE.format(masked)

// ---- 五态路由 ----

/**
 * 账号恢复五态入口。
 *
 * 外部交接点（本组件不发请求、不改密码）：
 * - [onSendCode] 选中手机/邮箱找回（发码或发验证邮件）
 * - [onVerifyPhone] / [onVerifyEmail] 提交验证码
 * - [onSubmitNewPassword] 提交新密码（明文只经此回调）
 * - [onResend] 重发验证码或验证邮件
 * - [onContactSupport] 两种方式都不可用时联系客服
 * - [onBackToLogin] 成功页返回登录
 */
@Composable
fun AccountRecoveryFlow(
    state: AccountRecoveryUiState,
    onIntent: (AccountRecoveryIntent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSendCode: (RecoveryChannel) -> Unit = {},
    onVerifyPhone: (String) -> Unit = {},
    onVerifyEmail: (String) -> Unit = {},
    onSubmitNewPassword: (String) -> Unit = {},
    onResend: () -> Unit = {},
    onContactSupport: () -> Unit = {},
    onBackToLogin: () -> Unit = {},
) {
    when (state.step) {
        RecoveryStep.Choose -> RecoveryChooseView(
            state = state,
            onSelectPhone = {
                onSendCode(RecoveryChannel.Phone)
                onIntent(AccountRecoveryIntent.ChoosePhone)
            },
            onSelectEmail = {
                onSendCode(RecoveryChannel.Email)
                onIntent(AccountRecoveryIntent.ChooseEmail)
            },
            onContactSupport = onContactSupport,
            onBack = onBack,
            modifier = modifier,
        )

        RecoveryStep.PhoneVerify -> RecoveryPhoneVerifyView(
            state = state,
            onCodeChanged = { onIntent(AccountRecoveryIntent.CodeChanged(it)) },
            onResend = {
                onResend()
                onIntent(AccountRecoveryIntent.ResendCode)
            },
            onVerify = {
                onVerifyPhone(state.code)
                onIntent(AccountRecoveryIntent.Verify)
            },
            onBack = { onIntent(AccountRecoveryIntent.BackToChoose) },
            modifier = modifier,
        )

        RecoveryStep.EmailVerify -> RecoveryEmailVerifyView(
            state = state,
            onCodeChanged = { onIntent(AccountRecoveryIntent.CodeChanged(it)) },
            onResend = {
                onResend()
                onIntent(AccountRecoveryIntent.ResendEmail)
            },
            onVerify = {
                onVerifyEmail(state.code)
                onIntent(AccountRecoveryIntent.Verify)
            },
            onBack = { onIntent(AccountRecoveryIntent.BackToChoose) },
            modifier = modifier,
        )

        RecoveryStep.SetPassword -> RecoverySetPasswordView(
            state = state,
            onNewPasswordChanged = { onIntent(AccountRecoveryIntent.NewPasswordChanged(it)) },
            onConfirmPasswordChanged = { onIntent(AccountRecoveryIntent.ConfirmPasswordChanged(it)) },
            onSubmit = {
                onSubmitNewPassword(state.newPassword)
                onIntent(AccountRecoveryIntent.SubmitPassword)
            },
            onBack = { onIntent(AccountRecoveryIntent.BackToChoose) },
            modifier = modifier,
        )

        RecoveryStep.Success -> RecoverySuccessView(
            onBackToLogin = {
                onBackToLogin()
                onIntent(AccountRecoveryIntent.BackToLogin)
            },
            modifier = modifier,
        )
    }
}

// ---- 五屏 ----

/** v-choose 找回方式选择。 */
@Composable
private fun RecoveryChooseView(
    state: AccountRecoveryUiState,
    onSelectPhone: () -> Unit,
    onSelectEmail: () -> Unit,
    onContactSupport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Page),
    ) {
        AuthNavBar(TITLE_RECOVERY, onBack)
        AuthHero(
            headline = HEADLINE_CHOOSE_RECOVERY,
            subline = SUBLINE_CHOOSE_RECOVERY,
            modifier = Modifier.padding(top = QkSpacing.Xl),
        )

        QkCard(modifier = Modifier.padding(top = QkSpacing.Xl)) {
            QkListRow(
                title = ENTRY_RECOVER_BY_PHONE,
                subtitle = state.phoneMasked,
                icon = IconPhone,
                onClick = onSelectPhone,
                trailing = { QkIcon(QkIcons.ChevronRight, size = QkSpacing.Lg) },
            )
            QkListRow(
                title = ENTRY_RECOVER_BY_EMAIL,
                subtitle = state.emailMasked,
                icon = IconMail,
                onClick = onSelectEmail,
                trailing = { QkIcon(QkIcons.ChevronRight, size = QkSpacing.Lg) },
                showDivider = false,
            )
        }

        AuthBanner(
            text = BANNER_CONTACT_SUPPORT,
            modifier = Modifier.padding(top = QkSpacing.CardGap),
        )

        Spacer(modifier = Modifier.weight(1f))
        QkButton(
            text = ACTION_CONTACT_SUPPORT,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Secondary,
            onClick = onContactSupport,
        )
    }
}

/** v-phone-verify 手机验证。 */
@Composable
private fun RecoveryPhoneVerifyView(
    state: AccountRecoveryUiState,
    onCodeChanged: (String) -> Unit,
    onResend: () -> Unit,
    onVerify: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Page),
    ) {
        AuthNavBar(TITLE_PHONE_VERIFY, onBack)
        AuthHero(
            headline = HEADLINE_PHONE_VERIFY,
            subline = phoneVerifySubtitle(state.phoneMasked),
            modifier = Modifier.padding(top = QkSpacing.Xl),
        )

        AuthCodeField(
            value = state.code,
            onValueChange = onCodeChanged,
            secondsLeft = state.codeSecondsLeft,
            onResend = onResend,
            modifier = Modifier.padding(top = QkSpacing.Xl),
        )
        AuthBanner(
            text = BANNER_CODE_SECURITY,
            modifier = Modifier.padding(top = QkSpacing.CardGap),
            warn = true,
        )

        Spacer(modifier = Modifier.weight(1f))
        QkButton(
            text = ACTION_VERIFY_AND_CONTINUE,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canVerify,
            onClick = onVerify,
        )
    }
}

/** v-email-verify 邮箱验证。 */
@Composable
private fun RecoveryEmailVerifyView(
    state: AccountRecoveryUiState,
    onCodeChanged: (String) -> Unit,
    onResend: () -> Unit,
    onVerify: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Page),
    ) {
        AuthNavBar(TITLE_EMAIL_VERIFY, onBack)
        AuthHero(
            headline = HEADLINE_EMAIL_VERIFY,
            subline = emailSentSubtitle(state.emailMasked),
            modifier = Modifier.padding(top = QkSpacing.Xl),
        )

        // 链接验证说明卡（原型 .card.soft：薄荷底 + 邮件图标 + 两行说明）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Xl)
                .clip(RoundedCornerShape(QkRadius.Card))
                .background(QkColor.Mint)
                .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkIconBubble(icon = IconMail, tone = QkGlyphTone.Green)
            Text(
                EMAIL_LINK_HINT,
                modifier = Modifier.padding(start = QkSpacing.Md).weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
            )
        }

        AuthTextField(
            value = state.code,
            onValueChange = onCodeChanged,
            modifier = Modifier.padding(top = QkSpacing.Lg),
            label = LABEL_EMAIL_CODE,
            placeholder = PLACEHOLDER_VERIFICATION_CODE,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        AuthBanner(
            text = BANNER_EMAIL_NOT_RECEIVED,
            modifier = Modifier.padding(top = QkSpacing.CardGap),
        )

        Spacer(modifier = Modifier.weight(1f))
        QkButton(
            text = ACTION_VERIFY_AND_CONTINUE,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canVerify,
            onClick = onVerify,
        )
        QkButton(
            text = ACTION_RESEND_EMAIL,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            style = QkButtonStyle.Ghost,
            onClick = onResend,
        )
    }
}

/** v-set-password 设置新密码。 */
@Composable
private fun RecoverySetPasswordView(
    state: AccountRecoveryUiState,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Page),
    ) {
        AuthNavBar(HEADLINE_SET_PASSWORD, onBack)
        AuthHero(
            headline = HEADLINE_SET_PASSWORD,
            subline = HINT_NEW_PASSWORD_DIFFERS,
            modifier = Modifier.padding(top = QkSpacing.Xl),
        )

        AuthPasswordSection(
            newPassword = state.newPassword,
            confirmPassword = state.confirmPassword,
            validation = state.validation,
            onNewPasswordChanged = onNewPasswordChanged,
            onConfirmPasswordChanged = onConfirmPasswordChanged,
            modifier = Modifier.padding(top = QkSpacing.Xl),
        )
        AuthBanner(
            text = HINT_OTHER_SESSIONS_INVALID,
            modifier = Modifier.padding(top = QkSpacing.CardGap),
            warn = true,
        )

        Spacer(modifier = Modifier.weight(1f))
        QkButton(
            text = ACTION_CONFIRM_RESET,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmitPassword,
            onClick = onSubmit,
        )
    }
}

/** v-success 重置成功。 */
@Composable
private fun RecoverySuccessView(
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(QkSpacing.Xl * 3)
                .clip(CircleShape)
                .background(QkColor.Mint),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(QkIcons.Habit, tint = QkColor.Primary, size = QkSpacing.Xl * 2)
        }
        Text(
            HEADLINE_RESET_SUCCESS,
            modifier = Modifier.padding(top = QkSpacing.Xl),
            style = MaterialTheme.typography.displaySmall,
            color = QkColor.Fg,
        )
        Text(
            "$SUCCESS_LINE_RELOGIN\n$SUCCESS_LINE_OTHER_SESSIONS",
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Muted,
            textAlign = TextAlign.Center,
        )
        QkButton(
            text = ACTION_BACK_TO_LOGIN,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            onClick = onBackToLogin,
        )
    }
}
