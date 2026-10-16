package com.xcwk.scalehub.application.ai

import com.xcwk.scalehub.application.sync.CloudSessionPort

/**
 * application service — AI 问答编排（旧库 CloudSync.ask 语义）：
 * 游客拦截（:63-65 "请先使用手机号登录后再提问"）、
 * 网络异常文案（:69）、空输入拦截（:159 "请输入问题"）。
 */
class AiService(
    private val session: CloudSessionPort,
    private val ai: AiPort,
) {

    suspend fun ask(turns: List<Pair<String, Boolean>>): AiOutcome {
        if (!session.isFormalAccount()) {
            return AiOutcome(false, "请先使用手机号登录后再提问")
        }
        if (turns.none { it.first.isNotBlank() }) {
            return AiOutcome(false, "请输入问题")
        }
        val result = runCatching { ai.ask(turns) }
            .getOrElse { return AiOutcome(false, "当前网络不可用，请联网后使用AI功能") }
        return AiOutcome(result.first, result.second)
    }
}

data class AiOutcome(val ok: Boolean, val message: String)
