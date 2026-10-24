package com.xcwk.scalehub.feature.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkProgress
import com.xcwk.scalehub.ui.components.QkSegmented
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkStroke

/**
 * feedback-flow — 反馈提交、提交结果与诊断授权（T-093 · 原型08
 * v-feedback / v-feedback-submit / v-feedback-success / v-feedback-fail / v-diag）。
 *
 * ## 验收（AC-N3 then 第 3–8 条）
 * - 反馈填写页：问题类型三选一 + 描述 + 隐私说明 + 提交
 * - 提交中 → 成功（工单号）/ 失败（保留已填内容）→ 重试
 * - 诊断授权页：三项可勾选，健康数据项单独授权，未授权不上传
 *
 * ## 呈现式约束（AC-N3 forbidden 原文）
 * 1. 提交反馈、上传诊断日志一律经回调（[HelpCenterFlow] 的 onSubmitFeedback /
 *    onCopyDiagnostics），呈现层不碰网络、蓝牙、系统日志与数据库；
 * 2. 诊断项未勾选任何一项时不允许上传（[DiagnosticConsent.canUpload]）；
 * 3. 失败态保留草稿（[feedbackReduce] 在 SubmitFailed 时不动 draft）；
 * 4. 颜色/间距一律 Qk token。
 *
 * ## 状态机
 * 七态与原型七 view 一一对应；[feedbackReduce] 是本流程唯一的纯函数推进表，
 * 副作用（真实提交、真实上传）全部留在调用方。
 */

// ---- 静态文案（逐字取自原型，JVM 单测校验） ----

/** 原型五个反馈/诊断 view 的文案（逐字，勿改）。 */
object FeedbackText {
    // v-feedback
    const val FormTopBarTitle = "意见反馈"
    const val TypeLabel = "问题类型"
    const val DescriptionLabel = "描述"
    const val DescriptionHint = "描述你遇到的问题或建议…"
    const val PrivacyBanner = "默认不收集健康数据，诊断日志需单独授权。"
    const val SimulateFailure = "模拟失败"
    const val Submit = "提交"

    // v-feedback-submit
    const val SubmittingTitle = "正在提交反馈…"
    const val SubmittingHint = "请勿关闭页面"
    const val SimulateDone = "模拟完成 → 成功"

    // v-feedback-success
    const val SuccessTitle = "反馈已提交"
    const val SuccessTicketLabel = "工单号"
    const val SuccessHint = "1–2 个工作日内回复。"
    const val BackToHelp = "返回帮助中心"
    const val ContinueFeedback = "继续反馈"

    // v-feedback-fail
    const val FailTitle = "反馈提交失败"
    const val FailBody = "网络开小差了，反馈未发送。"
    const val FailKept = "填写内容已保留。"
    const val Retry = "重新提交"
    const val BackToEdit = "返回修改"

    // v-diag
    const val DiagTopBarTitle = "诊断信息"
    const val DiagTitle = "上传诊断日志？"
    const val DiagHint = "用于更快定位问题。"
    const val DiagNote = "日志 30 天后自动删除，可随时撤回。"
    const val DiagConsent = "同意并上传"
    const val DiagAppLog = "应用运行日志"
    const val DiagAppLogDesc = "不含健康数据与个人信息"
    const val DiagBluetoothLog = "蓝牙连接日志"
    const val DiagMeasurements = "测量数值"
    const val DiagMeasurementsDesc = "可关闭"
    const val CopyDiagnostics = "复制诊断信息"
}

/** 描述长度上限（超出即不可提交，规则见 [FeedbackDraft.validation]）。 */
const val FEEDBACK_DESCRIPTION_MAX = 500

/**
 * 原型示例工单号。真实工单号由提交结果注入（[HelpCenterIntent.SubmitSucceeded]）；
 * 本常量只供原型「模拟完成」按钮与无后端演示兜底。
 */
const val PROTOTYPE_TICKET_NO = "#FB-20261022"

// ---- 反馈类型与草稿 ----

/** 反馈问题类型（原型 v-feedback 三分段）。 */
enum class FeedbackType(val label: String) {
    Measure("测量"),
    Connect("连接"),
    Other("其他"),
}

