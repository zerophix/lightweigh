package com.xcwk.scalehub.feature.device

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * device-detail — 设备详情 / 设置页（T-105 · 原型 04 `v-settings`）。
 *
 * ## 原型布局
 * 导航条「设备设置」→ 设备卡（图标 +「轻衡 S1」+「● 已连接 · 电量 82%」）
 * → 信息行（设备名称 / 体重单位 / 固件版本 +「最新」标签）
 * →「断开连接」行 →「删除设备」危险行 → 说明「删除设备不会删除已同步的测量记录。」
 * → 解绑二次确认弹层（`解绑「轻衡 S1」？`）。
 *
 * ## 与既有设备三屏的关系（本任务不重复实现）
 * - 重命名：行点击上交 [onRename]，落 `DeviceRenameScreen`（T-080）
 * - 固件：行点击上交 [onOpenFirmware]，落 `FirmwareManager`（T-079）
 * - 绑定成员：行点击上交 [onOpenMemberLink]，落 `MemberDeviceLinkScreen`（T-080）
 * 本文件只做「聚合的详情页 + 谁进来、返回去哪」，不实现三者的任何逻辑。
 *
 * ## 边界（呈现式组件）
 * 设备名称 / 连接态 / 电量 / 单位 / 固件版本 / 协议 / 绑定成员数全部由 [DeviceDetailUiState]
 * 外部注入，本文件不读设备档案、不发起 GATT 连接、不写库：
 * - 断开连接只把本页连接态置为未连接（乐观态），真实断连由接入层回调后重建 state
 * - 解绑确认只上交 [onUnbind]，解绑请求本身由接入层负责
 *
 * ## 与原型的两处刻意差异（诚实标注）
 * 1. 「体重单位」行原型带 chevron，但本任务回调契约未包含单位切换；为避免死入口，
 *    该行只展示值不带箭头，等接入层给出 `onUnitChange` 后再补。
 * 2. 「协议信息」「绑定成员」两行是任务描述要求的外部注入字段，原型该屏没有这两行；
 *    文案风格与原型同构（label + 值 / chevron）。
 */

/** 导航条标题（原型 navbar 文案）。 */
const val DEVICE_DETAIL_TITLE = "设备设置"

/** 信息行标签（原型 .rows 逐字）。 */
const val DEVICE_DETAIL_ROW_NAME = "设备名称"
const val DEVICE_DETAIL_ROW_UNIT = "体重单位"
const val DEVICE_DETAIL_ROW_FIRMWARE = "固件版本"
const val DEVICE_DETAIL_ROW_PROTOCOL = "协议信息"
const val DEVICE_DETAIL_ROW_MEMBERS = "绑定成员"

/** 体重单位值（原型逐字，半角括号 + 空格）。 */
const val DEVICE_DETAIL_UNIT_KG = "公斤 (kg)"

/** 固件已是最新的标签（原型 .tag 逐字）。 */
const val DEVICE_DETAIL_TAG_LATEST = "最新"

/** 危险区两行（原型逐字）。 */
const val DEVICE_DETAIL_DISCONNECT = "断开连接"
const val DEVICE_DETAIL_DELETE = "删除设备"

/** 危险区说明（原型 .note 逐字）。 */
const val DEVICE_DETAIL_NOTE = "删除设备不会删除已同步的测量记录。"

/** 解绑确认弹层（原型 overlay#dlg-unbind 逐字）。 */
const val DEVICE_DETAIL_UNBIND_BODY = "解绑后需重新配对才能测量。"
const val DEVICE_DETAIL_UNBIND_BODY_STRONG = "已同步的测量记录不受影响"
const val DEVICE_DETAIL_UNBIND_CANCEL = "取消"
const val DEVICE_DETAIL_UNBIND_CONFIRM = "解绑"

/** 连接态圆点（原型「● 已连接」实心 / 「○ 未连接」空心，与状态一致）。 */
private const val STATUS_DOT_CONNECTED = "●"
private const val STATUS_DOT_DISCONNECTED = "○"

/** 设备详情页状态：设备侧数据全部外部注入，[unbindConfirmVisible] 是本页临时弹层态。 */
data class DeviceDetailUiState(
    val name: String = DEFAULT_DEVICE_NAME,
    val connected: Boolean = true,
    val batteryPercent: Int = 82,
    val weightUnit: String = DEVICE_DETAIL_UNIT_KG,
    val firmwareVersion: String = DEFAULT_LATEST_VERSION,
    val firmwareUpToDate: Boolean = true,
    val protocolId: String = "0x11",
    val linkedMemberCount: Int = 3,
    val unbindConfirmVisible: Boolean = false,
)

