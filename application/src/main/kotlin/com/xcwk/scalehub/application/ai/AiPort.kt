package com.xcwk.scalehub.application.ai

/**
 * AI 问答端口（M2）——旧库 CloudSync.askNow（:133-165）切面：
 * 多轮上下文（takeLast(8)、单条 trim1500）、成功返回正文，失败返回文案。
 * 云函数调用（/v1/functions/ai-chat，55s 超时）由实现持有。
 */
interface AiPort {
    /** @param turns (文本, 是否我方)；@return (ok, 正文或文案)。 */
    suspend fun ask(turns: List<Pair<String, Boolean>>): Pair<Boolean, String>
}
