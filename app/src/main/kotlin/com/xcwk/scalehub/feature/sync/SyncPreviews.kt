package com.xcwk.scalehub.feature.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/** 同步、冲突、帮助与反馈交付屏的 Compose 预览集合。 */

/** 数据冲突 · 默认态。 */
@Preview(name = "数据冲突 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DataConflictScreenPreviewDefault() {
    QkTheme {
        DataConflictScreen(state = DataConflictUiState())
    }
}

/** 数据冲突路由 · 默认态。 */
@Preview(name = "数据冲突路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DataConflictRoutePreviewDefault() {
    QkTheme {
        DataConflictRoute()
    }
}

/** 同步中心路由 · 默认态。 */
@Preview(name = "同步中心 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SyncCenterRoutePreviewDefault() {
    QkTheme {
        SyncCenterRoute()
    }
}

/** 网络状态 · 默认态。 */
@Preview(name = "网络状态 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun NetworkStatusScreenPreviewDefault() {
    QkTheme {
        NetworkStatusScreen(state = NetworkStatusUiState())
    }
}

/** 服务状态 · 默认态。 */
@Preview(name = "服务状态 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ServiceStatusScreenPreviewDefault() {
    QkTheme {
        ServiceStatusScreen(state = ServiceStatusUiState())
    }
}

/** 帮助中心 · 默认态。 */
@Preview(name = "帮助中心 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HelpCenterScreenPreviewDefault() {
    QkTheme {
        HelpCenterScreen(
            faqs = HELP_FAQ_ITEMS,
            onBack = {},
            onOpenFaq = {},
            onOpenDiagnostics = {},
            onOpenFeedback = {},
        )
    }
}

/** 帮助中心 · 搜索无结果（空态）。 */
@Preview(name = "帮助中心 · 无搜索结果", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HelpCenterScreenPreviewNoResult() {
    QkTheme {
        HelpCenterScreen(
            faqs = emptyList(),
            onBack = {},
            onOpenFaq = {},
            onOpenDiagnostics = {},
            onOpenFeedback = {},
            searchQuery = "找不到的问题",
        )
    }
}

/** FAQ 详情 · 默认态。 */
@Preview(name = "FAQ 详情 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FaqDetailScreenPreviewDefault() {
    QkTheme {
        FaqDetailScreen(detail = WEIGHT_FLUCTUATION_FAQ, onBack = {}, onVerdict = {})
    }
}

/** 帮助中心流程 · 默认态。 */
@Preview(name = "帮助中心流程 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HelpCenterFlowPreviewDefault() {
    QkTheme {
        HelpCenterFlow(state = HelpCenterUiState(), onIntent = {})
    }
}

/** 帮助中心路由 · 默认态。 */
@Preview(name = "帮助中心路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HelpCenterRoutePreviewDefault() {
    QkTheme {
        HelpCenterRoute()
    }
}

/** 意见反馈表单 · 默认态。 */
@Preview(name = "反馈表单 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FeedbackFormScreenPreviewDefault() {
    QkTheme {
        FeedbackFormScreen(
            draft = FeedbackDraft(),
            onSelectType = {},
            onDescriptionChange = {},
            onBack = {},
            onSubmit = {},
            onSimulateFailure = {},
        )
    }
}

/** 反馈提交中 · 默认态。 */
@Preview(name = "反馈提交中", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FeedbackSubmitScreenPreviewDefault() {
    QkTheme {
        FeedbackSubmitScreen(onBack = {}, onSimulateDone = {})
    }
}

/** 反馈成功 · 默认态。 */
@Preview(name = "反馈成功", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FeedbackSuccessScreenPreviewDefault() {
    QkTheme {
        FeedbackSuccessScreen(
            ticketNo = "FB20261024001",
            onBackToHelp = {},
            onContinueFeedback = {},
        )
    }
}

/** 反馈失败（错误态）。 */
@Preview(name = "反馈失败", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FeedbackFailScreenPreviewDefault() {
    QkTheme {
        FeedbackFailScreen(onRetry = {}, onBackToEdit = {})
    }
}

/** 诊断信息授权 · 默认态。 */
@Preview(name = "诊断授权 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DiagnosticsScreenPreviewDefault() {
    QkTheme {
        DiagnosticsScreen(
            consent = DiagnosticConsent(),
            onToggle = {},
            onBack = {},
            onConsentAndUpload = {},
        )
    }
}
