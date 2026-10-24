package com.xcwk.scalehub.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkProgress
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import java.time.LocalDate

/**
 * data-recovery — 数据恢复页（T-092 · 原型24 v-recovery）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/24-remaining-gaps.html` id="v-recovery"、
 *   v-recovery-done
 * - 验收：AC-S5「SDK 清单与数据恢复」，页内文案逐字取自原型
 *
 * ## 呈现式约束（AC-S5 forbidden，硬约束）
 * 1. 云状态、最近同步时间、最早同步日、恢复进度全部外部注入（[RecoveryUiState]）；
 * 2. 通知记录由回调发起：开始恢复 → [DataRecoveryScreen] 的 onStartRecovery，
 *    取消 → onCancelRecovery；页面不拉云端数据、不写 DB；
 * 3. 恢复范围选择与日期范围校验是纯函数（[validateRecoveryRange] /
 *    [reduce]），不改外部状态、可 JVM 单测；
 * 4. 未展示「覆盖本地现有数据」警示前不放行开始（警示常驻且开始按钮受校验约束）。
 */

/** 原型 v-recovery / v-recovery-done 文案（逐字，勿改）。 */
object DataRecoveryText {
    const val TopBarTitle = "数据恢复"
    const val Title = "数据恢复"
    const val Subtitle = "从云端重新拉取历史数据"
    const val CloudAvailable = "云端数据可用"
    const val LastSyncPrefix = "最近同步："
    const val OptionsLabel = "恢复选项"
    const val OptionAllTitle = "恢复全部数据"
    const val OptionAllSubtitle = "从云端拉取所有历史记录"
    const val OptionRecentTitle = "恢复最近 30 天"
    const val OptionRecentSubtitle = "仅拉取近 30 天记录"
    const val OptionCustomTitle = "恢复指定日期范围"
    const val OptionCustomSubtitle = "自定义起止日期"
    const val WarnLine1 = "恢复操作将"
    const val WarnOverwrite = "覆盖本地现有数据"
    const val WarnLine2 = "，"
    const val WarnLine3 = "建议先备份。"
    const val StartAction = "开始恢复"
    const val CancelAction = "取消"
    const val RunningHint = "正在从云端恢复数据…"
    const val CustomStartLabel = "开始日期"
    const val CustomEndLabel = "结束日期"
    const val CompletedTitle = "数据恢复完成"
    const val CompletedCountTemplate = "已从云端恢复 %d 条记录。本地数据已更新。"
}

/** 恢复范围三选一（互斥）。 */
enum class RecoveryScopeOption(val title: String, val subtitle: String) {
    All(DataRecoveryText.OptionAllTitle, DataRecoveryText.OptionAllSubtitle),
    Recent30Days(DataRecoveryText.OptionRecentTitle, DataRecoveryText.OptionRecentSubtitle),
    CustomRange(DataRecoveryText.OptionCustomTitle, DataRecoveryText.OptionCustomSubtitle),
}

/** 恢复流程阶段：确认（选范围）→ 进行中 → 完成 / 失败。 */
enum class RecoveryPhase { Selecting, Running, Completed, Failed }

/** 日期范围校验结果：null 表示合法（纯函数返回值）。 */
enum class RecoveryRangeError {
    /** 起止日期任一为空。 */
    Empty,

    /** 不是 ISO-8601（yyyy-MM-dd）日期。 */
    InvalidFormat,

    /** 起始日晚于结束日。 */
    StartAfterEnd,

    /** 起始日早于云端最早同步日。 */
    BeforeEarliestSync,
}

/** 恢复页状态：全部字段由调用方注入，纯函数只做拷贝式推进。 */
data class RecoveryUiState(
    val phase: RecoveryPhase = RecoveryPhase.Selecting,
    val selectedScope: RecoveryScopeOption = RecoveryScopeOption.All,
    val customStartText: String = "",
    val customEndText: String = "",
    val cloudStatusLabel: String = DataRecoveryText.CloudAvailable,
    val lastSyncLabel: String = "10月21日 07:30",
    val earliestSyncDate: LocalDate? = null,
    val progressPercent: Int = 0,
    val restoredCount: Int = 0,
    val failureReason: String = "",
) {
    /** 当前选中范围的日期校验结果；非自定义范围恒为 null。 */
    val rangeError: RecoveryRangeError?
        get() = if (selectedScope == RecoveryScopeOption.CustomRange) {
            validateRecoveryRange(customStartText, customEndText, earliestSyncDate)
        } else {
            null
        }

    /** 能否发起恢复：仅在确认阶段且日期范围合法时可点。 */
    val canStart: Boolean get() = phase == RecoveryPhase.Selecting && rangeError == null
}

