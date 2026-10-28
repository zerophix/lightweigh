package com.xcwk.scalehub.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.feature.guide.GuideScreen
import com.xcwk.scalehub.feature.guide.PermissionFlow
import com.xcwk.scalehub.feature.guide.PermissionFlowIntent
import com.xcwk.scalehub.feature.guide.PermissionFlowUiState
import com.xcwk.scalehub.feature.guide.PermissionResult
import com.xcwk.scalehub.feature.guide.ProfileSetupFlow
import com.xcwk.scalehub.feature.guide.ProfileSetupUiState
import com.xcwk.scalehub.feature.guide.permissionFlowReduce
import com.xcwk.scalehub.feature.guide.reduce
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * auth + guide 的接入装配点（本轮只解决「可达性」，不接数据层）。
 *
 * ## 为什么单独一层
 * 已交付的 `EmailLogin` / `GuestMode` / `PasswordReset` / `AccountRecovery` /
 * `PermissionFlow` / `ProfileSetup` 都是**呈现式**组件（收 state + 回调），各自没有
 * 「下一步去哪」的知识。本文件把它们的回调接成一条链，宿主（[MainShell] /
 * [com.xcwk.scalehub.ui.shell.OverlayHost]）只挂一个入口：
 *
 * ```
 * 登录方式入口 ─┬─ 邮箱登录 ──────────┐
 *               ├─ 手机号登录 → 验证码 ─┤
 *               ├─ 密码登录 ───────────┤
 *               ├─ 访客体验 ──────────┼─ 权限说明 → 资料完善(1/2 → 2/2 → 完成)
 *               ├─ 忘记密码（验证码）─┤
 *               └─ 找回账号（五态）───┘
 * ```
 *
 * ## 边界
 * - 流程推进状态用 `remember` 模拟；各屏的输入态也留在这里，不进 ViewModel。
 * - 登录 / 发码 / 改密 / 存资料仍是空回调：真实动作由后续接入 ViewModel 的波次补齐。
 * - 权限说明页的系统授权结果在本轮固定回传「已允许」（没有真实 Permission API 调用），
 *   拒绝分支（去设置 / 暂不连接）等真实授权接入后才可达。
 */

// ---- 流程状态机（纯函数，JVM 单测钉死推进表） ----

/** 接入流程的呈现步（与屏幕一一对应）。 */
enum class AuthFlowStep {
    /** 登录方式入口（宿主从登录页「其他方式登录」进入）。 */
    Entry,
    EmailLogin,
    PhoneLogin,

    /** 手机号登录的验证码步（原型 v-verify）。 */
    PhoneLoginVerify,
    PasswordLogin,
    Guest,
    PasswordReset,
    Recovery,
    Permission,
    Profile,
}

/** 流程事件：只表达「去哪」，不携带数据（数据留在各屏自己的 state 里）。 */
sealed interface AuthFlowEvent {
    data object OpenEmailLogin : AuthFlowEvent
    data object OpenPhoneLogin : AuthFlowEvent
    data object OpenPasswordLogin : AuthFlowEvent
    data object OpenGuest : AuthFlowEvent
    data object OpenPasswordReset : AuthFlowEvent
    data object OpenRecovery : AuthFlowEvent

    /** 手机号登录页「获取验证码」成功：进入验证码步。 */
    data object CodeSent : AuthFlowEvent

    /** 登录 / 进访客 / 改密成功：统一进入权限说明。 */
    data object Authenticated : AuthFlowEvent

    /** 权限说明页走完（连接设备或暂不连接）。 */
    data object PermissionPassed : AuthFlowEvent

    /** 返回登录方式入口（各屏的返回键）。 */
    data object BackToEntry : AuthFlowEvent
}

/**
 * 纯推进函数。
 *
 * 口径：
 * - 各入口事件在任何步骤都切到对应屏（等价于顶层菜单跳转）
 * - [AuthFlowEvent.Authenticated] 只在凭据类步骤（邮箱/手机号/验证码/密码/访客/重置/找回）有效，
 *   在入口或已过门后重复上报不推进——避免双重回调把用户弹回中间态
 * - [AuthFlowEvent.CodeSent] 只在手机号登录页有效，推进到验证码步
 * - [AuthFlowEvent.PermissionPassed] 只在权限说明页有效
 * - [AuthFlowEvent.BackToEntry] 恒回入口（完成页的「回到登录主页」也走它）
 */