/**
 * 反馈草稿：类型 + 描述。
 *
 * [validation] 与 [canSubmit] 是纯函数口径，UI 只消费结果：
 * - 描述空白（空串或纯空格）→ [FeedbackValidation.EmptyDescription]
 * - 描述超过 [FEEDBACK_DESCRIPTION_MAX] 字 → [FeedbackValidation.TooLong]
 * - 类型已选 + 描述合法 → 可提交
 */
data class FeedbackDraft(
    val type: FeedbackType? = FeedbackType.Measure,
    val description: String = "",
) {
    val validation: FeedbackValidation
        get() = when {
            description.isBlank() -> FeedbackValidation.EmptyDescription
            description.length > FEEDBACK_DESCRIPTION_MAX -> FeedbackValidation.TooLong
            else -> FeedbackValidation.Ready
        }

    val canSubmit: Boolean get() = type != null && validation == FeedbackValidation.Ready

    /** 是否已填写过内容（失败态「填写内容已保留」的判定依据）。 */
    val hasContent: Boolean get() = type != null || description.isNotBlank()
}

/** 草稿校验结果。 */
enum class FeedbackValidation { EmptyDescription, TooLong, Ready }

// ---- 诊断项授权 ----

/** 三个可勾选的诊断项（原型 v-diag 三行）。 */
enum class DiagnosticItemKind { AppLog, BluetoothLog, Measurements }

/**
 * 诊断项授权状态。
 *
 * 默认值与原型一致：应用日志、蓝牙日志默认勾选，测量数值默认关闭（可关闭 = 默认关）。
 * [canUpload] 在「一项都没勾」时为 false —— 未授权不上传（AC forbidden）。
 * [includesHealthData] 标示是否包含健康数据，仅当测量数值被单独勾选时为 true。
 */
data class DiagnosticConsent(
    val appLog: Boolean = true,
    val bluetoothLog: Boolean = true,
    val measurements: Boolean = false,
) {
    val anySelected: Boolean get() = appLog || bluetoothLog || measurements
    val canUpload: Boolean get() = anySelected
    val includesHealthData: Boolean get() = measurements
}

/** 诊断项切换（纯函数）。 */
fun DiagnosticConsent.toggled(kind: DiagnosticItemKind): DiagnosticConsent = when (kind) {
    DiagnosticItemKind.AppLog -> copy(appLog = !appLog)
    DiagnosticItemKind.BluetoothLog -> copy(bluetoothLog = !bluetoothLog)
    DiagnosticItemKind.Measurements -> copy(measurements = !measurements)
}

// ---- 全流程状态与推进表 ----

/** 帮助中心流程七态（与原型七个 view 一一对应）。 */
enum class HelpCenterStep { Help, Faq, Feedback, Submitting, Success, Failed, Diagnostics }

/** 呈现式 UI 状态：全部字段由调用方注入/推进，组件不自建业务状态。 */
data class HelpCenterUiState(
    val step: HelpCenterStep = HelpCenterStep.Help,
    /** v-help 搜索框内容（过滤在调用方） */
    val searchQuery: String = "",
    /** 正在查看的 FAQ id（null = 未展开详情） */
    val openFaqId: String? = null,
    /** 已上报的 FAQ 评价（按 id 留存，便于回列表后展示） */
    val faqVerdicts: Map<String, FaqVerdict> = emptyMap(),
    val draft: FeedbackDraft = FeedbackDraft(),
    /** 提交成功后的工单号（外部注入，如 #FB-20261022） */
    val ticketNo: String = "",
    val diagnostics: DiagnosticConsent = DiagnosticConsent(),
) {
    /** 失败态是否仍保留着已填内容（原型「填写内容已保留。」的判定）。 */
    val draftRetainedOnFailure: Boolean
        get() = step == HelpCenterStep.Failed && draft.hasContent
}

/** 全流程事件。外部结果（提交成功/失败）由调用方以事件形式回灌。 */
sealed interface HelpCenterIntent {
    // v-help
    data class SearchChanged(val text: String) : HelpCenterIntent
    data class OpenFaq(val faqId: String) : HelpCenterIntent
    data object CloseFaq : HelpCenterIntent
    data class JudgeFaq(val faqId: String, val verdict: FaqVerdict) : HelpCenterIntent
    data object OpenFeedback : HelpCenterIntent
    data object OpenDiagnostics : HelpCenterIntent
    data object BackToHelp : HelpCenterIntent

