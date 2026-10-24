package com.xcwk.scalehub.feature.ai

/**
 * ai — 四件套之 UiState（对话流 + 原型 06 五态，T-083）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/06-ai.html`（v-generating / v-failed / v-unauth /
 *   v-plan / v-auth）
 * - 验收：AC-J1-10「AI 建议生成与展示」
 *
 * ## 与 T-085 的分工（不重叠）
 * 本文件只接管 **生成链路的五个状态**：生成中、发送失败、未授权（通用咨询）、
 * 本周行动计划、授权设置。服务不可用 / 报告过期 / 重新分析 / 成员未授权等
 * **边界六态**归 `AiBoundaries.kt`（T-085），本文件不重复实现、也不改动其判定顺序。
 *
 * ## 状态推进口径
 * 阶段推进一律走纯函数 [reduceAiStage]：输入当前 [AiStage] 与 [AiStageEvent]，
 * 输出下一阶段；非法事件原地不动（幂等，不会把页面推到无关状态）。
 * 这样「生成中 → 成功 / 失败」「未授权 → 授权引导」这类关键跳转有 JVM 单测钉死，
 * 不依赖真机点按。
 */

/** 一条对话消息（mine = 用户自己说的）。 */
data class AiMessage(val mine: Boolean, val text: String)

/**
 * AI 页面阶段。
 *
 * [Chat] 是既有的对话主路径（T-083 之前就有），其余五项对应原型 06 的五个 view。
 */
enum class AiStage {
    /** 对话主路径（原型 v-chat）。 */
    Chat,

    /** 生成中（原型 v-generating）。 */
    Generating,

    /** 发送失败（原型 v-failed）。 */
    Failed,

    /** 未开启个性化，仅通用咨询（原型 v-unauth）。 */
    Unauthenticated,

    /** 本周行动计划（原型 v-plan）。 */
    Plan,

    /** AI 数据设置（原型 v-auth）。 */
    AuthSettings,
}

/** 阶段推进事件：UI 只发事件，不自己算下一阶段。 */
sealed interface AiStageEvent {
    /** 用户点发送 / 重发后开始生成。 */
    data object StartGeneration : AiStageEvent

    /** 生成成功，回到对话并追加回答。 */
    data object GenerationSucceeded : AiStageEvent

    /** 生成失败（断网等），停在失败态可重发。 */
    data object GenerationFailed : AiStageEvent

    /** 用户点「停止生成」。 */
    data object StopGeneration : AiStageEvent

    /** 失败后点「重发」。 */
    data object Retry : AiStageEvent

    /** 进入本周行动计划。 */
    data object OpenPlan : AiStageEvent

    /** 进入 AI 数据设置。 */
    data object OpenAuthSettings : AiStageEvent

    /** 未授权态点「去开启个性化」。 */
    data object EnablePersonalization : AiStageEvent

    /** 设置页确认撤回全部授权。 */
    data object WithdrawAuthorization : AiStageEvent

    /** 回到对话。 */
    data object BackToChat : AiStageEvent
}

/**
 * 纯函数状态机：当前阶段 + 事件 → 下一阶段。
 *
 * 只允许这两条路径之外的跳转一律原地不动：
 * 1. 生成链路：Chat → Generating →（成功 / 停止）Chat 或（失败）Failed →（重发）Generating；
 * 2. 授权链路：Unauthenticated →（去开启个性化）AuthSettings →（撤回）Unauthenticated。
 */
fun reduceAiStage(stage: AiStage, event: AiStageEvent): AiStage = when (stage) {
    AiStage.Chat -> when (event) {
        AiStageEvent.StartGeneration -> AiStage.Generating
        AiStageEvent.OpenPlan -> AiStage.Plan
        AiStageEvent.OpenAuthSettings -> AiStage.AuthSettings
        else -> stage
    }

    AiStage.Generating -> when (event) {
        AiStageEvent.GenerationSucceeded, AiStageEvent.StopGeneration -> AiStage.Chat
        AiStageEvent.GenerationFailed -> AiStage.Failed
        else -> stage
    }

    AiStage.Failed -> when (event) {
        AiStageEvent.Retry -> AiStage.Generating
        AiStageEvent.BackToChat -> AiStage.Chat
        else -> stage
    }

    AiStage.Unauthenticated -> when (event) {
        AiStageEvent.EnablePersonalization -> AiStage.AuthSettings
        AiStageEvent.BackToChat -> AiStage.Chat
        else -> stage
    }

    AiStage.Plan -> when (event) {
        AiStageEvent.BackToChat -> AiStage.Chat
        else -> stage
    }

    AiStage.AuthSettings -> when (event) {
        AiStageEvent.WithdrawAuthorization -> AiStage.Unauthenticated
        AiStageEvent.BackToChat -> AiStage.Chat
        else -> stage
    }
}