/** 恢复页意图：宿主 ViewModel 收到 UI 事件后转成意图，纯函数负责状态推进。 */
sealed interface RecoveryIntent {
    data class SelectScope(val option: RecoveryScopeOption) : RecoveryIntent
    data class ChangeCustomRange(val startText: String, val endText: String) : RecoveryIntent
    data object Start : RecoveryIntent
    data class Progress(val percent: Int) : RecoveryIntent
    data class Complete(val restoredCount: Int) : RecoveryIntent
    data class Fail(val reason: String) : RecoveryIntent
    data object Reset : RecoveryIntent
}

/** 解析 ISO 日期；非法返回 null（不抛异常，供纯函数使用）。 */
fun parseRecoveryDate(text: String): LocalDate? =
    runCatching { LocalDate.parse(text.trim()) }.getOrNull()

/**
 * 指定日期范围合法性（AC-S5 then：起 <= 止 且不早于最早同步日）。
 *
 * [earliestSyncDate] 为 null 表示云端尚未返回最早同步日，此时只校验先后关系。
 */
fun validateRecoveryRange(
    startText: String,
    endText: String,
    earliestSyncDate: LocalDate?,
): RecoveryRangeError? {
    if (startText.isBlank() || endText.isBlank()) return RecoveryRangeError.Empty
    val start = parseRecoveryDate(startText) ?: return RecoveryRangeError.InvalidFormat
    val end = parseRecoveryDate(endText) ?: return RecoveryRangeError.InvalidFormat
    if (start.isAfter(end)) return RecoveryRangeError.StartAfterEnd
    if (earliestSyncDate != null && start.isBefore(earliestSyncDate)) {
        return RecoveryRangeError.BeforeEarliestSync
    }
    return null
}

/**
 * 纯状态转移：非法迁移一律原样返回（幂等），保证「确认 → 进行中 → 完成/失败」
 * 只能单向推进；进行中不可改范围，也不可重复开始。
 */
fun RecoveryUiState.reduce(intent: RecoveryIntent): RecoveryUiState = when (intent) {
    is RecoveryIntent.SelectScope -> when (phase) {
        RecoveryPhase.Selecting -> copy(selectedScope = intent.option)
        else -> this
    }

    is RecoveryIntent.ChangeCustomRange -> when (phase) {
        RecoveryPhase.Selecting -> copy(
            customStartText = intent.startText,
            customEndText = intent.endText,
        )

        else -> this
    }

    RecoveryIntent.Start -> when {
        !canStart -> this
        else -> copy(phase = RecoveryPhase.Running, progressPercent = 0, restoredCount = 0, failureReason = "")
    }

    is RecoveryIntent.Progress -> when (phase) {
        RecoveryPhase.Running -> copy(progressPercent = intent.percent.coerceIn(0, 100))
        else -> this
    }

    is RecoveryIntent.Complete -> when (phase) {
        RecoveryPhase.Running -> copy(
            phase = RecoveryPhase.Completed,
            progressPercent = 100,
            restoredCount = intent.restoredCount.coerceAtLeast(0),
        )

        else -> this
    }

    is RecoveryIntent.Fail -> when (phase) {
        RecoveryPhase.Running -> copy(phase = RecoveryPhase.Failed, failureReason = intent.reason)
        else -> this
    }

    RecoveryIntent.Reset -> when (phase) {
        RecoveryPhase.Failed, RecoveryPhase.Completed -> RecoveryUiState(
            selectedScope = selectedScope,
            customStartText = customStartText,
            customEndText = customEndText,
            cloudStatusLabel = cloudStatusLabel,
            lastSyncLabel = lastSyncLabel,
            earliestSyncDate = earliestSyncDate,
        )

        else -> this
    }
}

