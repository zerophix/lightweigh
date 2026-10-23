package com.xcwk.scalehub.feature.privacy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.theme.QkColor

/**
 * 隐私授权五态（T-096 · Wave 1 · 原型 10）。
 *
 * 原型页脚：轻衡 · 隐私与授权交互原型 · 含 1 个弹层。
 * 授权不落库：同意、仅本机、确认撤回只通过 [onPersistConsent] 交给既有流程。
 *
 * 单文件会超过 400 行，因此按 view 拆开：
 * 本文件持有状态、文案与分发；[PrivacyConsentPages] 为 v-auth / v-granted；
 * [PrivacyRestrictedPages] 为 v-unauth / v-consult / v-rules。
 */
enum class PrivacyScreen { Auth, Granted, Unauth, Consult, Rules }

enum class PrivacyConsent { None, Granted, Withdrawn }

data class PrivacyFlowState(
    val screen: PrivacyScreen = PrivacyScreen.Auth,
    val consent: PrivacyConsent = PrivacyConsent.None,
    val shareBodyComposition: Boolean = true,
    val shareHealthProfile: Boolean = true,
    val cloudSync: Boolean = false,
    val grantedOnLabel: String = "",
    val withdrawnOnLabel: String = "",
    val withdrawSheetVisible: Boolean = false,
)

sealed interface PrivacyEvent {
    data class AgreeAndContinue(
        val shareBodyComposition: Boolean,
        val shareHealthProfile: Boolean,
        val cloudSync: Boolean,
    ) : PrivacyEvent

    data object LocalOnly : PrivacyEvent
    data object OpenRules : PrivacyEvent
    data object PreviewWithdraw : PrivacyEvent
    data object OpenWithdrawSheet : PrivacyEvent
    data object ConfirmWithdraw : PrivacyEvent
    data object KeepEnabled : PrivacyEvent
    data object Reenable : PrivacyEvent
    data object OpenConsult : PrivacyEvent
    data object OpenPersonalization : PrivacyEvent
    data object Back : PrivacyEvent
}

data class PrivacyStep(
    val state: PrivacyFlowState,
    /** 为 true 时调用方才应持久化授权；预览与返回不会置位。 */
    val persist: Boolean,
    /** 授权页返回时离开本流程。 */
    val leave: Boolean,
)

data class DeletionRule(
    val title: String,
    val effect: String,
    val destructive: Boolean,
)

internal object PrivacyCopy {
    const val AUTH_TITLE = "健康数据授权"
    const val AUTH_HEAD = "健康数据属于敏感信息"
    const val AUTH_SUB = "需你的单独同意，我们才处理以下数据。"
    const val BODY_COMPOSITION = "身体成分数据"
    const val BODY_COMPOSITION_DETAIL = "体重、体脂率、肌肉量等"
    const val HEALTH_PROFILE = "健康档案信息"
    const val HEALTH_PROFILE_DETAIL = "年龄、性别、身高"
    const val CLOUD_SYNC = "云端同步"
    const val CLOUD_SYNC_DETAIL = "加密上传（可选）"
    const val AUTH_NOTE = "不同步时数据仅存本机，可随时在隐私管理中调整。"
    const val AGREE = "同意并继续"
    const val LOCAL_ONLY = "仅本机，暂不同步"
    const val GRANTED_TITLE = "隐私与数据管理"
    const val GRANTED_STATUS = "健康数据授权已开启"
    const val RULES_ENTRY = "数据删除与保留规则"
    const val RULES_ENTRY_DETAIL = "四类操作如何区分"
    const val PREVIEW_ENTRY = "撤回授权后会怎样"
    const val PREVIEW_ENTRY_DETAIL = "预览功能降级"
    const val WITHDRAW_ALL = "撤回全部授权"
    const val WITHDRAW_ALL_DETAIL = "关闭后仍可一般咨询"
    const val GRANTED_NOTE = "撤回、删除对话、删除记录、注销是四种不同操作。"
    const val WITHDRAW_TITLE = "撤回个性化分析授权？"
    const val WITHDRAW_BODY = "AI 将不再使用你的健康数据，报告解读停止更新，仍可一般咨询。"
    const val CONFIRM_WITHDRAW = "确认撤回"
    const val KEEP_ENABLED = "保持开启"
    const val UNAUTH_TITLE = "AI 数据设置"
    const val UNAUTH_STATUS = "个性化分析已关闭"
    const val WITHDRAWN_ON = "撤回时间"
    const val REENABLE = "重新开启"
    const val AFFECTED = "受影响的功能"
    const val REPORT = "AI 报告解读"
    const val REPORT_DETAIL = "已停止，历史报告保留不再更新"
    const val TREND = "个性化趋势分析"
    const val TREND_DETAIL = "已关闭，仅基础图表"
    const val WEEKLY = "每周行动建议"
    const val WEEKLY_DETAIL = "已停止生成"
    const val MEASURE = "测量与记录"
    const val MEASURE_DETAIL = "不受影响"
    const val UNAUTH_NOTE = "重新开启后 AI 基于最新数据恢复分析，历史对话不找回。"
    const val GO_CONSULT = "去一般咨询（撤回后仍可用）"
    const val CONSULT_TITLE = "AI 健康助手"
    const val OPEN_PERSONAL = "开启个性化"
    const val CONSULT_BANNER = "未开启个性化，AI 仅提供通用健康知识。"
    const val CHIP_BMI = "BMI 22.1 正常吗？"
    const val CHIP_FAT = "体脂率怎么看？"
    const val CONSULT_CARD = "通用健康问题都可以问我。要获得针对你数据的解读，请先开启个性化分析。"
    const val CONSULT_FIELD = "问问健康知识…"
    const val RULES_TITLE = "数据删除与保留"
    const val RULES_BANNER = "四种操作范围不同，按需选择避免误删。"
    const val RETENTION_TITLE = "保留规则"
    const val RETENTION = "· 删除的记录保留 7 天可恢复\n· 注销后 7 天冷静期，可撤销\n· 冷静期后 30 天内备份残余覆盖"
    const val SAMPLE_GRANTED_ON = "2026年10月21日"
    const val SAMPLE_WITHDRAWN_ON = "2026年10月22日"
}

