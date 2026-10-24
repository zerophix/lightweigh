package com.xcwk.scalehub.feature

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.feature.ai.AiWeeklySummaryUiState
import com.xcwk.scalehub.feature.ai.MemberIdentity
import com.xcwk.scalehub.feature.ai.MemberOverview
import com.xcwk.scalehub.feature.ai.MemberPermissionDetailUiState
import com.xcwk.scalehub.feature.ai.WeeklyMetric
import com.xcwk.scalehub.feature.ai.goalProgressPercent
import com.xcwk.scalehub.feature.ai.lostKg
import com.xcwk.scalehub.feature.ai.overviewGoalText
import com.xcwk.scalehub.feature.ai.overviewLostText
import org.junit.Test

/**
 * Wave 1（T-084 AI 周总结与成员权限详情）纯逻辑单测。
 *
 * 覆盖验收中**唯一可自动判定**的部分：
 * - AC-J1-11 的空态分支：周数据为空走通用空态，不渲染假指标（与 AC-H1 同口径）
 * - 目标进度的除零兜底：起始区间 ≤ 0 时不崩溃、不产出 NaN 百分比
 *
 * 视觉与布局部分不在此测（需真机/截图），gov-prototype-verify --strict 负责文案与原型对齐。
 */
class Wave1AiWeeklyTest {

    // ---- T-084 周总结：空态分支（AC-J1-11「周数据为空时走通用空态」）----

    @Test
    fun `默认空状态判定为无数据走通用空态`() {
        assertThat(AiWeeklySummaryUiState().hasData).isFalse()
    }

    @Test
    fun `只有成员与日期没有总结与指标仍判定为空`() {
        val state = AiWeeklySummaryUiState(
            memberName = "林悦",
            dateRange = "2026年10月14日 — 10月20日",
        )
        assertThat(state.hasData).isFalse()
    }

    @Test
    fun `有总结句即视为有数据`() {
        val state = AiWeeklySummaryUiState(summary = "本周体重下降 0.4 kg，趋势稳健，继续保持。")
        assertThat(state.hasData).isTrue()
    }

    @Test
    fun `仅有关键变化指标也视为有数据`() {
        val state = AiWeeklySummaryUiState(metrics = listOf(WeeklyMetric("5/7", "测量天数")))
        assertThat(state.hasData).isTrue()
    }

    @Test
    fun `空白字符串不算数据`() {
        val state = AiWeeklySummaryUiState(summary = "   ", metrics = emptyList())
        assertThat(state.hasData).isFalse()
    }

    // ---- T-084 成员详情：目标进度除零兜底 ----

    @Test
    fun `原型数据算出的目标进度与文案一致`() {
        // 原型 v-member-detail：起始 65.2 → 当前 62.4，目标 58.0（已减 2.8 / 区间 7.2 = 39%）
        val overview = MemberOverview(measureCount = 128, startKg = 65.2, currentKg = 62.4, goalKg = 58.0)
        assertThat(goalProgressPercent(overview.startKg, overview.currentKg, overview.goalKg)).isEqualTo(39)
        assertThat(overviewLostText(overview)).isEqualTo("已减 2.8 kg")
        assertThat(overviewGoalText(overview)).isEqualTo("目标 58.0 kg · 39%")
    }

    @Test
    fun `起始等于目标不除零返回零`() {
        assertThat(goalProgressPercent(60.0, 59.0, 60.0)).isEqualTo(0)
        assertThat(goalProgressPercent(0.0, 0.0, 0.0)).isEqualTo(0)
    }

    @Test
    fun `起始低于目标（无下降空间）返回零`() {
        assertThat(goalProgressPercent(55.0, 54.0, 58.0)).isEqualTo(0)
    }

    @Test
    fun `超额达成目标夹在一百以内`() {
        assertThat(goalProgressPercent(65.0, 50.0, 58.0)).isEqualTo(100)
    }

    @Test
    fun `反向增长不出现负的已减重量`() {
        assertThat(lostKg(62.4, 65.2)).isEqualTo(0.0)
        assertThat(goalProgressPercent(65.2, 66.0, 58.0)).isEqualTo(0)
    }

    @Test
    fun `未开始（当前等于起始）进度为零`() {
        assertThat(goalProgressPercent(65.2, 65.2, 58.0)).isEqualTo(0)
    }

    // ---- T-084 成员详情：空状态不补假行 ----

    @Test
    fun `默认状态不携带任何权限假行`() {
        val state = MemberPermissionDetailUiState(
            identity = MemberIdentity(name = "林悦", glyph = "悦", relationLine = "本人 · 女 · 30岁 · 168cm", isCurrent = true),
        )
        assertThat(state.permissions).isEmpty()
        assertThat(state.overview).isNull()
        assertThat(state.device).isNull()
        assertThat(state.note).isEmpty()
    }
}
