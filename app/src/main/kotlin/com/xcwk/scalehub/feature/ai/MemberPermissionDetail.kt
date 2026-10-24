package com.xcwk.scalehub.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkDivider
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkProgress
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme
import java.util.Locale
import kotlin.math.roundToInt

/**
 * ai — 成员权限详情页（T-084 · Wave 1，原型 20 `v-member-detail`）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/20-ai-members.html` id="v-member-detail"（Figma B04 缺口）
 * - 验收：AC-J1-11「周报与成员详情」——身份行（本人 · 女 · 30岁 · 168cm）+
 *   权限管理开关（查看测量记录 / 编辑测量记录 / 删除测量记录）及各自说明文案
 *
 * ## 范围（关键约束）
 * 本任务**只新建文件**，不改任何既有 ai 文件（`AiScreen` / `AiViewModel` 等归 Wave 2 的 T-083）。
 *
 * ## 呈现式约束
 * Screen 纯渲染：全部数据经 [MemberPermissionDetailUiState] 传入，本文件不读 repository、
 * 不写存储；开关变化只回调 [onTogglePermission]，落库由上层处理。
 * 权限行、数据概览、关联设备均按状态渲染——状态为空即整块不渲染，不补假行。
 * 目标进度百分比经 [goalProgressPercent] 计算，起始区间 ≤ 0 时返回 0，绝不除零。
 * 颜色、圆角、间距一律取 QkColor / QkRadius / QkSpacing token。
 */

/** 成员身份行（原型顶部 .card.soft：头像字形 + 姓名 + 身份行 + 「当前」标签）。 */
data class MemberIdentity(
    val name: String,
    /** 头像字形（原型头像取姓氏字：悦）。 */
    val glyph: String,
    /** 身份行原文，如「本人 · 女 · 30岁 · 168cm」。 */
    val relationLine: String,
    /** 是否当前成员（展示「当前」标签）。 */
    val isCurrent: Boolean,
)

/** 权限管理的一行：标题 + 说明 + 开关状态（原型 .row 五项之一）。 */
data class MemberPermissionRow(
    val title: String,
    val description: String,
    val enabled: Boolean,
)

/** 数据概览（原型「数据概览」卡：测量次数 / 起始 / 当前 / 目标进度）。 */
data class MemberOverview(
    val measureCount: Int,
    val startKg: Double,
    val currentKg: Double,
    val goalKg: Double,
)

/** 关联设备行（原型「关联设备」：名称 + 连接状态 + 右侧动作）。 */
data class MemberDeviceRow(
    val name: String,
    val statusLine: String,
    val actionLabel: String,
)

/** 成员权限详情页的渲染状态（不可变，由上层传入）。 */
data class MemberPermissionDetailUiState(
    val identity: MemberIdentity,
    val permissions: List<MemberPermissionRow> = emptyList(),
    val overview: MemberOverview? = null,
    val device: MemberDeviceRow? = null,
    /** 底部说明（原型 .note），空串则不渲染。 */
    val note: String = "",
)

/** 已减重量（起始 − 当前）；起始不高于当前则归 0，不出现「已减 -0.4 kg」。 */
fun lostKg(startKg: Double, currentKg: Double): Double = (startKg - currentKg).coerceAtLeast(0.0)

/**
 * 目标完成百分比：`(起始 − 当前) / (起始 − 目标) * 100`，四舍五入并夹在 0..100。
 *
 * 起始不高于目标（区间 ≤ 0）时直接返回 0 —— 这里是本页唯一的比例计算，
 * 除零与负区间都在此兜住，空态/异常数据不会算出 NaN 或崩溃。
 */
fun goalProgressPercent(startKg: Double, currentKg: Double, goalKg: Double): Int {
    val range = startKg - goalKg
    if (range <= 0.0) return 0
    val percent = lostKg(startKg, currentKg) / range * 100.0
    return percent.roundToInt().coerceIn(0, 100)
}

/** 原型文案「已减 2.8 kg」。 */
fun overviewLostText(overview: MemberOverview): String =
    "已减 ${kg(lostKg(overview.startKg, overview.currentKg))} kg"

/** 原型文案「目标 58.0 kg · 39%」。 */
fun overviewGoalText(overview: MemberOverview): String =
    "目标 ${kg(overview.goalKg)} kg · " +
        "${goalProgressPercent(overview.startKg, overview.currentKg, overview.goalKg)}%"

private fun kg(value: Double): String = "%.1f".format(Locale.US, value)

/**
 * 成员权限详情页（原型 v-member-detail）。
 *
 * @param state 页面状态
 * @param onBack 返回（原型导航栏返回回到 AI 周总结）
 * @param onEditProfile 导航栏「编辑」
 * @param onTogglePermission 权限开关变化，参数为权限标题与新状态
 * @param onViewWeeklySummary 底部「查看 AI 周总结」
 * @param onSavePermissions 底部「保存权限」
 * @param onDisconnectDevice 关联设备行右侧动作
 */
