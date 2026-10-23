package com.xcwk.scalehub.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-099（Wave 0 组件补全）JVM 单测：骨架屏尺寸稳定性 + 长按菜单回调语义。
 *
 * 覆盖的都是「写错了没人会发现」的判定逻辑：
 * - line 宽度百分比夹取（越界参数不该撑破布局）
 * - shimmer 窗口宽度恒定（动画只平移不缩放 → 加载后无跳动的可判定部分）
 * - 菜单回调只触发一次、未知项不误触发、空列表不弹出
 */
class QkSkeletonLongPressTest {

    // ---- 骨架屏：line 宽度百分比夹取 ----

    @Test
    fun `line 宽度百分比在合法区间内原样换算`() {
        assertThat(skeletonWidthFraction(35f)).isEqualTo(0.35f)
        assertThat(skeletonWidthFraction(55f)).isEqualTo(0.55f)
        assertThat(skeletonWidthFraction(100f)).isEqualTo(1f)
    }

    @Test
    fun `line 宽度百分比越界时夹取而不是抛异常`() {
        assertThat(skeletonWidthFraction(0f)).isEqualTo(0.05f)
        assertThat(skeletonWidthFraction(-20f)).isEqualTo(0.05f)
        assertThat(skeletonWidthFraction(240f)).isEqualTo(1f)
    }

    // ---- 骨架屏：shimmer 窗口宽度恒定 ----

    @Test
    fun `shimmer 窗口宽度在整个动画周期内恒等于占位宽度`() {
        val widthPx = 360f
        var progress = 0f
        while (progress <= 1.0001f) {
            assertThat(shimmerGradientRange(progress, widthPx).windowWidth).isEqualTo(widthPx)
            progress += 0.05f
        }
    }

    @Test
    fun `shimmer 进度越界时夹取且窗口仍稳定`() {
        val range = shimmerGradientRange(5f, 200f)
        assertThat(range.windowWidth).isEqualTo(200f)
        // 进度 5 与 1 等价：起点都是 2f * widthPx
        assertThat(range.startX).isEqualTo(shimmerGradientRange(1f, 200f).startX)
        assertThat(shimmerGradientRange(-3f, 200f).startX)
            .isEqualTo(shimmerGradientRange(0f, 200f).startX)
    }

    @Test
    fun `shimmer 起点随进度单调右移（亮带平移而非跳变）`() {
        val widthPx = 100f
        var previous = shimmerGradientRange(0f, widthPx).startX
        var progress = 0.1f
        while (progress <= 1.0001f) {
            val current = shimmerGradientRange(progress, widthPx).startX
            assertThat(current).isGreaterThan(previous)
            previous = current
            progress += 0.1f
        }
    }

    // ---- 长按菜单：回调语义 ----

    @Test
    fun `长按弹出菜单时保留传入顺序且初始无选中`() {
        val controller = QkLongPressMenuController(onAction = { })
        controller.show(listOf("复制", "分享", "全选"))
        assertThat(controller.visible).isTrue()
        assertThat(controller.menuItems).containsExactly("复制", "分享", "全选").inOrder()
        assertThat(controller.selectedIndex).isEqualTo(-1)
    }

    @Test
    fun `空菜单列表不弹出`() {
        val controller = QkLongPressMenuController(onAction = { })
        controller.show(emptyList())
        assertThat(controller.visible).isFalse()
    }

    @Test
    fun `点选命中项回调一次并收起`() {
        val actions = mutableListOf<String>()
        val controller = QkLongPressMenuController(onAction = actions::add)
        controller.show(listOf("复制", "分享", "全选"))

        controller.select("分享")

        assertThat(actions).containsExactly("分享")
        assertThat(controller.visible).isFalse()
        assertThat(controller.selectedIndex).isEqualTo(-1) // dismiss 后清空
    }

    @Test
    fun `点选未命中项不回调也不收起`() {
        val actions = mutableListOf<String>()
        val controller = QkLongPressMenuController(onAction = actions::add)
        controller.show(listOf("复制", "分享"))

        controller.select("全选")

        assertThat(actions).isEmpty()
        assertThat(controller.visible).isTrue()
    }

    @Test
    fun `收起后重置全部状态`() {
        val controller = QkLongPressMenuController(onAction = { })
        controller.show(listOf("复制"))
        controller.dismiss()
        assertThat(controller.visible).isFalse()
        assertThat(controller.menuItems).isEmpty()
        assertThat(controller.selectedIndex).isEqualTo(-1)
    }

    @Test
    fun `竖分隔线只出现在项与项之间`() {
        val controller = QkLongPressMenuController(onAction = { })
        controller.show(listOf("复制", "分享", "全选"))
        assertThat(controller.dividerAfter(0)).isTrue()
        assertThat(controller.dividerAfter(1)).isTrue()
        assertThat(controller.dividerAfter(2)).isFalse() // 最后一项后不画
        assertThat(controller.dividerAfter(-1)).isFalse()
    }

    @Test
    fun `再次长按刷新菜单内容`() {
        val controller = QkLongPressMenuController(onAction = { })
        controller.show(listOf("复制"))
        controller.select("复制")
        controller.show(listOf("分享", "全选"))
        assertThat(controller.menuItems).containsExactly("分享", "全选").inOrder()
        assertThat(controller.visible).isTrue()
    }
}