/** 信息行的语义 key（渲染与回调路由都以它为准，不靠字符串比对）。 */
enum class DeviceDetailRowKind {
    Name,
    WeightUnit,
    Firmware,
    Protocol,
    Members,
}

/** 单行的呈现数据：标签 + 值 + 可选标签 + 是否带右箭头。 */
data class DeviceDetailRow(
    val kind: DeviceDetailRowKind,
    val label: String,
    val value: String,
    val tag: String? = null,
    val chevron: Boolean = false,
)

/** 页面动作：行点击与解绑确认统一收口到这里，再分发给 [DeviceDetailScreen] 的回调。 */
enum class DeviceDetailAction {
    Rename,
    Unbind,
    OpenFirmware,
    OpenMemberLink,
    Back,
}

/** 页面意图。 */
enum class DeviceDetailIntent {
    /** 断开当前连接（本页乐观态，真实断连归接入层） */
    Disconnect,

    /** 点「删除设备」→ 打开解绑确认 */
    RequestUnbind,

    /** 弹层「取消」 */
    CancelUnbind,

    /** 弹层「解绑」→ 关弹层并上交 onUnbind */
    ConfirmUnbind,
}

/** 状态迁移结果：新状态 + 需要上交的动作（纯函数，无副作用）。 */
data class DeviceDetailTransition(
    val state: DeviceDetailUiState,
    val action: DeviceDetailAction? = null,
)

/** 迁移纯函数：解绑必须二次确认，取消不产生任何动作。 */
fun deviceDetailReduce(
    state: DeviceDetailUiState,
    intent: DeviceDetailIntent,
): DeviceDetailTransition = when (intent) {
    DeviceDetailIntent.Disconnect ->
        DeviceDetailTransition(state.copy(connected = false))

    DeviceDetailIntent.RequestUnbind ->
        DeviceDetailTransition(state.copy(unbindConfirmVisible = true))

    DeviceDetailIntent.CancelUnbind ->
        DeviceDetailTransition(state.copy(unbindConfirmVisible = false))

    DeviceDetailIntent.ConfirmUnbind ->
        DeviceDetailTransition(
            state.copy(unbindConfirmVisible = false),
            DeviceDetailAction.Unbind,
        )
}

/** 连接态文案（原型「● 已连接 · 电量 82%」）：电量越界时只显示连接态，不编造数值。 */
fun deviceDetailStatusText(connected: Boolean, batteryPercent: Int): String {
    val head = "${if (connected) STATUS_DOT_CONNECTED else STATUS_DOT_DISCONNECTED} " +
        if (connected) "已连接" else "未连接"
    return if (batteryPercent in 0..100) "$head · 电量 $batteryPercent%" else head
}

/** 协议行文案（`0x11` → 「协议 0x11」，与原设备页 `协议 ${protocolId}` 同口径）。 */
fun deviceDetailProtocolLabel(protocolId: String): String = "协议 $protocolId"

/** 解绑确认标题（原型「解绑「轻衡 S1」？」）。 */
fun deviceDetailUnbindTitle(name: String): String = "解绑「$name」？"

/** 信息行数据（顺序 = 原型顺序 + 任务要求的协议 / 成员两行）。 */
fun deviceDetailRows(state: DeviceDetailUiState): List<DeviceDetailRow> = listOf(
    DeviceDetailRow(
        kind = DeviceDetailRowKind.Name,
        label = DEVICE_DETAIL_ROW_NAME,
        value = state.name,
        chevron = true,
    ),
    DeviceDetailRow(
        kind = DeviceDetailRowKind.WeightUnit,
        label = DEVICE_DETAIL_ROW_UNIT,
        value = state.weightUnit,
        // 回调契约无单位切换，故不给箭头，避免死入口（见文件头「刻意差异」）
        chevron = false,
    ),
    DeviceDetailRow(
        kind = DeviceDetailRowKind.Firmware,
        label = DEVICE_DETAIL_ROW_FIRMWARE,
        value = state.firmwareVersion,
        tag = if (state.firmwareUpToDate) DEVICE_DETAIL_TAG_LATEST else UPDATE_AVAILABLE_TAG,
        // 原型固件行只有标签没有箭头，保持一致；点击仍进入固件管理
        chevron = false,
    ),
    DeviceDetailRow(
        kind = DeviceDetailRowKind.Protocol,
        label = DEVICE_DETAIL_ROW_PROTOCOL,
        value = deviceDetailProtocolLabel(state.protocolId),
        chevron = false,
    ),
    DeviceDetailRow(
        kind = DeviceDetailRowKind.Members,
        label = DEVICE_DETAIL_ROW_MEMBERS,
        value = linkedMemberSummary(state.linkedMemberCount),
        chevron = true,
    ),
)

