package com.xcwk.scalehub.feature.member

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * T-088 家庭邀请六屏（呈现式组件）纯 JVM 单测。
 *
 * 覆盖验收中唯一可自动判定的三类事实：
 * 1. 文案逐字取自原型23 六个 view（AC-J1-15 then 原文，改一个字即红）；
 * 2. 邀请码 24 小时倒计时与权限勾选为纯函数——倒计时归零后邀请码不可用，
 *    只能「重新生成邀请码」；撤销全部共享经二次确认状态门控；
 * 3. 源码守卫：目标文件不接触剪贴板/系统分享/网络/数据库 API、无硬编码颜色与裸 dp。
 *
 * 视觉呈现（排版/配色落位）不在此测——:app 无 compose ui-test 基建，
 * 属已声明盲区；视觉由原型对照与人工验收关闭。
 */
class FamilyInviteTest {

    // ---- 1. 文案逐字（AC-J1-15 then 第 1..6 条）----

    @Test
    fun `v-invite 文案逐字取自原型`() {
        assertThat(FamilyInviteText.NavInvite).isEqualTo("邀请家人")
        assertThat(FamilyInviteText.InviteTitle).isEqualTo("邀请家人加入")
        assertThat(FamilyInviteText.InviteSubtitle).isEqualTo("生成邀请码，分享给家人")
        assertThat(FamilyInviteText.CodeHint).isEqualTo("邀请码（24 小时内有效）")
        assertThat(FamilyInviteText.SampleCode).isEqualTo("QH8K2M")
        assertThat(FamilyInviteText.Copy).isEqualTo("复制")
        assertThat(FamilyInviteText.Share).isEqualTo("分享给家人")
        assertThat(FamilyInviteText.InvitePermissionSection).isEqualTo("共享权限设置")
        assertThat(FamilyInviteText.InvitePermissionRecords).isEqualTo("允许查看测量记录")
        assertThat(FamilyInviteText.InvitePermissionReport).isEqualTo("允许查看身体报告")
        assertThat(FamilyInviteText.InvitePermissionAi).isEqualTo("允许查看 AI 解读")
        assertThat(FamilyInviteText.CodeNoticeLine1).isEqualTo("邀请码仅用于身份验证，")
        assertThat(FamilyInviteText.CodeNoticeLine2).isEqualTo("不包含任何健康数据。")
        assertThat(FamilyInviteText.SendInvite).isEqualTo("发送邀请")
    }

    @Test
    fun `v-invite-done 文案逐字取自原型`() {
        assertThat(FamilyInviteText.NavInviteDone).isEqualTo("邀请已发送")
        assertThat(FamilyInviteText.SentTitle).isEqualTo("邀请已发送")
        assertThat(FamilyInviteText.SentSubtitle).isEqualTo("等待家人接受邀请")
        assertThat(FamilyInviteText.PendingInvite).isEqualTo("待接受邀请")
        assertThat(FamilyInviteText.PendingTag).isEqualTo("待接受")
        assertThat(FamilyInviteText.NoticeManageLine).isEqualTo("管理他的查看权限。")
        assertThat(FamilyInviteText.ReturnToFamilyShare).isEqualTo("返回家庭共享")
        assertThat(FamilyInviteText.RegenerateCode).isEqualTo("重新生成邀请码")
        // 组合文案逐字：「邀请码 QH8K2M」/「24 小时后过期」/「家人接受后，你可以在「家庭共享」中」
        assertThat(inviteCodeRowText(FamilyInviteText.SampleCode)).isEqualTo("邀请码 QH8K2M")
        assertThat(inviteExpiryText(FAMILY_INVITE_CODE_TTL_MILLIS)).isEqualTo("24 小时后过期")
        assertThat(noticeEntryLine(FamilyInviteText.FamilyShareEntry))
            .isEqualTo("家人接受后，你可以在「家庭共享」中")
    }

    @Test
    fun `v-accept 文案逐字取自原型`() {
        assertThat(FamilyInviteText.NavAccept).isEqualTo("接受邀请")
        assertThat(FamilyInviteText.AcceptTitle).isEqualTo("输入邀请码")
        assertThat(FamilyInviteText.AcceptSubtitle).isEqualTo("向家人索取邀请码后输入")
        assertThat(FamilyInviteText.CodeInputLabel).isEqualTo("邀请码")
        assertThat(FamilyInviteText.CodePlaceholder).isEqualTo("6 位邀请码")
        assertThat(FamilyInviteText.AcceptNoticeLine1).isEqualTo("接受邀请后，对方可以查看你授权的数据。")
        assertThat(FamilyInviteText.AcceptNoticeLine2).isEqualTo("你可以随时在设置中撤销。")
        assertThat(FamilyInviteText.AcceptAction).isEqualTo("接受邀请")
    }

