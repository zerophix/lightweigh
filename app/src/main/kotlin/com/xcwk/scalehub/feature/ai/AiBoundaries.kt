package com.xcwk.scalehub.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyph
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.components.QkSkeletonBlock
import com.xcwk.scalehub.ui.components.QkSkeletonCircle
import com.xcwk.scalehub.ui.components.QkSkeletonLine
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkComponentSize
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * ai — AI 边界与降级状态（T-085 · Wave 1，原型 25 `25-ai-boundaries.html`）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/25-ai-boundaries.html`
 * - 验收：AC-J1-12「AI 边界与降级状态」，六个 view：
 *   `v-ai-unavailable` / `v-report-expired` / `v-reanalyzing` / `v-report-done` /
 *   `v-unauth-consult` / `v-member-unauth`
 *
 * ## 范围（关键约束）
 * 本任务**只新建本文件**，不改既有 AI 报告页（`AiScreen` / `AiViewModel` 等），
 * 也不重写报告生成主路径：主路径仍归原型 16 `v-ai-report`，本文件只接管异常态。
 *
 * ## 呈现式约束
 * 1. 只渲染传入的 [AiBoundaryUiState]，不自建状态、不读仓库；
 * 2. 重试连接、重新分析、开启个性化、切换成员、发送咨询一律经回调交给调用方，
 *    本文件不 import 任何网络 / 数据库 / ViewModel；
 * 3. 边界判定与可展示内容判定是[纯函数]（[resolveAiBoundaryStage] /
 *    [canShowPersonalConclusion]），JVM 单测钉死；
 * 4. 文案逐字取自原型，禁止改写；颜色/间距/圆角一律取 Qk token，禁止裸 dp 与色值。
 *
 * ## 状态机口径（可用性 → 授权范围 → 数据新鲜度）
 * 判定顺序写死在 [resolveAiBoundaryStage]：
 * 1. 服务不可用时，任何个性化内容都不可信 → [AiBoundaryStage.Unavailable]；
 * 2. 授权范围其次：未授权成员 / 未开启个性化都不得展示针对个人的结论
 *    （若把新鲜度排在授权之前，会给未授权成员渲染出「体重下降 1.4 kg」这类结论，
 *    正是验收 forbidden 明令禁止的）；
 * 3. 最后才是数据新鲜度与重算进度 → 旧报告 / 分析中 / 分析完成；
 * 4. 全部健康时返回 `null`：本组件让位，由主报告页渲染。
 */

// ---- 逐字文案（原型 25；JVM 单测逐条校验） ----

/** 原型 25 的六屏文案与规格。逐字照抄，改动即验收失败。 */
object AiBoundaryText {
    // 共用
    const val TitleReport = "AI 报告解读"
    const val TitleAssistant = "AI 健康助手"
    const val Disclaimer = "AI 建议仅供参考，不作疾病诊断。"
    const val Back = "返回"

    // v-ai-unavailable
    const val UnavailableTitle = "AI 服务暂时不可用"
    const val UnavailableDescLead = "AI 服务正在维护，\n预计 "
    const val UnavailableEta = "15 分钟"
    const val UnavailableDescTail = "内恢复。\n你的测量与记录不受影响。"
    const val UnavailableAffectedLine = "受影响：AI 解读 · 周总结 · 行动建议"
    const val UnavailableUnaffectedLine = "不受影响：测量 · 记录 · 趋势图表"
    const val RetryConnection = "重试连接"
    const val ViewHistoryReports = "先看历史报告"

    // v-report-expired
    const val ExpiredBannerLead = "你有 "
    const val ExpiredBannerTail = "，\n本报告基于旧数据生成。"
    const val OldReportTag = "旧报告"
    const val OldReportDate = "10月20日"
    const val OldConclusion = "近 30 天体重下降 1.4 kg，趋势稳健。"
    const val UpdateHeader = "重新分析后将更新"
    const val UpdateItemTrend = "更新体重/体脂趋势结论"
    const val UpdateItemAdvice = "重新生成可执行建议"
    const val HistoryKept = "旧报告保留在历史中，不会被覆盖。"
    const val ReanalyzeAction = "用最新数据重新分析"
    const val ContinueOldReport = "继续查看旧报告"

    // v-reanalyzing
    const val ReanalyzingTitle = "正在重新分析…"
    const val ReanalyzingEta = "约需 10–20 秒"

    // v-report-done
    const val NewReportTag = "新报告"
    const val LatestReportDate = "10月21日"
    const val NewConclusion = "近 30 天体重下降 1.6 kg，纳入最新数据后趋势更清晰。"
    const val DeltaHighlight = "1.6 kg"
    const val DeltaTail = "，体脂率下降 1.5 个百分点。趋势与减重目标一致。"

    // v-unauth-consult
    const val ConsultBannerLead = "未开启个性化分析，AI "
    const val ConsultBannerHighlight = "不使用你的健康数据"
    const val ConsultBannerTail = "，\n仅提供通用健康知识。"
    const val ConsultIntro = "通用健康问题都可以问我。要获得针对你数据的解读，请先开启个性化分析。"
    const val ConsultNote = "未开启个性化时，AI 不会读取你的测量记录、健康档案和目标。"
    const val EnablePersonalization = "开启个性化分析"
    const val ConsultHint = "问问健康知识…"

    // v-member-unauth
    const val DefaultMemberName = "妈妈"
    const val MemberViewingPrefix = "当前查看："
    const val MemberAiLine = "AI 解读 · 未授权"
    const val MemberUnauthTag = "未授权"
    const val MemberUnauthTitle = "该成员未开启 AI 解读"
    const val SwitchMember = "切换到已授权成员"

    /** 「你有 N 条新测量记录」中的计量短语。 */
    fun recordsCount(count: Int): String = "$count 条新测量记录"

    /** 「纳入最新 N 条测量记录」（过期页更新项 + 分析中副文案共用）。 */
    fun recordsIncluded(count: Int): String = "纳入最新 $count 条测量记录"

    /** 分析完成横幅「已基于最新 N 条记录重新分析。」 */
    fun doneBanner(count: Int): String = "已基于最新 $count 条记录重新分析。"

    /** 结论修正说明的前半句（后半句固定 [DeltaTail]，加粗只在 [DeltaHighlight] 上）。 */
    fun deltaLead(count: Int): String = "纳入最新 $count 条记录后，30 天体重下降从 1.4 kg 修正为 "

    /** 旧报告时间戳「基于 10月20日数据」。 */
    fun basedOnOldReport(date: String): String = "基于 $date" + "数据"

    /** 新报告时间戳「基于 10月21日最新数据」。 */
    fun basedOnLatestReport(date: String): String = "基于 $date" + "最新数据"

    /** 成员未授权正文；成员名由状态注入，文案模板逐字取自原型。 */
    fun memberUnauthDesc(name: String): String =
        "「$name」尚未授权 AI 使用她的健康数据。\nAI 无法为她生成报告解读。"

    /** 「邀请妈妈开启」。 */
    fun inviteMember(name: String): String = "邀请$name" + "开启"
}

/** 原型 v-report-expired「重新分析后将更新」三条（条数随状态变化）。 */
fun updatePreviewItems(newRecordCount: Int): List<String> = listOf(
    AiBoundaryText.recordsIncluded(newRecordCount),
    AiBoundaryText.UpdateItemTrend,
    AiBoundaryText.UpdateItemAdvice,
)

/** 原型 v-unauth-consult 的三个示例问题。 */
val AI_CONSULT_SUGGESTIONS: List<String> = listOf(
    "BMI 22.1 正常吗？",
    "体脂率怎么看？",
    "如何健康减脂？",
)

// ---- 边界维度与状态机 ----

/** AI 服务可用性。 */
enum class AiServiceAvailability { Available, Unavailable }

/** 报告数据新鲜度（是否有新测量记录未纳入）。 */
enum class AiDataFreshness { Fresh, Stale }

/** AI 可读取的数据范围。 */
enum class AiDataScope {
    /** 已开启个性化分析：可解读本成员数据 */
    Personalized,

    /** 未开启个性化：仅通用健康知识 */
    GenericOnly,

    /** 当前查看的家庭成员未授权 */
    MemberUnauthorized,
}

/** 重新分析的进度（只在数据过期路径上有意义）。 */
enum class AiReanalysisPhase { Idle, Running, Done }

/** 六个边界屏（与原型六个 view 一一对应）。 */
enum class AiBoundaryStage { Unavailable, ReportExpired, Reanalyzing, ReportDone, UnauthConsult, MemberUnauth }

/**
 * 呈现式状态。
 *
 * [stage] 为 `null` 表示「无边界」：AI 服务可用、数据新鲜、已授权，
 * 由主报告页（原型 16）渲染，本组件不接管。
 */
data class AiBoundaryUiState(
    val availability: AiServiceAvailability = AiServiceAvailability.Available,
    val freshness: AiDataFreshness = AiDataFreshness.Fresh,
    val scope: AiDataScope = AiDataScope.Personalized,
    val reanalysis: AiReanalysisPhase = AiReanalysisPhase.Idle,
    val stage: AiBoundaryStage? = null,
    /** 未纳入报告的新测量记录条数（原型为 2） */
    val newRecordCount: Int = DEFAULT_NEW_RECORD_COUNT,
    /** 旧报告数据日期（原型 10月20日） */
    val oldReportDate: String = AiBoundaryText.OldReportDate,
    /** 新报告数据日期（原型 10月21日） */
    val latestReportDate: String = AiBoundaryText.LatestReportDate,
    /** 当前查看的成员名（原型 妈妈） */
    val memberName: String = AiBoundaryText.DefaultMemberName,
    /** 旧报告结论（原型示例文案，真实报告由调用方注入） */
    val oldConclusion: String = AiBoundaryText.OldConclusion,
    /** 新报告结论（原型示例文案，真实报告由调用方注入） */
    val newConclusion: String = AiBoundaryText.NewConclusion,
    /** 咨询输入框草稿 */
    val consultDraft: String = "",
) {
    val oldReportBasedOn: String get() = AiBoundaryText.basedOnOldReport(oldReportDate)

    val latestReportBasedOn: String get() = AiBoundaryText.basedOnLatestReport(latestReportDate)

    val memberViewing: String get() = AiBoundaryText.MemberViewingPrefix + memberName

    val memberUnauthDesc: String get() = AiBoundaryText.memberUnauthDesc(memberName)

    val inviteMemberLabel: String get() = AiBoundaryText.inviteMember(memberName)

    companion object {
        const val DEFAULT_NEW_RECORD_COUNT = 2
    }
}

/** 边界屏上的可判定事件。真实副作用（重试/重算/授权/发问）经回调外出，不进 reduce。 */
sealed interface AiBoundaryIntent {
    /** 点「重试连接」：只触发回调，状态等 [ConnectionRestored] 再变 */
    data object RetryConnection : AiBoundaryIntent

    /** 调用方通知连接已恢复 */
    data object ConnectionRestored : AiBoundaryIntent

    /** 「先看历史报告」（原型 data-goto 落在未授权咨询页） */
    data object ViewHistoryReports : AiBoundaryIntent

    /** 「用最新数据重新分析」：进入分析中（真实重算经回调触发） */
    data object StartReanalysis : AiBoundaryIntent

    /** 调用方通知重新分析完成 */
    data object ReanalysisCompleted : AiBoundaryIntent

    /** 「继续查看旧报告」：出栈由调用方处理，本状态不变 */
    data object ContinueOldReport : AiBoundaryIntent

    /** 「开启个性化分析」：跳授权页由调用方处理，本状态不变 */
    data object OpenPersonalization : AiBoundaryIntent

    /** 调用方通知个性化分析已开启 */
    data object PersonalizationEnabled : AiBoundaryIntent

    data class ConsultInputChanged(val text: String) : AiBoundaryIntent

    data object SendConsult : AiBoundaryIntent

    /** 「切换到已授权成员」：切换由调用方处理 */
    data object SwitchMember : AiBoundaryIntent

    /** 「邀请妈妈开启」：发邀请由调用方处理 */
    data object InviteMember : AiBoundaryIntent

    /** 调用方通知该成员已授权 */
    data object MemberAuthorizationGranted : AiBoundaryIntent
}

/**
 * 边界屏分发（纯函数）——验收 DoD 要求的「可用性 → 授权范围 → 数据新鲜度」判定。
 *
 * 返回 `null` 表示没有边界要接管（交还主报告页）。
 */
fun resolveAiBoundaryStage(
    availability: AiServiceAvailability,
    scope: AiDataScope,
    freshness: AiDataFreshness,
    reanalysis: AiReanalysisPhase = AiReanalysisPhase.Idle,
): AiBoundaryStage? = when {
    availability == AiServiceAvailability.Unavailable -> AiBoundaryStage.Unavailable
    scope == AiDataScope.MemberUnauthorized -> AiBoundaryStage.MemberUnauth
    scope == AiDataScope.GenericOnly -> AiBoundaryStage.UnauthConsult
    freshness == AiDataFreshness.Stale && reanalysis == AiReanalysisPhase.Running -> AiBoundaryStage.Reanalyzing
    freshness == AiDataFreshness.Stale -> AiBoundaryStage.ReportExpired
    reanalysis == AiReanalysisPhase.Running -> AiBoundaryStage.Reanalyzing
    reanalysis == AiReanalysisPhase.Done -> AiBoundaryStage.ReportDone
    else -> null
}

/** 状态版分发。 */
fun resolveAiBoundaryStage(state: AiBoundaryUiState): AiBoundaryStage? =
    resolveAiBoundaryStage(state.availability, state.scope, state.freshness, state.reanalysis)

/**
 * 是否允许展示「针对个人数据」的结论性解读（纯函数）。
 *
 * 服务不可用或数据范围不是本人已授权个性化时一律禁止 —— 验收 forbidden
 * 明确禁止在未授权/不可用时仍渲染结论。
 */
fun canShowPersonalConclusion(
    availability: AiServiceAvailability,
    scope: AiDataScope,
): Boolean = availability == AiServiceAvailability.Available && scope == AiDataScope.Personalized

/** 状态版可展示判定。 */
fun canShowPersonalConclusion(state: AiBoundaryUiState): Boolean =
    canShowPersonalConclusion(state.availability, state.scope)

/** 由边界维度构造初始状态（stage 由纯函数推导，调用方不手写）。 */
fun aiBoundaryStateOf(
    availability: AiServiceAvailability = AiServiceAvailability.Available,
    scope: AiDataScope = AiDataScope.Personalized,
    freshness: AiDataFreshness = AiDataFreshness.Fresh,
    reanalysis: AiReanalysisPhase = AiReanalysisPhase.Idle,
): AiBoundaryUiState = AiBoundaryUiState(
    availability = availability,
    scope = scope,
    freshness = freshness,
    reanalysis = reanalysis,
).recomputed()

/** 边界屏内的呈现态转移（纯函数，JVM 单测钉死）。 */
fun aiBoundaryReduce(state: AiBoundaryUiState, intent: AiBoundaryIntent): AiBoundaryUiState = when (intent) {
    // 按钮回调本身不改状态：连接是否恢复由调用方用 ConnectionRestored 通知
    AiBoundaryIntent.RetryConnection -> state
    AiBoundaryIntent.ConnectionRestored -> state.copy(
        availability = AiServiceAvailability.Available,
    ).recomputed()

    // 原型 v-ai-unavailable 的「先看历史报告」按钮 data-goto="v-unauth-consult"，
    // 按原型落到未授权咨询页；不自造第七屏
    AiBoundaryIntent.ViewHistoryReports -> state.copy(stage = AiBoundaryStage.UnauthConsult)
    AiBoundaryIntent.StartReanalysis -> state.copy(reanalysis = AiReanalysisPhase.Running).recomputed()
    AiBoundaryIntent.ReanalysisCompleted -> state.copy(
        reanalysis = AiReanalysisPhase.Done,
        freshness = AiDataFreshness.Fresh,
    ).recomputed()

    // 以下三个按钮的真实动作都在调用方（出栈 / 跳授权页 / 邀请），状态不变
    AiBoundaryIntent.ContinueOldReport -> state
    AiBoundaryIntent.OpenPersonalization -> state
    AiBoundaryIntent.InviteMember -> state
    AiBoundaryIntent.SwitchMember -> state

    AiBoundaryIntent.PersonalizationEnabled -> state.copy(
        scope = AiDataScope.Personalized,
    ).recomputed()
    AiBoundaryIntent.MemberAuthorizationGranted -> state.copy(
        scope = AiDataScope.Personalized,
    ).recomputed()

    is AiBoundaryIntent.ConsultInputChanged -> state.copy(consultDraft = intent.text)
    AiBoundaryIntent.SendConsult -> state.copy(consultDraft = "")
}

/** 按当前维度重算 stage（不给调用方手写 stage 的机会）。 */
private fun AiBoundaryUiState.recomputed(): AiBoundaryUiState = copy(stage = resolveAiBoundaryStage(this))

// ---- 六屏路由 ----

/**
 * AI 边界屏幕路由：按 [AiBoundaryUiState.stage] 渲染对应边界屏。
 *
 * 外部交接点（全部由调用方实现，本文件不触达网络/数据库/导航）：
 * - [onRetryConnection] 重试连接（成功后调用方回传 [AiBoundaryIntent.ConnectionRestored]）
 * - [onStartReanalysis] 触发真实重新分析（完成后回传 [AiBoundaryIntent.ReanalysisCompleted]）
 * - [onOpenPersonalization] 跳转个性化授权
 * - [onAskSuggestion] 点击示例问题 / 提交咨询问题
 * - [onSendConsult] 提交输入框里的问题
 * - [onSwitchMember] 切换到已授权成员
 * - [onInviteMember] 邀请该成员开启 AI 解读
 * - [onBack] 返回（含「继续查看旧报告」「返回」）
 */
@Composable
fun AiBoundariesScreen(
    state: AiBoundaryUiState,
    onIntent: (AiBoundaryIntent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onRetryConnection: () -> Unit = {},
    onStartReanalysis: () -> Unit = {},
    onOpenPersonalization: () -> Unit = {},
    onAskSuggestion: (String) -> Unit = {},
    onSendConsult: (String) -> Unit = {},
    onSwitchMember: () -> Unit = {},
    onInviteMember: () -> Unit = {},
) {
    when (state.stage) {
        // 无边界：交还主报告页（原型 16），本组件不渲染
        null -> Unit

        AiBoundaryStage.Unavailable -> AiBoundaryPage(
            title = AiBoundaryText.TitleAssistant,
            onBack = onBack,
            modifier = modifier,
        ) {
            AiUnavailableContent(
                state = state,
                onRetry = {
                    onRetryConnection()
                    onIntent(AiBoundaryIntent.RetryConnection)
                },
                onViewHistory = { onIntent(AiBoundaryIntent.ViewHistoryReports) },
            )
        }

        AiBoundaryStage.ReportExpired -> AiBoundaryPage(
            title = AiBoundaryText.TitleReport,
            onBack = onBack,
            modifier = modifier,
        ) {
            AiReportExpiredContent(
                state = state,
                onReanalyze = {
                    onStartReanalysis()
                    onIntent(AiBoundaryIntent.StartReanalysis)
                },
                onContinueOldReport = {
                    onIntent(AiBoundaryIntent.ContinueOldReport)
                    onBack()
                },
            )
        }

        AiBoundaryStage.Reanalyzing -> AiBoundaryPage(
            title = AiBoundaryText.TitleReport,
            onBack = onBack,
            modifier = modifier,
        ) {
            AiReanalyzingContent(state)
        }

        AiBoundaryStage.ReportDone -> AiBoundaryPage(
            title = AiBoundaryText.TitleReport,
            onBack = onBack,
            modifier = modifier,
        ) {
            AiReportDoneContent(state, onBack)
        }

        AiBoundaryStage.UnauthConsult -> AiBoundaryPage(
            title = AiBoundaryText.TitleAssistant,
            onBack = onBack,
            modifier = modifier,
        ) {
            AiUnauthConsultContent(
                state = state,
                onIntent = onIntent,
                onOpenPersonalization = {
                    onOpenPersonalization()
                    onIntent(AiBoundaryIntent.OpenPersonalization)
                },
                onAskSuggestion = onAskSuggestion,
                onSendConsult = onSendConsult,
            )
        }

        AiBoundaryStage.MemberUnauth -> AiBoundaryPage(
            title = AiBoundaryText.TitleReport,
            onBack = onBack,
            modifier = modifier,
        ) {
            AiMemberUnauthContent(
                state = state,
                onSwitchMember = {
                    onSwitchMember()
                    onIntent(AiBoundaryIntent.SwitchMember)
                },
                onInviteMember = {
                    onInviteMember()
                    onIntent(AiBoundaryIntent.InviteMember)
                },
            )
        }
    }
}

// ---- 六屏内容 ----

/** v-ai-unavailable：图标泡 + 标题 + 维护说明 + 影响范围 + 重试/历史报告。 */
@Composable
private fun AiUnavailableContent(
    state: AiBoundaryUiState,
    onRetry: () -> Unit,
    onViewHistory: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QkIconBubble(
            icon = QkIcons.Ai,
            tone = QkGlyphTone.Orange,
            size = QkSpacing.Xl * 3,
            contentDescription = AiBoundaryText.UnavailableTitle,
        )
        Spacer(Modifier.height(QkSpacing.Lg))
        Text(
            text = AiBoundaryText.UnavailableTitle,
            color = QkColor.Fg,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(QkSpacing.Md))
        HighlightedText(
            lead = AiBoundaryText.UnavailableDescLead,
            highlight = AiBoundaryText.UnavailableEta,
            tail = AiBoundaryText.UnavailableDescTail,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(QkSpacing.Lg))
        AiImpactCard()
        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = AiBoundaryText.RetryConnection,
            modifier = Modifier.fillMaxWidth(),
            onClick = onRetry,
        )
        Spacer(Modifier.height(QkSpacing.Md))
        QkButton(
            text = AiBoundaryText.ViewHistoryReports,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Ghost,
            onClick = onViewHistory,
        )
    }
}

/** 影响范围小卡（原型：浅底方框，两行「受影响 / 不受影响」）。 */
@Composable
private fun AiImpactCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(QkColor.MintSoft)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
    ) {
        ImpactLine(AiBoundaryText.UnavailableAffectedLine)
        ImpactLine(
            AiBoundaryText.UnavailableUnaffectedLine,
            modifier = Modifier.padding(top = QkSpacing.Xs),
        )
    }
}

@Composable
private fun ImpactLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = QkColor.Sub,
    )
}

/** v-report-expired：新记录横幅 + 旧报告卡 + 更新预告 + 历史保留说明 + 两个动作。 */
@Composable
private fun AiReportExpiredContent(
    state: AiBoundaryUiState,
    onReanalyze: () -> Unit,
    onContinueOldReport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md)) {
        AiBoundaryBanner(
            icon = QkIcons.History,
            lead = AiBoundaryText.ExpiredBannerLead,
            highlight = AiBoundaryText.recordsCount(state.newRecordCount),
            tail = AiBoundaryText.ExpiredBannerTail,
        )

        QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QkPill(text = AiBoundaryText.OldReportTag, tone = QkPillTone.Gray)
                Text(
                    text = state.oldReportBasedOn,
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Sub,
                )
            }
            if (canShowPersonalConclusion(state)) {
                Text(
                    text = state.oldConclusion,
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                    style = MaterialTheme.typography.titleLarge,
                    color = QkColor.Fg,
                )
            }
        }

        QkCard(
            modifier = Modifier.padding(top = QkSpacing.CardGap),
            padding = QkSpacing.CardPad,
        ) {
            Text(
                text = AiBoundaryText.UpdateHeader,
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
            Spacer(Modifier.height(QkSpacing.Sm))
            updatePreviewItems(state.newRecordCount).forEach { item -> UpdateItem(item) }
        }

        AiBoundaryBanner(
            icon = QkIcons.About,
            lead = AiBoundaryText.HistoryKept,
            modifier = Modifier.padding(top = QkSpacing.CardGap),
        )

        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = AiBoundaryText.ReanalyzeAction,
            modifier = Modifier.fillMaxWidth(),
            onClick = onReanalyze,
        )
        Spacer(Modifier.height(QkSpacing.Md))
        QkButton(
            text = AiBoundaryText.ContinueOldReport,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Secondary,
            onClick = onContinueOldReport,
        )
    }
}

@Composable
private fun UpdateItem(text: String) {
    Text(
        text = "· $text",
        modifier = Modifier.padding(top = QkSpacing.Xs),
        style = MaterialTheme.typography.bodySmall,
        color = QkColor.Sub,
    )
}

/** v-reanalyzing：加载指示 + 「正在重新分析…」+ 骨架占位；等待期不展示任何结论。 */
@Composable
private fun AiReanalyzingContent(state: AiBoundaryUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QkSkeletonCircle()
            Spacer(Modifier.height(QkSpacing.Lg))
            Text(
                text = AiBoundaryText.ReanalyzingTitle,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(QkSpacing.Sm))
            Text(
                text = AiBoundaryText.recordsIncluded(state.newRecordCount) + "\n" + AiBoundaryText.ReanalyzingEta,
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }

        // 骨架占位：等待期只示意结构，不放任何结论文案
        QkCard(modifier = Modifier.padding(top = QkSpacing.Xl)) {
            QkSkeletonLine(widthPercent = 60f)
            Spacer(Modifier.height(QkSpacing.Sm))
            QkSkeletonLine(widthPercent = 90f)
            Spacer(Modifier.height(QkSpacing.Sm))
            QkSkeletonLine(widthPercent = 80f)
            Spacer(Modifier.height(QkSpacing.Md))
            QkSkeletonBlock(height = QkSpacing.Xl * 2)
        }
    }
}

/** v-report-done：完成横幅 + 新报告卡 + 结论修正说明 + 免责声明 + 返回。 */
@Composable
private fun AiReportDoneContent(state: AiBoundaryUiState, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md)) {
        AiBoundaryBanner(
            icon = QkIcons.Habit,
            lead = AiBoundaryText.doneBanner(state.newRecordCount),
        )

        QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QkPill(text = AiBoundaryText.NewReportTag, tone = QkPillTone.Green)
                Text(
                    text = state.latestReportBasedOn,
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Sub,
                )
            }
            if (canShowPersonalConclusion(state)) {
                Text(
                    text = state.newConclusion,
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                    style = MaterialTheme.typography.titleLarge,
                    color = QkColor.Fg,
                )
            }
        }

        QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
            HighlightedText(
                lead = AiBoundaryText.deltaLead(state.newRecordCount),
                highlight = AiBoundaryText.DeltaHighlight,
                tail = AiBoundaryText.DeltaTail,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
            )
        }

        Text(
            text = AiBoundaryText.Disclaimer,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )

        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = AiBoundaryText.Back,
            modifier = Modifier.fillMaxWidth(),
            onClick = onBack,
        )
    }
}

/** v-unauth-consult：未授权横幅 + 示例问题 + 说明 + 开启个性化 + 咨询输入。 */
@Composable
private fun AiUnauthConsultContent(
    state: AiBoundaryUiState,
    onIntent: (AiBoundaryIntent) -> Unit,
    onOpenPersonalization: () -> Unit,
    onAskSuggestion: (String) -> Unit,
    onSendConsult: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md)) {
        AiBoundaryBanner(
            icon = QkIcons.Privacy,
            lead = AiBoundaryText.ConsultBannerLead,
            highlight = AiBoundaryText.ConsultBannerHighlight,
            tail = AiBoundaryText.ConsultBannerTail,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Md)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
        ) {
            AI_CONSULT_SUGGESTIONS.forEach { question ->
                Box(modifier = Modifier.clickable { onAskSuggestion(question) }) {
                    QkPill(text = question, tone = QkPillTone.Green)
                }
            }
        }

        QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
            Text(
                text = AiBoundaryText.ConsultIntro,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
            )
        }

        Text(
            text = AiBoundaryText.ConsultNote,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )

        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = AiBoundaryText.EnablePersonalization,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenPersonalization,
        )
        Spacer(Modifier.height(QkSpacing.Md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkTextField(
                value = state.consultDraft,
                onValueChange = { onIntent(AiBoundaryIntent.ConsultInputChanged(it)) },
                modifier = Modifier.weight(1f),
                placeholder = AiBoundaryText.ConsultHint,
            )
            Box(
                modifier = Modifier
                    .size(QkComponentSize.SkeletonAvatar)
                    .clip(CircleShape)
                    .background(QkColor.Mint)
                    .clickable {
                        onSendConsult(state.consultDraft)
                        onIntent(AiBoundaryIntent.SendConsult)
                    },
                contentAlignment = Alignment.Center,
            ) {
                QkIcon(QkIcons.Send, tint = QkColor.Primary, contentDescription = AiBoundaryText.ConsultHint)
            }
        }
    }
}

/** v-member-unauth：成员头（未授权标签）+ 图标泡 + 说明 + 切换/邀请。 */
@Composable
private fun AiMemberUnauthContent(
    state: AiBoundaryUiState,
    onSwitchMember: () -> Unit,
    onInviteMember: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(QkRadius.Card))
                .background(QkColor.Mint)
                .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkGlyph(
                text = state.memberName.take(1),
                tone = QkGlyphTone.Green,
                size = QkComponentSize.SkeletonAvatar,
            )
            Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
                Text(
                    text = state.memberViewing,
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Fg,
                )
                Text(
                    text = AiBoundaryText.MemberAiLine,
                    modifier = Modifier.padding(top = QkSpacing.Xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Sub,
                )
            }
            QkPill(text = AiBoundaryText.MemberUnauthTag, tone = QkPillTone.Amber)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QkIconBubble(
                icon = QkIcons.Privacy,
                tone = QkGlyphTone.Orange,
                size = QkSpacing.Xl * 3,
                contentDescription = AiBoundaryText.MemberUnauthTitle,
            )
            Spacer(Modifier.height(QkSpacing.Lg))
            Text(
                text = AiBoundaryText.MemberUnauthTitle,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(QkSpacing.Md))
            Text(
                text = state.memberUnauthDesc,
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(QkSpacing.Lg))
            QkButton(
                text = AiBoundaryText.SwitchMember,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSwitchMember,
            )
            Spacer(Modifier.height(QkSpacing.Md))
            QkButton(
                text = state.inviteMemberLabel,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Ghost,
                onClick = onInviteMember,
            )
        }
    }
}

// ---- 共享原语 ----

/** 边界屏骨架：导航条（返回 + 标题）+ 可滚动内容区。 */
@Composable
private fun AiBoundaryPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(
                title = title,
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
        }
        content()
    }
}

/** 边界页横幅（原型 .banner：薄荷底圆角条 + 线性图标 + 加粗关键短语）。 */
@Composable
private fun AiBoundaryBanner(
    icon: ImageVector,
    lead: String,
    modifier: Modifier = Modifier,
    highlight: String = "",
    tail: String = "",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(QkColor.Mint)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(icon, tint = QkColor.Primary, size = QkSpacing.Lg)
        HighlightedText(
            lead = lead,
            highlight = highlight,
            tail = tail,
            modifier = Modifier.padding(start = QkSpacing.Sm).weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * 带加粗短语的文案（原型里 `<b>` 的那些关键词）。
 *
 * @param highlight 为空时退化为整段普通文本（如「旧报告保留在历史中」横幅）
 */
@Composable
private fun HighlightedText(
    lead: String,
    highlight: String,
    tail: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color = QkColor.Sub,
    textAlign: TextAlign? = null,
) {
    Text(
        text = highlightedText(lead = lead, highlight = highlight, tail = tail, color = QkColor.Fg),
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
    )
}

/** 纯函数：拼接「普通 + 加粗关键词 + 普通」的三段文案（供 UI 与单测共用）。 */
fun highlightedText(
    lead: String,
    highlight: String,
    tail: String,
    color: androidx.compose.ui.graphics.Color = QkColor.Fg,
): AnnotatedString = buildAnnotatedString {
    append(lead)
    if (highlight.isNotEmpty()) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { append(highlight) }
    }
    append(tail)
}
