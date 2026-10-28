package com.xcwk.scalehub.application.ai

import com.xcwk.scalehub.application.sync.CloudSessionPort

/**
 * application service — AI 问答编排（增强版）。
 *
 * 语义（旧库 CloudSync.ask）：
 *  - 游客拦截（"请先使用手机号登录后再提问"）
 *  - 网络异常文案（"当前网络不可用，请联网后使用AI功能"）
 *  - 空输入拦截（"请输入问题"）
 *
 * 增强（DEC-081 Batch3）：
 *  - 周总结生成（基于历史测量 + 习惯数据）
 *  - 会话边界管理（最大 20 轮对话，超过时自动浓缩）
 *  - 多轮上下文保持
 */
class AiService(
    private val session: CloudSessionPort,
    private val ai: AiPort,
) {

    /** 最大对话轮次。 */
    companion object {
        const val MAX_TURNS = 20
    }

    /**
     * 发送多轮对话。
     * @param turns 对话历史 (content, isUser)
     * @return AI 回答
     */
    suspend fun ask(turns: List<Pair<String, Boolean>>): AiOutcome {
        if (!session.isFormalAccount()) {
            return AiOutcome(false, "请先使用手机号登录后再提问")
        }
        if (turns.none { it.first.isNotBlank() }) {
            return AiOutcome(false, "请输入问题")
        }
        // 边界检查：超过最大轮次时裁剪
        val trimmed = if (turns.size > MAX_TURNS) {
            turns.take(1) + turns.takeLast(MAX_TURNS - 1)
        } else turns

        val result = runCatching { ai.ask(trimmed) }
            .getOrElse { return AiOutcome(false, "当前网络不可用，请联网后使用AI功能") }
        return AiOutcome(result.first, result.second)
    }

    /**
     * 生成周总结。
     * @param weekStart 周开始时间戳
     * @param weekEnd 周结束时间戳
     * @param weeklyData 周内的测量/习惯数据摘要
     */
    suspend fun weeklySummary(
        weekStart: Long,
        weekEnd: Long,
        weeklyData: WeeklyData,
    ): AiOutcome {
        if (!session.isFormalAccount()) {
            return AiOutcome(false, "请先使用手机号登录后再查看周总结")
        }
        if (weeklyData.measurements.isEmpty() && weeklyData.habits.isEmpty()) {
            return AiOutcome(false, "本周暂无数据，继续坚持就能生成总结~")
        }
        val prompt = buildWeeklyPrompt(weekStart, weekEnd, weeklyData)
        val result = runCatching { ai.weeklySummary(prompt) }
            .getOrElse {
                return AiOutcome(false, "生成周总结失败：${it.message ?: "请稍后重试"}}")
            }
        return AiOutcome(result.first, result.second)
    }

    /**
     * AI 建议生成（用于报告页）。
     */
    suspend fun generateAdvice(context: String, query: String): AiOutcome {
        if (!session.isFormalAccount()) {
            return AiOutcome(false, "请先使用手机号登录后再获取建议")
        }
        val result = runCatching { ai.healthAdvice(context, query) }
            .getOrElse {
                return AiOutcome(false, "获取建议失败：${it.message ?: "请稍后重试"}}")
            }
        return AiOutcome(result.first, result.second)
    }

    /** 检查是否达到对话上限。 */
    fun isMaxTurns(turns: List<Pair<String, Boolean>>): Boolean = turns.size >= MAX_TURNS

    /** 裁剪对话历史（保留最近 MAX_TURNS 轮）。 */
    fun trimHistory(turns: List<Pair<String, Boolean>>): List<Pair<String, Boolean>> {
        return if (turns.size > MAX_TURNS) {
            turns.take(1) + turns.takeLast(MAX_TURNS - 1)
        } else turns
    }

    private fun buildWeeklyPrompt(
        weekStart: Long,
        weekEnd: Long,
        weeklyData: WeeklyData,
    ): String {
        val sb = StringBuilder()
        sb.append("请根据以下数据生成一周（")
        sb.append(formatDate(weekStart))
        sb.append(" 至 ")
        sb.append(formatDate(weekEnd))
        sb.append("）的健康周总结，控制在 200 字以内，使用友好温馨的语气：\n\n")
        sb.append("体重变化：")
        weeklyData.measurements.forEach { sb.append("${formatDate(it.time)}: ${it.weight}kg（体脂${it.fatRate}%）\n") }
        sb.append("\n习惯打卡：")
        weeklyData.habits.forEach { sb.append("${it.name}: ${it.completed}/${it.target} 次\n") }
        sb.append("\n请从「数据趋势」「习惯表现」「改进建议」三个方面简要总结。")
        return sb.toString()
    }

    private fun formatDate(time: Long): String {
        return java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(time))
    }
}

/** AI 交互结果。 */
data class AiOutcome(val ok: Boolean, val message: String)

/** 周数据摘要。 */
data class WeeklyData(
    val measurements: List<WeeklyMeasurement>,
    val habits: List<WeeklyHabit>,
)

/** 周测量条目。 */
data class WeeklyMeasurement(
    val time: Long,
    val weight: Double,
    val fatRate: Double?,
)

/** 周习惯条目。 */
data class WeeklyHabit(
    val name: String,
    val completed: Int,
    val target: Int,
)