@Composable
fun MemberPermissionDetailScreen(
    state: MemberPermissionDetailUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onTogglePermission: (String, Boolean) -> Unit = { _, _ -> },
    onViewWeeklySummary: () -> Unit = {},
    onSavePermissions: () -> Unit = {},
    onDisconnectDevice: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(QkSpacing.Page),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = onBack)
            QkPageTitle(
                title = "成员详情",
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
            Text(
                text = "编辑",
                modifier = Modifier
                    .clickable(onClick = onEditProfile)
                    .padding(start = QkSpacing.Sm),
                style = MaterialTheme.typography.labelLarge,
                color = QkColor.Primary,
            )
        }

        IdentityCard(
            identity = state.identity,
            modifier = Modifier.padding(top = QkSpacing.Md),
        )

        // 权限管理（原型 .rows：白底圆角容器 + 行间分隔）
        if (state.permissions.isNotEmpty()) {
            SectionLabel(text = "权限管理", modifier = Modifier.padding(top = QkSpacing.CardGap))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(QkRadius.Card))
                    .background(QkColor.Card),
            ) {
                state.permissions.forEachIndexed { index, permission ->
                    PermissionRow(
                        permission = permission,
                        onToggle = { onTogglePermission(permission.title, it) },
                    )
                    if (index < state.permissions.lastIndex) {
                        QkDivider()
                    }
                }
            }
        }

        // 数据概览（状态缺省时整块不渲染）
        state.overview?.let { overview ->
            SectionLabel(text = "数据概览", modifier = Modifier.padding(top = QkSpacing.CardGap))
            QkCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OverviewStat(
                        value = overview.measureCount.toString(),
                        label = "测量次数",
                        modifier = Modifier.weight(1f),
                    )
                    OverviewStat(
                        value = kg(overview.startKg),
                        label = "起始 kg",
                        modifier = Modifier.weight(1f),
                    )
                    OverviewStat(
                        value = kg(overview.currentKg),
                        label = "当前 kg",
                        highlight = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                QkProgress(
                    percent = goalProgressPercent(
                        startKg = overview.startKg,
                        currentKg = overview.currentKg,
                        goalKg = overview.goalKg,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = QkSpacing.Md),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = QkSpacing.Sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = overviewLostText(overview),
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Primary,
                    )
                    Text(
                        text = overviewGoalText(overview),
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Muted,
                    )
                }
            }
        }

        // 关联设备（状态缺省时整块不渲染）
        state.device?.let { device ->
            SectionLabel(text = "关联设备", modifier = Modifier.padding(top = QkSpacing.CardGap))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(QkRadius.Card))
                    .background(QkColor.Card)
                    .padding(horizontal = QkSpacing.Lg),
            ) {
                QkListRow(
                    title = device.name,
                    subtitle = device.statusLine,
                    icon = QkIcons.Bluetooth,
                    showDivider = false,
                    trailing = {
                        Text(
                            text = device.actionLabel,
                            modifier = Modifier.clickable(onClick = onDisconnectDevice),
                            style = MaterialTheme.typography.labelLarge,
                            color = QkColor.Primary,
                        )
                    },
                )
            }
        }

        // 底部说明（原型 .note）
        if (state.note.isNotBlank()) {
            Text(
                text = state.note,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = QkSpacing.Md),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkButton(
                text = "查看 AI 周总结",
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = onViewWeeklySummary,
            )
            QkButton(
                text = "保存权限",
                modifier = Modifier.weight(1f),
                onClick = onSavePermissions,
            )
        }
    }
}

/** 分区标签（原型 .lbl）。 */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = QkColor.Muted,
    )
}

/** 身份卡：薄荷底圆角卡 + 圆形字形头像 + 姓名 + 身份行 + 「当前」标签。 */
@Composable
private fun IdentityCard(identity: MemberIdentity, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Card))
            .background(QkColor.Mint)
            .padding(QkSpacing.CardPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(QkSpacing.Xl * 2)
                .clip(CircleShape)
                .background(QkColor.Card),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = identity.glyph,
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Primary,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = QkSpacing.Md),
        ) {
            Text(
                text = identity.name,
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
            Text(
                text = identity.relationLine,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }
        if (identity.isCurrent) {
            QkPill(text = "当前")
        }
    }
}

/** 权限行：标题 + 说明 + 开关（说明文案逐字取自原型）。 */
@Composable
private fun PermissionRow(
    permission: MemberPermissionRow,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.CardPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = permission.title,
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
            Text(
                text = permission.description,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }
        Switch(
            checked = permission.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = QkColor.Card,
                checkedTrackColor = QkColor.Primary,
                checkedBorderColor = QkColor.Primary,
                uncheckedThumbColor = QkColor.Card,
                uncheckedTrackColor = QkColor.Secondary,
                uncheckedBorderColor = QkColor.Secondary,
            ),
        )
    }
}

/** 数据概览的一格：大数字 + 小标签（当前值主绿）。 */
@Composable
private fun OverviewStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = if (highlight) QkColor.Primary else QkColor.Fg,
        )
        Text(
            text = label,
            modifier = Modifier.padding(top = QkSpacing.Xs),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MemberPermissionDetailPreview() {
    QkTheme {
        MemberPermissionDetailScreen(
            state = MemberPermissionDetailUiState(
                identity = MemberIdentity(
                    name = "林悦",
                    glyph = "悦",
                    relationLine = "本人 · 女 · 30岁 · 168cm",
                    isCurrent = true,
                ),
                permissions = listOf(
                    MemberPermissionRow("查看测量记录", "允许查看该成员的全部记录", true),
                    MemberPermissionRow("编辑测量记录", "允许修改备注和时间", true),
                    MemberPermissionRow("删除测量记录", "允许删除该成员的记录", false),
                    MemberPermissionRow("查看 AI 报告", "允许 AI 使用该成员数据", true),
                    MemberPermissionRow("管理健康目标", "允许修改体重目标", true),
                ),
                overview = MemberOverview(
                    measureCount = 128,
                    startKg = 65.2,
                    currentKg = 62.4,
                    goalKg = 58.0,
                ),
                device = MemberDeviceRow(
                    name = "轻衡 S1",
                    statusLine = "● 已连接 · 电量 82%",
                    actionLabel = "断开",
                ),
                note = "权限变更立即生效，不影响已保存的历史记录。",
            ),
        )
    }
}
