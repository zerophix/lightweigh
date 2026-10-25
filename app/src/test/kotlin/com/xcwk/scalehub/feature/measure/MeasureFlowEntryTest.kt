package com.xcwk.scalehub.feature.measure

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 首页「开始测量」入口文案。
 *
 * 层状态机（MeasureFlowLayer）已随原型镜像流程一并删除：首页主 CTA 现在直接
 * 打开真实蓝牙测量（ScaleRoute），不再有"演示流程层"这一跳。
 */
class MeasureFlowEntryTest {

    @Test
    fun `入口文案与设计稿一致`() {
        assertThat(MEASURE_ENTRY_TITLE).isEqualTo("开始测量")
    }
}
