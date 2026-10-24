package com.xcwk.scalehub.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.ai.AiService
import com.xcwk.scalehub.application.auth.AuthPort
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ai — ViewModel：问答编排随旧库 CloudSync.ask（游客拦截/网络文案由 AiService 给出，
 * 本层只维护对话流）。上下文裁剪（takeLast8/trim1500）在实现层。
 *
 * ## T-083 补全
 * 五态推进全部经纯函数 [reduceAiStage]，本类只负责「发事件 + 更新数据」：
 * - 发送 → [AiStage.Generating]；成功 → 回到 [AiStage.Chat]；失败 → [AiStage.Failed]（可重发）；
 * - 停止生成直接取消在途请求并回对话，迟到的结果因 Job 已取消而不会写入；
 * - 未授权 ↔ 授权设置双向可达（「去开启个性化」/「撤回全部授权」）。
 */
class AiViewModel(
    private val aiService: AiService,
    private val auth: AuthPort,
    initialInput: String = "",
) : ViewModel() {

    private val _state = MutableStateFlow(AiUiState(input = initialInput))
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    /** 在途提问；停止生成 / 重发前先取消，避免旧结果覆盖新状态。 */
    private var askJob: Job? = null

    init {
        _state.update { it.copy(loading = false, formal = auth.isFormalAccount()) }
    }

    fun onIntent(intent: AiIntent) {
        when (intent) {
            is AiIntent.InputChanged -> _state.update { it.copy(input = intent.value, error = null) }
            AiIntent.Send -> send()
            AiIntent.StopGeneration -> stopGeneration()
            AiIntent.Retry -> retry()
            AiIntent.OpenPlan -> transition(AiStageEvent.OpenPlan)
            AiIntent.OpenAuthSettings -> transition(AiStageEvent.OpenAuthSettings)
            AiIntent.EnablePersonalization -> transition(AiStageEvent.EnablePersonalization)
            AiIntent.WithdrawAuthorization -> withdrawAuthorization()
            AiIntent.BackToChat -> transition(AiStageEvent.BackToChat)
            AiIntent.DeleteAllMessages -> _state.update { it.copy(messages = emptyList()) }
        }
    }

    /** 发送新提问：空输入拦截后进入生成中。 */
    private fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(error = "请输入问题") }
            return
        }
        _state.update {
            it.copy(
                stage = reduceAiStage(it.stage, AiStageEvent.StartGeneration),
                messages = it.messages + AiMessage(true, text),
                generatingQuestion = text,
                failedQuestion = "",
                input = "",
                error = null,
                sending = true,
            )
        }
        ask()
    }

    /** 重发失败的那条提问：复用失败态里保留的原文，不新增一条用户消息。 */
    private fun retry() {
        val question = _state.value.failedQuestion
        if (question.isBlank()) {
            transition(AiStageEvent.BackToChat)
            return
        }
        _state.update {
            it.copy(
                stage = reduceAiStage(it.stage, AiStageEvent.Retry),
                generatingQuestion = question,
                error = null,
                sending = true,
            )
        }
        ask()
    }

    /** 停止生成：取消在途请求并回对话，保留已发出的提问。 */
    private fun stopGeneration() {
        askJob?.cancel()
        askJob = null
        _state.update {
            it.copy(
                stage = reduceAiStage(it.stage, AiStageEvent.StopGeneration),
                sending = false,
                error = null,
            )
        }
    }

    /** 撤回全部授权：设置关闭并落到未授权（通用咨询）态。 */
    private fun withdrawAuthorization() {
        _state.update {
            it.copy(
                stage = reduceAiStage(it.stage, AiStageEvent.WithdrawAuthorization),
                authorization = it.authorization.copy(enabled = false),
                sending = false,
            )
        }
    }

    /** 发起请求；结果只在仍处于生成中时写回（停止后迟到的结果一律丢弃）。 */
    private fun ask() {
        askJob?.cancel()
        askJob = viewModelScope.launch {
            val turns = _state.value.messages.map { it.text to it.mine }
            val outcome = aiService.ask(turns)
            _state.update {
                if (it.stage != AiStage.Generating) {
                    // 已停止 / 已离开生成态：不写回，避免「停了又冒出一条回答」
                    it
                } else if (outcome.ok) {
                    it.copy(
                        stage = reduceAiStage(it.stage, AiStageEvent.GenerationSucceeded),
                        messages = it.messages + AiMessage(false, outcome.message),
                        sending = false,
                    )
                } else {
                    it.copy(
                        stage = reduceAiStage(it.stage, AiStageEvent.GenerationFailed),
                        failedQuestion = it.generatingQuestion,
                        sending = false,
                        error = outcome.message,
                    )
                }
            }
        }
    }

    private fun transition(event: AiStageEvent) {
        _state.update { it.copy(stage = reduceAiStage(it.stage, event)) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                AiViewModel(app.container.aiService, app.container.authPort, initialInput = app.container.aiPrefill())
            }
        }
    }
}
