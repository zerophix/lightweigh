package com.xcwk.scalehub.feature.measure

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 首页「开始测量」入口（T-077 接入波第三批）—— 入口映射与进入/返回路径。
 *
 * 钉的是两件会手改坏的事：
 * 1. 入口文案被顺手改掉（原型首页主按钮逐字「开始测量」）；
 * 2. 进入覆盖层后回不到首页（层状态机漏了「关闭」方向）。
 */
class MeasureFlowEntryTest {

    @Test
    fun `入口文案与原型一致`() {
        assertThat(MEASURE_ENTRY_TITLE).isEqualTo("开始测量")
    }

    @Test
    fun `进入测量流程`() {
        assertThat(measureHomeLayerReduce(MeasureHomeLayer.MeasureFlow))
            .isEqualTo(MeasureHomeLayer.MeasureFlow)
    }

    @Test
    fun `进入数据同步`() {
        assertThat(measureHomeLayerReduce(MeasureHomeLayer.SyncCenter))
            .isEqualTo(MeasureHomeLayer.SyncCenter)
    }

    @Test
    fun `返回即回首页`() {
        assertThat(measureHomeLayerReduce(null)).isEqualTo(MeasureHomeLayer.Home)
    }

    @Test
    fun `进入再返回`() {
        val entered = measureHomeLayerReduce(MeasureHomeLayer.MeasureFlow)
        val back = measureHomeLayerReduce(null)

        assertThat(entered).isNotEqualTo(MeasureHomeLayer.Home)
        assertThat(back).isEqualTo(MeasureHomeLayer.Home)
    }

    @Test
    fun `测量流程默认从检查态开始`() {
        // 入口不注入状态时，流程从头开始（T-077 八态的第一态），不是从中间态进入
        assertThat(MeasureFlowUiState().step).isEqualTo(MeasureFlowStep.Check)
    }
}
