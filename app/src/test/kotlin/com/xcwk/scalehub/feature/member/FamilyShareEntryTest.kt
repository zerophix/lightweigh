package com.xcwk.scalehub.feature.member

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

/**
 * 成员页「家庭邀请」入口 —— 三入口登记 + 六屏之间的进入/返回路径。
 *
 * 钉的是「从成员页点得进、走完一步能回来、撤销必须二次确认」：
 * T-088 六屏此前零调用方，入口没接上时只有手点才能发现。
 */
class FamilyShareEntryTest {

    private fun opened(page: FamilySharePage) = familyShareReduce(FamilyShareState(), FamilyShareEvent.Open(page))

    @Test
    fun `三个入口文案都非空且指向不同界面`() {
        assertThat(FamilyShareEntry.INVITE_TITLE).isEqualTo("邀请家人加入")
        assertThat(FamilyShareEntry.ACCEPT_TITLE).isEqualTo("接受邀请")
        assertThat(FamilyShareEntry.REVOKE_TITLE).isEqualTo("管理共享权限")
        assertThat(
            setOf(FamilyShareEntry.ID_INVITE, FamilyShareEntry.ID_ACCEPT, FamilyShareEntry.ID_REVOKE),
        ).hasSize(3)
    }

    @Test
    fun `从成员页点邀请进邀请页`() {
        assertThat(opened(FamilySharePage.Invite).page).isEqualTo(FamilySharePage.Invite)
    }

    @Test
    fun `发送邀请进已发送页并可重新生成`() {
        val done = familyShareReduce(opened(FamilySharePage.Invite), FamilyShareEvent.SendInvite)
        assertThat(done.page).isEqualTo(FamilySharePage.InviteDone)

        val regenerated = familyShareReduce(done, FamilyShareEvent.Regenerate("AB23CD"))
        assertThat(regenerated.page).isEqualTo(FamilySharePage.Invite)
        assertThat(regenerated.code).isEqualTo("AB23CD")
        assertThat(regenerated.remainingMillis).isEqualTo(FAMILY_INVITE_CODE_TTL_MILLIS)
    }

    @Test
    fun `接受邀请要求 6 位码`() {
        val accept = opened(FamilySharePage.Accept)
        val short = accept.copy(acceptInput = "AB23C")
        assertThat(familyShareReduce(short, FamilyShareEvent.AcceptInvite).page)
            .isEqualTo(FamilySharePage.Accept)

        val full = accept.copy(acceptInput = "AB23CD")
        assertThat(familyShareReduce(full, FamilyShareEvent.AcceptInvite).page)
            .isEqualTo(FamilySharePage.AcceptConfirm)
    }

    @Test
    fun `输入超过 6 位被截断`() {
        val typed = familyShareReduce(opened(FamilySharePage.Accept), FamilyShareEvent.CodeChanged("AB23CDEF"))

        assertThat(typed.acceptInput).isEqualTo("AB23CD")
    }

    @Test
    fun `确认加入进已加入页且返回回成员页`() {
        val confirm = opened(FamilySharePage.AcceptConfirm)
        val joined = familyShareReduce(confirm, FamilyShareEvent.ConfirmJoin)
        assertThat(joined.page).isEqualTo(FamilySharePage.Joined)

        assertThat(familyShareReduce(joined, FamilyShareEvent.Close).page).isNull()
    }

    @Test
    fun `撤销全部共享必须二次确认`() {
        val revoke = opened(FamilySharePage.Revoke)
        val asking = familyShareReduce(revoke, FamilyShareEvent.RequestRevokeAll)

        assertThat(asking.showRevokeConfirm).isTrue()
        assertThat(asking.permissions).isNotEmpty()

        val done = familyShareReduce(asking, FamilyShareEvent.ConfirmRevokeAll)
        assertThat(done.showRevokeConfirm).isFalse()
        assertThat(done.permissions).isEmpty()
        assertThat(done.page).isNull()
    }

    @Test
    fun `取消撤销只关弹层不改权限`() {
        val asking = familyShareReduce(opened(FamilySharePage.Revoke), FamilyShareEvent.RequestRevokeAll)
        val kept = familyShareReduce(asking, FamilyShareEvent.CancelRevoke)

        assertThat(kept.showRevokeConfirm).isFalse()
        assertThat(kept.permissions).isEqualTo(asking.permissions)
        assertThat(kept.page).isEqualTo(FamilySharePage.Revoke)
    }

    @Test
    fun `邀请页与撤销页共用同一组权限`() {
        val toggled = familyShareReduce(opened(FamilySharePage.Invite), FamilyShareEvent.TogglePermission(FamilyPermission.Ai))

        assertThat(toggled.permissions).contains(FamilyPermission.Ai)
        assertThat(toggled.revokeState.permissions).contains(FamilyPermission.Ai)
    }

    @Test
    fun `生成的邀请码是 6 位且只用约定字符集`() {
        val code = newFamilyInviteCode(Random(7))

        assertThat(code).hasLength(FAMILY_INVITE_CODE_LENGTH)
        assertThat(code.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" }).isTrue()
    }
}
