package com.xcwk.scalehub.feature.ai

/**
 * ai — AI 周总结入口登记（宿主页 = AI 问答页）。
 *
 * ## 为什么单列一份
 * [AiWeeklySummaryScreen] 是呈现式组件，不知道自己在导航里的位置。入口文案、宿主页、
 * 目标屏三者写在一处，[AiScreen] 只引一行；「入口没接上」这类只在手点时才暴露的问题
 * 因此能在 JVM 单测里挡住。
 *
 * ## 数据口径
 * 本仓库没有周报聚合与生成服务，入口点进去是 [AiWeeklySummaryUiState] 的空态
 * （「本周暂无 AI 周总结」）。这里不塞任何示例指标——宁可空态，不造数。
 */
object AiWeeklyEntry {
    /** 稳定标识（进程重建 / 埋点用，不随文案改动）。 */
    const val ID: String = "ai.weekly"

    /** 入口行标题。 */
    const val TITLE: String = "AI 周总结"

    /** 入口行副标题（说明点进去能看到什么）。 */
    const val HINT: String = "本周关键变化、亮点与下周建议"

    /** 目标屏（与 [AiWeeklySummaryScreen] 一一对应，测试据此校验入口未指向空屏）。 */
    const val TARGET: String = "AiWeeklySummaryScreen"

    /** 无周报数据时的空态标题（与 [AiWeeklySummaryScreen] 内文案同源）。 */
    const val EMPTY_TITLE: String = "本周暂无 AI 周总结"
}
