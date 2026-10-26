package com.xcwk.scalehub.feature

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.feature.ai.AiWeeklySummaryUiState
import com.xcwk.scalehub.feature.ai.bodyRecompositionOf
import org.junit.Test

/**
 * T-064 身体重组洞察（纯逻辑）。
 *
 * 钉的是「什么时候该说、什么时候必须闭嘴」：
 * - 肌肉↑ 且 脂肪↓ 同时超过阈值才给解读
 * - 任一侧低于阈值（测量噪声）不编解读
 * - 数据缺失不推断
 * - 文案包含真实变化量，不出现占位数字
 */
class BodyRecompositionTest {

    @Test
    fun `肌肉升脂肪降时给出洞察`() {
        val insight = bodyRecompositionOf(muscleDeltaKg = 0.4, fatDeltaKg = -0.6)
        assertThat(insight).isNotNull()
        assertThat(insight!!.title).isEqualTo("身体正在重组")
        assertThat(insight.detail).contains("+0.4")
        assertThat(insight.detail).contains("-0.6")
    }

    @Test
    fun `低于阈值视为噪声不编解读`() {
        assertThat(bodyRecompositionOf(0.2, -0.2)).isNull()
        assertThat(bodyRecompositionOf(0.0, 0.0)).isNull()
    }

    @Test
    fun `只有肌肉升但脂肪没降时不给`() {
        assertThat(bodyRecompositionOf(0.8, -0.1)).isNull()
        assertThat(bodyRecompositionOf(0.8, 0.3)).isNull()
    }

    @Test
    fun `只有脂肪降但肌肉没升时不给`() {
        assertThat(bodyRecompositionOf(0.1, -0.9)).isNull()
    }

    @Test
    fun `缺任一侧数据时不推断`() {
        assertThat(bodyRecompositionOf(null, -0.8)).isNull()
        assertThat(bodyRecompositionOf(0.8, null)).isNull()
    }

    @Test
    fun `时间窗文案来自调用方`() {
        val insight = bodyRecompositionOf(0.5, -0.5, windowLabel = "本月")
        assertThat(insight!!.detail).startsWith("本月肌肉")
    }

    @Test
    fun `默认状态不携带洞察`() {
        assertThat(AiWeeklySummaryUiState().recomposition).isNull()
    }
}
