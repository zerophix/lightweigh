package com.xcwk.scalehub.feature.ai

/** ai — 四件套之 UiState（对话流）。 */
data class AiMessage(val mine: Boolean, val text: String)

data class AiUiState(
    val loading: Boolean = true,
    val formal: Boolean = false,
    val messages: List<AiMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
)

sealed interface AiIntent {
    data class InputChanged(val value: String) : AiIntent
    data object Send : AiIntent
}