    @Test
    fun `v-accept-confirm 文案逐字取自原型`() {
        assertThat(FamilyInviteText.NavJoinConfirm).isEqualTo("确认加入")
        assertThat(FamilyInviteText.InviterName).isEqualTo("林悦")
        assertThat(FamilyInviteText.FamilyName).isEqualTo("林悦的家庭")
        assertThat(FamilyInviteText.WillViewSection).isEqualTo("对方将可以查看")
        assertThat(FamilyInviteText.WillViewRecords).isEqualTo("你的测量记录")
        assertThat(FamilyInviteText.WillViewReport).isEqualTo("你的身体报告")
        assertThat(FamilyInviteText.WillViewAiUnauthorized).isEqualTo("AI 解读（未授权）")
        assertThat(FamilyInviteText.JoinNoticeLine1).isEqualTo("接受后对方可以查看你的健康数据，")
        assertThat(FamilyInviteText.JoinNoticeLine2).isEqualTo("你可以随时在设置中撤销共享。")
        assertThat(FamilyInviteText.ConfirmJoin).isEqualTo("确认加入")
        assertThat(FamilyInviteText.Cancel).isEqualTo("取消")
        // 组合文案逐字：「林悦 邀请你加入」/「「林悦的家庭」」
        assertThat(joinInviteTitle(FamilyInviteText.InviterName)).isEqualTo("林悦 邀请你加入")
        assertThat(familyQuoteLine(FamilyInviteText.FamilyName)).isEqualTo("「林悦的家庭」")
    }

    @Test
    fun `v-accept-done 文案逐字取自原型`() {
        assertThat(FamilyInviteText.NavJoined).isEqualTo("已加入")
        assertThat(FamilyInviteText.JoinedSection).isEqualTo("你现在可以")
        assertThat(FamilyInviteText.JoinedNoticeLine1).isEqualTo("你的数据仍然独立保存，")
        assertThat(FamilyInviteText.JoinedNoticeLine2).isEqualTo("不会自动共享给对方。")
        assertThat(FamilyInviteText.EnterFamilyShare).isEqualTo("进入家庭共享")
        // 组合文案逐字：「已加入「林悦的家庭」」/「现在可以查看林悦的健康数据」/ 三项清单
        assertThat(joinedTitleText(FamilyInviteText.FamilyName)).isEqualTo("已加入「林悦的家庭」")
        assertThat(joinedSubtitleText(FamilyInviteText.InviterName)).isEqualTo("现在可以查看林悦的健康数据")
        assertThat(joinedItemText(FamilyInviteText.InviterName, FamilyJoinedItem.Records))
            .isEqualTo("查看林悦的测量记录")
        assertThat(joinedItemText(FamilyInviteText.InviterName, FamilyJoinedItem.Report))
            .isEqualTo("查看林悦的身体报告")
        assertThat(joinedItemText(FamilyInviteText.InviterName, FamilyJoinedItem.Trend))
            .isEqualTo("查看林悦的趋势图")
    }

    @Test
    fun `v-revoke 文案逐字取自原型`() {
        assertThat(FamilyInviteText.NavRevoke).isEqualTo("撤销共享")
        assertThat(FamilyInviteText.RevokeMemberGlyph).isEqualTo("妈")
        assertThat(FamilyInviteText.RevokeMemberName).isEqualTo("妈妈")
        assertThat(FamilyInviteText.CurrentViewLine).isEqualTo("当前可查看：测量记录 + 身体报告")
        assertThat(FamilyInviteText.RevokePermissionSection).isEqualTo("调整查看权限")
        assertThat(FamilyInviteText.RevokePermissionRecords).isEqualTo("查看测量记录")
        assertThat(FamilyInviteText.RevokePermissionReport).isEqualTo("查看身体报告")
        assertThat(FamilyInviteText.RevokePermissionAi).isEqualTo("查看 AI 解读")
        assertThat(FamilyInviteText.RevokeNoticeLine1).isEqualTo("关闭全部权限后，")
        assertThat(FamilyInviteText.SavePermissions).isEqualTo("保存权限")
        assertThat(FamilyInviteText.RevokeAll).isEqualTo("撤销全部共享")
        // 二次确认弹层逐字
        assertThat(FamilyInviteText.RevokeWarnTitle).isEqualTo("撤销全部共享？")
        assertThat(FamilyInviteText.RevokeDialogLine2).isEqualTo("此操作可重新邀请恢复。")
        assertThat(FamilyInviteText.ConfirmRevokeAll).isEqualTo("确认撤销")
        // 组合文案逐字：「妈妈将无法再查看你的数据。」/「妈妈将立即无法查看你的任何健康数据。」
        assertThat(revokeWarnLine2(FamilyInviteText.RevokeMemberName)).isEqualTo("妈妈将无法再查看你的数据。")
        assertThat(revokeDialogLine1(FamilyInviteText.RevokeMemberName))
            .isEqualTo("妈妈将立即无法查看你的任何健康数据。")
    }

