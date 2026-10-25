package com.xcwk.scalehub.feature.privacy

import com.xcwk.scalehub.ui.components.QkHeaderStyle
import com.xcwk.scalehub.ui.components.QkScreenHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * privacy — 隐私授权流程五态（T-096 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/10-privacy-authorization.html` 的五个 view：
 * - `v-auth` 授权页：两类必选数据项 + 可选云同步，单独同意
 * - `v-granted` 已授权管理：开启时间 + 规则入口 + 撤回预览 + 撤回弹层
 * - `v-unauth` 撤回后：撤回时间 + 受影响功能清单 + 重新开启
 * - `v-consult` 未授权一般咨询：「仅提供通用健康知识」引导
 * - `v-rules` 数据删除与保留：四类删除操作 + 影响范围 + 保留规则
 *
 * ## 验收（AC-V5）
 * - 授权页列出「身体成分数据」「健康档案信息」两类数据项及单独同意文案
 * - 已授权页展示开启时间与入口；未授权页展示撤回时间与受影响功能
 * - 咨询页未开启个性化时提示仅提供通用健康知识并引导开启
 * - 规则页区分四类删除操作及影响范围
 *
 * ## 边界（呈现式组件）
 * 本流程**不实现真实授权落库**：同意/撤回经 [onConsentGranted]/[onConsentWithdrawn]
 * 回调交由既有流程处理；本文件只维护五态之间的呈现状态。
 *
 * ## 文件拆分（DoD 说明）
 * 五 view 全量文案 + 布局单文件超 400 行，按 view 拆为三个文件，均在 privacy/ 内：
 * - `PrivacyAuthFlow.kt`（本文件）：状态机、文案规格、共享原语、路由
 * - `PrivacyConsentViews.kt`：v-auth + v-granted
 * - `PrivacyStatusViews.kt`：v-unauth + v-consult + v-rules
 */

// ---- 五态状态机 ----

/** 隐私授权流程的五个呈现态（与原型五个 view 一一对应）。 */
enum class PrivacyAuthStep { Auth, Granted, Unauth, Consult, Rules }

/** 呈现式 UI 状态。时间戳由调用方注入（授权/撤回发生在既有流程里）。 */
data class PrivacyAuthUiState(
    val step: PrivacyAuthStep = PrivacyAuthStep.Auth,
    /** 云端同步（可选项）是否勾选 */
    val cloudSyncSelected: Boolean = false,
    /** 撤回确认弹层是否可见 */
    val withdrawSheetVisible: Boolean = false,
    /** 授权开启时间文案（如 2026年10月21日） */
    val grantedAt: String = "",
    /** 撤回时间文案（如 2026年10月22日） */
    val withdrawnAt: String = "",
    /** 咨询输入框草稿 */
    val consultInput: String = "",
)

/** 流程内的呈现态转移。外部副作用（落库、发消息、真删除）走回调，不进 reduce。 */
sealed interface PrivacyAuthIntent {
    data object ToggleCloudSync : PrivacyAuthIntent
    data object AgreeAndContinue : PrivacyAuthIntent
    data object LocalOnly : PrivacyAuthIntent
    data object OpenRules : PrivacyAuthIntent
    data object PreviewWithdraw : PrivacyAuthIntent
    data object RequestWithdraw : PrivacyAuthIntent
    data object ConfirmWithdraw : PrivacyAuthIntent
    data object KeepGranted : PrivacyAuthIntent
    data object ReEnable : PrivacyAuthIntent
    data object GoConsult : PrivacyAuthIntent
    data object OpenPersonalization : PrivacyAuthIntent
    data object SendConsult : PrivacyAuthIntent
    data class ConsultInputChanged(val text: String) : PrivacyAuthIntent
}

/**
 * 纯呈现态转移 —— JVM 单测钉死的部分。
 *
 * 口径：
 * - [PrivacyAuthIntent.AgreeAndContinue] 与 [PrivacyAuthIntent.LocalOnly] 都到达已授权页，
 *   但「仅本机」强制关闭云同步（仅本机 = 不同步）
 * - 撤回经两步：[PrivacyAuthIntent.RequestWithdraw] 只开弹层，
 *   [PrivacyAuthIntent.ConfirmWithdraw] 才转移到未授权页并关弹层
 * - [PrivacyAuthIntent.PreviewWithdraw]（「撤回授权后会怎样」）是**预览**：
 *   只切到未授权视图看降级效果，不关弹层也不代表已撤回
 */
fun privacyAuthReduce(state: PrivacyAuthUiState, intent: PrivacyAuthIntent): PrivacyAuthUiState = when (intent) {
    PrivacyAuthIntent.ToggleCloudSync -> state.copy(cloudSyncSelected = !state.cloudSyncSelected)
    PrivacyAuthIntent.AgreeAndContinue -> state.copy(step = PrivacyAuthStep.Granted, withdrawSheetVisible = false)
    PrivacyAuthIntent.LocalOnly -> state.copy(
        step = PrivacyAuthStep.Granted,
        cloudSyncSelected = false,
        withdrawSheetVisible = false,
    )

    PrivacyAuthIntent.OpenRules -> state.copy(step = PrivacyAuthStep.Rules)
    PrivacyAuthIntent.PreviewWithdraw -> state.copy(step = PrivacyAuthStep.Unauth)
    PrivacyAuthIntent.RequestWithdraw -> state.copy(withdrawSheetVisible = true)
    PrivacyAuthIntent.ConfirmWithdraw -> state.copy(step = PrivacyAuthStep.Unauth, withdrawSheetVisible = false)
    PrivacyAuthIntent.KeepGranted -> state.copy(withdrawSheetVisible = false)
    PrivacyAuthIntent.ReEnable -> state.copy(step = PrivacyAuthStep.Auth)
    PrivacyAuthIntent.GoConsult -> state.copy(step = PrivacyAuthStep.Consult)
    PrivacyAuthIntent.OpenPersonalization -> state.copy(step = PrivacyAuthStep.Auth)
    PrivacyAuthIntent.SendConsult -> state.copy(consultInput = "")
    is PrivacyAuthIntent.ConsultInputChanged -> state.copy(consultInput = intent.text)
}

// ---- 静态文案与数据规格（逐字取自原型，JVM 单测校验） ----

const val TITLE_AUTH = "健康数据授权"
const val TITLE_GRANTED = "隐私与数据管理"
const val TITLE_UNAUTH = "AI 数据设置"
const val TITLE_CONSULT = "AI 健康助手"
const val TITLE_RULES = "数据删除与保留"

// v-auth
const val AUTH_HEADLINE = "健康数据属于敏感信息"
const val AUTH_SUBLINE = "需你的单独同意，我们才处理以下数据。"
const val AUTH_NOTE = "不同步时数据仅存本机，可随时在隐私管理中调整。"
const val AUTH_AGREE = "同意并继续"
const val AUTH_LOCAL_ONLY = "仅本机，暂不同步"

// v-granted
const val GRANTED_ON = "健康数据授权已开启"
const val ENTRY_RULES = "数据删除与保留规则"
const val ENTRY_RULES_DESC = "四类操作如何区分"
const val ENTRY_WITHDRAW_PREVIEW = "撤回授权后会怎样"
const val ENTRY_WITHDRAW_PREVIEW_DESC = "预览功能降级"
const val REVOKE_ALL = "撤回全部授权"
const val REVOKE_ALL_DESC = "关闭后仍可一般咨询"
const val GRANTED_NOTE = "撤回、删除对话、删除记录、注销是四种不同操作。"
const val WITHDRAW_SHEET_TITLE = "撤回个性化分析授权？"
const val WITHDRAW_SHEET_BODY = "AI 将不再使用你的健康数据，报告解读停止更新，仍可一般咨询。"
const val WITHDRAW_CONFIRM = "确认撤回"
const val WITHDRAW_KEEP = "保持开启"

// v-unauth
const val UNAUTH_OFF = "个性化分析已关闭"
const val UNAUTH_WITHDRAWN_AT_PREFIX = "撤回时间："
const val UNAUTH_RE_ENABLE = "重新开启"
const val UNAUTH_AFFECTED_LABEL = "受影响的功能"
const val UNAUTH_NOTE = "重新开启后 AI 基于最新数据恢复分析，历史对话不找回。"
const val UNAUTH_GO_CONSULT = "去一般咨询（撤回后仍可用）"

// v-consult
const val CONSULT_ENABLE_LINK = "开启个性化"
const val CONSULT_BANNER = "未开启个性化，AI 仅提供通用健康知识。"
const val CONSULT_INTRO = "通用健康问题都可以问我。要获得针对你数据的解读，请先开启个性化分析。"
const val CONSULT_INPUT_HINT = "问问健康知识…"

// v-rules
const val RULES_BANNER = "四种操作范围不同，按需选择避免误删。"
const val RULES_RETENTION_TITLE = "保留规则"

/** v-auth 的一条数据项：[required] 为 true 时必须勾选（单独同意的必选项）。 */
data class PrivacyDataItem(val title: String, val desc: String, val required: Boolean)

/** v-unauth 的一条受影响功能。 */
data class AffectedFunctionItem(val title: String, val desc: String, val affected: Boolean)

/** 四类删除操作（原型 v-rules 的四行，危险级仅「注销账号」）。 */
enum class DeleteOp { SingleRecord, SingleConversation, AllConversations, Account }

/** v-rules 的一条删除操作：[danger] 为 true 表示永久不可恢复。 */
data class DeleteOperationItem(val op: DeleteOp, val title: String, val desc: String, val danger: Boolean)

/** 授权页两类必选数据项 + 一项可选云同步（原型 v-auth 三行）。 */
val AUTH_ITEMS = listOf(
    PrivacyDataItem("身体成分数据", "体重、体脂率、肌肉量等", required = true),
    PrivacyDataItem("健康档案信息", "年龄、性别、身高", required = true),
    PrivacyDataItem("云端同步", "加密上传（可选）", required = false),
)

/** 撤回后受影响的功能清单（原型 v-unauth：前三项受影响，测量与记录不受影响）。 */
val AFFECTED_FUNCTIONS = listOf(
    AffectedFunctionItem("AI 报告解读", "已停止，历史报告保留不再更新", affected = true),
    AffectedFunctionItem("个性化趋势分析", "已关闭，仅基础图表", affected = true),
    AffectedFunctionItem("每周行动建议", "已停止生成", affected = true),
    AffectedFunctionItem("测量与记录", "不受影响", affected = false),
)

/** 四类删除操作及影响范围（原型 v-rules 四行）。 */
val DELETE_OPERATIONS = listOf(
    DeleteOperationItem(DeleteOp.SingleRecord, "删除单条测量记录", "仅删这一条，7 天内可恢复", danger = false),
    DeleteOperationItem(DeleteOp.SingleConversation, "删除单段 AI 对话", "不影响报告与健康数据", danger = false),
    DeleteOperationItem(DeleteOp.AllConversations, "清空全部 AI 对话", "清空对话历史，健康数据保留", danger = false),
    DeleteOperationItem(DeleteOp.Account, "注销账号", "永久删除全部数据，不可恢复", danger = true),
)

/** 保留规则三条（原型 v-rules 保留规则卡）。 */
val RETENTION_RULES = listOf(
    "删除的记录保留 7 天可恢复",
    "注销后 7 天冷静期，可撤销",
    "冷静期后 30 天内备份残余覆盖",
)

/** 未授权咨询页的两个引导问题（原型 v-consult 气泡）。 */
val CONSULT_SUGGESTIONS = listOf(
    "BMI 22.1 正常吗？",
    "体脂率怎么看？",
)

// ---- 五 view 共享的呈现原语 ----

/** 压栈页导航条：返回圆钮 + 页标题 + 可选右侧动作（原型 .navbar .lnk）。 */
@Composable
internal fun PrivacyNavBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    QkScreenHeader(title, onBack, modifier = modifier, style = QkHeaderStyle.Title, action = action)
}

/** 提示横幅（原型 .banner：薄荷底圆角条 + 左侧线性图标 + 小字）。 */
@Composable
internal fun PrivacyBanner(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(QkColor.Mint)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(icon, tint = QkColor.Primary, size = 18.dp)
        Text(
            text,
            modifier = Modifier.padding(start = QkSpacing.Sm).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.GhostInk,
        )
    }
}

/** 授权/撤回状态卡（原型 .card.soft：薄荷底 + 白色圆底图标 + 标题 + 时间 + 可选尾动作）。 */
@Composable
internal fun PrivacyStatusCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Card))
            .background(QkColor.Mint)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(QkColor.Card),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(icon, tint = iconTint, size = 20.dp)
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
            Text(
                subtitle,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.GhostInk,
            )
        }
        trailing?.invoke()
    }
}

/** 授权项勾选框（原型 .chk：22dp 圆角方块，选中主绿底白勾）。 */
@Composable
internal fun PrivacyCheck(checked: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(QkRadius.Field)
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(shape)
            .background(if (checked) QkColor.Primary else Color.Transparent)
            .border(1.5.dp, if (checked) QkColor.Primary else QkColor.Border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text("✓", color = Color.White, fontSize = 12.sp, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * 五态路由：按 [PrivacyAuthUiState.step] 呈现对应 view。
 *
 * 外部交接点（授权写入经此交由既有流程，本组件不落库）：
 * - [onConsentGranted] 同意授权（[includeCloudSync] = 是否含云端同步）
 * - [onConsentWithdrawn] 确认撤回全部授权
 * - [onAskSuggestion] 咨询页点击引导问题
 * - [onDeleteOperation] 规则页选中某类删除操作
 */
@Composable
fun PrivacyAuthFlow(
    state: PrivacyAuthUiState,
    onIntent: (PrivacyAuthIntent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onConsentGranted: (includeCloudSync: Boolean) -> Unit = {},
    onConsentWithdrawn: () -> Unit = {},
    onAskSuggestion: (String) -> Unit = {},
    onDeleteOperation: (DeleteOp) -> Unit = {},
) {
    when (state.step) {
        PrivacyAuthStep.Auth -> PrivacyAuthView(
            state = state,
            onIntent = { intent ->
                when (intent) {
                    PrivacyAuthIntent.AgreeAndContinue -> onConsentGranted(state.cloudSyncSelected)
                    PrivacyAuthIntent.LocalOnly -> onConsentGranted(false)
                    else -> Unit
                }
                onIntent(intent)
            },
            onBack = onBack,
            modifier = modifier,
        )

        PrivacyAuthStep.Granted -> PrivacyGrantedView(
            state = state,
            onIntent = { intent ->
                if (intent == PrivacyAuthIntent.ConfirmWithdraw) onConsentWithdrawn()
                onIntent(intent)
            },
            onBack = onBack,
            modifier = modifier,
        )

        PrivacyAuthStep.Unauth -> PrivacyUnauthView(state, onIntent, onBack, modifier)
        PrivacyAuthStep.Consult -> PrivacyConsultView(state, onIntent, onBack, onAskSuggestion, modifier)
        PrivacyAuthStep.Rules -> PrivacyRulesView(onBack, onDeleteOperation, modifier)
    }
}