    // v-feedback
    data class SelectFeedbackType(val type: FeedbackType) : HelpCenterIntent
    data class DescriptionChanged(val text: String) : HelpCenterIntent
    data object SubmitFeedback : HelpCenterIntent
    data object SimulateFailure : HelpCenterIntent
    data class SubmitSucceeded(val ticketNo: String) : HelpCenterIntent
    data object SubmitFailed : HelpCenterIntent

    // v-feedback-submit / success / fail
    data object RetrySubmit : HelpCenterIntent
    data object BackToEdit : HelpCenterIntent
    data object ContinueFeedback : HelpCenterIntent

    // v-diag
    data class ToggleDiagnosticItem(val kind: DiagnosticItemKind) : HelpCenterIntent
    data object ConsentAndUpload : HelpCenterIntent
}

/**
 * 全流程推进表 —— JVM 单测钉死的部分。
 *
 * 口径：
 * - 草稿不合法时 [HelpCenterIntent.SubmitFeedback] / [HelpCenterIntent.RetrySubmit] 不推进
 * - [HelpCenterIntent.SubmitSucceeded] 只在提交中生效（防止乱序回包直接跳成功）
 * - [HelpCenterIntent.SubmitFailed] / [HelpCenterIntent.SimulateFailure] 保留草稿，
 *   失败页「填写内容已保留」由此成立
 * - [HelpCenterIntent.ContinueFeedback] 清空草稿进入新反馈
 * - [HelpCenterIntent.ConsentAndUpload] 在未勾选任何诊断项时不推进（不上传未授权日志）
 */
fun feedbackReduce(state: HelpCenterUiState, intent: HelpCenterIntent): HelpCenterUiState = when (intent) {
    is HelpCenterIntent.SearchChanged -> state.copy(searchQuery = intent.text)

    is HelpCenterIntent.OpenFaq -> state.copy(step = HelpCenterStep.Faq, openFaqId = intent.faqId)
    HelpCenterIntent.CloseFaq -> state.copy(step = HelpCenterStep.Help, openFaqId = null)
    is HelpCenterIntent.JudgeFaq -> state.copy(
        step = HelpCenterStep.Help,
        openFaqId = null,
        faqVerdicts = state.faqVerdicts + (intent.faqId to intent.verdict),
    )

    HelpCenterIntent.OpenFeedback -> state.copy(step = HelpCenterStep.Feedback)
    HelpCenterIntent.OpenDiagnostics -> state.copy(step = HelpCenterStep.Diagnostics)
    HelpCenterIntent.BackToHelp -> state.copy(step = HelpCenterStep.Help, openFaqId = null)

    is HelpCenterIntent.SelectFeedbackType -> state.copy(draft = state.draft.copy(type = intent.type))
    is HelpCenterIntent.DescriptionChanged -> state.copy(draft = state.draft.copy(description = intent.text))
    HelpCenterIntent.SubmitFeedback ->
        if (state.draft.canSubmit) state.copy(step = HelpCenterStep.Submitting) else state

    HelpCenterIntent.SimulateFailure -> state.copy(step = HelpCenterStep.Failed)
    is HelpCenterIntent.SubmitSucceeded ->
        if (state.step == HelpCenterStep.Submitting) {
            state.copy(step = HelpCenterStep.Success, ticketNo = intent.ticketNo)
        } else {
            state
        }

    HelpCenterIntent.SubmitFailed ->
        if (state.step == HelpCenterStep.Submitting) state.copy(step = HelpCenterStep.Failed) else state

    HelpCenterIntent.RetrySubmit ->
        if (state.draft.canSubmit) state.copy(step = HelpCenterStep.Submitting) else state
    HelpCenterIntent.BackToEdit -> state.copy(step = HelpCenterStep.Feedback)
    HelpCenterIntent.ContinueFeedback ->
        state.copy(step = HelpCenterStep.Feedback, draft = FeedbackDraft(), ticketNo = "")

    is HelpCenterIntent.ToggleDiagnosticItem ->
        state.copy(diagnostics = state.diagnostics.toggled(intent.kind))
    HelpCenterIntent.ConsentAndUpload ->
        if (state.diagnostics.canUpload) state.copy(step = HelpCenterStep.Help) else state
}

// ---- v-feedback 反馈填写 ----

