package com.xcwk.scalehub.feature.auth

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * auth — 账号/启动边界的接入装配点（T-074 交付屏 / 原型 14 接入波）。
 *
 * 三个边界页在这之前只有一个「事件 → 页面」分发函数（[accountBoundarySpec]），
 * 没有任何宿主把事件送进来，用户点不到。本文件补上三处真实触发点：
 *
 * ```
 * 登录门启动自检 ──── 系统版本过低 ── MinVersionScreen
 * 未同意隐私政策点登录 ─ 功能锁 ────── PrivacyLockScreen
 * 验证码登录失败 ───── 验证码错误页 ── CodeErrorScreen
 * 我的 → 健康数据说明 ─ 五问五答 ───── HealthInfoScreen
 * ```
 *
 * ## 边界（诚实标注）
 * - 启动自检只看本机 Android API 级：低版本设备在安装阶段就被 minSdk 拦下，
 *   因此本屏在受支持机型上不会出现，属防御性兜底；远端「最低 App 版本」配置
 *   接入后由同一函数返回。
 * - 验证码剩余次数按本机失败次数递减，服务端仍是最终裁决方；重发验证码即清零。
 */

/** 应用最低支持的 Android API 级（与 app/build.gradle.kts 的 minSdk 对齐，改动需同步）。 */
const val MIN_SUPPORTED_SDK = 26   // 与 app minSdk 对齐（Health Connect 要求 26）

/** 启动自检：系统低于最低支持版本时返回 [AccountBoundaryView.MinVersion]，否则放行。 */
fun startupBoundaryOf(sdkInt: Int, minSdk: Int = MIN_SUPPORTED_SDK): AccountBoundaryView? =
    if (sdkInt < minSdk) AccountBoundaryView.MinVersion else null

/**
 * 登录门前需要拦住的边界页（原型 14 的三个整页）。
 *
 * 优先级：启动自检 > 隐私锁 > 验证码错误。已进入应用的用户不再被验证码错误页拦住
 * （登录成功后残留的失败次数不应盖住后续界面）。
 */
fun authGateBoundaryOf(state: AuthUiState, sdkInt: Int): AccountBoundaryView? = when {
    startupBoundaryOf(sdkInt) != null -> AccountBoundaryView.MinVersion
    state.privacyBlocked -> AccountBoundaryView.PrivacyLock
    state.codeAttempts > 0 && !state.entered && state.loginMode == LoginMode.CODE ->
        AccountBoundaryView.CodeError

    else -> null
}

/** 验证码错误页的呈现状态：剩余次数 = 上限 - 本机已失败次数。 */
fun codeErrorStateOf(state: AuthUiState, attemptsUsed: Int = state.codeAttempts): CodeErrorUiState =
    CodeErrorUiState(
        phoneMasked = maskedPhone(state.phone),
        code = state.code,
        remainingAttempts = remainingCodeAttempts(attemptsUsed),
        resendSeconds = state.countdown,
    )

/**
 * 边界页宿主：只负责把 [AccountLifecycleScreen] 的十几个回调收敛到本波用得到的那几个。
 *
 * @param onDismiss 可关闭页的返回/关闭（强更不调用）
 */
@Composable
fun AuthBoundaryHost(
    view: AccountBoundaryView,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    codeError: CodeErrorUiState = CodeErrorUiState(),
    onCodeChange: (String) -> Unit = {},
    onResendCode: () -> Unit = {},
    onCodeLogin: () -> Unit = {},
    onNeedCodeHelp: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onViewUpgradeMethod: () -> Unit = {},
) {
    AccountLifecycleScreen(
        view = view,
        modifier = modifier,
        codeError = codeError,
        onDismiss = onDismiss,
        onOpenPrivacy = onOpenPrivacy,
        onViewUpgradeMethod = onViewUpgradeMethod,
        onCodeChange = onCodeChange,
        onResendCode = onResendCode,
        onCodeLogin = onCodeLogin,
        onNeedCodeHelp = onNeedCodeHelp,
        onAcknowledgeHealthInfo = onDismiss,
    )
}

/**
 * 登录门上的边界宿主：把 [AuthIntent] 翻译成边界页回调。
 *
 * 调用方先判 [authGateBoundaryOf] 是否命中，命中才渲染本函数。
 */
@Composable
fun AuthGateBoundaryHost(
    state: AuthUiState,
    onIntent: (AuthIntent) -> Unit,
    sdkInt: Int,
) {
    val view = authGateBoundaryOf(state, sdkInt) ?: return
    val context = LocalContext.current
    AuthBoundaryHost(
        view = view,
        onDismiss = { onIntent(AuthIntent.DismissBoundary) },
        codeError = codeErrorStateOf(state),
        onCodeChange = { onIntent(AuthIntent.CodeChanged(it)) },
        onResendCode = { onIntent(AuthIntent.SendCode) },
        onCodeLogin = { onIntent(AuthIntent.SignIn) },
        onNeedCodeHelp = {
            Toast.makeText(context, "请确认手机号可接收短信，或改用其他方式登录", Toast.LENGTH_SHORT).show()
            onIntent(AuthIntent.DismissBoundary)
        },
        onOpenPrivacy = { onIntent(AuthIntent.AgreedChanged(true)) },
        onViewUpgradeMethod = {
            Toast.makeText(context, "请在系统设置中检查系统更新", Toast.LENGTH_SHORT).show()
        },
    )
}
