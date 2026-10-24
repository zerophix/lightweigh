package com.xcwk.scalehub.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * maintenance — 服务维护模式页（T-092 · 原型24 v-maintenance）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/24-remaining-gaps.html` id="v-maintenance"
 * - 验收：AC-S5「SDK 清单与数据恢复」，页内文案逐字取自原型
 *
 * ## 呈现式约束（AC-S5 forbidden，硬约束）
 * 1. 维护态、预计时长、预计恢复时间、受影响功能清单全部外部注入
 *    （[MaintenanceUiState]）——页面不探测服务状态、不查公告接口；
 * 2. 维护模式下按受影响功能清单禁用对应入口：判定是纯函数
 *    [isFeatureBlocked] / [isEntryEnabled]，供宿主在入口处复用；
 * 3. 维护模式开关状态外部注入，开关只回传意图（[MaintenanceModeSwitch]）；
 *    同一意图重复到达时幂等（[applyMaintenanceRequest] 原地返回）。
 */

/** 原型 v-maintenance 文案（逐字，勿改）。 */
object MaintenanceText {
    const val Title = "服务维护中"
    const val DescriptionPrefix = "我们正在升级服务，预计"
    const val DurationTemplate = "%d 分钟"
    const val DurationSuffix = "内恢复。"
    const val DescriptionLine2 = "期间测量和查看数据不受影响，"
    const val DescriptionLine3 = "仅云同步和 AI 功能暂停。"
    const val EtaPrefix = "预计恢复时间："
    const val AffectedPrefix = "受影响功能："
    const val AffectedSeparator = " · "
    const val RetryAction = "稍后再试"
    const val AnnouncementAction = "查看状态公告"
    const val SwitchLabel = "维护模式"
}

/** 原型「受影响功能」清单里的三项。 */
enum class MaintenanceFeature(val label: String) {
    CloudSync("云同步"),
    AiInterpretation("AI 解读"),
    WeeklySummary("周总结"),
}

/** 维护模式状态：全部字段由调用方注入。 */
data class MaintenanceUiState(
    val active: Boolean = true,
    val estimatedMinutes: Int = 30,
    val estimatedRecoveryTime: String = "14:30",
    val affectedFeatures: List<MaintenanceFeature> = MaintenanceFeature.entries,
) {
    /** 原型「预计 30 分钟内恢复」中的时长片段。 */
    val durationLabel: String get() = MaintenanceText.DurationTemplate.format(estimatedMinutes)

    /** 原型「受影响功能：云同步 · AI 解读 · 周总结」的整行文案。 */
    val affectedLine: String
        get() = MaintenanceText.AffectedPrefix +
            affectedFeatures.joinToString(MaintenanceText.AffectedSeparator) { it.label }
}

/** 维护模式开关请求（只允许 enter/exit 两种意图）。 */
enum class MaintenanceRequest { Enter, Exit }

/**
 * 维护模式当前是否应禁用 [feature] 对应入口（纯函数）。
 *
 * 非维护态一律放行；维护态只拦截 [MaintenanceUiState.affectedFeatures] 内的功能。
 */
fun isFeatureBlocked(feature: MaintenanceFeature, state: MaintenanceUiState): Boolean =
    state.active && state.affectedFeatures.contains(feature)

/** [isFeatureBlocked] 的反向表述，便于入口处直接判 enable。 */
fun isEntryEnabled(feature: MaintenanceFeature, state: MaintenanceUiState): Boolean =
    !isFeatureBlocked(feature, state)

/** 按受影响清单筛出仍被放行的功能（纯函数，供批量渲染入口使用）。 */
fun enabledFeatures(
    features: List<MaintenanceFeature>,
    state: MaintenanceUiState,
): List<MaintenanceFeature> = features.filter { isEntryEnabled(it, state) }

/**
 * 纯状态转移：把维护态推进到 [request] 指定的目标。
 *
 * 幂等——已经在目标态时原样返回（同一实例），宿主可据此判等跳过重复副作用。
 */
fun MaintenanceUiState.applyMaintenanceRequest(request: MaintenanceRequest): MaintenanceUiState {
    val target = request == MaintenanceRequest.Enter
    return if (active == target) this else copy(active = target)
}

/** 该请求是否需要通知宿主（已处于目标态时不重复回调，开关幂等的门禁）。 */
fun MaintenanceUiState.requiresHostNotification(request: MaintenanceRequest): Boolean =
    active != (request == MaintenanceRequest.Enter)

/**
 * 维护模式页（原型 v-maintenance）。纯呈现。
 *
 * 非维护态下原型无对应文案，故不渲染任何内容（不自造「服务正常」页）；
 * 出栈与入口放行由调用方按 [isEntryEnabled] 决定。
 */
@Composable
fun MaintenanceScreen(
    state: MaintenanceUiState = MaintenanceUiState(),
    onExitMaintenance: () -> Unit = {},
    onViewAnnouncement: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    if (!state.active) return

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = QkSpacing.Page)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = QkSpacing.Xl, vertical = QkSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            QkIconBubble(
                icon = Icons.Outlined.CloudOff,
                tone = QkGlyphTone.Orange,
                size = QkSpacing.Xl * 3,
                contentDescription = MaintenanceText.Title,
            )
            Spacer(Modifier.height(QkSpacing.Lg))
            Text(
                text = MaintenanceText.Title,
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(QkSpacing.Md))
            Text(
                text = buildAnnotatedString {
                    append(MaintenanceText.DescriptionPrefix)
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = QkColor.Accent)) {
                        append(state.durationLabel)
                    }
                    append(MaintenanceText.DurationSuffix)
                },
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
                textAlign = TextAlign.Center,
            )
            Text(
                text = MaintenanceText.DescriptionLine2,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
                textAlign = TextAlign.Center,
            )
            Text(
                text = MaintenanceText.DescriptionLine3,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
                textAlign = TextAlign.Center,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = QkSpacing.Lg)
                    .clip(RoundedCornerShape(QkRadius.Button))
                    .background(QkColor.Secondary)
                    .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
            ) {
                Text(
                    text = MaintenanceText.EtaPrefix + state.estimatedRecoveryTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
                Text(
                    text = state.affectedLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
            }

            Spacer(Modifier.height(QkSpacing.Xl))
            QkButton(
                text = MaintenanceText.RetryAction,
                modifier = Modifier.fillMaxWidth(),
                onClick = onExitMaintenance,
            )
            Spacer(Modifier.height(QkSpacing.Md))
            QkButton(
                text = MaintenanceText.AnnouncementAction,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Ghost,
                onClick = onViewAnnouncement,
            )
        }
    }
}

/**
 * 维护模式开关（设置入口用）：状态由 [state] 注入，点按只回传进入/退出意图。
 *
 * 已处于目标态时不触发回调（[requiresHostNotification]），保证重复点按幂等。
 */
@Composable
fun MaintenanceModeSwitch(
    state: MaintenanceUiState,
    onEnterMaintenance: () -> Unit = {},
    onExitMaintenance: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = MaintenanceText.SwitchLabel,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Fg,
        )
        Switch(
            checked = state.active,
            onCheckedChange = { checked ->
                val request = if (checked) MaintenanceRequest.Enter else MaintenanceRequest.Exit
                if (state.requiresHostNotification(request)) {
                    if (request == MaintenanceRequest.Enter) onEnterMaintenance() else onExitMaintenance()
                }
            },
        )
    }
}
