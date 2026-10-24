package com.xcwk.scalehub.feature.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 首页八态接入（T-095 接入波第三批）—— 真实信号到八态的映射。
 *
 * 钉的是「什么时候该替换真实首页」：只有能证实的态才替换，
 * 信号不足时保持真实首页，不伪造未连接 / 同步中 / 目标达成。
 */
class HomeStateEntryTest {

    @Test
    fun `有记录且加载成功保持真实首页`() {
        assertThat(homeStageOf(hasMeasurements = true, loadFailed = false))
            .isEqualTo(HomeStage.Home)
    }

    @Test
    fun `没有记录进入空态`() {
        assertThat(homeStageOf(hasMeasurements = false, loadFailed = false))
            .isEqualTo(HomeStage.Empty)
    }

    @Test
    fun `加载失败进入错误态`() {
        assertThat(homeStageOf(hasMeasurements = true, loadFailed = true))
            .isEqualTo(HomeStage.Error)
    }

    @Test
    fun `加载失败优先于空数据`() {
        // 加载失败时不知道有没有记录，报「加载失败」比报「还没有数据」更诚实
        assertThat(homeStageOf(hasMeasurements = false, loadFailed = true))
            .isEqualTo(HomeStage.Error)
    }

    @Test
    fun `只有非首页态才替换真实首页`() {
        assertThat(homeStatesReplacesHome(HomeStage.Home)).isFalse()
        assertThat(homeStatesReplacesHome(HomeStage.Empty)).isTrue()
        assertThat(homeStatesReplacesHome(HomeStage.Error)).isTrue()
    }

    @Test
    fun `空态与错误态都能回到首页`() {
        listOf(HomeStage.Empty, HomeStage.Error).forEach { stage ->
            val state = HomeUiState(stage = stage)
            assertThat(homeReduce(state, HomeEvent.Back).stage).isEqualTo(HomeStage.Home)
        }
    }

    @Test
    fun `空态可进入第一次测量`() {
        val state = HomeUiState(stage = HomeStage.Empty)

        assertThat(homeReduce(state, HomeEvent.StartFirstMeasure).stage)
            .isEqualTo(HomeStage.Home)
    }
}