internal fun deletionRules(): List<DeletionRule> = listOf(
    DeletionRule("删除单条测量记录", "仅删这一条，7 天内可恢复", false),
    DeletionRule("删除单段 AI 对话", "不影响报告与健康数据", false),
    DeletionRule("清空全部 AI 对话", "清空对话历史，健康数据保留", false),
    DeletionRule("注销账号", "永久删除全部数据，不可恢复", true),
)

internal fun consultShowsGenericOnly(consent: PrivacyConsent): Boolean =
    consent != PrivacyConsent.Granted

internal fun privacyPrototypeState(): PrivacyFlowState = PrivacyFlowState(
    screen = PrivacyScreen.Granted,
    consent = PrivacyConsent.Granted,
    grantedOnLabel = PrivacyCopy.SAMPLE_GRANTED_ON,
    withdrawnOnLabel = PrivacyCopy.SAMPLE_WITHDRAWN_ON,
)

internal fun reducePrivacy(state: PrivacyFlowState, event: PrivacyEvent): PrivacyStep = when (event) {
    is PrivacyEvent.AgreeAndContinue -> PrivacyStep(
        state.copy(
            screen = PrivacyScreen.Granted,
            consent = PrivacyConsent.Granted,
            shareBodyComposition = event.shareBodyComposition,
            shareHealthProfile = event.shareHealthProfile,
            cloudSync = event.cloudSync,
            withdrawSheetVisible = false,
        ),
        persist = true,
        leave = false,
    )
    PrivacyEvent.LocalOnly -> PrivacyStep(
        state.copy(
            screen = PrivacyScreen.Granted,
            consent = PrivacyConsent.Granted,
            cloudSync = false,
            withdrawSheetVisible = false,
        ),
        persist = true,
        leave = false,
    )
    PrivacyEvent.OpenRules -> PrivacyStep(
        state.copy(screen = PrivacyScreen.Rules, withdrawSheetVisible = false),
        persist = false,
        leave = false,
    )
    PrivacyEvent.PreviewWithdraw -> PrivacyStep(
        state.copy(screen = PrivacyScreen.Unauth, withdrawSheetVisible = false),
        persist = false,
        leave = false,
    )
    PrivacyEvent.OpenWithdrawSheet -> PrivacyStep(
        state.copy(withdrawSheetVisible = true),
        persist = false,
        leave = false,
    )
    PrivacyEvent.ConfirmWithdraw -> PrivacyStep(
        state.copy(
            screen = PrivacyScreen.Unauth,
            consent = PrivacyConsent.Withdrawn,
            cloudSync = false,
            withdrawSheetVisible = false,
        ),
        persist = true,
        leave = false,
    )
    PrivacyEvent.KeepEnabled -> PrivacyStep(
        state.copy(withdrawSheetVisible = false),
        persist = false,
        leave = false,
    )
    PrivacyEvent.Reenable -> PrivacyStep(
        state.copy(screen = PrivacyScreen.Auth, withdrawSheetVisible = false),
        persist = false,
        leave = false,
    )
    PrivacyEvent.OpenConsult -> PrivacyStep(
        state.copy(screen = PrivacyScreen.Consult, withdrawSheetVisible = false),
        persist = false,
        leave = false,
    )
    PrivacyEvent.OpenPersonalization -> PrivacyStep(
        state.copy(screen = PrivacyScreen.Auth, withdrawSheetVisible = false),
        persist = false,
        leave = false,
    )
    PrivacyEvent.Back -> backOf(state)
}

