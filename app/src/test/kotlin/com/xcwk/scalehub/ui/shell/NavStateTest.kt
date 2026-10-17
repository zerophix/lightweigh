package com.xcwk.scalehub.ui.shell

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

/**
 * NavState 转移表测试 —— 导航正确性从「if 的书写顺序」搬到了「状态 × 事件的函数」，
 * 于是可以像测算法一样测导航：每个事件的结果都是可断言的确定值。
 *
 * 覆盖口径：页签为根、覆盖页为栈、记录表单为模态替换层；返回逐层出栈；
 * 切换页签清栈（旧实现漏清功能页/报告页，此处钉住修正后的语义）。
 */
class NavStateTest {

    @Test
    fun 初始状态在首页且没有任何可返回的层() {
        val s = NavState()
        assertThat(s.tab).isEqualTo(MainTab.HOME)
        assertThat(s.record).isNull()
        assertThat(s.overlays).isEmpty()
        assertThat(s.canPop).isFalse()
    }

    @Test
    fun 压入覆盖页后栈顶即当前页() {
        val s = NavState().reduce(NavEvent.Push(NavOverlay.Ai))
        assertThat(s.top).isEqualTo(NavOverlay.Ai)
        assertThat(s.canPop).isTrue()
    }

    @Test
    fun 返回逐层出栈_回到刚才页面() {
        val s = NavState()
            .reduce(NavEvent.Push(NavOverlay.Scale))
            .reduce(NavEvent.Push(NavOverlay.Ai))

        assertThat(s.top).isEqualTo(NavOverlay.Ai)

        val backOnce = s.reduce(NavEvent.Back)
        assertThat(backOnce.top).isEqualTo(NavOverlay.Scale)

        val backTwice = backOnce.reduce(NavEvent.Back)
        assertThat(backTwice.overlays).isEmpty()
        assertThat(backTwice.canPop).isFalse()
    }

    @Test
    fun 报告页询问AI后返回仍在报告页() {
        val s = NavState()
            .reduce(NavEvent.Push(NavOverlay.Report(1_700_000_000_000L)))
            .reduce(NavEvent.Push(NavOverlay.Ai))
            .reduce(NavEvent.Back)

        assertThat(s.top).isEqualTo(NavOverlay.Report(1_700_000_000_000L))
    }

    @Test
    fun 切换页签回到根并清空覆盖页() {
        val s = NavState()
            .reduce(NavEvent.Push(NavOverlay.Ai))
            .reduce(NavEvent.Push(NavOverlay.Report(1L)))
            .reduce(NavEvent.SelectTab(MainTab.TREND))

        assertThat(s.tab).isEqualTo(MainTab.TREND)
        assertThat(s.overlays).isEmpty()
        assertThat(s.record).isNull()
        assertThat(s.canPop).isFalse()
    }

    @Test
    fun 打开记录表单会清空覆盖页并保留当前页签() {
        val s = NavState(tab = MainTab.TREND)
            .reduce(NavEvent.Push(NavOverlay.Ai))
            .reduce(NavEvent.OpenRecord("weight", "62.5"))

        assertThat(s.tab).isEqualTo(MainTab.TREND)
        assertThat(s.record).isEqualTo(NavRecord("weight", "62.5"))
        assertThat(s.overlays).isEmpty()
        assertThat(s.canPop).isTrue()
    }

    @Test
    fun 关闭记录表单回到原页签() {
        val s = NavState(tab = MainTab.MINE)
            .reduce(NavEvent.OpenRecord("water"))
            .reduce(NavEvent.CloseRecord)

        assertThat(s.tab).isEqualTo(MainTab.MINE)
        assertThat(s.record).isNull()
        assertThat(s.canPop).isFalse()
    }

    @Test
    fun 称重页测完看报告是替换栈顶而非叠两层() {
        val s = NavState()
            .reduce(NavEvent.Push(NavOverlay.Scale))
            .reduce(NavEvent.ReplaceTop(NavOverlay.Report(42L)))

        assertThat(s.overlays).hasSize(1)
        assertThat(s.top).isEqualTo(NavOverlay.Report(42L))
    }

    @Test
    fun 空栈返回不改变状态且不抛异常() {
        val s = NavState(tab = MainTab.DEVICE)
        val after = s.reduce(NavEvent.Back)

        assertThat(after).isEqualTo(s)
    }

    @Test
    fun 历史页打开报告后返回仍在历史页() {
        val s = NavState(tab = MainTab.TREND)
            .reduce(NavEvent.Push(NavOverlay.History))
            .reduce(NavEvent.Push(NavOverlay.Report(7L)))
            .reduce(NavEvent.Back)

        assertThat(s.top).isEqualTo(NavOverlay.History)
        assertThat(s.tab).isEqualTo(MainTab.TREND)
    }

    @Test
    fun 我的页进入设置后返回仍在我的页签() {
        val s = NavState(tab = MainTab.MINE)
            .reduce(NavEvent.Push(NavOverlay.Settings))
            .reduce(NavEvent.Back)

        assertThat(s.canPop).isFalse()
        assertThat(s.tab).isEqualTo(MainTab.MINE)
    }

    @Test
    fun 随机事件序列不抛异常且状态自洽() {
        val random = Random(20261017)
        var s = NavState()
        repeat(1_000) {
            s = s.reduce(randomEvent(random))
            assertThat(s.top).isEqualTo(s.overlays.lastOrNull())
            assertThat(s.canPop).isEqualTo(s.record != null || s.overlays.isNotEmpty())
            // 不变量：模态记录表单与覆盖页栈互斥（表单打开时栈必为空）
            assertThat(s.record == null || s.overlays.isEmpty()).isTrue()
        }
    }

    private fun randomEvent(random: Random): NavEvent = when (random.nextInt(7)) {
        0 -> NavEvent.SelectTab(MainTab.values().random(random))
        1 -> NavEvent.OpenRecord("weight")
        2 -> NavEvent.CloseRecord
        3, 4 -> NavEvent.Push(randomOverlay(random))
        5 -> NavEvent.Back
        else -> NavEvent.CloseAll
    }

    private fun randomOverlay(random: Random): NavOverlay = when (random.nextInt(7)) {
        0 -> NavOverlay.Ai
        1 -> NavOverlay.Scale
        2 -> NavOverlay.Report(random.nextLong(1, 10_000))
        3 -> NavOverlay.Feature("feature-${random.nextInt(4)}")
        4 -> NavOverlay.History
        5 -> NavOverlay.Settings
        else -> NavOverlay.Members
    }
}