/**
 * 反馈填写（原型 v-feedback）。
 *
 * [onSubmit] 的可用性由 [FeedbackDraft.canSubmit] 决定（空描述/超长时按钮置灰），
 * 真实提交由调用方注入的 onSubmitFeedback 完成。
 */
@Composable
fun FeedbackFormScreen(
    draft: FeedbackDraft,
    onSelectType: (FeedbackType) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onSimulateFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val types = FeedbackType.entries
    Column(modifier = modifier.fillMaxSize().padding(horizontal = QkSpacing.Page)) {
        HelpNavBar(title = FeedbackText.FormTopBarTitle, onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            Text(
                FeedbackText.TypeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.FieldLabel,
            )
            QkSegmented(
                items = types.map { it.label },
                selectedIndex = types.indexOf(draft.type),
                onSelect = { index -> onSelectType(types[index]) },
            )
            QkTextField(
                value = draft.description,
                onValueChange = onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = FeedbackText.DescriptionLabel,
                placeholder = FeedbackText.DescriptionHint,
                singleLine = false,
            )
            HelpBanner(icon = QkIcons.Privacy, text = FeedbackText.PrivacyBanner)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkButton(
                text = FeedbackText.SimulateFailure,
                modifier = Modifier.weight(1f),
                style = QkButtonStyle.Secondary,
                onClick = onSimulateFailure,
            )
            QkButton(
                text = FeedbackText.Submit,
                modifier = Modifier.weight(1f),
                enabled = draft.canSubmit,
                onClick = onSubmit,
            )
        }
    }
}

// ---- v-feedback-submit 提交中 ----

/** 提交中（原型 v-feedback-submit）：转圈占位 + 进度条 + 原型「模拟完成」按钮。 */
@Composable
fun FeedbackSubmitScreen(
    onBack: () -> Unit,
    onSimulateDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = QkSpacing.Page)) {
        HelpNavBar(title = FeedbackText.FormTopBarTitle, onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkIconBubble(
                icon = QkIcons.Send,
                tone = QkGlyphTone.Green,
                size = QkSpacing.Xl * 2,
                contentDescription = FeedbackText.SubmittingTitle,
            )
            Text(
                FeedbackText.SubmittingTitle,
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
            Text(
                FeedbackText.SubmittingHint,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }
        QkCard(modifier = Modifier.padding(top = QkSpacing.Xl)) {
            QkProgress(percent = 60)
        }
        Spacer(Modifier.weight(1f))
        QkButton(
            text = FeedbackText.SimulateDone,
            modifier = Modifier.fillMaxWidth().padding(bottom = QkSpacing.Md),
            style = QkButtonStyle.Secondary,
            onClick = onSimulateDone,
        )
    }
}

// ---- v-feedback-success / v-feedback-fail 结果页共用版式 ----

/** 结果页版式（原型 .statepage：图标泡 + 标题 + 两行说明 + 主/幽灵按钮）。 */
@Composable
private fun FeedbackResultPage(
    icon: ImageVector,
    tone: QkGlyphTone,
    title: String,
    lines: List<String>,
    primaryText: String,
    secondaryText: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = QkSpacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        QkIconBubble(
            icon = icon,
            tone = tone,
            size = QkSpacing.Xl * 3,
            contentDescription = title,
        )
        Spacer(Modifier.height(QkSpacing.Lg))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(QkSpacing.Md))
        lines.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = primaryText,
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrimary,
        )
        Spacer(Modifier.height(QkSpacing.Md))
        QkButton(
            text = secondaryText,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Ghost,
            onClick = onSecondary,
        )
    }
}

/** 提交成功（原型 v-feedback-success）：工单号由外部注入。 */
@Composable
fun FeedbackSuccessScreen(
    ticketNo: String,
    onBackToHelp: () -> Unit,
    onContinueFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedbackResultPage(
        icon = QkIcons.Habit,
        tone = QkGlyphTone.Green,
        title = FeedbackText.SuccessTitle,
        lines = listOf("${FeedbackText.SuccessTicketLabel} $ticketNo", FeedbackText.SuccessHint),
        primaryText = FeedbackText.BackToHelp,
        secondaryText = FeedbackText.ContinueFeedback,
        onPrimary = onBackToHelp,
        onSecondary = onContinueFeedback,
        modifier = modifier,
    )
}

