package com.xcwk.scalehub.feature.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-080 成员设备关联纯逻辑单测。
 *
 * 覆盖可自动判定的部分：
 * - 勾选集合切换（[toggleMemberLink]）：逐人开关、未知 id 不产生幽灵勾选
 * - 设备行副标题由集合大小派生（「已关联 N 位成员」），非写死
 * - 成员行副标题（默认使用者 / 共用当前设备）
 * - 文案与三人成员数据逐字对齐原型 `24-remaining-gaps.html` 的 `v-member-device`
 */
class MemberDeviceLinkTest {

    private fun state(linked: Set<String> = DEFAULT_LINKED_MEMBER_IDS) =
        MemberDeviceLinkUiState(linkedIds = linked)

    // ---- 勾选集合 ----

    @Test
    fun `初始三人全部关联`() {
        val state = state()
        assertThat(state.linkedCount).isEqualTo(3)
        assertThat(state.members.map { it.name }).containsExactly("林悦", "妈妈", "爸爸").inOrder()
        assertThat(state.members.all { state.isLinked(it.id) }).isTrue()
    }

    @Test
    fun `取消一人后关联数随之下降`() {
        val next = toggleMemberLink(state(), "mama")
        assertThat(next.isLinked("mama")).isFalse()
        assertThat(next.linkedCount).isEqualTo(2)
        assertThat(next.linkedSummary).isEqualTo("已关联 2 位成员")
    }

    @Test
    fun `再次点击恢复关联`() {
        val off = toggleMemberLink(state(), "baba")
        val on = toggleMemberLink(off, "baba")
        assertThat(on.isLinked("baba")).isTrue()
        assertThat(on.linkedIds).containsExactly("linyue", "mama", "baba")
    }

    @Test
    fun `未知成员 id 原样返回不产生幽灵勾选`() {
        val before = state()
        val after = toggleMemberLink(before, "ghost")
        assertThat(after.linkedIds).isEqualTo(before.linkedIds)
    }

    @Test
    fun `全部取消后可保存空集合`() {
        var current = state()
        current.members.forEach { current = toggleMemberLink(current, it.id) }
        assertThat(current.linkedCount).isEqualTo(0)
        assertThat(current.linkedSummary).isEqualTo("已关联 0 位成员")
        assertThat(current.linkedIds).isEmpty()
    }

    // ---- 派生文案 ----

    @Test
    fun `已关联人数文案按数量派生`() {
        assertThat(linkedMemberSummary(1)).isEqualTo("已关联 1 位成员")
        assertThat(linkedMemberSummary(3)).isEqualTo("已关联 3 位成员")
    }

    @Test
    fun `默认使用者固定文案其余成员共用设备名`() {
        val linyue = DEFAULT_LINKABLE_MEMBERS.single { it.id == "linyue" }
        val mama = DEFAULT_LINKABLE_MEMBERS.single { it.id == "mama" }
        assertThat(memberUsageText(linyue, MEMBER_DEVICE_NAME)).isEqualTo("默认使用此设备")
        assertThat(memberUsageText(mama, MEMBER_DEVICE_NAME)).isEqualTo("共用客厅体脂秤")
    }

    // ---- 文案（原型逐字） ----

    @Test
    fun `成员设备关联页文案逐字对齐原型`() {
        assertThat(MEMBER_DEVICE_TITLE).isEqualTo("成员设备关联")
        assertThat(MEMBER_DEVICE_HEADLINE).isEqualTo("成员与设备关联")
        assertThat(MEMBER_DEVICE_SUBLINE).isEqualTo("管理哪些成员使用哪些设备")
        assertThat(MEMBER_DEVICE_SECTION).isEqualTo("设备")
        assertThat(MEMBER_DEVICE_MANAGE).isEqualTo("管理 ›")
        assertThat(MEMBER_LINK_SECTION).isEqualTo("关联成员")
        assertThat(MEMBER_DEVICE_NAME).isEqualTo("客厅体脂秤")
        assertThat(MEMBER_LINK_SAVE).isEqualTo("保存关联")
    }

    @Test
    fun `说明横幅分两行且语义完整`() {
        assertThat(MEMBER_DEVICE_NOTE).isEqualTo("一台体脂秤可关联多位成员，\n测量时会自动识别当前成员。")
        assertThat(MEMBER_DEVICE_NOTE).contains("一台体脂秤可关联多位成员")
        assertThat(MEMBER_DEVICE_NOTE).contains("测量时会自动识别当前成员")
    }

    @Test
    fun `成员数据初始字与默认使用者标记`() {
        assertThat(DEFAULT_LINKABLE_MEMBERS.map { it.initial }).containsExactly("悦", "妈", "爸").inOrder()
        assertThat(DEFAULT_LINKABLE_MEMBERS.filter { it.defaultUser }.map { it.name }).containsExactly("林悦")
    }
}
