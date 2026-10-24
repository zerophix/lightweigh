package com.xcwk.scalehub.feature.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-085 AI 边界六屏纯逻辑单测（原型 `25-ai-boundaries.html`）。
 *
 * 覆盖验收中**唯一可自动判定**的两部分：
 * 1. 文案逐字对齐原型六个 view（AC-J1-12 的六条 then）；
 * 2. 可判定逻辑：
 *    - 事件 → 屏幕分发（[resolveAiBoundaryStage] / [aiBoundaryReduce]）
 *    - 报告过期 / 重新分析的状态机（[AiReanalysisPhase] 三态）
 *    - 结论可展示判定（[canShowPersonalConclusion]）
 *
 * 视觉布局不在此测（需真机/截图）；状态机写错了没人会发现，故钉死。
 */
class AiBoundariesTest {

    private fun state(
        availability: AiServiceAvailability = AiServiceAvailability.Available,
        scope: AiDataScope = AiDataScope.Personalized,
        freshness: AiDataFreshness = AiDataFreshness.Fresh,
        reanalysis: AiReanalysisPhase = AiReanalysisPhase.Idle,
    ) = aiBoundaryStateOf(availability, scope, freshness, reanalysis)

    // ---- 一、边界分发（可用性 → 授权范围 → 数据新鲜度） ----

    @Test
    fun `全部健康时无边界由主报告页接管`() {
        assertThat(state().stage).isNull()
    }

    @Test
    fun `服务不可用优先于其他维度`() {
        val next = state(
            availability = AiServiceAvailability.Unavailable,
            scope = AiDataScope.MemberUnauthorized,
            freshness = AiDataFreshness.Stale,
        )
        assertThat(next.stage).isEqualTo(AiBoundaryStage.Unavailable)
    }

    @Test
    fun `未授权成员的判定先于数据新鲜度`() {
        // 若把新鲜度排在授权之前，会给未授权成员渲染出体重结论 —— 验收 forbidden 禁止
        val next = state(scope = AiDataScope.MemberUnauthorized, freshness = AiDataFreshness.Stale)
        assertThat(next.stage).isEqualTo(AiBoundaryStage.MemberUnauth)
    }

    @Test
    fun `未开启个性化进入通用咨询页`() {
        assertThat(state(scope = AiDataScope.GenericOnly).stage).isEqualTo(AiBoundaryStage.UnauthConsult)
    }

    @Test
    fun `数据过期进入旧报告页`() {
        assertThat(state(freshness = AiDataFreshness.Stale).stage).isEqualTo(AiBoundaryStage.ReportExpired)
    }

    @Test
    fun `重新分析中进入分析页`() {
        val next = state(freshness = AiDataFreshness.Stale, reanalysis = AiReanalysisPhase.Running)
        assertThat(next.stage).isEqualTo(AiBoundaryStage.Reanalyzing)
    }

    @Test
    fun `分析完成后进入新报告页`() {
        val next = state(reanalysis = AiReanalysisPhase.Done)
        assertThat(next.stage).isEqualTo(AiBoundaryStage.ReportDone)
    }

    // ---- 二、结论可展示判定 ----

    @Test
    fun `只有已授权且服务可用才可展示个人结论`() {
        assertThat(canShowPersonalConclusion(AiServiceAvailability.Available, AiDataScope.Personalized)).isTrue()

        assertThat(canShowPersonalConclusion(AiServiceAvailability.Unavailable, AiDataScope.Personalized)).isFalse()
        assertThat(canShowPersonalConclusion(AiServiceAvailability.Available, AiDataScope.GenericOnly)).isFalse()
        assertThat(canShowPersonalConclusion(AiServiceAvailability.Available, AiDataScope.MemberUnauthorized))
            .isFalse()
    }

    @Test
    fun `不可用与未授权状态下状态判定同样禁止结论`() {
        assertThat(canShowPersonalConclusion(state(availability = AiServiceAvailability.Unavailable))).isFalse()
        assertThat(canShowPersonalConclusion(state(scope = AiDataScope.MemberUnauthorized))).isFalse()
        assertThat(canShowPersonalConclusion(state(scope = AiDataScope.GenericOnly))).isFalse()
        assertThat(canShowPersonalConclusion(state())).isTrue()
    }

    // ---- 三、报告过期 → 重新分析 → 完成 状态机 ----

