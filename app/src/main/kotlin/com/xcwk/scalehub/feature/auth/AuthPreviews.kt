package com.xcwk.scalehub.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 认证与账号生命周期交付屏的 Compose 预览集合。
 *
 * 全部预览只传状态对象与空回调，不依赖 ViewModel。
 * `MineRoute` / `AuthFlowRoute` 以 ViewModel 或流程状态为入参，属接线层而非呈现层，不在预览范围内。
 */

/** 登录主屏 · 默认态。 */
@Preview(name = "登录主屏 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AuthScreenPreviewDefault() {
    QkTheme {
        AuthScreen(state = AuthUiState(), onIntent = {})
    }
}

/** 邮箱登录页 · 默认态。 */
@Preview(name = "邮箱登录 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun EmailLoginScreenPreviewDefault() {
    QkTheme {
        EmailLoginScreen(
            state = EmailLoginUiState(),
            onIntent = {},
            onEmailLogin = { _, _ -> },
            onOtherMethods = {},
        )
    }
}

/** 微信登录页 · 默认态。 */
@Preview(name = "微信登录 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun WechatLoginScreenPreviewDefault() {
    QkTheme {
        WechatLoginScreen(
            state = WechatLoginUiState(),
            onIntent = {},
            onWechatAuthorize = {},
            onOtherMethods = {},
        )
    }
}

/** 游客模式流程 · 默认态。 */
@Preview(name = "游客模式 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun GuestModeFlowPreviewDefault() {
    QkTheme {
        GuestModeFlow(
            state = GuestUiState(),
            onIntent = {},
            onEnterGuest = {},
            onBackToLogin = {},
            onLoginOrRegister = {},
        )
    }
}

/** 账号找回流程 · 默认态。 */
@Preview(name = "账号找回 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AccountRecoveryFlowPreviewDefault() {
    QkTheme {
        AccountRecoveryFlow(state = AccountRecoveryUiState(), onIntent = {})
    }
}

/** 最低版本拦截页。 */
@Preview(name = "账号边界 · 最低版本", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MinVersionScreenPreviewDefault() {
    QkTheme {
        MinVersionScreen(onViewUpgradeMethod = {}, onBack = {})
    }
}

/** 隐私锁拦截页。 */
@Preview(name = "账号边界 · 隐私锁", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PrivacyLockScreenPreviewDefault() {
    QkTheme {
        PrivacyLockScreen(onOpenPrivacy = {}, onBack = {})
    }
}

/** 验证码错误页（错误态）。 */
@Preview(name = "账号边界 · 验证码错误", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun CodeErrorScreenPreviewDefault() {
    QkTheme {
        CodeErrorScreen(
            state = CodeErrorUiState(),
            onCodeChange = {},
            onResendCode = {},
            onLogin = {},
            onNeedCodeHelp = {},
            onBack = {},
        )
    }
}

/** 健康信息告知页。 */
@Preview(name = "账号边界 · 健康信息告知", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HealthInfoScreenPreviewDefault() {
    QkTheme {
        HealthInfoScreen(onAcknowledge = {}, onBack = {})
    }
}

/** 账号生命周期总入口 · 强制更新态。 */
@Preview(name = "账号生命周期 · 强制更新", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AccountLifecycleScreenPreviewForceUpdate() {
    QkTheme {
        AccountLifecycleScreen(view = AccountBoundaryView.ForceUpdate)
    }
}

/** 账号生命周期总入口 · 多设备登录态。 */
@Preview(name = "账号生命周期 · 多设备登录", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AccountLifecycleScreenPreviewMultiDevice() {
    QkTheme {
        AccountLifecycleScreen(view = AccountBoundaryView.MultiDevice)
    }
}

/** 登录/注册流程路由 · 默认态。 */
@Preview(name = "认证流程路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AuthFlowRoutePreviewDefault() {
    QkTheme {
        AuthFlowRoute(onExit = {})
    }
}

/** 引导流程路由 · 默认态。 */
@Preview(name = "引导流程路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun GuideFlowRoutePreviewDefault() {
    QkTheme {
        GuideFlowRoute(onDone = {})
    }
}
