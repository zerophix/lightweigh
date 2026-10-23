package com.xcwk.scalehub.feature.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyph
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkProgress
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import kotlin.math.roundToInt

/**
 * 成员详情与权限（T-084 · Wave 1 · 原型 20 `v-member-detail`）。
 *
 * 身份行示例：本人 · 女 · 30岁 · 168cm。
 * 原型页脚：轻衡 · AI 总结与成员权限交互原型（补 Figma 缺口）。
 *
 * 权限开关只改页面状态，保存经 [onSave] 交回调用方，不写 repository。
 */
enum class MemberPermissionId {
    ViewRecords,
    EditRecords,
    DeleteRecords,
    ViewAiReports,
    ManageGoals,
}

data class MemberPermission(
    val id: MemberPermissionId,
    val title: String,
    val description: String,
    val enabled: Boolean,
)

data class MemberOverview(
    val measureCount: Int,
    val startKg: Double,
    val currentKg: Double,
    val goalKg: Double,
)

data class MemberDevice(
    val name: String,
    val connected: Boolean,
    val batteryPercent: Int?,
)

data class MemberDetailUi(
    val name: String,
    val relation: String,
    val sex: String,
    val ageYears: Int,
    val heightCm: Int,
    val current: Boolean = true,
    val overview: MemberOverview? = null,
    val device: MemberDevice? = null,
)

internal object MemberCopy {
    const val TITLE = "成员详情"
    const val EDIT = "编辑"
    const val TAG_CURRENT = "当前"
    const val SECTION_PERMISSIONS = "权限管理"
    const val SECTION_OVERVIEW = "数据概览"
    const val SECTION_DEVICE = "关联设备"
    const val LABEL_COUNT = "测量次数"
    const val LABEL_START = "起始 kg"
    const val LABEL_CURRENT = "当前 kg"
    const val NOTE = "权限变更立即生效，不影响已保存的历史记录。"
    const val BUTTON_WEEKLY = "查看 AI 周总结"
    const val BUTTON_SAVE = "保存权限"
    const val DISCONNECT = "断开"
    const val SAMPLE_NAME = "林悦"
    const val SAMPLE_DEVICE = "轻衡 S1"
}

internal data class MemberOverviewView(
    val measureCountLabel: String,
    val startLabel: String,
    val currentLabel: String,
    val lostLabel: String,
    val goalLabel: String,
    val progressPercent: Int?,
)

internal fun prototypePermissions(): List<MemberPermission> = listOf(
    MemberPermission(MemberPermissionId.ViewRecords, "查看测量记录", "允许查看该成员的全部记录", true),
    MemberPermission(MemberPermissionId.EditRecords, "编辑测量记录", "允许修改备注和时间", true),
    MemberPermission(MemberPermissionId.DeleteRecords, "删除测量记录", "允许删除该成员的记录", false),
    MemberPermission(MemberPermissionId.ViewAiReports, "查看 AI 报告", "允许 AI 使用该成员数据", true),
    MemberPermission(MemberPermissionId.ManageGoals, "管理健康目标", "允许修改体重目标", true),
)

internal fun prototypeMember(): MemberDetailUi = MemberDetailUi(
    name = MemberCopy.SAMPLE_NAME,
    relation = "本人",
    sex = "女",
    ageYears = 30,
    heightCm = 168,
    overview = MemberOverview(measureCount = 128, startKg = 65.2, currentKg = 62.4, goalKg = 58.0),
    device = MemberDevice(name = MemberCopy.SAMPLE_DEVICE, connected = true, batteryPercent = 82),
)

internal fun memberIdentityLine(relation: String, sex: String, ageYears: Int, heightCm: Int): String =
    "$relation · $sex · ${ageYears}岁 · ${heightCm}cm"

internal fun togglePermission(
    items: List<MemberPermission>,
    id: MemberPermissionId,
    enabled: Boolean,
): List<MemberPermission> = items.map { item ->
    if (item.id == id) item.copy(enabled = enabled) else item
}

/**
 * 目标进度百分比。起点与目标相同（跨度为 0）时返回 null，避免除零，
 * 页面也不再画一条假进度。
 */
internal fun goalProgressPercent(startKg: Double, currentKg: Double, goalKg: Double): Int? {
    if (!startKg.isFinite() || !currentKg.isFinite() || !goalKg.isFinite()) return null
    val span = startKg - goalKg
    if (span == 0.0) return null
    val ratio = (startKg - currentKg) / span
    if (!ratio.isFinite()) return null
    return (ratio * 100.0).roundToInt().coerceIn(0, 100)
}

