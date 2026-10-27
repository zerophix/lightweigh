package com.xcwk.scalehub.application.ai

/**
 * AI 问答端口（M2）——旧库 CloudSync.askNow（:133-165）切面：
 * 多轮上下文（takeLast(8)、单条 trim1500）、成功返回正文，失败返回文案。
 * 云函数调用（/v1/functions/ai-chat，55s 超时）由实现持有。
 *
 * 增强（DEC-081 Batch3）：
 *  - weeklySummary(prompt) — 生成周总结
 *  - healthAdvice(prompt) — 生成健康建议
 */
interface AiPort {
    /** @param turns (文本, 是否我方)；@return (ok, 正文或文案)。 */
    suspend fun ask(turns: List<Pair<String, Boolean>>): Pair<Boolean, String>

    /**
     * 生成周总结。
     * @param prompt 周数据摘要 Prompt
     * @return (ok, 正文或文案)
     */
    suspend fun weeklySummary(prompt: String): Pair<Boolean, String> {
        // 默认实现：走 ask 单轮
        return ask(listOf(prompt to true))
    }

    /**
     * 生成健康建议。
     * @param context 用户数据摘要
     * @param query 具体问题
     * @return (ok, 正文或文案)
     */
    suspend fun healthAdvice(context: String, query: String): Pair<Boolean, String> {
        val prompt = "你是一位健康顾问。根据以下用户数据：$context，请回答：$query"
        return ask(listOf(prompt to true))
    }
}