fun authFlowStepReduce(step: AuthFlowStep, event: AuthFlowEvent): AuthFlowStep = when (event) {
    AuthFlowEvent.OpenEmailLogin -> AuthFlowStep.EmailLogin
    AuthFlowEvent.OpenPhoneLogin -> AuthFlowStep.PhoneLogin
    AuthFlowEvent.OpenPasswordLogin -> AuthFlowStep.PasswordLogin
    AuthFlowEvent.OpenGuest -> AuthFlowStep.Guest
    AuthFlowEvent.OpenPasswordReset -> AuthFlowStep.PasswordReset
    AuthFlowEvent.OpenRecovery -> AuthFlowStep.Recovery
    AuthFlowEvent.CodeSent ->
        if (step == AuthFlowStep.PhoneLogin) AuthFlowStep.PhoneLoginVerify else step
    AuthFlowEvent.Authenticated ->
        if (step.isCredentialStep()) AuthFlowStep.Permission else step
    AuthFlowEvent.PermissionPassed ->
        if (step == AuthFlowStep.Permission) AuthFlowStep.Profile else step
    AuthFlowEvent.BackToEntry -> AuthFlowStep.Entry
}

private fun AuthFlowStep.isCredentialStep(): Boolean = when (this) {
    AuthFlowStep.EmailLogin,
    AuthFlowStep.PhoneLogin,
    AuthFlowStep.PhoneLoginVerify,
    AuthFlowStep.PasswordLogin,
    AuthFlowStep.Guest,
    AuthFlowStep.PasswordReset,
    AuthFlowStep.Recovery,
    -> true
    AuthFlowStep.Entry, AuthFlowStep.Permission, AuthFlowStep.Profile -> false
}

// ---- 文案 ----

const val AUTH_FLOW_ENTRY_TITLE = "其他方式登录"
private const val AUTH_FLOW_EMAIL_TITLE = "邮箱登录"
private const val AUTH_FLOW_EMAIL_HINT = "用邮箱和密码登录"
private const val AUTH_FLOW_PHONE_TITLE = "手机号登录"
private const val AUTH_FLOW_PHONE_HINT = "用手机号和短信验证码登录"
private const val AUTH_FLOW_PASSWORD_TITLE = "密码登录"
private const val AUTH_FLOW_PASSWORD_HINT = "用手机号或邮箱和密码登录"
private const val AUTH_FLOW_GUEST_TITLE = "访客体验"
private const val AUTH_FLOW_GUEST_HINT = "数据只存本机，换设备会丢失"
private const val AUTH_FLOW_RESET_TITLE = "忘记密码"
private const val AUTH_FLOW_RESET_HINT = "用短信验证码重置密码"
private const val AUTH_FLOW_RECOVERY_TITLE = "找回账号"
private const val AUTH_FLOW_RECOVERY_HINT = "手机或邮箱验证后设置新密码"
private const val AUTH_FLOW_PLACEHOLDER_NOTE =
    "接入占位：登录、发送验证码与保存资料尚未接数据层。"

// ---- 装配 ----

/**
 * 登录方式入口 + 四条支线（邮箱 / 访客 / 忘记密码 / 找回账号）的宿主。
 *
 * @param onExit 回到登录页（各屏返回键、完成页「回到登录主页」都走这里）
 */
