package com.xcwk.scalehub.feature.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xcwk.scalehub.application.support.FeedbackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * feedback-screen — 反馈提交入口屏幕（T-1011）。
 *
 * 该屏幕是反馈功能的路由入口，桥接：[FeedbackService] 后端与 [FeedbackFormScreen] 等 UI。
 * - 点击「提交」→ 调用 FeedbackService.submit → 成功返回工单号，失败保留草稿
 * - 点击「上传诊断」→ 调用 FeedbackService.collectDiagnostics → 调起 [DiagnosticsScreen]
 *
 * 呈现式约束（同 FeedbackFlow.kt）：
 * - UI 不直接触碰网络/数据库，副作用通过 ViewModel + 回调完成
 * - 状态单向流：ViewModel 持有 UiState，UI 只消费 onIntent
 */
class FeedbackViewModel(
    private val feedbackService: FeedbackService,
) : ViewModel() {

    private val _state = MutableStateFlow<FeedbackUiState>(FeedbackUiState.Idle)
    val state: StateFlow<FeedbackUiState> = _state

    /** 提交反馈。 */
    fun submit(
        userId: String,
        type: com.xcwk.scalehub.application.support.FeedbackCategory,
        content: String,
        contact: String,
    ) {
        viewModelScope.launch {
            _state.value = FeedbackUiState.Submitting
            val result = feedbackService.submit(
                userId = userId,
                content = content,
                category = type.name,
                contact = contact,
            )
            if (result.ok) {
                _state.value = FeedbackUiState.Success(ticketId = result.issueId ?: "")
            } else {
                _state.value = FeedbackUiState.Failed(
                    message = result.message,
                    canRetry = result.canRetry,
                )
            }
        }
    }

    /** 收集诊断信息。 */
    fun collectDiagnostics() {
        viewModelScope.launch {
            val info = feedbackService.collectDiagnostics()
            if (info != null) {
                _state.value = FeedbackUiState.Diagnostics(info.deviceInfo, info.appVersion, info.logs.size)
            }
        }
    }

    /** 重试提交。 */
    fun retry(
        userId: String,
        type: com.xcwk.scalehub.application.support.FeedbackCategory,
        content: String,
        contact: String,
    ) {
        submit(userId, type, content, contact)
    }

    /** 重置到 Idle。 */
    fun reset() {
        _state.value = FeedbackUiState.Idle
    }
}

/** 反馈 UI 状态。 */
sealed interface FeedbackUiState {
    data object Idle : FeedbackUiState
    data object Submitting : FeedbackUiState
    data class Success(val ticketId: String) : FeedbackUiState
    data class Failed(val message: String, val canRetry: Boolean) : FeedbackUiState
    data class Diagnostics(
        val deviceInfo: Map<String, String>,
        val appVersion: String,
        val logCount: Int,
    ) : FeedbackUiState
}

/**
 * 反馈屏幕 — 组合 FeedbackFlow 中的子组件。
 *
 * 作为路由入口，处理状态流转，委托 UI 渲染给 FeedbackFormScreen/FeedbackSubmitScreen 等。
 */
@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel,
    onBack: () -> Unit,
    onNavigateHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 状态由调用方传入的 HelpCenterUiState 驱动时使用下面的简单版本
    // 这里仅作为入口屏幕，实际 UI 由 FeedbackFlow.kt 的组件完成
    FeedbackScreenImpl(
        state = viewModel.state.value,
        onFormSubmit = { type, content, contact, userId ->
            viewModel.submit(userId, type, content, contact)
        },
        onRetry = { type, content, contact, userId ->
            viewModel.retry(userId, type, content, contact)
        },
        onCollectDiagnostics = { viewModel.collectDiagnostics() },
        onBack = onBack,
        onNavigateHelp = onNavigateHelp,
        modifier = modifier,
    )
}

/**
 * 反馈屏幕实现 — 托管到 FeedbackFlow.kt 的组件。
 */
@Composable
private fun FeedbackScreenImpl(
    state: FeedbackUiState,
    onFormSubmit: (com.xcwk.scalehub.application.support.FeedbackCategory, String, String, String) -> Unit,
    onRetry: (com.xcwk.scalehub.application.support.FeedbackCategory, String, String, String) -> Unit,
    onCollectDiagnostics: () -> Unit,
    onBack: () -> Unit,
    onNavigateHelp: () -> Unit,
    modifier: Modifier,
) {
    when (state) {
        is FeedbackUiState.Idle -> {
            FeedbackFormScreen(
                draft = FeedbackDraft(),
                onSelectType = { },
                onDescriptionChange = { },
                onBack = onBack,
                onSubmit = { onFormSubmit(com.xcwk.scalehub.application.support.FeedbackCategory.OTHER, "", "", "") },
                onSimulateFailure = { },
                modifier = modifier,
            )
        }

        is FeedbackUiState.Submitting -> {
            FeedbackSubmitScreen(
                onBack = onBack,
                onSimulateDone = { onNavigateHelp() },
                modifier = modifier,
            )
        }

        is FeedbackUiState.Success -> {
            FeedbackSuccessScreen(
                ticketNo = state.ticketId,
                onBackToHelp = onNavigateHelp,
                onContinueFeedback = { },
                modifier = modifier,
            )
        }

        is FeedbackUiState.Failed -> {
            FeedbackFailScreen(
                onRetry = { onRetry(com.xcwk.scalehub.application.support.FeedbackCategory.OTHER, "", "", "") },
                onBackToEdit = { },
                modifier = modifier,
            )
        }

        is FeedbackUiState.Diagnostics -> {
            DiagnosticsScreen(
                consent = DiagnosticConsent(),
                onToggle = { },
                onBack = onBack,
                onConsentAndUpload = { onCollectDiagnostics() },
                modifier = modifier,
            )
        }
    }
}
