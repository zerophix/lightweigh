package com.xcwk.scalehub.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 成员槽位与命名纯函数（旧库 MemberStore :80-84 语义）。 */
class MemberOpsTest {

    private fun m(id: String, slot: Int) = Member(id, slot, "用户")

    @Test
    fun `next id is max numeric plus one`() {
        assertThat(Member.nextId(listOf(m("1", 1)))).isEqualTo("2")
        assertThat(Member.nextId(listOf(m("1", 1), m("3", 2)))).isEqualTo("4")
        assertThat(Member.nextId(emptyList())).isEqualTo("1")
    }

    @Test
    fun `next slot is first free in one to max`() {
        assertThat(Member.nextSlot(listOf(m("1", 1)))).isEqualTo(2)
        assertThat(Member.nextSlot(listOf(m("1", 1), m("2", 2), m("3", 3)))).isEqualTo(4)
        assertThat(Member.nextSlot(listOf(m("1", 2), m("2", 1)))).isEqualTo(3)
    }

    @Test
    fun `nickname trims to twenty chars and falls back`() {
        assertThat(Member.normalizeNickname("  阿杰  ", 2)).isEqualTo("阿杰")
        assertThat(Member.normalizeNickname("名".repeat(30), 2)).isEqualTo("名".repeat(20))
        assertThat(Member.normalizeNickname("", 3)).isEqualTo("用户3")
        assertThat(Member.normalizeNickname("   ", 3)).isEqualTo("用户3")
    }

    @Test
    fun `max members is eight`() {
        assertThat(Member.MAX).isEqualTo(8)
    }
}