@Composable
fun AuthFlowRoute(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableStateOf(AuthFlowStep.Entry) }

    fun go(event: AuthFlowEvent) {
        step = authFlowStepReduce(step, event)
    }

    // 系统返回：任何非入口步骤都退回入口，不直接退出流程（避免误触丢输入）
    BackHandler(enabled = step != AuthFlowStep.Entry) { go(AuthFlowEvent.BackToEntry) }

    when (step) {
        AuthFlowStep.Entry -> AuthFlowEntryView(
            onEmailLogin = { go(AuthFlowEvent.OpenEmailLogin) },
            onPhoneLogin = { go(AuthFlowEvent.OpenPhoneLogin) },
            onPasswordLogin = { go(AuthFlowEvent.OpenPasswordLogin) },
            onGuest = { go(AuthFlowEvent.OpenGuest) },
            onPasswordReset = { go(AuthFlowEvent.OpenPasswordReset) },
            onRecovery = { go(AuthFlowEvent.OpenRecovery) },
            onBack = onExit,
            modifier = modifier,
        )

        AuthFlowStep.PhoneLogin -> {
            var phone by remember { mutableStateOf(PhoneLoginUiState()) }
            PhoneLoginScreen(
                state = phone,
                onIntent = { phone = phoneLoginReduce(phone, it) },
                // 发码仍是空回调：倒计时由屏幕自己跑，成功后推进到验证码步
                onGetCode = { go(AuthFlowEvent.CodeSent) },
                onPhoneLogin = { _, _ -> go(AuthFlowEvent.Authenticated) },
                onOtherMethods = { go(AuthFlowEvent.BackToEntry) },
                modifier = modifier,
            )
        }

        AuthFlowStep.PhoneLoginVerify -> {
            var verify by remember { mutableStateOf(VerifyCodeUiState()) }
            VerifyCodeScreen(
                state = verify,
                onIntent = { verify = verifyCodeReduce(verify, it) },
                onVerifyLogin = { _ -> go(AuthFlowEvent.Authenticated) },
                onResend = { go(AuthFlowEvent.CodeSent) },
                onCantReceive = { go(AuthFlowEvent.OpenRecovery) },
                modifier = modifier,
                onBack = { go(AuthFlowEvent.OpenPhoneLogin) },
            )
        }

        AuthFlowStep.PasswordLogin -> {
            var pwd by remember { mutableStateOf(PasswordLoginUiState()) }
            PasswordLoginScreen(
                state = pwd,
                onIntent = { pwd = passwordLoginReduce(pwd, it) },
                onPasswordLogin = { _, _ -> go(AuthFlowEvent.Authenticated) },
                onForgotPassword = { go(AuthFlowEvent.OpenPasswordReset) },
                onOtherMethods = { go(AuthFlowEvent.BackToEntry) },
                modifier = modifier,
            )
        }

        AuthFlowStep.EmailLogin -> {
            var form by remember { mutableStateOf(EmailLoginUiState()) }
            EmailLoginScreen(
                state = form,
                onIntent = { form = emailLoginReduce(form, it) },
                // 登录动作仍是空回调：表单校验通过即视为「已登录」，进入权限说明
                onEmailLogin = { _, _ -> go(AuthFlowEvent.Authenticated) },
                onOtherMethods = { go(AuthFlowEvent.BackToEntry) },
                modifier = modifier,
            )
        }

        AuthFlowStep.Guest -> {
            var guest by remember { mutableStateOf(GuestUiState()) }
            GuestModeFlow(
                state = guest,
                onIntent = { guest = guestModeReduce(guest, it) },
                onEnterGuest = { go(AuthFlowEvent.Authenticated) },
                onBackToLogin = { go(AuthFlowEvent.BackToEntry) },
                onLoginOrRegister = { go(AuthFlowEvent.BackToEntry) },
                modifier = modifier,
            )
        }

        AuthFlowStep.PasswordReset -> {
            var reset by remember { mutableStateOf(PasswordResetUiState()) }
            PasswordResetView(
                state = reset,
                onCodeChanged = { reset = reset.copy(code = it) },
                onNewPasswordChanged = { reset = reset.copy(newPassword = it) },
                onConfirmPasswordChanged = { reset = reset.copy(confirmPassword = it) },
                // 改密请求仍是空回调：规则全过点「确认重置」即视为成功
                onSubmit = { go(AuthFlowEvent.Authenticated) },
                modifier = modifier,
                onBack = { go(AuthFlowEvent.BackToEntry) },
            )
        }

        AuthFlowStep.Recovery -> {
            var recovery by remember { mutableStateOf(AccountRecoveryUiState()) }
            AccountRecoveryFlow(
                state = recovery,
                onIntent = { recovery = accountRecoveryReduce(recovery, it) },
                modifier = modifier,
                onBack = { go(AuthFlowEvent.BackToEntry) },
                onBackToLogin = { go(AuthFlowEvent.BackToEntry) },
            )
        }

        AuthFlowStep.Permission -> {
            var permission by remember { mutableStateOf(PermissionFlowUiState()) }
            PermissionFlow(
                state = permission,
                onIntent = { permission = permissionFlowReduce(permission, it) },
                modifier = modifier,
                // 本轮没有真实 Permission API：说明页「继续」后按「已允许」回传
                onPermissionRequested = { kind ->
                    permission = permissionFlowReduce(
                        permission,
                        PermissionFlowIntent.Resulted(kind, PermissionResult.Granted),
                    )
                },
                onConnectDevice = { go(AuthFlowEvent.PermissionPassed) },
                onSkipConnection = { go(AuthFlowEvent.PermissionPassed) },
            )
        }

        AuthFlowStep.Profile -> {
            var profile by remember { mutableStateOf(ProfileSetupUiState()) }
            ProfileSetupFlow(
                state = profile,
                onIntent = { profile = profile.reduce(it) },
                modifier = modifier,
                // 资料落库仍是空回调（ProfileSetupFlow 的默认实现）
                onRestart = { go(AuthFlowEvent.BackToEntry) },
                onBackToLogin = onExit,
                // 跳过 / 完成都离开登录流程进入首页，避免卡在资料页
                onSkipProfile = onExit,
                onFinished = onExit,
            )
        }
    }
}

