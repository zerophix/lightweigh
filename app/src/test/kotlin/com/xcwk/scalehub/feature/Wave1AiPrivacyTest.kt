package com.xcwk.scalehub.feature

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.feature.ai.MemberOverview
import com.xcwk.scalehub.feature.ai.MemberPermissionId
import com.xcwk.scalehub.feature.ai.WeeklySummaryInput
import com.xcwk.scalehub.feature.ai.WeeklyView
import com.xcwk.scalehub.feature.ai.averageDailyChange
import com.xcwk.scalehub.feature.ai.deviceSubtitle
import com.xcwk.scalehub.feature.ai.formatSignedChange
import com.xcwk.scalehub.feature.ai.goalProgressPercent
import com.xcwk.scalehub.feature.ai.memberIdentityLine
import com.xcwk.scalehub.feature.ai.measuredDaysLabel
import com.xcwk.scalehub.feature.ai.presentOverview
import com.xcwk.scalehub.feature.ai.prototypeMember
import com.xcwk.scalehub.feature.ai.prototypePermissions
import com.xcwk.scalehub.feature.ai.prototypeWeeklyInput
import com.xcwk.scalehub.feature.ai.resolveWeekly
import com.xcwk.scalehub.feature.ai.togglePermission
import com.xcwk.scalehub.feature.ai.weeklyHeadline
import com.xcwk.scalehub.feature.privacy.PrivacyConsent
import com.xcwk.scalehub.feature.privacy.PrivacyEvent
import com.xcwk.scalehub.feature.privacy.PrivacyFlowState
import com.xcwk.scalehub.feature.privacy.PrivacyScreen
import com.xcwk.scalehub.feature.privacy.consultShowsGenericOnly
import com.xcwk.scalehub.feature.privacy.deletionRules
import com.xcwk.scalehub.feature.privacy.reducePrivacy
import org.junit.Test

/**
 * Wave 1（T-084 周总结与成员权限 / T-096 隐私授权五态）纯逻辑单测。
 *
 * 空周不产出读数、计划天数为 0 与目标跨度为 0 不除零、授权只通过 reduce 的 persist
 * 标记交给调用方。视觉走查不在此列。
 */
class Wave1AiPrivacyTest {

    @Test
    fun `周数据缺失时为空态且不含样例数字`() {
        val empty = resolveWeekly(
            WeeklySummaryInput(
                memberName = "林悦",
                rangeLabel = "2026年10月14日 — 10月20日",
                startWeightKg = null,
                endWeightKg = null,
                startFatPercent = null,
                endFatPercent = null,
                measuredDays = 0,
                plannedDays = 7,
            ),
        )
        assertThat(empty).isEqualTo(WeeklyView.Empty)
        assertThat(empty.toString()).doesNotContain("62.4")
        assertThat(empty.toString()).doesNotContain("0.4")
    }

    @Test
    fun `非有限读数同样走空态`() {
        val view = resolveWeekly(prototypeWeeklyInput().copy(endWeightKg = Double.NaN))
        assertThat(view).isEqualTo(WeeklyView.Empty)
    }

    @Test
    fun `原型周总结文案与读数`() {
        val view = resolveWeekly(prototypeWeeklyInput())
        assertThat(view).isInstanceOf(WeeklyView.Ready::class.java)
        val ready = view as WeeklyView.Ready
        assertThat(ready.memberLine).isEqualTo("林悦 · 2026年10月14日 — 10月20日")
        assertThat(ready.headline).isEqualTo("本周体重下降 0.4 kg，趋势稳健，继续保持。")
        assertThat(ready.weightChange).isEqualTo("−0.4")
        assertThat(ready.fatChange).isEqualTo("−0.3")
        assertThat(ready.measuredLabel).isEqualTo("5/7")
        assertThat(ready.narrative).isEqualTo(
            "本周体重从 62.8 kg 下降到 62.4 kg，体脂率从 28.9% 降到 28.6%。两项指标均呈下降趋势，方向与你的减重目标一致。",
        )
    }

    @Test
    fun `体重上升时不写成下降`() {
        assertThat(weeklyHeadline(0.4)).isEqualTo("本周体重上升 0.4 kg。")
        assertThat(formatSignedChange(0.4)).isEqualTo("+0.4")
    }

    @Test
    fun `测量天数为零不除零`() {
        assertThat(averageDailyChange(-0.4, 0)).isNull()
        assertThat(averageDailyChange(-0.4, -1)).isNull()
        assertThat(measuredDaysLabel(5, 0)).isEqualTo("5")
        assertThat(averageDailyChange(-0.4, 4)).isWithin(1e-9).of(-0.1)
    }

    @Test
    fun `身份行与三项权限说明`() {
        assertThat(memberIdentityLine("本人", "女", 30, 168)).isEqualTo("本人 · 女 · 30岁 · 168cm")
        val titles = prototypePermissions().map { it.title }
        assertThat(titles).containsAtLeast("查看测量记录", "编辑测量记录", "删除测量记录")
        val delete = prototypePermissions().first { it.id == MemberPermissionId.DeleteRecords }
        assertThat(delete.description).isEqualTo("允许删除该成员的记录")
        assertThat(delete.enabled).isFalse()
    }

    @Test
    fun `切换权限不改原列表`() {
        val original = prototypePermissions()
        val next = togglePermission(original, MemberPermissionId.DeleteRecords, true)
        assertThat(original.first { it.id == MemberPermissionId.DeleteRecords }.enabled).isFalse()
        assertThat(next.first { it.id == MemberPermissionId.DeleteRecords }.enabled).isTrue()
    }

