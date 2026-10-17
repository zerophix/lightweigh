package com.xcwk.scalehub.feature.member

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.Member
import org.junit.Test

/**
 * 成员管理行为（刀B，移植补齐）：
 * 切换触发刷新广播、添加即切换且满 8 拦截、删除至少留一与联动清理、同成员切换无操作。
 */
class MembersViewModelTest {

    private class FakeOps {
        val list = mutableListOf(Member("1", 1, "我"))
        var activeId = "1"
        var switches = mutableListOf<String>()
        var removedLocal = mutableListOf<String>()

        fun vm(): MembersViewModel = MembersViewModel(
            memberList = { list.toList() },
            activeMemberId = { activeId },
            switchMember = { id -> switches += id; activeId = id },
            addMember = { name ->
                if (list.size >= Member.MAX) {
                    false
                } else {
                    val m = Member(Member.nextId(list), Member.nextSlot(list), Member.normalizeNickname(name, list.size + 1))
                    list += m
                    activeId = m.id
                    true
                }
            },
            removeMember = { id ->
                if (list.size <= 1) {
                    false
                } else {
                    list.removeAll { it.id == id }
                    removedLocal += id
                    if (activeId == id) activeId = list.first().id
                    true
                }
            },
            memberAvatarPath = { "" },
        )
    }

    @Test
    fun `switch changes active and records`() {
        val ops = FakeOps()
        ops.list += Member("2", 2, "家人")
        val vm = ops.vm()
        vm.onIntent(MembersIntent.Switch("2"))
        assertThat(ops.switches).containsExactly("2")
        assertThat(vm.state.value.activeId).isEqualTo("2")
        assertThat(vm.state.value.message).isEqualTo("已切换测量用户")
    }

    @Test
    fun `switching same member is no-op`() {
        val ops = FakeOps()
        val vm = ops.vm()
        vm.onIntent(MembersIntent.Switch("1"))
        assertThat(ops.switches).isEmpty()
    }

    @Test
    fun `add creates and becomes active with cap`() {
        val ops = FakeOps()
        val vm = ops.vm()
        vm.onIntent(MembersIntent.AskAdd)
        vm.onIntent(MembersIntent.AddNameChanged("小明"))
        vm.onIntent(MembersIntent.ConfirmAdd)
        var s = vm.state.value
        assertThat(s.members).hasSize(2)
        assertThat(s.activeId).isEqualTo("2")
        assertThat(s.message).isEqualTo("已添加并切换")

        repeat(6) {   // 凑满 8 人
            vm.onIntent(MembersIntent.AskAdd)
            vm.onIntent(MembersIntent.ConfirmAdd)
        }
        s = vm.state.value
        assertThat(s.members).hasSize(8)
        assertThat(s.maxReached).isTrue()
        vm.onIntent(MembersIntent.AskAdd)
        vm.onIntent(MembersIntent.ConfirmAdd)
        assertThat(vm.state.value.members).hasSize(8)
        assertThat(vm.state.value.message).isEqualTo("最多 8 个测量用户")
    }

    @Test
    fun `remove keeps at least one and cleans local data`() {
        val ops = FakeOps()
        ops.list += Member("2", 2, "家人")
        val vm = ops.vm()
        vm.onIntent(MembersIntent.AskDelete(Member("1", 1, "我")))
        assertThat(vm.state.value.pendingDelete?.id).isEqualTo("1")
        vm.onIntent(MembersIntent.ConfirmDelete)
        val s = vm.state.value
        assertThat(s.members).hasSize(1)
        assertThat(s.activeId).isEqualTo("2")            // 删的是当前→切到第一个
        assertThat(s.pendingDelete).isNull()
        assertThat(ops.removedLocal).containsExactly("1")

        // 最后一人不可删
        vm.onIntent(MembersIntent.AskDelete(Member("2", 2, "家人")))
        vm.onIntent(MembersIntent.ConfirmDelete)
        assertThat(vm.state.value.members).hasSize(1)
    }
}
