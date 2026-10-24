package com.xcwk.scalehub.feature.privacy

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-096 隐私授权五态纯逻辑单测。
 *
 * 覆盖验收中**唯一可自动判定**的部分：
 * - 五态呈现转移（[privacyAuthReduce]）：同意/仅本机/撤回两步/预览/重新开启/咨询/规则
 * - 静态文案与规格逐字对齐原型 `10-privacy-authorization.html`（AC-V5 五条 then）
 *
 * 视觉布局不在此测（需真机/截图）；状态转移写错了没人会发现，故钉死。
 */
class PrivacyAuthFlowTest {

    private fun state(
        step: PrivacyAuthStep = PrivacyAuthStep.Auth,
        cloudSync: Boolean = false,
        sheet: Boolean = false,
    ) = PrivacyAuthUiState(step = step, cloudSyncSelected = cloudSync, withdrawSheetVisible = sheet)

    // ---- 五态转移 ----

    @Test
    fun `初始态为授权页`() {
        assertThat(state().step).isEqualTo(PrivacyAuthStep.Auth)
    }

    @Test
    fun `同意并继续进入已授权并保留云同步选择`() {
        val next = privacyAuthReduce(state(cloudSync = true), PrivacyAuthIntent.AgreeAndContinue)
        assertThat(next.step).isEqualTo(PrivacyAuthStep.Granted)
        assertThat(next.cloudSyncSelected).isTrue()
    }

    @Test
    fun `仅本机暂不同步强制关闭云同步`() {
        // 「仅本机」语义 = 不同步：即便之前勾选了云端同步也必须清掉
        val next = privacyAuthReduce(state(cloudSync = true), PrivacyAuthIntent.LocalOnly)
        assertThat(next.step).isEqualTo(PrivacyAuthStep.Granted)
        assertThat(next.cloudSyncSelected).isFalse()
    }

    @Test
    fun `云端同步开关往返翻转`() {
        assertThat(privacyAuthReduce(state(cloudSync = false), PrivacyAuthIntent.ToggleCloudSync).cloudSyncSelected).isTrue()
        assertThat(privacyAuthReduce(state(cloudSync = true), PrivacyAuthIntent.ToggleCloudSync).cloudSyncSelected).isFalse()
    }

    @Test
    fun `撤回是两步动作先开弹层再确认`() {
        // 第一步：只开弹层，状态仍是已授权（防误触直接撤回）
        val asked = privacyAuthReduce(state(step = PrivacyAuthStep.Granted), PrivacyAuthIntent.RequestWithdraw)
        assertThat(asked.withdrawSheetVisible).isTrue()
        assertThat(asked.step).isEqualTo(PrivacyAuthStep.Granted)
        // 第二步：确认才转移到未授权并关弹层
        val confirmed = privacyAuthReduce(asked, PrivacyAuthIntent.ConfirmWithdraw)
        assertThat(confirmed.step).isEqualTo(PrivacyAuthStep.Unauth)
        assertThat(confirmed.withdrawSheetVisible).isFalse()
    }

    @Test
    fun `保持开启只关弹层不改状态`() {
        val next = privacyAuthReduce(state(step = PrivacyAuthStep.Granted, sheet = true), PrivacyAuthIntent.KeepGranted)
        assertThat(next.withdrawSheetVisible).isFalse()
        assertThat(next.step).isEqualTo(PrivacyAuthStep.Granted)
    }

    @Test
    fun `预览撤回降级只切视图不触碰弹层`() {
        // 「撤回授权后会怎样」是预览：进未授权视图看降级，不代表已撤回
        val next = privacyAuthReduce(state(step = PrivacyAuthStep.Granted), PrivacyAuthIntent.PreviewWithdraw)
        assertThat(next.step).isEqualTo(PrivacyAuthStep.Unauth)
        assertThat(next.withdrawSheetVisible).isFalse()
    }

    @Test
    fun `重新开启回到授权页重新单独同意`() {
        val next = privacyAuthReduce(state(step = PrivacyAuthStep.Unauth), PrivacyAuthIntent.ReEnable)
        assertThat(next.step).isEqualTo(PrivacyAuthStep.Auth)
    }

    @Test
    fun `撤回后可去一般咨询`() {
        val next = privacyAuthReduce(state(step = PrivacyAuthStep.Unauth), PrivacyAuthIntent.GoConsult)
        assertThat(next.step).isEqualTo(PrivacyAuthStep.Consult)
    }

    @Test
    fun `咨询页开启个性化回到授权页`() {
        val next = privacyAuthReduce(state(step = PrivacyAuthStep.Consult), PrivacyAuthIntent.OpenPersonalization)
        assertThat(next.step).isEqualTo(PrivacyAuthStep.Auth)
    }

    @Test
    fun `已授权页可打开删除保留规则`() {
        val next = privacyAuthReduce(state(step = PrivacyAuthStep.Granted), PrivacyAuthIntent.OpenRules)
        assertThat(next.step).isEqualTo(PrivacyAuthStep.Rules)
    }

    @Test
    fun `咨询输入更新与发送清空`() {
        val typed = privacyAuthReduce(state(), PrivacyAuthIntent.ConsultInputChanged("BMI 22"))
        assertThat(typed.consultInput).isEqualTo("BMI 22")
        val sent = privacyAuthReduce(typed, PrivacyAuthIntent.SendConsult)
        assertThat(sent.consultInput).isEmpty()
    }

    // ---- 静态文案与规格（原型逐字） ----