/** 数据恢复页（原型 v-recovery / v-recovery-done）。纯呈现，状态与回调全部外部注入。 */
@Composable
fun DataRecoveryScreen(
    state: RecoveryUiState = RecoveryUiState(),
    onStartRecovery: () -> Unit = {},
    onCancelRecovery: () -> Unit = {},
    onCustomRangeChange: (String, String) -> Unit = { _, _ -> },
    onScopeSelected: (RecoveryScopeOption) -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = QkSpacing.Page)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(
                title = DataRecoveryText.TopBarTitle,
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                text = DataRecoveryText.Title,
                style = MaterialTheme.typography.headlineSmall,
                color = QkColor.Fg,
            )
            Text(
                text = DataRecoveryText.Subtitle,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )

            QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QkIconBubble(
                        icon = QkIcons.Restore,
                        tone = QkGlyphTone.Green,
                        size = QkSpacing.Xl * 2,
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
                        Text(
                            text = state.cloudStatusLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = QkColor.Fg,
                        )
                        Text(
                            text = DataRecoveryText.LastSyncPrefix + state.lastSyncLabel,
                            modifier = Modifier.padding(top = QkSpacing.Xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.Muted,
                        )
                    }
                }
            }

            QkSectionHead(
                title = DataRecoveryText.OptionsLabel,
                modifier = Modifier.padding(top = QkSpacing.Lg),
            )
            QkCard(modifier = Modifier.padding(top = QkSpacing.Sm)) {
                RecoveryScopeOption.entries.forEach { option ->
                    RecoveryScopeRow(
                        option = option,
                        selected = state.selectedScope == option,
                        enabled = state.phase == RecoveryPhase.Selecting,
                        onClick = { onScopeSelected(option) },
                    )
                }
            }

            if (state.selectedScope == RecoveryScopeOption.CustomRange &&
                state.phase == RecoveryPhase.Selecting
            ) {
                QkTextField(
                    value = state.customStartText,
                    onValueChange = { onCustomRangeChange(it, state.customEndText) },
                    modifier = Modifier.padding(top = QkSpacing.Md),
                    label = DataRecoveryText.CustomStartLabel,
                    placeholder = "YYYY-MM-DD",
                )
                QkTextField(
                    value = state.customEndText,
                    onValueChange = { onCustomRangeChange(state.customStartText, it) },
                    modifier = Modifier.padding(top = QkSpacing.Md),
                    label = DataRecoveryText.CustomEndLabel,
                    placeholder = "YYYY-MM-DD",
                )
            }

            QkNoticeBanner(
                icon = Icons.Outlined.WarningAmber,
                tone = QkGlyphTone.Orange,
                background = QkColor.AccentSoft,
                modifier = Modifier.padding(top = QkSpacing.Lg),
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(DataRecoveryText.WarnLine1)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = QkColor.Fg)) {
                            append(DataRecoveryText.WarnOverwrite)
                        }
                        append(DataRecoveryText.WarnLine2)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
                Text(
                    text = DataRecoveryText.WarnLine3,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
            }

            RecoveryPhaseBody(state = state)

            Spacer(Modifier.height(QkSpacing.Xl))

            QkButton(
                text = DataRecoveryText.StartAction,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canStart,
                onClick = onStartRecovery,
            )
            Spacer(Modifier.height(QkSpacing.Md))
            QkButton(
                text = DataRecoveryText.CancelAction,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Ghost,
                onClick = onCancelRecovery,
            )
            Spacer(Modifier.height(QkSpacing.Xl))
        }
    }
}

/** 恢复范围单选项（原型 radio 行）。 */
@Composable
private fun RecoveryScopeRow(
    option: RecoveryScopeOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
            Text(
                text = option.subtitle,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }
        RadioButton(selected = selected, enabled = enabled, onClick = onClick)
    }
}

/** 阶段附加区：进行中显示进度条，完成/失败显示结果文案。 */
@Composable
private fun RecoveryPhaseBody(state: RecoveryUiState) {
    when (state.phase) {
        RecoveryPhase.Selecting -> Unit

        RecoveryPhase.Running -> Column(modifier = Modifier.padding(top = QkSpacing.Lg)) {
            Text(
                text = DataRecoveryText.RunningHint,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
            QkProgress(
                percent = state.progressPercent,
                modifier = Modifier.padding(top = QkSpacing.Sm),
            )
        }

        RecoveryPhase.Completed -> Column(modifier = Modifier.padding(top = QkSpacing.Lg)) {
            Text(
                text = DataRecoveryText.CompletedTitle,
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
            Text(
                text = DataRecoveryText.CompletedCountTemplate.format(state.restoredCount),
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }

        RecoveryPhase.Failed -> Column(modifier = Modifier.padding(top = QkSpacing.Lg)) {
            Text(
                text = state.failureReason,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Danger,
            )
        }
    }
}
