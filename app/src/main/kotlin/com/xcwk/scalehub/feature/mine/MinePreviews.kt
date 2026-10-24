package com.xcwk.scalehub.feature.mine

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 「我的」模块交付屏的 Compose 预览集合。
 *
 * `MineRoute` 以 ViewModel 为唯一入参，属接线层，不在预览范围内。
 */

/** 我的主页 · 默认态。 */
@Preview(name = "我的 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MineScreenPreviewDefault() {
    QkTheme {
        MineScreen(state = MineUiState(), onIntent = {})
    }
}

/** 关于页 · 默认态。 */
@Preview(name = "关于 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AboutScreenPreviewDefault() {
    QkTheme {
        AboutScreen(version = "1.0.0", onRowClick = {})
    }
}

/** 账号安全 · 默认态。 */
@Preview(name = "账号安全 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AccountSecurityScreenPreviewDefault() {
    QkTheme {
        AccountSecurityScreen(
            phoneMasked = "138****8888",
            emailState = "未绑定",
            passwordState = "已设置",
            onRowClick = {},
            onClosureClick = {},
        )
    }
}

/** 注销申请页 · 默认态。 */
@Preview(name = "注销 · 申请", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ClosureApplyScreenPreviewDefault() {
    QkTheme {
        ClosureApplyScreen(onNext = {}, onReconsider = {})
    }
}

/** 注销验证页 · 默认态。 */
@Preview(name = "注销 · 验证码", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ClosureVerifyScreenPreviewDefault() {
    QkTheme {
        ClosureVerifyScreen(resendSecondsLeft = 59, code = "1234", onCodeChange = {}, onConfirm = {})
    }
}

/** 注销冷静期页 · 默认态。 */
@Preview(name = "注销 · 冷静期", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ClosureProcessingScreenPreviewDefault() {
    QkTheme {
        ClosureProcessingScreen(coolingHoursLeft = 72L, onRevoke = {}, onSimulateEnd = {})
    }
}

/** 注销结果页 · 默认态。 */
@Preview(name = "注销 · 结果", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ClosureResultScreenPreviewDefault() {
    QkTheme {
        ClosureResultScreen(onBack = {})
    }
}

/** 隐私详情页 · 默认态（无删除确认弹窗）。 */
@Preview(name = "隐私详情 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PrivacyDetailScreenPreviewDefault() {
    QkTheme {
        PrivacyDetailScreen(
            deleteDialogOpen = false,
            onRowClick = {},
            onDeleteAll = {},
            onConfirmDelete = {},
            onCancelDelete = {},
        )
    }
}

/** 隐私详情页 · 删除确认弹窗（弹层态）。 */
@Preview(name = "隐私详情 · 删除确认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PrivacyDetailScreenPreviewDialog() {
    QkTheme {
        PrivacyDetailScreen(
            deleteDialogOpen = true,
            onRowClick = {},
            onDeleteAll = {},
            onConfirmDelete = {},
            onCancelDelete = {},
        )
    }
}