private fun backOf(state: PrivacyFlowState): PrivacyStep = when {
    state.withdrawSheetVisible -> PrivacyStep(state.copy(withdrawSheetVisible = false), false, false)
    state.screen == PrivacyScreen.Rules -> PrivacyStep(state.copy(screen = PrivacyScreen.Granted), false, false)
    state.screen == PrivacyScreen.Unauth -> PrivacyStep(state.copy(screen = PrivacyScreen.Consult), false, false)
    state.screen == PrivacyScreen.Consult -> PrivacyStep(state.copy(screen = PrivacyScreen.Rules), false, false)
    state.screen == PrivacyScreen.Granted -> PrivacyStep(state.copy(screen = PrivacyScreen.Auth), false, false)
    else -> PrivacyStep(state, persist = false, leave = true)
}

@Composable
fun PrivacyAuthFlow(
    onPersistConsent: (PrivacyConsent, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialState: PrivacyFlowState = PrivacyFlowState(),
    onRuleSelected: (DeletionRule) -> Unit = {},
    onAsk: (String) -> Unit = {},
) {
    var state by remember(initialState) { mutableStateOf(initialState) }
    PrivacyAuthContent(
        state = state,
        modifier = modifier,
        onEvent = { event ->
            val step = reducePrivacy(state, event)
            if (step.persist) onPersistConsent(step.state.consent, step.state.cloudSync)
            if (step.leave) onBack() else state = step.state
        },
        onRuleSelected = onRuleSelected,
        onAsk = onAsk,
    )
}

@Composable
internal fun PrivacyAuthContent(
    state: PrivacyFlowState,
    onEvent: (PrivacyEvent) -> Unit,
    modifier: Modifier = Modifier,
    onRuleSelected: (DeletionRule) -> Unit = {},
    onAsk: (String) -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state.screen) {
            PrivacyScreen.Auth -> PrivacyAuthPage(state = state, onEvent = onEvent)
            PrivacyScreen.Granted -> PrivacyGrantedPage(state = state, onEvent = onEvent)
            PrivacyScreen.Unauth -> PrivacyUnauthPage(state = state, onEvent = onEvent)
            PrivacyScreen.Consult -> PrivacyConsultPage(
                restricted = consultShowsGenericOnly(state.consent),
                onEvent = onEvent,
                onAsk = onAsk,
            )
            PrivacyScreen.Rules -> PrivacyRulesPage(onEvent = onEvent, onRuleSelected = onRuleSelected)
        }
        if (state.withdrawSheetVisible) {
            AlertDialog(
                onDismissRequest = { onEvent(PrivacyEvent.KeepEnabled) },
                title = { Text(text = PrivacyCopy.WITHDRAW_TITLE, color = QkColor.Fg) },
                text = { Text(text = PrivacyCopy.WITHDRAW_BODY, color = QkColor.Muted) },
                confirmButton = {
                    TextButton(onClick = { onEvent(PrivacyEvent.ConfirmWithdraw) }) {
                        Text(text = PrivacyCopy.CONFIRM_WITHDRAW, color = QkColor.Danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onEvent(PrivacyEvent.KeepEnabled) }) {
                        Text(text = PrivacyCopy.KEEP_ENABLED, color = QkColor.Primary)
                    }
                },
            )
        }
    }
}