/** 提交失败（原型 v-feedback-fail）。草稿保留由状态机保证，本页只呈现。 */
@Composable
fun FeedbackFailScreen(
    onRetry: () -> Unit,
    onBackToEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedbackResultPage(
        icon = Icons.Outlined.WarningAmber,
        tone = QkGlyphTone.Orange,
        title = FeedbackText.FailTitle,
        lines = listOf(FeedbackText.FailBody, FeedbackText.FailKept),
        primaryText = FeedbackText.Retry,
        secondaryText = FeedbackText.BackToEdit,
        onPrimary = onRetry,
        onSecondary = onBackToEdit,
        modifier = modifier,
    )
}

// ---- v-diag 诊断授权 ----

/** 诊断勾选框（原型 .chk：22dp 圆角方块，选中主绿底白勾）。 */
@Composable
private fun DiagnosticCheck(checked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(QkRadius.Field)
    Box(
        modifier = Modifier
            .size(QkSpacing.Xl)
            .clip(shape)
            .background(if (checked) QkColor.Primary else Color.Transparent)
            .border(QkStroke.Thin, if (checked) QkColor.Primary else QkColor.Border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text("✓", color = QkColor.Card, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** 诊断项一行：主副文案 + 勾选框。 */
@Composable
private fun DiagnosticItemRow(
    title: String,
    desc: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
            if (desc != null) {
                Text(
                    desc,
                    modifier = Modifier.padding(top = QkSpacing.Xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
        }
        DiagnosticCheck(checked = checked, onClick = onToggle)
    }
}

/**
 * 诊断授权（原型 v-diag）。
 *
 * [diagnosticsSummary] 是调用方注入的诊断摘要文本（可为空串：未接入时不出复制入口）；
 * 复制动作经 [onCopyDiagnostics] 交由调用方（剪贴板属系统能力，留在宿主）。
 */
@Composable
fun DiagnosticsScreen(
    consent: DiagnosticConsent,
    onToggle: (DiagnosticItemKind) -> Unit,
    onBack: () -> Unit,
    onConsentAndUpload: () -> Unit,
    modifier: Modifier = Modifier,
    diagnosticsSummary: String = "",
    onCopyDiagnostics: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = QkSpacing.Page)) {
        HelpNavBar(title = FeedbackText.DiagTopBarTitle, onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Lg),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                QkIconBubble(
                    icon = QkIcons.Agreement,
                    tone = QkGlyphTone.Green,
                    size = QkSpacing.Xl * 2,
                    contentDescription = FeedbackText.DiagTitle,
                )
                Spacer(Modifier.height(QkSpacing.Lg))
                Text(
                    FeedbackText.DiagTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = QkColor.Fg,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(QkSpacing.Xs))
                Text(
                    FeedbackText.DiagHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                    textAlign = TextAlign.Center,
                )
            }
            QkCard {
                DiagnosticItemRow(
                    title = FeedbackText.DiagAppLog,
                    desc = FeedbackText.DiagAppLogDesc,
                    checked = consent.appLog,
                    onToggle = { onToggle(DiagnosticItemKind.AppLog) },
                )
                DiagnosticItemRow(
                    title = FeedbackText.DiagBluetoothLog,
                    desc = null,
                    checked = consent.bluetoothLog,
                    onToggle = { onToggle(DiagnosticItemKind.BluetoothLog) },
                )
                DiagnosticItemRow(
                    title = FeedbackText.DiagMeasurements,
                    desc = FeedbackText.DiagMeasurementsDesc,
                    checked = consent.measurements,
                    onToggle = { onToggle(DiagnosticItemKind.Measurements) },
                )
            }
            Text(
                FeedbackText.DiagNote,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
            if (diagnosticsSummary.isNotEmpty()) {
                QkCard(onClick = { onCopyDiagnostics(diagnosticsSummary) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            diagnosticsSummary,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.Sub,
                        )
                        Text(
                            FeedbackText.CopyDiagnostics,
                            modifier = Modifier.padding(start = QkSpacing.Sm),
                            style = MaterialTheme.typography.labelMedium,
                            color = QkColor.Primary,
                        )
                    }
                }
            }
        }
        QkButton(
            text = FeedbackText.DiagConsent,
            modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
            enabled = consent.canUpload,
            onClick = onConsentAndUpload,
        )
    }
}