    @Test
    fun `点重新分析进入分析中且等待期不可见结论`() {
        val expired = state(freshness = AiDataFreshness.Stale)
        val running = aiBoundaryReduce(expired, AiBoundaryIntent.StartReanalysis)
        assertThat(running.stage).isEqualTo(AiBoundaryStage.Reanalyzing)
        assertThat(running.reanalysis).isEqualTo(AiReanalysisPhase.Running)
    }

    @Test
    fun `分析完成后数据转为最新并进入新报告页`() {
        val running = aiBoundaryReduce(state(freshness = AiDataFreshness.Stale), AiBoundaryIntent.StartReanalysis)
        val done = aiBoundaryReduce(running, AiBoundaryIntent.ReanalysisCompleted)
        assertThat(done.stage).isEqualTo(AiBoundaryStage.ReportDone)
        assertThat(done.reanalysis).isEqualTo(AiReanalysisPhase.Done)
        assertThat(done.freshness).isEqualTo(AiDataFreshness.Fresh)
        assertThat(canShowPersonalConclusion(done)).isTrue()
    }

    @Test
    fun `继续查看旧报告不改变状态也不产生新结论`() {
        val expired = state(freshness = AiDataFreshness.Stale)
        val next = aiBoundaryReduce(expired, AiBoundaryIntent.ContinueOldReport)
        assertThat(next).isEqualTo(expired)
        assertThat(next.stage).isEqualTo(AiBoundaryStage.ReportExpired)
    }

    // ---- 四、服务不可用 → 重试 → 恢复 ----

    @Test
    fun `点重试连接只触发回调状态等恢复事件`() {
        val unavailable = state(availability = AiServiceAvailability.Unavailable)
        assertThat(aiBoundaryReduce(unavailable, AiBoundaryIntent.RetryConnection)).isEqualTo(unavailable)
    }

    @Test
    fun `连接恢复后按新鲜度落到旧报告页`() {
        val unavailable = state(availability = AiServiceAvailability.Unavailable, freshness = AiDataFreshness.Stale)
        val restored = aiBoundaryReduce(unavailable, AiBoundaryIntent.ConnectionRestored)
        assertThat(restored.availability).isEqualTo(AiServiceAvailability.Available)
        assertThat(restored.stage).isEqualTo(AiBoundaryStage.ReportExpired)
    }

    @Test
    fun `连接恢复且数据最新时交还主报告页`() {
        val unavailable = state(availability = AiServiceAvailability.Unavailable)
        val restored = aiBoundaryReduce(unavailable, AiBoundaryIntent.ConnectionRestored)
        assertThat(restored.stage).isNull()
    }

    @Test
    fun `先看历史报告按原型落到未授权咨询页`() {
        val unavailable = state(availability = AiServiceAvailability.Unavailable)
        val next = aiBoundaryReduce(unavailable, AiBoundaryIntent.ViewHistoryReports)
        assertThat(next.stage).isEqualTo(AiBoundaryStage.UnauthConsult)
    }

    // ---- 五、授权范围变化 ----

    @Test
    fun `未授权成员授权后回到主报告或旧报告页`() {
        val member = state(scope = AiDataScope.MemberUnauthorized, freshness = AiDataFreshness.Stale)
        val granted = aiBoundaryReduce(member, AiBoundaryIntent.MemberAuthorizationGranted)
        assertThat(granted.scope).isEqualTo(AiDataScope.Personalized)
        assertThat(granted.stage).isEqualTo(AiBoundaryStage.ReportExpired)
    }

    @Test
    fun `开启个性化后有待重算数据则进旧报告页`() {
        val consult = state(scope = AiDataScope.GenericOnly, freshness = AiDataFreshness.Stale)
        val enabled = aiBoundaryReduce(consult, AiBoundaryIntent.PersonalizationEnabled)
        assertThat(enabled.scope).isEqualTo(AiDataScope.Personalized)
        assertThat(enabled.stage).isEqualTo(AiBoundaryStage.ReportExpired)
    }

    @Test
    fun `开启个性化且数据最新时交还主报告页`() {
        val consult = state(scope = AiDataScope.GenericOnly)
        val enabled = aiBoundaryReduce(consult, AiBoundaryIntent.PersonalizationEnabled)
        assertThat(enabled.stage).isNull()
    }

