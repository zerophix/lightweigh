package com.xcwk.scalehub.feature.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * AI 问答页「AI 周总结」入口 —— 入口登记、目标屏与空态口径。
 *
 * 钉的是两件事：入口点得到（文案与目标屏非空且指向 T-084 那屏），
 * 以及没有周报数据时不冒出假指标（[AiWeeklySummaryUiState.hasData] 为 false 走空态）。
 */
class AiWeeklyEntryTest {

    @Test
    fun `入口文案与目标屏都指向 AI 周总结`() {
        assertThat(AiWeeklyEntry.ID).isEqualTo("ai.weekly")
        assertThat(AiWeeklyEntry.TITLE).isEqualTo("AI 周总结")
        assertThat(AiWeeklyEntry.HINT).contains("本周")
        assertThat(AiWeeklyEntry.TARGET).contains("AiWeeklySummary")
    }

    @Test
    fun `无周报数据时走空态且不出现指标`() {
        val state = AiWeeklySummaryUiState()

        assertThat(state.hasData).isFalse()
        assertThat(state.metrics).isEmpty()
        assertThat(state.summary).isEmpty()
        assertThat(AiWeeklyEntry.EMPTY_TITLE).isEqualTo("本周暂无 AI 周总结")
    }

    @Test
    fun `有总结句即有正文`() {
        val state = AiWeeklySummaryUiState(summary = "本周体重下降 0.4 kg。")

        assertThat(state.hasData).isTrue()
    }

    @Test
    fun `入口标题与空态标题都不是空串`() {
        assertThat(AiWeeklyEntry.TITLE).isNotEmpty()
        assertThat(AiWeeklyEntry.EMPTY_TITLE).isNotEmpty()
    }
}