    @Test
    fun `授权页两类必选数据项与一项可选云同步`() {
        // AC-V5 then-1：身体成分数据（体重、体脂率、肌肉量等）与健康档案信息（年龄、性别、身高）
        assertThat(AUTH_ITEMS).hasSize(3)
        val required = AUTH_ITEMS.filter { it.required }
        assertThat(required.map { it.title }).containsExactly("身体成分数据", "健康档案信息").inOrder()
        assertThat(required[0].desc).isEqualTo("体重、体脂率、肌肉量等")
        assertThat(required[1].desc).isEqualTo("年龄、性别、身高")
        val optional = AUTH_ITEMS.single { !it.required }
        assertThat(optional.title).isEqualTo("云端同步")
        assertThat(optional.desc).isEqualTo("加密上传（可选）")
    }

    @Test
    fun `授权页单独同意文案与原型一致`() {
        assertThat(AUTH_HEADLINE).isEqualTo("健康数据属于敏感信息")
        assertThat(AUTH_SUBLINE).isEqualTo("需你的单独同意，我们才处理以下数据。")
        assertThat(AUTH_NOTE).isEqualTo("不同步时数据仅存本机，可随时在隐私管理中调整。")
        assertThat(AUTH_AGREE).isEqualTo("同意并继续")
        assertThat(AUTH_LOCAL_ONLY).isEqualTo("仅本机，暂不同步")
    }

    @Test
    fun `已授权页入口与撤回文案与原型一致`() {
        // AC-V5 then-2：开启时间 + 三个入口（规则/撤回预览/撤回）
        assertThat(GRANTED_ON).isEqualTo("健康数据授权已开启")
        assertThat(ENTRY_RULES).isEqualTo("数据删除与保留规则")
        assertThat(ENTRY_RULES_DESC).isEqualTo("四类操作如何区分")
        assertThat(ENTRY_WITHDRAW_PREVIEW).isEqualTo("撤回授权后会怎样")
        assertThat(ENTRY_WITHDRAW_PREVIEW_DESC).isEqualTo("预览功能降级")
        assertThat(REVOKE_ALL).isEqualTo("撤回全部授权")
        assertThat(REVOKE_ALL_DESC).isEqualTo("关闭后仍可一般咨询")
        assertThat(GRANTED_NOTE).isEqualTo("撤回、删除对话、删除记录、注销是四种不同操作。")
        assertThat(WITHDRAW_SHEET_BODY)
            .isEqualTo("AI 将不再使用你的健康数据，报告解读停止更新，仍可一般咨询。")
    }

    @Test
    fun `未授权页受影响功能三项受影响一项不受影响`() {
        // AC-V5 then-3：AI 报告解读已停止等受影响清单 + 测量与记录不受影响
        assertThat(AFFECTED_FUNCTIONS).hasSize(4)
        val affected = AFFECTED_FUNCTIONS.filter { it.affected }
        assertThat(affected).hasSize(3)
        assertThat(affected.map { it.title })
            .containsExactly("AI 报告解读", "个性化趋势分析", "每周行动建议")
        val unaffected = AFFECTED_FUNCTIONS.single { !it.affected }
        assertThat(unaffected.title).isEqualTo("测量与记录")
        assertThat(unaffected.desc).isEqualTo("不受影响")
    }

    @Test
    fun `未授权页撤回时间前缀与原型一致`() {
        assertThat(UNAUTH_OFF).isEqualTo("个性化分析已关闭")
        assertThat(UNAUTH_WITHDRAWN_AT_PREFIX).isEqualTo("撤回时间：")
        assertThat(UNAUTH_AFFECTED_LABEL).isEqualTo("受影响的功能")
        assertThat(UNAUTH_RE_ENABLE).isEqualTo("重新开启")
    }

    @Test
    fun `咨询页提示仅提供通用健康知识并引导开启`() {
        // AC-V5 then-4：未开启个性化时提示仅提供通用健康知识并引导开启
        assertThat(CONSULT_BANNER).isEqualTo("未开启个性化，AI 仅提供通用健康知识。")
        assertThat(CONSULT_BANNER).contains("仅提供通用健康知识")
        assertThat(CONSULT_ENABLE_LINK).isEqualTo("开启个性化")
        assertThat(CONSULT_INPUT_HINT).isEqualTo("问问健康知识…")
    }

    @Test
    fun `规则页四类删除操作仅注销账号不可恢复`() {
        // AC-V5 then-5：四类删除操作及影响范围
        assertThat(DELETE_OPERATIONS).hasSize(4)
        assertThat(DELETE_OPERATIONS.map { it.op })
            .containsExactly(
                DeleteOp.SingleRecord,
                DeleteOp.SingleConversation,
                DeleteOp.AllConversations,
                DeleteOp.Account,
            )
            .inOrder()
        val dangerous = DELETE_OPERATIONS.filter { it.danger }
        assertThat(dangerous).hasSize(1)
        assertThat(dangerous.single().title).isEqualTo("注销账号")
        assertThat(dangerous.single().desc).isEqualTo("永久删除全部数据，不可恢复")
    }

    @Test
    fun `保留规则三条与原型一致`() {
        assertThat(RETENTION_RULES).containsExactly(
            "删除的记录保留 7 天可恢复",
            "注销后 7 天冷静期，可撤销",
            "冷静期后 30 天内备份残余覆盖",
        ).inOrder()
    }

    @Test
    fun `咨询引导问题两条与原型一致`() {
        assertThat(CONSULT_SUGGESTIONS).containsExactly("BMI 22.1 正常吗？", "体脂率怎么看？").inOrder()
    }
}