    @Test
    fun `切换成员与邀请开启只触发回调不改状态`() {
        val member = state(scope = AiDataScope.MemberUnauthorized)
        assertThat(aiBoundaryReduce(member, AiBoundaryIntent.SwitchMember)).isEqualTo(member)
        assertThat(aiBoundaryReduce(member, AiBoundaryIntent.InviteMember)).isEqualTo(member)
        assertThat(aiBoundaryReduce(member, AiBoundaryIntent.OpenPersonalization)).isEqualTo(member)
    }

    // ---- 六、咨询输入 ----

    @Test
    fun `咨询草稿可输入并发送后清空`() {
        val typed = aiBoundaryReduce(state(scope = AiDataScope.GenericOnly), AiBoundaryIntent.ConsultInputChanged("BMI 22"))
        assertThat(typed.consultDraft).isEqualTo("BMI 22")
        val sent = aiBoundaryReduce(typed, AiBoundaryIntent.SendConsult)
        assertThat(sent.consultDraft).isEmpty()
        assertThat(sent.stage).isEqualTo(AiBoundaryStage.UnauthConsult)
    }

    // ---- 七、文案逐字（原型 25 六屏） ----

    @Test
    fun `不可用页文案与原型一致`() {
        // AC-J1-12 then-1
        assertThat(AiBoundaryText.TitleAssistant).isEqualTo("AI 健康助手")
        assertThat(AiBoundaryText.UnavailableTitle).isEqualTo("AI 服务暂时不可用")
        assertThat(AiBoundaryText.UnavailableDescLead + AiBoundaryText.UnavailableEta + AiBoundaryText.UnavailableDescTail)
            .isEqualTo("AI 服务正在维护，\n预计 15 分钟内恢复。\n你的测量与记录不受影响。")
        assertThat(AiBoundaryText.UnavailableAffectedLine).isEqualTo("受影响：AI 解读 · 周总结 · 行动建议")
        assertThat(AiBoundaryText.UnavailableUnaffectedLine).isEqualTo("不受影响：测量 · 记录 · 趋势图表")
        assertThat(AiBoundaryText.RetryConnection).isEqualTo("重试连接")
        assertThat(AiBoundaryText.ViewHistoryReports).isEqualTo("先看历史报告")
    }

    @Test
    fun `报告过期页文案与原型一致`() {
        // AC-J1-12 then-2
        val s = state(freshness = AiDataFreshness.Stale)
        assertThat(AiBoundaryText.ExpiredBannerLead + AiBoundaryText.recordsCount(s.newRecordCount) + AiBoundaryText.ExpiredBannerTail)
            .isEqualTo("你有 2 条新测量记录，\n本报告基于旧数据生成。")
        assertThat(AiBoundaryText.OldReportTag).isEqualTo("旧报告")
        assertThat(s.oldReportBasedOn).isEqualTo("基于 10月20日数据")
        assertThat(s.oldConclusion).isEqualTo("近 30 天体重下降 1.4 kg，趋势稳健。")
        assertThat(AiBoundaryText.UpdateHeader).isEqualTo("重新分析后将更新")
        assertThat(updatePreviewItems(s.newRecordCount)).containsExactly(
            "纳入最新 2 条测量记录",
            "更新体重/体脂趋势结论",
            "重新生成可执行建议",
        ).inOrder()
        assertThat(AiBoundaryText.HistoryKept).isEqualTo("旧报告保留在历史中，不会被覆盖。")
        assertThat(AiBoundaryText.ReanalyzeAction).isEqualTo("用最新数据重新分析")
        assertThat(AiBoundaryText.ContinueOldReport).isEqualTo("继续查看旧报告")
    }

    @Test
    fun `重新分析页文案与原型一致且等待期无结论`() {
        // AC-J1-12 then-3：等待期不展示结论
        assertThat(AiBoundaryText.ReanalyzingTitle).isEqualTo("正在重新分析…")
        assertThat(AiBoundaryText.ReanalyzingEta).isEqualTo("约需 10–20 秒")
        assertThat(AiBoundaryText.recordsIncluded(2)).isEqualTo("纳入最新 2 条测量记录")

        val running = aiBoundaryReduce(state(freshness = AiDataFreshness.Stale), AiBoundaryIntent.StartReanalysis)
        assertThat(running.stage).isEqualTo(AiBoundaryStage.Reanalyzing)
        // 分析中仍是旧数据 + 未完成：结论不可视为最新
        assertThat(running.freshness).isEqualTo(AiDataFreshness.Stale)
        assertThat(running.reanalysis).isEqualTo(AiReanalysisPhase.Running)
    }

