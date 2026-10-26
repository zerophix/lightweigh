package com.xcwk.scalehub.feature

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.feature.measure.goalCurveDeltaPercents
import com.xcwk.scalehub.feature.measure.goalCurvePercents
import org.junit.Test

/**
 * T-066 目标进度曲线纯逻辑。
 *
 * 钉住"不画假线"：点数不足、没设目标、起点已低于目标，都不产出曲线。
 */
class GoalCurveTest {

    @Test
    fun `按起点到目标线性换算百分比`() {
        // 起点 65，目标 60，跨度 5：65→0，62.5→50，60→100
        val got = goalCurvePercents(listOf(65.0, 62.5, 60.0), 60f)
        assertThat(got).containsExactly(0, 50, 100).inOrder()
    }

    @Test
    fun `低于目标时夹到一百不超额`() {
        val got = goalCurvePercents(listOf(65.0, 58.0), 60f)
        assertThat(got).containsExactly(0, 100).inOrder()
    }

    @Test
    fun `反向增长夹到零不出现负进度`() {
        val got = goalCurvePercents(listOf(65.0, 66.0), 60f)
        assertThat(got).containsExactly(0, 0).inOrder()
    }

    @Test
    fun `少于两个点不产出`() {
        assertThat(goalCurvePercents(emptyList(), 60f)).isEmpty()
        assertThat(goalCurvePercents(listOf(65.0), 60f)).isEmpty()
    }

    @Test
    fun `未设目标不产出`() {
        assertThat(goalCurvePercents(listOf(65.0, 64.0), 0f)).isEmpty()
    }

    @Test
    fun `起点不高于目标不产出`() {
        assertThat(goalCurvePercents(listOf(60.0, 59.0), 60f)).isEmpty()
        assertThat(goalCurvePercents(listOf(55.0, 54.0), 60f)).isEmpty()
    }

    @Test
    fun `变化量取末点减首点`() {
        assertThat(goalCurveDeltaPercents(listOf(0, 20, 45))).isEqualTo(45)
        assertThat(goalCurveDeltaPercents(listOf(10))).isNull()
    }
}