/** 登录方式入口：四条支线各一行（原型 v-login 的登录方式抽屉）。 */
@Composable
private fun AuthFlowEntryView(
    onEmailLogin: () -> Unit,
    onPhoneLogin: () -> Unit,
    onPasswordLogin: () -> Unit,
    onGuest: () -> Unit,
    onPasswordReset: () -> Unit,
    onRecovery: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(
                title = AUTH_FLOW_ENTRY_TITLE,
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
        }
        QkCard {
            QkListRow(title = AUTH_FLOW_PHONE_TITLE, subtitle = AUTH_FLOW_PHONE_HINT, onClick = onPhoneLogin)
            QkListRow(title = AUTH_FLOW_PASSWORD_TITLE, subtitle = AUTH_FLOW_PASSWORD_HINT, onClick = onPasswordLogin)
            QkListRow(title = AUTH_FLOW_EMAIL_TITLE, subtitle = AUTH_FLOW_EMAIL_HINT, onClick = onEmailLogin)
            QkListRow(title = AUTH_FLOW_GUEST_TITLE, subtitle = AUTH_FLOW_GUEST_HINT, onClick = onGuest)
            QkListRow(title = AUTH_FLOW_RESET_TITLE, subtitle = AUTH_FLOW_RESET_HINT, onClick = onPasswordReset)
            QkListRow(
                title = AUTH_FLOW_RECOVERY_TITLE,
                subtitle = AUTH_FLOW_RECOVERY_HINT,
                showDivider = false,
                onClick = onRecovery,
            )
        }
        Text(
            text = AUTH_FLOW_PLACEHOLDER_NOTE,
            modifier = Modifier.padding(top = QkSpacing.Lg),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
        )
    }
}

/**
 * guide 屏的接入装配点：引导三步走完后进入资料完善（1/2 → 2/2 → 完成）。
 *
 * 宿主（[com.xcwk.scalehub.ui.shell.OverlayHost] 的 `NavOverlay.Guide`）只挂这一个入口，
 * [GuideScreen] 与 [ProfileSetupFlow] 本身不改：完成 / 跳过都进资料完善，
 * 完成页的「重新走一遍流程」回引导首屏，「回到登录主页」交给宿主收尾。
 *
 * @param onDone 引导 + 资料完善整段落幕（宿主出栈）
 */
@Composable
fun GuideFlowRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var profileStage by rememberSaveable { mutableStateOf(false) }
    var profile by remember { mutableStateOf(ProfileSetupUiState()) }

    if (!profileStage) {
        GuideScreen(
            onDone = { profileStage = true },
            onSkip = { profileStage = true },
        )
    } else {
        ProfileSetupFlow(
            state = profile,
            onIntent = { profile = profile.reduce(it) },
            modifier = modifier,
            // 资料落库仍是空回调（ProfileSetupFlow 的默认实现）
            onRestart = {
                profile = ProfileSetupUiState()
                profileStage = false
            },
            onBackToLogin = onDone,
            onSkipProfile = onDone,
            onFinished = onDone,
        )
    }
}