    @Test
    fun `分析完成页文案与原型一致`() {
        // AC-J1-12 then-4
        val done = state(reanalysis = AiReanalysisPhase.Done)
        assertThat(AiBoundaryText.doneBanner(done.newRecordCount)).isEqualTo("已基于最新 2 条记录重新分析。")
        assertThat(AiBoundaryText.NewReportTag).isEqualTo("新报告")
        assertThat(done.latestReportBasedOn).isEqualTo("基于 10月21日最新数据")
        assertThat(done.newConclusion).isEqualTo("近 30 天体重下降 1.6 kg，纳入最新数据后趋势更清晰。")
        assertThat(AiBoundaryText.deltaLead(done.newRecordCount) + AiBoundaryText.DeltaHighlight + AiBoundaryText.DeltaTail)
            .isEqualTo("纳入最新 2 条记录后，30 天体重下降从 1.4 kg 修正为 1.6 kg，体脂率下降 1.5 个百分点。趋势与减重目标一致。")
        assertThat(AiBoundaryText.Disclaimer).isEqualTo("AI 建议仅供参考，不作疾病诊断。")
        assertThat(AiBoundaryText.Back).isEqualTo("返回")
    }

    @Test
    fun `未授权咨询页文案与原型一致`() {
        // AC-J1-12 then-5
        assertThat(
            AiBoundaryText.ConsultBannerLead +
                AiBoundaryText.ConsultBannerHighlight +
                AiBoundaryText.ConsultBannerTail,
        ).isEqualTo("未开启个性化分析，AI 不使用你的健康数据，\n仅提供通用健康知识。")
        assertThat(AI_CONSULT_SUGGESTIONS).containsExactly(
            "BMI 22.1 正常吗？",
            "体脂率怎么看？",
            "如何健康减脂？",
        ).inOrder()
        assertThat(AiBoundaryText.ConsultIntro)
            .isEqualTo("通用健康问题都可以问我。要获得针对你数据的解读，请先开启个性化分析。")
        assertThat(AiBoundaryText.ConsultNote)
            .isEqualTo("未开启个性化时，AI 不会读取你的测量记录、健康档案和目标。")
        assertThat(AiBoundaryText.EnablePersonalization).isEqualTo("开启个性化分析")
        assertThat(AiBoundaryText.ConsultHint).isEqualTo("问问健康知识…")
    }

    @Test
    fun `成员未授权页文案与原型一致`() {
        // AC-J1-12 then-6
        val member = state(scope = AiDataScope.MemberUnauthorized)
        assertThat(AiBoundaryText.TitleReport).isEqualTo("AI 报告解读")
        assertThat(member.memberViewing).isEqualTo("当前查看：妈妈")
        assertThat(AiBoundaryText.MemberAiLine).isEqualTo("AI 解读 · 未授权")
        assertThat(AiBoundaryText.MemberUnauthTag).isEqualTo("未授权")
        assertThat(AiBoundaryText.MemberUnauthTitle).isEqualTo("该成员未开启 AI 解读")
        assertThat(member.memberUnauthDesc)
            .isEqualTo("「妈妈」尚未授权 AI 使用她的健康数据。\nAI 无法为她生成报告解读。")
        assertThat(AiBoundaryText.SwitchMember).isEqualTo("切换到已授权成员")
        assertThat(member.inviteMemberLabel).isEqualTo("邀请妈妈开启")
    }

    // ---- 八、加粗关键词拼接（原型 <b> 片段） ----

    @Test
    fun `加粗片段拼接后仍是连续文案`() {
        val text = highlightedText(
            lead = AiBoundaryText.UnavailableDescLead,
            highlight = AiBoundaryText.UnavailableEta,
            tail = AiBoundaryText.UnavailableDescTail,
        )
        assertThat(text.text).isEqualTo("AI 服务正在维护，\n预计 15 分钟内恢复。\n你的测量与记录不受影响。")

        val noHighlight = highlightedText(lead = AiBoundaryText.HistoryKept, highlight = "", tail = "")
        assertThat(noHighlight.text).isEqualTo("旧报告保留在历史中，不会被覆盖。")
    }
}