    // ---- 2. 纯函数（AC-J1-15 then 第 7 条 + DoD：有效期/勾选/撤销可用性）----

    @Test
    fun `邀请码倒计时剩余时可用`() {
        assertThat(inviteCodeUsable(FAMILY_INVITE_CODE_TTL_MILLIS)).isTrue()
        assertThat(inviteCodeUsable(1L)).isTrue()
    }

    @Test
    fun `邀请码倒计时归零后不可用且只能重新生成`() {
        assertThat(inviteCodeUsable(0L)).isFalse()
        assertThat(inviteCodeUsable(-1L)).isFalse()
        assertThat(FamilyInviteUiState(remainingMillis = 0L).codeUsable).isFalse()
        assertThat(FamilyInviteUiState().codeUsable).isTrue()
        // 归零后页面唯一可用出口是「重新生成邀请码」（文案仍在，主按钮随 codeUsable 禁用）
        assertThat(FamilyInviteText.RegenerateCode).isEqualTo("重新生成邀请码")
    }

    @Test
    fun `原型默认勾选为测量记录与身体报告开 AI 解读关`() {
        assertThat(defaultFamilyPermissions())
            .containsExactly(FamilyPermission.Records, FamilyPermission.Report)
    }

    @Test
    fun `权限勾选切换为纯函数`() {
        val on = toggleFamilyPermission(defaultFamilyPermissions(), FamilyPermission.Ai)
        assertThat(on).containsExactly(FamilyPermission.Records, FamilyPermission.Report, FamilyPermission.Ai)
        val off = toggleFamilyPermission(on, FamilyPermission.Records)
        assertThat(off).containsExactly(FamilyPermission.Report, FamilyPermission.Ai)
        // 原入参不可变（纯函数不原地改集合）
        assertThat(defaultFamilyPermissions()).containsExactly(FamilyPermission.Records, FamilyPermission.Report)
    }

    @Test
    fun `撤销全部共享仅在仍有可查看权限时可用`() {
        assertThat(revokeAllAvailable(defaultFamilyPermissions())).isTrue()
        assertThat(revokeAllAvailable(emptySet())).isFalse()
        assertThat(
            revokeAllAvailable(
                toggleFamilyPermission(
                    toggleFamilyPermission(defaultFamilyPermissions(), FamilyPermission.Records),
                    FamilyPermission.Report,
                ),
            ),
        ).isFalse()
    }

    // ---- 3. 源码守卫（AC-J1-15 forbidden：直连系统 API / 硬编码颜色间距 / 撤销缺二次确认）----

    private fun sourceOf(fileName: String): String {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val f = File(dir, "app/src/main/kotlin/com/xcwk/scalehub/feature/member/$fileName")
            if (f.exists()) return f.readText(Charsets.UTF_8)
            dir = dir.parentFile
        }
        error("$fileName 未找到（从 ${System.getProperty("user.dir")} 向上探测失败）")
    }

    /** 去掉注释后再断言：文档里允许出现约束原文，代码本身不许出现违规符号。 */
    private fun codeOnly(source: String): String =
        source
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?m)//.*$"""), "")

    private val target = "FamilyInvite.kt"

    @Test
    fun `目标文件不接触剪贴板系统分享网络或数据库`() {
        val code = codeOnly(sourceOf(target))
        assertThat(code).doesNotContain("Clipboard")
        assertThat(code).doesNotContain("ClipData")
        assertThat(code).doesNotContain("startActivity")
        assertThat(code).doesNotContain("android.content")
        assertThat(code).doesNotContain("Intent")
        assertThat(code).doesNotContain("java.net")
        assertThat(code).doesNotContain("okhttp")
        assertThat(code).doesNotContain("retrofit")
        assertThat(code).doesNotContain("Room")
        assertThat(code).doesNotContain("SQLite")
    }

    @Test
    fun `目标文件无硬编码颜色与裸 dp`() {
        val code = codeOnly(sourceOf(target))
        assertThat(code).doesNotMatch(Pattern.compile("""Color\(0x[0-9A-Fa-f]{6,8}\)"""))
        // 与 gov-prototype-verify 的 HARDCODED_DP 同口径：Qk token 之外不许写 NN.dp
        assertThat(code).doesNotMatch(Pattern.compile("""(?<!Qk)\b\d+\.dp\b"""))
    }

    @Test
    fun `撤销执行被二次确认状态门控`() {
        val code = codeOnly(sourceOf(target))
        // 确认撤销只在弹层内回调：没有 showRevokeConfirm 门控就不许出现确认按钮
        assertThat(code).contains("if (state.showRevokeConfirm)")
    }

    @Test
    fun `目标文件复用 Qk 设计系统组件`() {
        val source = sourceOf(target)
        assertThat(source).contains("QkCard")
        assertThat(source).contains("QkButton")
        assertThat(source).contains("QkColor")
        assertThat(source).contains("QkSpacing")
    }
}
