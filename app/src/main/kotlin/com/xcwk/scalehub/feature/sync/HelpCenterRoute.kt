package com.xcwk.scalehub.feature.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** 帮助中心路由名（宿主接入导航时使用）。 */
const val HELP_CENTER_ROUTE_NAME = "help-center"

/**
 * help-center-route — 帮助中心装配点（T-093）。
 *
 * 职责只有两件：
 * 1. 用 `remember` 持有 [HelpCenterUiState]，把 [HelpCenterIntent] 交给纯函数
 *    [feedbackReduce] 推进（推进表见 FeedbackFlow.kt）；
 * 2. 把宿主注入的外部动作原样转交给 [HelpCenterFlow]。
 *
 * 本文件不做真实提交、上传、剪贴板、导航——全部经回调交给宿主，
 * 因此接入真实数据层时只需替换宿主侧回调，不改本文件与各 view。
 *
 * @param initialStep 入口态：默认帮助中心列表；从同步异常页直接进 FAQ 时可指定。
 * @param diagnosticsSummary 调用方注入的诊断摘要文本（空串 = 尚未接入，不呈现复制入口）。
 * @param simulateTicketNo 原型「模拟完成 → 成功」用的工单号；真实工单号由宿主以
 *   [HelpCenterIntent.SubmitSucceeded] 回灌。
 */
@Composable
fun HelpCenterRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSubmitFeedback: (FeedbackDraft) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenFaq: (FaqItem) -> Unit = {},
    onCopyDiagnostics: (String) -> Unit = {},
    initialStep: HelpCenterStep = HelpCenterStep.Help,
    faqs: List<FaqItem> = HELP_FAQ_ITEMS,
    diagnosticsSummary: String = "",
    simulateTicketNo: String = PROTOTYPE_TICKET_NO,
) {
    var state by remember { mutableStateOf(HelpCenterUiState(step = initialStep)) }

    HelpCenterFlow(
        state = state,
        onIntent = { intent -> state = feedbackReduce(state, intent) },
        modifier = modifier,
        faqs = faqs,
        diagnosticsSummary = diagnosticsSummary,
        onBack = onBack,
        onOpenFaq = onOpenFaq,
        onSubmitFeedback = onSubmitFeedback,
        onRetry = onRetry,
        onCopyDiagnostics = onCopyDiagnostics,
        onSimulateSuccess = {
            state = feedbackReduce(state, HelpCenterIntent.SubmitSucceeded(simulateTicketNo))
        },
    )
}