/** 行动计划的一条任务。 */
data class AiPlanItem(
    val title: String,
    val detail: String,
    val done: Boolean = false,
)

/** 本周行动计划（原型 v-plan）。 */
data class AiPlan(
    val tag: String = AiStateText.PlanTag,
    val effective: String = AiStateText.PlanEffective,
    val progressText: String = AiStateText.PlanProgress,
    val percent: Int = 33,
    val items: List<AiPlanItem> = defaultAiPlanItems(),
)

/** 默认的三条计划任务（原型 v-plan 逐字）。 */
fun defaultAiPlanItems(): List<AiPlanItem> = listOf(
    AiPlanItem(
        title = AiStateText.PlanItemWalk,
        detail = AiStateText.PlanItemWalkDone,
        done = true,
    ),
    AiPlanItem(
        title = AiStateText.PlanItemMeasure,
        detail = AiStateText.PlanItemMeasureHint,
    ),
    AiPlanItem(
        title = AiStateText.PlanItemSleep,
        detail = AiStateText.PlanItemSleepHint,
    ),
)

/** AI 数据授权设置（原型 v-auth）。 */
data class AiAuthorization(
    val enabled: Boolean = true,
    val title: String = AiStateText.AuthEnabledTitle,
    val grantedAt: String = AiStateText.AuthGrantedAt,
    val useMeasurements: Boolean = true,
    val useProfile: Boolean = true,
    val useGoals: Boolean = true,
)

/** 页面状态：既有对话字段保持原样，新增的只有阶段与各阶段数据。 */
data class AiUiState(
    val loading: Boolean = true,
    val formal: Boolean = false,
    val messages: List<AiMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    // ---- T-083 新增 ----
    /** 当前阶段；默认对话主路径，既有行为不变。 */
    val stage: AiStage = AiStage.Chat,
    /** 生成中那条提问（原型「怎样测量更可比？」）。 */
    val generatingQuestion: String = "",
    /** 发送失败的那条提问（原型「睡眠不好会影响体脂吗？」）。 */
    val failedQuestion: String = "",
    /** 本周行动计划。 */
    val plan: AiPlan = AiPlan(),
    /** AI 授权设置。 */
    val authorization: AiAuthorization = AiAuthorization(),
)

/** 用户意图。 */
sealed interface AiIntent {
    data class InputChanged(val value: String) : AiIntent
    data object Send : AiIntent

    // ---- T-083 新增 ----
    /** 停止生成（原型「停止生成」）。 */
    data object StopGeneration : AiIntent

    /** 重发失败的那条提问（原型「重发」）。 */
    data object Retry : AiIntent

    /** 打开本周行动计划。 */
    data object OpenPlan : AiIntent

    /** 打开 AI 数据设置。 */
    data object OpenAuthSettings : AiIntent

    /** 未授权态「去开启个性化」。 */
    data object EnablePersonalization : AiIntent

    /** 撤回全部授权。 */
    data object WithdrawAuthorization : AiIntent

    /** 回到对话。 */
    data object BackToChat : AiIntent

    /** 删除全部 AI 对话（原型 data-toast，本层只清对话流）。 */
    data object DeleteAllMessages : AiIntent
}

/**
 * 当前阶段应当展示的文案（逐字取自原型 06，供 compose 与非 UI 单测共用）。
 *
 * 之所以做成纯函数而不是写在 Compose 里：验收要求「五个 view 文案有单测逐字断言」，
 * 文案若散落在 @Composable 内部就只能靠截图验证，状态一改就没人发现。
 */