    @Test
    fun `目标跨度为零时不计算进度`() {
        assertThat(goalProgressPercent(58.0, 58.0, 58.0)).isNull()
        assertThat(presentOverview(MemberOverview(1, Double.NaN, 60.0, 58.0))).isNull()
    }

    @Test
    fun `原型成员概览与设备文案`() {
        val member = prototypeMember()
        val overview = presentOverview(member.overview!!)
        assertThat(overview).isNotNull()
        assertThat(overview!!.lostLabel).isEqualTo("已减 2.8 kg")
        assertThat(overview.goalLabel).isEqualTo("目标 58.0 kg · 39%")
        assertThat(overview.progressPercent).isEqualTo(39)
        assertThat(deviceSubtitle(member.device!!)).isEqualTo("● 已连接 · 电量 82%")
    }

    @Test
    fun `同意授权才要求持久化且带上云同步选择`() {
        val step = reducePrivacy(
            PrivacyFlowState(),
            PrivacyEvent.AgreeAndContinue(
                shareBodyComposition = true,
                shareHealthProfile = true,
                cloudSync = true,
            ),
        )
        assertThat(step.persist).isTrue()
        assertThat(step.state.consent).isEqualTo(PrivacyConsent.Granted)
        assertThat(step.state.cloudSync).isTrue()
        assertThat(step.state.screen).isEqualTo(PrivacyScreen.Granted)
    }

    @Test
    fun `仅本机关闭云同步但仍是已授权`() {
        val step = reducePrivacy(
            PrivacyFlowState(cloudSync = true),
            PrivacyEvent.LocalOnly,
        )
        assertThat(step.persist).isTrue()
        assertThat(step.state.consent).isEqualTo(PrivacyConsent.Granted)
        assertThat(step.state.cloudSync).isFalse()
    }

    @Test
    fun `预览撤回不改授权也不持久化`() {
        val granted = PrivacyFlowState(screen = PrivacyScreen.Granted, consent = PrivacyConsent.Granted)
        val step = reducePrivacy(granted, PrivacyEvent.PreviewWithdraw)
        assertThat(step.persist).isFalse()
        assertThat(step.state.consent).isEqualTo(PrivacyConsent.Granted)
        assertThat(step.state.screen).isEqualTo(PrivacyScreen.Unauth)
    }

    @Test
    fun `确认撤回才持久化未授权`() {
        val opened = reducePrivacy(
            PrivacyFlowState(consent = PrivacyConsent.Granted),
            PrivacyEvent.OpenWithdrawSheet,
        )
        assertThat(opened.persist).isFalse()
        assertThat(opened.state.withdrawSheetVisible).isTrue()
        val kept = reducePrivacy(opened.state, PrivacyEvent.KeepEnabled)
        assertThat(kept.persist).isFalse()
        assertThat(kept.state.consent).isEqualTo(PrivacyConsent.Granted)
        val withdrawn = reducePrivacy(opened.state, PrivacyEvent.ConfirmWithdraw)
        assertThat(withdrawn.persist).isTrue()
        assertThat(withdrawn.state.consent).isEqualTo(PrivacyConsent.Withdrawn)
        assertThat(withdrawn.state.cloudSync).isFalse()
        assertThat(withdrawn.state.screen).isEqualTo(PrivacyScreen.Unauth)
    }

    @Test
    fun `重新开启只回到授权页不自动同意`() {
        val step = reducePrivacy(
            PrivacyFlowState(screen = PrivacyScreen.Unauth, consent = PrivacyConsent.Withdrawn),
            PrivacyEvent.Reenable,
        )
        assertThat(step.persist).isFalse()
        assertThat(step.state.screen).isEqualTo(PrivacyScreen.Auth)
        assertThat(step.state.consent).isEqualTo(PrivacyConsent.Withdrawn)
    }

    @Test
    fun `未授权咨询提示通用知识已授权则不提示`() {
        assertThat(consultShowsGenericOnly(PrivacyConsent.Withdrawn)).isTrue()
        assertThat(consultShowsGenericOnly(PrivacyConsent.None)).isTrue()
        assertThat(consultShowsGenericOnly(PrivacyConsent.Granted)).isFalse()
    }

    @Test
    fun `删除规则恰好四类且注销不可恢复`() {
        val rules = deletionRules()
        assertThat(rules).hasSize(4)
        assertThat(rules.map { it.title }).containsExactly(
            "删除单条测量记录",
            "删除单段 AI 对话",
            "清空全部 AI 对话",
            "注销账号",
        ).inOrder()
        assertThat(rules.last().destructive).isTrue()
        assertThat(rules.last().effect).contains("不可恢复")
    }

    @Test
    fun `授权页返回离开流程其余返回只切屏`() {
        val leave = reducePrivacy(PrivacyFlowState(), PrivacyEvent.Back)
        assertThat(leave.leave).isTrue()
        assertThat(leave.persist).isFalse()
        val fromUnauth = reducePrivacy(
            PrivacyFlowState(screen = PrivacyScreen.Unauth),
            PrivacyEvent.Back,
        )
        assertThat(fromUnauth.leave).isFalse()
        assertThat(fromUnauth.state.screen).isEqualTo(PrivacyScreen.Consult)
    }
}
