package com.xcwk.scalehub.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.ai.AiService
import com.xcwk.scalehub.application.auth.AuthPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ai — ViewModel：问答编排随旧库 CloudSync.ask（游客拦截/网络文案由 AiService 给出，
 * 本层只维护对话流）。上下文裁剪（takeLast8/trim1500）在实现层。
 */
class AiViewModel(
    private val aiService: AiService,
    private val auth: AuthPort,
    initialInput: String = "",
) : ViewModel() {

    private val _state = MutableStateFlow(AiUiState(input = initialInput))
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(loading = false, formal = auth.isFormalAccount()) }
    }

    fun onIntent(intent: AiIntent) {
        when (intent) {
            is AiIntent.InputChanged -> _state.update { it.copy(input = intent.value, error = null) }
            AiIntent.Send -> send()
        }
    }

    private fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(error = "请输入问题") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null, messages = it.messages + AiMessage(true, text), input = "") }
            val turns = _state.value.messages.map { it.text to it.mine }
            val outcome = aiService.ask(turns)
            _state.update {
                if (outcome.ok) {
                    it.copy(sending = false, messages = it.messages + AiMessage(false, outcome.message))
                } else {
                    it.copy(sending = false, error = outcome.message, messages = it.messages.dropLast(1))
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                AiViewModel(app.container.aiService, app.container.sessionStore, initialInput = app.container.aiPrefill())
            }
        }
    }
}