/** 行 → 动作路由（回调用它分发；无入口的行返回 null，保证不出现死点击）。 */
fun deviceDetailRowAction(kind: DeviceDetailRowKind): DeviceDetailAction? = when (kind) {
    DeviceDetailRowKind.Name -> DeviceDetailAction.Rename
    DeviceDetailRowKind.Firmware -> DeviceDetailAction.OpenFirmware
    DeviceDetailRowKind.Members -> DeviceDetailAction.OpenMemberLink
    DeviceDetailRowKind.WeightUnit, DeviceDetailRowKind.Protocol -> null
}

/**
 * 设备详情 / 设置页（原型 `v-settings`）。
 *
 * @param state 设备侧数据（外部注入）
 * @param onRename 设备名称行 → 重命名页
 * @param onUnbind 解绑确认弹层「解绑」→ 接入层解绑
 * @param onOpenFirmware 固件版本行 → 固件管理
 * @param onOpenMemberLink 绑定成员行 → 成员设备关联
 */
@Composable
fun DeviceDetailScreen(
    state: DeviceDetailUiState,
    onRename: () -> Unit = {},
    onUnbind: () -> Unit = {},
    onOpenFirmware: () -> Unit = {},
    onOpenMemberLink: () -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    // 本页临时态（弹层 / 乐观断连）由纯函数迁移；外部注入的 state 变了就整页重建
    var page by remember(state) { mutableStateOf(state) }
    // 设备校准（T-062）：只做引导状态机，不伪造设备端写入
    var showCalibration by remember { mutableStateOf(false) }
    var calibStep by remember { mutableStateOf(CalibrationStep.Idle) }

    // 迁移 + 动作分发：确认解绑才上交 onUnbind
    val dispatch: (DeviceDetailIntent) -> Unit = { intent ->
        val next = deviceDetailReduce(page, intent)
        page = next.state
        if (next.action == DeviceDetailAction.Unbind) onUnbind()
    }

    val onAction: (DeviceDetailAction) -> Unit = { action ->
        when (action) {
            DeviceDetailAction.Rename -> onRename()
            DeviceDetailAction.OpenFirmware -> onOpenFirmware()
            DeviceDetailAction.OpenMemberLink -> onOpenMemberLink()
            DeviceDetailAction.Back -> onBack()
            // 解绑动作只从弹层确认产生，行点击不会直接走到这里
            DeviceDetailAction.Unbind -> dispatch(DeviceDetailIntent.RequestUnbind)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        DevicePageNavBar(DEVICE_DETAIL_TITLE, onBack)

        DeviceDetailHeroCard(page)

        // 信息行（原型 .rows：label + 值 + 可选标签 / 箭头）
        QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
            val rows = deviceDetailRows(page)
            rows.forEachIndexed { index, row ->
                val action = deviceDetailRowAction(row.kind)
                QkListRow(
                    title = row.label,
                    showDivider = index != rows.lastIndex,
                    onClick = action?.let { { onAction(it) } },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                row.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = QkColor.Sub,
                            )
                            row.tag?.let { tag ->
                                QkPill(
                                    tag,
                                    QkPillTone.Green,
                                    modifier = Modifier.padding(start = QkSpacing.Sm),
                                )
                            }
                            if (row.chevron) {
                                QkIcon(
                                    QkIcons.ChevronRight,
                                    size = QkSpacing.Lg,
                                    modifier = Modifier.padding(start = QkSpacing.Xs),
                                )
                            }
                        }
                    },
                )
            }
        }

        // 设备校准：引导用户检查放置与归零，不冒充"已写入校准值"
        QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
            QkListRow(
                title = "设备校准",
                showDivider = false,
                onClick = {
                    calibStep = CalibrationStep.Idle
                    showCalibration = true
                },
            )
        }

        if (showCalibration) {
            val isDone = calibStep == CalibrationStep.Done
            AlertDialog(
                onDismissRequest = { showCalibration = false },
                title = { Text("设备校准") },
                text = { Text(calibrationHint(calibStep)) },
                confirmButton = {
                    TextButton(onClick = {
                        val event = when (calibStep) {
                            CalibrationStep.Idle -> CalibrationEvent.Start
                            CalibrationStep.PlaceOnFloor -> CalibrationEvent.FloorConfirmed
                            CalibrationStep.StepOn -> CalibrationEvent.SteppedOn
                            CalibrationStep.StepOff -> CalibrationEvent.SteppedOff
                            CalibrationStep.Done -> CalibrationEvent.Restart
                        }
                        calibStep = calibrationNext(calibStep, event)
                        if (isDone) showCalibration = false
                    }) {
                        Text(if (isDone) "完成" else "下一步")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCalibration = false }) { Text("关闭") }
                },
            )
        }

        // 断开连接（原型 .rows 第一组；本页只置乐观态，真实断连归接入层）
        QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
            QkListRow(
                title = DEVICE_DETAIL_DISCONNECT,
                showDivider = false,
                onClick = { dispatch(DeviceDetailIntent.Disconnect) },
            )
        }

        // 删除设备（危险行 + 解绑确认）
        QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(QkRadius.Glyph))
                    .clickable { dispatch(DeviceDetailIntent.RequestUnbind) }
                    .padding(vertical = QkSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // QkGlyphTone 无危险色档位；危险圆底直接取 QkColor.DangerSoft + 图标 QkColor.Danger
                Box(
                    modifier = Modifier
                        .size(QkSpacing.Xl + QkSpacing.Md)
                        .clip(CircleShape)
                        .background(QkColor.DangerSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    // QkIcons 无垃圾桶图标，取语义最近的删除图标（新增图标需改 QkIcons，非本任务范围）
                    QkIcon(QkIcons.DeleteAccount, tint = QkColor.Danger, size = QkSpacing.Lg)
                }
                Text(
                    DEVICE_DETAIL_DELETE,
                    modifier = Modifier.padding(start = QkSpacing.Md),
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Danger,
                )
            }
        }

        Text(
            DEVICE_DETAIL_NOTE,
            modifier = Modifier.padding(start = QkSpacing.Xs, top = QkSpacing.Sm),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )

        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }

    // 解绑二次确认（原型 overlay#dlg-unbind）：取消只关弹层，解绑才上交
    if (page.unbindConfirmVisible) {
        AlertDialog(
            onDismissRequest = { dispatch(DeviceDetailIntent.CancelUnbind) },
            title = { Text(deviceDetailUnbindTitle(page.name)) },
            text = {
                Column {
                    Text(DEVICE_DETAIL_UNBIND_BODY, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        DEVICE_DETAIL_UNBIND_BODY_STRONG,
                        modifier = Modifier.padding(top = QkSpacing.Xs),
                        style = MaterialTheme.typography.labelLarge,
                        color = QkColor.Fg,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { dispatch(DeviceDetailIntent.ConfirmUnbind) }) {
                    Text(DEVICE_DETAIL_UNBIND_CONFIRM, color = QkColor.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { dispatch(DeviceDetailIntent.CancelUnbind) }) {
                    Text(DEVICE_DETAIL_UNBIND_CANCEL, color = QkColor.Primary)
                }
            },
        )
    }
}

/** 设备卡：白色圆底图标 + 设备名 + 连接态（原型 .card.soft 首位卡片）。 */
@Composable
private fun DeviceDetailHeroCard(state: DeviceDetailUiState) {
    QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(QkSpacing.Xl * 2)
                    .clip(CircleShape)
                    .background(QkColor.Mint),
                contentAlignment = Alignment.Center,
            ) {
                QkIcon(QkIcons.Device, tint = QkColor.Primary, size = QkSpacing.Xl)
            }
            Column(modifier = Modifier.padding(start = QkSpacing.Md)) {
                Text(
                    state.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = QkColor.Fg,
                )
                Text(
                    deviceDetailStatusText(state.connected, state.batteryPercent),
                    modifier = Modifier.padding(top = QkSpacing.Xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Primary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceDetailScreenPreview() {
    QkTheme {
        DeviceDetailScreen(state = DeviceDetailUiState())
    }
}