fun aiStageVisibleTexts(state: AiUiState): List<String> = when (state.stage) {
    AiStage.Chat -> listOf(AiStateText.TitleAssistant) + state.messages.map { it.text }

    AiStage.Generating -> listOf(
        AiStateText.TitleAssistant,
        state.generatingQuestion.ifBlank { AiStateText.SampleGeneratingQuestion },
        AiStateText.Speaker,
        AiStateText.Analyzing,
        AiStateText.Generating,
        AiStateText.StopGenerating,
        AiStateText.AutoCompleteHint,
    )

    AiStage.Failed -> listOf(
        AiStateText.TitleAssistant,
        AiStateText.SendFailed,
        AiStateText.Resend,
        state.failedQuestion.ifBlank { AiStateText.SampleFailedQuestion },
        AiStateText.NetworkLostBanner,
        AiStateText.InputPlaceholder,
    )

    AiStage.Unauthenticated -> listOf(
        AiStateText.TitleAssistant,
        AiStateText.SettingsLink,
        AiStateText.UnauthBanner,
        AiStateText.SampleUnauthQuestion1,
        AiStateText.SampleUnauthQuestion2,
        AiStateText.UnauthIntro,
        AiStateText.EnablePersonalization,
    )

    AiStage.Plan -> listOf(
        AiStateText.PlanTitle,
        state.plan.tag,
        state.plan.effective,
        state.plan.progressText,
    ) + state.plan.items.flatMap { listOf(it.title, it.detail) } +
        AiStateText.PlanNote

    AiStage.AuthSettings -> listOf(
        AiStateText.AuthTitle,
        state.authorization.title,
        state.authorization.grantedAt,
        AiStateText.AuthUseMeasurements,
        AiStateText.AuthUseProfile,
        AiStateText.AuthUseGoals,
        AiStateText.AuthDeleteAllChats,
        AiStateText.AuthWithdrawAll,
        AiStateText.AuthWithdrawHint,
        AiStateText.AuthNote,
    )
}

/**
 * 原型 06 的逐字文案。改一个字就等于改验收，故集中在此并由单测逐条比对。
 *
 * 共用标题「AI 健康助手」出现在 v-generating / v-failed / v-unauth 三个 view。
 */
object AiStateText {
    // ---- 共用 ----
    const val TitleAssistant = "AI 健康助手"

    // ---- v-generating ----
    const val SampleGeneratingQuestion = "怎样测量更可比？"
    const val Speaker = "轻衡 AI"
    const val Analyzing = "正在分析…"
    const val Generating = "生成中…"
    const val StopGenerating = "停止生成"
    const val AutoCompleteHint = "（或等待自动完成 → ）"

    // ---- v-failed ----
    const val SendFailed = "发送失败"
    const val Resend = "重发"
    const val SampleFailedQuestion = "睡眠不好会影响体脂吗？"
    const val NetworkLostBanner = "网络连接已断开，恢复后可重发。"
    const val InputPlaceholder = "继续聊聊你的健康问题…"

    // ---- v-unauth ----
    const val SettingsLink = "设置"
    const val UnauthBanner = "未开启个性化分析，AI 仅提供通用健康知识。"
    const val SampleUnauthQuestion1 = "BMI 22.1 正常吗？"
    const val SampleUnauthQuestion2 = "体脂率怎么看？"
    const val UnauthIntro = "通用健康问题都可以问我。要获得针对你数据的解读，请先开启个性化分析。"
    const val EnablePersonalization = "去开启个性化"

    // ---- v-plan ----
    const val PlanTitle = "本周行动计划"
    const val PlanTag = "已采纳"
    const val PlanEffective = "10月21日生效"
    const val PlanProgress = "本周 1/3 已完成，继续保持。"
    const val PlanItemWalk = "轻松散步 20 分钟"
    const val PlanItemWalkDone = "已完成 · 10月21日"
    const val PlanItemMeasure = "固定时段测量体重"
    const val PlanItemMeasureHint = "建议任选 3 晚"
    const val PlanItemSleep = "记录睡眠感受"
    const val PlanItemSleepHint = "长按跳过示例"
    const val PlanNote = "计划为建议，经你确认后生效，不承诺效果。"
    const val PlanEdit = "调整本周计划"

    // ---- v-auth ----
    const val AuthTitle = "AI 数据设置"
    const val AuthEnabledTitle = "个性化分析已开启"
    const val AuthGrantedAt = "2026年10月21日授权"
    const val AuthUseMeasurements = "使用测量记录"
    const val AuthUseProfile = "使用基本资料"
    const val AuthUseGoals = "使用个人目标"
    const val AuthDeleteAllChats = "删除全部 AI 对话"
    const val AuthWithdrawAll = "撤回全部授权"
    const val AuthWithdrawHint = "关闭后仍可一般咨询"
    const val AuthNote = "关闭后 AI 不使用你的个人健康数据。"
}