internal fun presentOverview(overview: MemberOverview): MemberOverviewView? {
    if (overview.measureCount < 0) return null
    if (!overview.startKg.isFinite() || !overview.currentKg.isFinite() || !overview.goalKg.isFinite()) {
        return null
    }
    val lost = overview.startKg - overview.currentKg
    val progress = goalProgressPercent(overview.startKg, overview.currentKg, overview.goalKg)
    val lostLabel = when {
        lost > 0 -> "已减 ${formatOneDecimal(lost)} kg"
        lost < 0 -> "已增 ${formatOneDecimal(-lost)} kg"
        else -> "已减 ${formatOneDecimal(0.0)} kg"
    }
    val goalNumber = formatOneDecimal(overview.goalKg)
    val goalLabel = if (progress == null) "目标 $goalNumber kg" else "目标 $goalNumber kg · $progress%"
    return MemberOverviewView(
        measureCountLabel = overview.measureCount.toString(),
        startLabel = formatOneDecimal(overview.startKg),
        currentLabel = formatOneDecimal(overview.currentKg),
        lostLabel = lostLabel,
        goalLabel = goalLabel,
        progressPercent = progress,
    )
}

internal fun deviceSubtitle(device: MemberDevice): String {
    val link = if (device.connected) "已连接" else "未连接"
    val battery = device.batteryPercent?.let { " · 电量 $it%" }.orEmpty()
    return "● $link$battery"
}

@Composable
fun MemberPermissionDetail(
    member: MemberDetailUi,
    permissions: List<MemberPermission>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onOpenWeekly: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onPermissionChange: (MemberPermissionId, Boolean) -> Unit = { _, _ -> },
    onSave: (List<MemberPermission>) -> Unit = {},
) {
    var current by remember(permissions) { mutableStateOf(permissions) }
    val identity = memberIdentityLine(member.relation, member.sex, member.ageYears, member.heightCm)
    val overview = member.overview?.let(::presentOverview)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(Modifier.height(QkSpacing.Md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = onBack)
            Spacer(Modifier.width(QkSpacing.Sm))
            Text(
                text = MemberCopy.TITLE,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = MemberCopy.EDIT,
                color = QkColor.Primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(end = QkSpacing.Sm)
                    .clickable(onClick = onEdit),
            )
        }
        Spacer(Modifier.height(QkSpacing.Sm))
        QkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QkGlyph(text = member.name.take(1).ifEmpty { " " })
                Spacer(Modifier.width(QkSpacing.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = member.name, color = QkColor.Fg, style = MaterialTheme.typography.titleMedium)
                    Text(text = identity, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
                }
                if (member.current) QkPill(text = MemberCopy.TAG_CURRENT)
            }
        }
        Spacer(Modifier.height(QkSpacing.Lg))
        Text(
            text = MemberCopy.SECTION_PERMISSIONS,
            color = QkColor.Muted,
            style = MaterialTheme.typography.labelMedium,
        )
        current.forEach { item ->
            QkListRow(
                title = item.title,
                subtitle = item.description,
                trailing = {
                    Switch(
                        checked = item.enabled,
                        onCheckedChange = { checked ->
                            current = togglePermission(current, item.id, checked)
                            onPermissionChange(item.id, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = QkColor.Primary,
                            uncheckedTrackColor = QkColor.Secondary,
                        ),
                    )
                },
            )
        }
        if (overview != null) {
            Spacer(Modifier.height(QkSpacing.Lg))
            Text(
                text = MemberCopy.SECTION_OVERVIEW,
                color = QkColor.Muted,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(QkSpacing.Sm))
            QkCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OverviewCell(overview.measureCountLabel, MemberCopy.LABEL_COUNT, Modifier.weight(1f))
                    OverviewCell(overview.startLabel, MemberCopy.LABEL_START, Modifier.weight(1f))
                    OverviewCell(overview.currentLabel, MemberCopy.LABEL_CURRENT, Modifier.weight(1f), emphasize = true)
                }
                overview.progressPercent?.let { percent ->
                    Spacer(Modifier.height(QkSpacing.Md))
                    QkProgress(percent = percent)
                }
                Spacer(Modifier.height(QkSpacing.Sm))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = overview.lostLabel,
                        color = QkColor.Primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = overview.goalLabel,
                        color = QkColor.Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        member.device?.let { device ->
            Spacer(Modifier.height(QkSpacing.Lg))
            Text(
                text = MemberCopy.SECTION_DEVICE,
                color = QkColor.Muted,
                style = MaterialTheme.typography.labelMedium,
            )
            QkListRow(
                title = device.name,
                subtitle = deviceSubtitle(device),
                trailing = {
                    Text(
                        text = MemberCopy.DISCONNECT,
                        color = QkColor.Primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                onClick = onDisconnect,
            )
        }
        Spacer(Modifier.height(QkSpacing.Md))
        Text(text = MemberCopy.NOTE, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(QkSpacing.Lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkButton(
                text = MemberCopy.BUTTON_WEEKLY,
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = onOpenWeekly,
            )
            QkButton(
                text = MemberCopy.BUTTON_SAVE,
                modifier = Modifier.weight(1f),
                onClick = { onSave(current) },
            )
        }
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

@Composable
private fun OverviewCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = if (emphasize) QkColor.Primary else QkColor.Fg,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(text = label, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
    }
}
