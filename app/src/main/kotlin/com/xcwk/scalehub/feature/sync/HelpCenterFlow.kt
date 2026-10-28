package com.xcwk.scalehub.feature.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * help-center-flow — 帮助中心七态路由（T-093）。
 *
 * 把原型08 的七个 view 串成一条可达路径：
 *
 * ```
 * v-help ─┬─ v-faq ──────────────► 回到 v-help（记录「没解决 / 解决了」）
 *         ├─ v-diag ─────────────► 同意并上传 → v-help
 *         └─ v-feedback ─┬─ 提交 → v-feedback-submit ─┬─ 成功 → v-feedback-success
 *                        │                             └─ 失败 → v-feedback-fail
 *                        └─ 模拟失败 ──────────────────► v-feedback-fail
 * ```
 *
 * ## 边界（呈现式）
 * - 状态与推进全部来自 [state] + [onIntent]，本组件不持有 `remember`；
 * - 真实提交走 [onSubmitFeedback]、重试走 [onRetry]、打开 FAQ 走 [onOpenFaq]、
 *   复制诊断走 [onCopyDiagnostics]、出栈走 [onBack]；
 * - 原型里的「模拟失败」「模拟完成 → 成功」是原型演示按钮，保留原文案：
 *   前者推 [HelpCenterIntent.SimulateFailure]，后者默认推
 *   [HelpCenterIntent.SubmitSucceeded]（工单号由 [onSimulateSuccess] 注入）。
 */
@Composable
fun HelpCenterFlow(
    state: HelpCenterUiState,
    onIntent: (HelpCenterIntent) -> Unit,
    modifier: Modifier = Modifier,
    faqs: List<FaqItem> = HELP_FAQ_ITEMS,
    faqDetails: Map<String, FaqDetail> = mapOf(WEIGHT_FLUCTUATION_FAQ.id to WEIGHT_FLUCTUATION_FAQ),
    diagnosticsSummary: String = "",
    onBack: () -> Unit = {},
    onOpenFaq: (FaqItem) -> Unit = {},
    onSubmitFeedback: (FeedbackDraft) -> Unit = {},
    onRetry: () -> Unit = {},
    onCopyDiagnostics: (String) -> Unit = {},
    onToggleBluetooth: () -> Unit = {},
    onRefreshCloud: () -> Unit = {},
    deviceInfo: DiagDeviceInfo = DiagDeviceInfo(),
    bluetoothState: DiagBluetoothState = DiagBluetoothState(),
    networkState: DiagNetworkState = DiagNetworkState(),
    cloudState: DiagCloudState = DiagCloudState(),
    recentLogs: List<String> = emptyList(),
    onSimulateSuccess: () -> Unit = {
        onIntent(HelpCenterIntent.SubmitSucceeded(PROTOTYPE_TICKET_NO))
    },
) {
    when (state.step) {
        HelpCenterStep.Help -> HelpCenterScreen(
            faqs = faqs,
            onBack = onBack,
            onOpenFaq = { faq ->
                onOpenFaq(faq)
                onIntent(HelpCenterIntent.OpenFaq(faq.id))
            },
            onOpenDiagnostics = { onIntent(HelpCenterIntent.OpenDiagnostics) },
            onOpenFeedback = { onIntent(HelpCenterIntent.OpenFeedback) },
            modifier = modifier,
            searchQuery = state.searchQuery,
            onSearchChange = { onIntent(HelpCenterIntent.SearchChanged(it)) },
        )

        HelpCenterStep.Faq -> FaqDetailScreen(
            detail = state.openFaqId?.let { faqDetails[it] } ?: WEIGHT_FLUCTUATION_FAQ,
            onBack = { onIntent(HelpCenterIntent.CloseFaq) },
            onVerdict = { verdict ->
                state.openFaqId?.let { onIntent(HelpCenterIntent.JudgeFaq(it, verdict)) }
            },
            modifier = modifier,
        )

        HelpCenterStep.Feedback -> FeedbackFormScreen(
            draft = state.draft,
            onSelectType = { onIntent(HelpCenterIntent.SelectFeedbackType(it)) },
            onDescriptionChange = { onIntent(HelpCenterIntent.DescriptionChanged(it)) },
            onBack = { onIntent(HelpCenterIntent.BackToHelp) },
            onSubmit = {
                if (state.draft.canSubmit) {
                    onIntent(HelpCenterIntent.SubmitFeedback)
                    onSubmitFeedback(state.draft)
                }
            },
            onSimulateFailure = { onIntent(HelpCenterIntent.SimulateFailure) },
            modifier = modifier,
        )

        HelpCenterStep.Submitting -> FeedbackSubmitScreen(
            onBack = { onIntent(HelpCenterIntent.BackToHelp) },
            onSimulateDone = onSimulateSuccess,
            modifier = modifier,
        )

        HelpCenterStep.Success -> FeedbackSuccessScreen(
            ticketNo = state.ticketNo,
            onBackToHelp = { onIntent(HelpCenterIntent.BackToHelp) },
            onContinueFeedback = { onIntent(HelpCenterIntent.ContinueFeedback) },
            modifier = modifier,
        )

        HelpCenterStep.Failed -> FeedbackFailScreen(
            onRetry = {
                onIntent(HelpCenterIntent.RetrySubmit)
                onRetry()
            },
            onBackToEdit = { onIntent(HelpCenterIntent.BackToEdit) },
            modifier = modifier,
        )

        HelpCenterStep.Diagnostics -> DiagnosticsScreen(
            consent = state.diagnostics,
            onToggle = { onIntent(HelpCenterIntent.ToggleDiagnosticItem(it)) },
            onBack = { onIntent(HelpCenterIntent.BackToHelp) },
            onConsentAndUpload = {
                if (state.diagnostics.canUpload) onIntent(HelpCenterIntent.ConsentAndUpload)
            },
            modifier = modifier,
            diagnosticsSummary = diagnosticsSummary,
            onCopyDiagnostics = onCopyDiagnostics,
        )

        HelpCenterStep.DiagDetails -> DiagScreen(
            deviceInfo = deviceInfo,
            bluetoothState = bluetoothState,
            networkState = networkState,
            cloudState = cloudState,
            recentLogs = recentLogs,
            onCopyDiagnostics = { onCopyDiagnostics(diagnosticsSummary) },
            onToggleBluetooth = onToggleBluetooth,
            onRefreshCloud = onRefreshCloud,
            onBack = { onIntent(HelpCenterIntent.BackToHelp) },
            modifier = modifier,
        )
    }
}
