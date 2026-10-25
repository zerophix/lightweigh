package com.xcwk.scalehub.feature.ai

import com.xcwk.scalehub.BuildConfig

import com.xcwk.scalehub.ui.components.QkScreenHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyph
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.components.QkProgress
import com.xcwk.scalehub.ui.components.QkSkeletonLine
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.shell.WiringEntries
import com.xcwk.scalehub.ui.states.QkInlineLoading
import com.xcwk.scalehub.ui.states.QkPartialErrorBar
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkStroke
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * ai — 四件套之 Screen：AI 健康助手（T-083 补全五个状态）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/06-ai.html`
 *   五态 = v-generating / v-failed / v-unauth / v-plan / v-auth
 * - 验收：AC-J1-10「AI 建议生成与展示」
 *
 * ## 与既有页面的关系
 * [AiStage.Chat] 是原对话主路径，渲染逻辑与 T-083 之前一致（消息流 + 输入框 + 边界入口），
 * 只是标题按原型改为「AI 健康助手」，并新增「设置」入口。其余五态为新分支。
 *
 * ## 呈现式约束
 * 1. 只渲染传入的 [AiUiState]，不读仓库、不发请求；
 * 2. 所有动作经 [AiIntent] 回到 ViewModel，本文件不自己改状态；
 * 3. 文案一律取 [AiStateText]（逐字原型），颜色 / 间距 / 圆角取 Qk token；
 * 4. 服务不可用等边界六态不在这里，走 `AiBoundaries.kt`（T-085）。
 */
@Composable
fun AiScreen(
    state: AiUiState,
    onIntent: (AiIntent) -> Unit,
    onBack: () -> Unit = {},
) {
    // 接入波：AI 边界与降级示例（T-085）从本页进入，返回即回问答页
    var showBoundaries by rememberSaveable { mutableStateOf(false) }
    if (showBoundaries) {
        AiBoundariesRoute(onBack = { showBoundaries = false })
        return
    }

    // 接入波第四批：AI 周总结（T-084）从问答页进入；无周报数据源时不造数，交给空态
    var showWeekly by rememberSaveable { mutableStateOf(false) }
    if (showWeekly) {
        AiWeeklySummaryScreen(
            state = AiWeeklySummaryUiState(),
            onBack = { showWeekly = false },
            onGeneratePlan = {
                showWeekly = false
                onIntent(AiIntent.OpenPlan)
            },
        )
        return
    }

    when (state.stage) {
        AiStage.Chat -> AiChatBody(
            state = state,
            onIntent = onIntent,
            onBack = onBack,
            onOpenBoundaries = { showBoundaries = true },
            onOpenWeekly = { showWeekly = true },
        )
        AiStage.Generating -> AiGeneratingBody(state, onIntent, onBack)
        AiStage.Failed -> AiFailedBody(state, onIntent, onBack)
        AiStage.Unauthenticated -> AiUnauthBody(state, onIntent, onBack)
        AiStage.Plan -> AiPlanBody(state, onBack)
        AiStage.AuthSettings -> AiAuthBody(state, onIntent, onBack)
    }
}

// ---------------------------------------------------------------------------
// 共用骨架
// ---------------------------------------------------------------------------

/** 对话气泡最大宽度（原型 max-width 290，取 token 推导值，避免裸 dp）。 */
private val AiBubbleMaxWidth = QkSpacing.Xl * 12

/** 顶部栏：返回 + 标题 + 可选右侧文字动作（原型 .navbar）。 */
@Composable
private fun AiTopBar(
    title: String,
    onBack: () -> Unit,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    QkScreenHeader(
        title = title,
        onBack = onBack,
        action = if (actionText != null && onAction != null) {
            {
                Text(
                    text = actionText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(QkRadius.SegmentItem))
                        .clickable(onClick = onAction)
                        .padding(horizontal = QkSpacing.Sm, vertical = QkSpacing.Xs),
                    style = MaterialTheme.typography.labelLarge,
                    color = QkColor.Primary,
                )
            }
        } else {
            null
        },
    )
}

/** 用户气泡（靠右，浅底）。 */
@Composable
private fun AiUserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = text,
            modifier = Modifier
                .widthIn(max = AiBubbleMaxWidth)
                .clip(RoundedCornerShape(topStart = QkRadius.Card, topEnd = QkRadius.SegmentItem, bottomEnd = QkRadius.Card, bottomStart = QkRadius.Card))
                .background(QkColor.Secondary)
                .padding(horizontal = QkSpacing.Md, vertical = QkSpacing.Md),
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Fg,
        )
    }
}

/** AI 说话人一行：图标块 + 名字 + 状态（原型「轻衡 AI · 正在分析…」）。 */
@Composable
private fun AiSpeakerRow(status: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md, bottom = QkSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
    ) {
        QkGlyph(text = "AI", tone = QkGlyphTone.Green, size = QkSpacing.Xl)
        Text(AiStateText.Speaker, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
        if (status != null) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        }
    }
}

// ---------------------------------------------------------------------------
// v-chat —— 对话主路径（既有行为保持）
// ---------------------------------------------------------------------------

@Composable
private fun AiChatBody(
    state: AiUiState,
    onIntent: (AiIntent) -> Unit,
    onBack: () -> Unit,
    onOpenBoundaries: () -> Unit,
    onOpenWeekly: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(QkSpacing.Lg)) {
        AiTopBar(
            title = AiStateText.TitleAssistant,
            onBack = onBack,
            actionText = AiStateText.SettingsLink,
            onAction = { onIntent(AiIntent.OpenAuthSettings) },
        )
        QkCard(modifier = Modifier.padding(top = QkSpacing.Sm)) {
            // 六态降级演示是调试入口，不进正式产品的入口层。
            if (BuildConfig.DEBUG) {
                QkListRow(
                    title = "（调试）${WiringEntries.AiBoundaries.title}",
                    subtitle = WiringEntries.AiBoundaries.hint,
                    onClick = onOpenBoundaries,
                )
            }
            // 接入波第四批：AI 周总结（入口文案见 AiWeeklyEntry）
            QkListRow(
                title = AiWeeklyEntry.TITLE,
                subtitle = AiWeeklyEntry.HINT,
                showDivider = false,
                onClick = onOpenWeekly,
            )
        }
        if (!state.formal) {
            QkCard(modifier = Modifier.padding(top = QkSpacing.Sm)) {
                Text(
                    "AI 问答需要手机号登录；游客数据不送出本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = QkSpacing.Sm)) {
            state.messages.forEach { msg ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Xs)) {
                    Text(
                        msg.text,
                        modifier = Modifier
                            .widthIn(max = AiBubbleMaxWidth)
                            .padding(
                                start = if (msg.mine) QkSpacing.Xl * 2 else QkSpacing.Xs * 0,
                                end = if (msg.mine) QkSpacing.Xs * 0 else QkSpacing.Xl * 2,
                            ),
                        color = if (msg.mine) QkColor.Primary else QkColor.Fg,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            // 「查看本周计划 ›」只在已有 AI 回答时出现（原型 v-chat 回答卡内入口）
            if (state.messages.any { !it.mine }) {
                Text(
                    text = "查看本周计划 ›",
                    modifier = Modifier
                        .padding(top = QkSpacing.Sm)
                        .clip(RoundedCornerShape(QkRadius.SegmentItem))
                        .background(QkColor.MintSoft)
                        .clickable { onIntent(AiIntent.OpenPlan) }
                        .padding(horizontal = QkSpacing.Md, vertical = QkSpacing.Sm),
                    style = MaterialTheme.typography.labelMedium,
                    color = QkColor.Primary,
                )
            }
        }
        state.error?.let {
            Text(
                it,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                color = QkColor.Danger,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkTextField(
                value = state.input,
                onValueChange = { onIntent(AiIntent.InputChanged(it)) },
                placeholder = AiStateText.InputPlaceholder,
                enabled = !state.sending,
                modifier = Modifier.weight(1f),
            )
            QkButton(
                text = if (state.sending) "…" else "发送",
                enabled = !state.sending,
                modifier = Modifier.padding(start = QkSpacing.Sm),
                onClick = { onIntent(AiIntent.Send) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// v-generating —— 生成中
// ---------------------------------------------------------------------------

@Composable
private fun AiGeneratingBody(state: AiUiState, onIntent: (AiIntent) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(QkSpacing.Lg)) {
        AiTopBar(title = AiStateText.TitleAssistant, onBack = onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            AiUserBubble(state.generatingQuestion.ifBlank { AiStateText.SampleGeneratingQuestion })
            AiSpeakerRow(status = AiStateText.Analyzing)
            QkCard {
                QkSkeletonLine(widthPercent = 90f)
                Spacer(Modifier.height(QkSpacing.Sm))
                QkSkeletonLine(widthPercent = 75f)
                Spacer(Modifier.height(QkSpacing.Sm))
                QkSkeletonLine(widthPercent = 82f)
                QkInlineLoading(label = AiStateText.Generating)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = AiStateText.StopGenerating,
                modifier = Modifier
                    .clip(RoundedCornerShape(QkRadius.Button))
                    .background(QkColor.Card)
                    .clickable { onIntent(AiIntent.StopGeneration) }
                    .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Sm),
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.Sub,
            )
            Text(
                text = AiStateText.AutoCompleteHint,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Hint,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// v-failed —— 发送失败
// ---------------------------------------------------------------------------

@Composable
private fun AiFailedBody(state: AiUiState, onIntent: (AiIntent) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(QkSpacing.Lg)) {
        AiTopBar(title = AiStateText.TitleAssistant, onBack = onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = AiStateText.SendFailed,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Danger,
                )
                Text(
                    text = AiStateText.Resend,
                    modifier = Modifier
                        .padding(start = QkSpacing.Sm)
                        .clip(RoundedCornerShape(QkRadius.SegmentItem))
                        .clickable { onIntent(AiIntent.Retry) }
                        .padding(horizontal = QkSpacing.Xs, vertical = QkSpacing.Xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Primary,
                )
            }
            Text(
                text = state.failedQuestion.ifBlank { AiStateText.SampleFailedQuestion },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = QkSpacing.Sm)
                    .widthIn(max = AiBubbleMaxWidth)
                    .clip(RoundedCornerShape(QkRadius.Card))
                    .background(QkColor.Card)
                    .padding(horizontal = QkSpacing.Md, vertical = QkSpacing.Md),
                style = MaterialTheme.typography.bodyMedium,
                color = QkColor.Muted,
            )
            QkPartialErrorBar(
                message = AiStateText.NetworkLostBanner,
                modifier = Modifier.padding(top = QkSpacing.Md),
            )
            state.error?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
        }
        QkTextField(
            value = state.input,
            onValueChange = { onIntent(AiIntent.InputChanged(it)) },
            placeholder = AiStateText.InputPlaceholder,
            modifier = Modifier.padding(top = QkSpacing.Sm),
        )
    }
}

// ---------------------------------------------------------------------------
// v-unauth —— 未开启个性化（仅通用咨询）
// ---------------------------------------------------------------------------

@Composable
private fun AiUnauthBody(state: AiUiState, onIntent: (AiIntent) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(QkSpacing.Lg)) {
        AiTopBar(
            title = AiStateText.TitleAssistant,
            onBack = onBack,
            actionText = AiStateText.SettingsLink,
            onAction = { onIntent(AiIntent.OpenAuthSettings) },
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            QkCard(modifier = Modifier.padding(top = QkSpacing.Sm)) {
                Text(
                    text = AiStateText.UnauthBanner,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
            ) {
                listOf(
                    AiStateText.SampleUnauthQuestion1,
                    AiStateText.SampleUnauthQuestion2,
                ).forEach { question ->
                    QkPill(text = question, tone = QkPillTone.Green)
                }
            }
            QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
                Text(
                    text = AiStateText.UnauthIntro,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
            }
        }
        QkButton(
            text = AiStateText.EnablePersonalization,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            onClick = { onIntent(AiIntent.EnablePersonalization) },
        )
    }
}

// ---------------------------------------------------------------------------
// v-plan —— 本周行动计划
// ---------------------------------------------------------------------------

@Composable
private fun AiPlanBody(state: AiUiState, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(QkSpacing.Lg)) {
        AiTopBar(title = AiStateText.PlanTitle, onBack = onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            QkCard(modifier = Modifier.padding(top = QkSpacing.Sm)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    QkPill(text = state.plan.tag, tone = QkPillTone.Green)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = state.plan.effective,
                        style = MaterialTheme.typography.labelSmall,
                        color = QkColor.Sub,
                    )
                }
                Text(
                    text = state.plan.progressText,
                    modifier = Modifier.padding(top = QkSpacing.Md),
                    style = MaterialTheme.typography.titleLarge,
                    color = QkColor.Fg,
                )
                QkProgress(
                    percent = state.plan.percent,
                    modifier = Modifier.padding(top = QkSpacing.Md),
                )
            }
            QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
                state.plan.items.forEachIndexed { index, item ->
                    val trailing: (@Composable () -> Unit)? = if (item.done) {
                        {
                            QkIcon(
                                icon = QkIcons.Habit,
                                tint = QkColor.Primary,
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    }
                    QkListRow(
                        title = item.title,
                        subtitle = item.detail,
                        showDivider = index != state.plan.items.lastIndex,
                        trailing = trailing,
                    )
                }
            }
            Text(
                text = AiStateText.PlanNote,
                modifier = Modifier.padding(top = QkSpacing.Md),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Hint,
            )
        }
        // 原型 data-toast「进入计划编辑（演示）」：计划编辑不在本任务范围，按钮只做占位
        QkButton(
            text = AiStateText.PlanEdit,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            style = QkButtonStyle.Secondary,
            onClick = {},
        )
    }
}

// ---------------------------------------------------------------------------
// v-auth —— AI 数据设置
// ---------------------------------------------------------------------------

@Composable
private fun AiAuthBody(state: AiUiState, onIntent: (AiIntent) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(QkSpacing.Lg)) {
        AiTopBar(title = AiStateText.AuthTitle, onBack = onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            QkCard(modifier = Modifier.padding(top = QkSpacing.Sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QkIconBubble(
                        icon = QkIcons.Privacy,
                        tone = QkGlyphTone.Green,
                        size = QkSpacing.Xl * 2,
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
                        Text(
                            text = state.authorization.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = QkColor.Fg,
                        )
                        Text(
                            text = state.authorization.grantedAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = QkColor.Sub,
                        )
                    }
                }
            }
            QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
                AiSwitchRow(
                    title = AiStateText.AuthUseMeasurements,
                    checked = state.authorization.useMeasurements,
                    onCheckedChange = {},
                )
                AiSwitchRow(
                    title = AiStateText.AuthUseProfile,
                    checked = state.authorization.useProfile,
                    onCheckedChange = {},
                )
                AiSwitchRow(
                    title = AiStateText.AuthUseGoals,
                    checked = state.authorization.useGoals,
                    onCheckedChange = {},
                    showDivider = false,
                )
            }
            QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
                QkListRow(
                    title = AiStateText.AuthDeleteAllChats,
                    onClick = { onIntent(AiIntent.DeleteAllMessages) },
                )
                QkListRow(
                    title = AiStateText.AuthWithdrawAll,
                    subtitle = AiStateText.AuthWithdrawHint,
                    showDivider = false,
                    onClick = { onIntent(AiIntent.WithdrawAuthorization) },
                )
            }
            Text(
                text = AiStateText.AuthNote,
                modifier = Modifier.padding(top = QkSpacing.Md),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Hint,
            )
        }
    }
}

/** 授权开关行（原型 .row + .sw）。 */
@Composable
private fun AiSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (showDivider) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QkStroke.Thin)
                    .background(QkColor.LineSoft),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 预览
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun AiScreenChatPreview() {
    QkTheme {
        AiScreen(
            state = AiUiState(
                loading = false,
                formal = true,
                messages = listOf(
                    AiMessage(true, "减脂期晚餐怎么吃？"),
                    AiMessage(false, "优先蛋白质与蔬菜，主食减半，睡前两小时不加餐。"),
                ),
            ),
            onIntent = {},
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun AiScreenGeneratingPreview() {
    QkTheme {
        AiScreen(
            state = AiUiState(loading = false, formal = true, stage = AiStage.Generating),
            onIntent = {},
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun AiScreenFailedPreview() {
    QkTheme {
        AiScreen(
            state = AiUiState(loading = false, formal = true, stage = AiStage.Failed),
            onIntent = {},
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun AiScreenUnauthPreview() {
    QkTheme {
        AiScreen(
            state = AiUiState(loading = false, formal = true, stage = AiStage.Unauthenticated),
            onIntent = {},
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun AiScreenPlanPreview() {
    QkTheme {
        AiScreen(
            state = AiUiState(loading = false, formal = true, stage = AiStage.Plan),
            onIntent = {},
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun AiScreenAuthPreview() {
    QkTheme {
        AiScreen(
            state = AiUiState(loading = false, formal = true, stage = AiStage.AuthSettings),
            onIntent = {},
        ) {}
    }
}
