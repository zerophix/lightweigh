package com.xcwk.scalehub.feature.device

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * device — 配对阶段：连接中 / 连接成功 / 连接失败（T-103 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/18-device-setup.html` 的后两个 view：
 * - `v-connecting` 连接中：标题「正在连接 轻衡 S1…」+ 四步行进（蓝牙已开启 / 已发现设备 /
 *   建立连接… / 同步设备信息）+ 「模拟成功」「取消」
 * - `v-connected` 连接成功：勾选泡 + 「连接成功」+「「轻衡 S1」已连接，正在检查固件版本…」+「继续」
 * - 失败态原型未画，按业务需要的分支补（与权限被拒分支同一口径：不静默停在连接中）
 *
 * ## 边界（呈现式组件）
 * 本文件**不发起 GATT 连接、不轮询进度**：进度经 [DeviceConnectIntent.StageChanged] 注入，
 * 成功 / 失败经 [DeviceConnectIntent.Succeeded] / [DeviceConnectIntent.Failed] 注入。
 * 「模拟成功」是原型自带的演示触发器，仅在四步全部走完（[ConnectStage.SyncingInfo]）时才可点，
 * 生产环境用外部注入的成功事件即可。
 *
 * ## 不变量（JVM 单测钉死）
 * 1. 进度单调前进：迟到的低阶段事件不会把行进条拉回去；
 * 2. 成功只在「连接中」成立；失败态不被迟到的成功事件静默改写（需重试重新开始）；
 * 3. 重试把行进条复位到 [ConnectStage.Establishing] 并清空失败原因；
 * 4. 取消与「模拟成功」的可用性由纯函数 [canSimulateSuccess] 决定，不散落在 UI 里。
 */

// ---- 连接子状态机 ----

/** 连接行进条的四个阶段（原型 v-connecting 四行，顺序即推进顺序）。 */
enum class ConnectStage { BluetoothOn, DeviceFound, Establishing, SyncingInfo }

/** 单个阶段在进度条上的状态。 */
enum class ConnectStageState { Done, Active, Pending }

/** 配对阶段三态（原型 v-connecting / v-connected + 补充的失败分支）。 */
enum class DeviceConnectStep { Connecting, Connected, Failed }

/**
 * 配对阶段的呈现态。设备名与进度由调用方注入。
 *
 * @param stage 当前进行到的阶段（之前的阶段一律 Done，之后的 Pending）
 * @param failureReason 失败原因（失败态才非空）
 */
data class DeviceConnectUiState(
    val step: DeviceConnectStep = DeviceConnectStep.Connecting,
    val deviceName: String = DEFAULT_DEVICE_SHORT_NAME,
    val stage: ConnectStage = ConnectStage.Establishing,
    val failureReason: String = "",
)

/** 配对阶段事件，全部由外部（真实 BLE 栈）注入。 */
sealed interface DeviceConnectIntent {
    /** 进度回填：只前进不后退。 */
    data class StageChanged(val stage: ConnectStage) : DeviceConnectIntent

    /** GATT 连接 + 设备信息同步完成。 */
    data object Succeeded : DeviceConnectIntent

    /** 连接或同步失败，[reason] 用于失败页展示。 */
    data class Failed(val reason: String = "") : DeviceConnectIntent

    /** 失败页「重试」：复位进度重新连接。 */
    data object Retry : DeviceConnectIntent
}

/**
 * 纯呈现态转移（配对阶段）—— JVM 单测钉死的部分。
 *
 * 口径：
 * - [DeviceConnectIntent.StageChanged] 仅在连接中生效，且只接受不早于当前阶段的输入
 * - [DeviceConnectIntent.Succeeded] 仅在连接中生效，落到已连接并把行进条拉满
 * - [DeviceConnectIntent.Failed] 从连接中或已连接（同步阶段失败）落到失败态
 * - [DeviceConnectIntent.Retry] 从失败态复位到连接中的「建立连接」阶段
 */
fun deviceConnectReduce(
    state: DeviceConnectUiState,
    intent: DeviceConnectIntent,
): DeviceConnectUiState = when (intent) {
    is DeviceConnectIntent.StageChanged ->
        if (state.step != DeviceConnectStep.Connecting || intent.stage.ordinal < state.stage.ordinal) {
            state
        } else {
            state.copy(stage = intent.stage)
        }

    DeviceConnectIntent.Succeeded ->
        if (state.step == DeviceConnectStep.Connecting) {
            state.copy(step = DeviceConnectStep.Connected, stage = ConnectStage.SyncingInfo)
        } else {
            state
        }

    is DeviceConnectIntent.Failed ->
        if (state.step == DeviceConnectStep.Failed) {
            state
        } else {
            state.copy(step = DeviceConnectStep.Failed, failureReason = intent.reason)
        }

    DeviceConnectIntent.Retry ->
        if (state.step == DeviceConnectStep.Failed) {
            state.copy(
                step = DeviceConnectStep.Connecting,
                stage = ConnectStage.Establishing,
                failureReason = "",
            )
        } else {
            state
        }
}

/** 某阶段在行进条上的状态：早于 [current] 为完成，等于为进行中，晚于为未开始。 */
fun connectStageState(stage: ConnectStage, current: ConnectStage): ConnectStageState = when {
    stage.ordinal < current.ordinal -> ConnectStageState.Done
    stage.ordinal == current.ordinal -> ConnectStageState.Active
    else -> ConnectStageState.Pending
}

/** 阶段文案（原型 v-connecting 四行逐字）。 */
fun connectStageLabel(stage: ConnectStage): String = when (stage) {
    ConnectStage.BluetoothOn -> STAGE_BLUETOOTH_ON
    ConnectStage.DeviceFound -> STAGE_DEVICE_FOUND
    ConnectStage.Establishing -> STAGE_ESTABLISHING
    ConnectStage.SyncingInfo -> STAGE_SYNC_INFO
}

/** 「模拟成功」是否可点：连接中且四步全部走完（原型演示触发器的生产口径收敛）。 */
fun canSimulateSuccess(state: DeviceConnectUiState): Boolean =
    state.step == DeviceConnectStep.Connecting && state.stage == ConnectStage.SyncingInfo

// ---- 静态文案（逐字取自原型，JVM 单测校验） ----

const val TITLE_CONNECTING = "连接设备"
const val TITLE_CONNECTED = "连接成功"

// v-connecting
const val CONNECTING_SUBLINE = "请保持手机靠近体脂秤，\n轻踩秤面确保唤醒"
const val STAGE_BLUETOOTH_ON = "蓝牙已开启"
const val STAGE_DEVICE_FOUND = "已发现设备"
const val STAGE_ESTABLISHING = "建立连接…"
const val STAGE_SYNC_INFO = "同步设备信息"
const val ACTION_SIMULATE_SUCCESS = "模拟成功"
const val ACTION_CANCEL = "取消"

// v-connected
const val CONNECTED_TITLE = "连接成功"
const val ACTION_GO_FIRMWARE = "继续"

// 失败分支（原型未画；与「权限被拒」同口径补出口，不静默停在连接中）
const val TITLE_CONNECT_FAILED = "连接失败"
const val CONNECT_FAILED_HEADLINE = "未能连接体脂秤"
const val CONNECT_FAILED_DEFAULT_REASON = "请确认蓝牙已开启、手机靠近秤面，然后重试。"
const val ACTION_RETRY = "重试"
const val ACTION_BACK_TO_CONFIRM = "返回设备列表"

/** 连接中标题：原型「正在连接 轻衡 S1…」。 */
fun connectingHeadline(deviceName: String): String = "正在连接 $deviceName…"

/** 已连接副文案：原型「「轻衡 S1」已连接，正在检查固件版本…」。 */
fun connectedSubtitle(deviceName: String): String = "「$deviceName」已连接，\n正在检查固件版本…"

/** 失败页副文案：优先展示外部注入的原因，缺省用通用指引。 */
fun connectFailureText(reason: String): String =
    reason.ifBlank { CONNECT_FAILED_DEFAULT_REASON }

// ---- 原型实测尺寸（一律由 Qk token 组合，页面不出现裸 dp 字面量） ----

/** 连接中主进度环（原型 48px）。 */
private val SpinnerSize: Dp = QkSpacing.Xl * 2

/** 连接中主进度环描边（原型 4px）。 */
private val SpinnerStroke: Dp = QkSpacing.Xs

/** 行进条行内小环（原型 20px）。 */
private val RowSpinnerSize: Dp = QkSpacing.Xl - QkSpacing.Xs

/** 行进条行内小环描边。 */
private val RowSpinnerStroke: Dp = QkSpacing.Xs / 2

/** 已连接页状态泡（原型 .statepage .sic 88px）。 */
private val StateRingSize: Dp = QkSpacing.Xl * 3 + QkSpacing.Lg

/** 已连接页状态勾（泡的一半）。 */
private val StateGlyphSize: Dp = QkSpacing.Xl + QkSpacing.Sm

/** 行进条行首图标底（原型 .ic 28px）。 */
private val StageIconSize: Dp = QkSpacing.Xl + QkSpacing.Xs

/** 行进条行首图标本体。 */
private val StageGlyphSize: Dp = QkSpacing.Lg + QkSpacing.Xs

// ---- 三态路由 ----

/**
 * 配对阶段入口：按 [DeviceConnectUiState.step] 呈现连接中 / 已连接 / 失败。
 *
 * 由 [BtPermissionFlow] 在 [BtFlowStep.Connect] 时调用，共用同一份 [BtFlowUiState]。
 */
@Composable
fun DeviceConnectFlow(
    state: BtFlowUiState,
    onIntent: (BtFlowIntent) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onCancel: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    val connect = state.connect
    val dispatch: (DeviceConnectIntent) -> Unit = { onIntent(BtFlowIntent.ConnectEvent(it)) }
    when (connect.step) {
        DeviceConnectStep.Connecting -> ConnectingView(
            state = connect,
            onSimulateSuccess = { dispatch(DeviceConnectIntent.Succeeded) },
            onCancel = {
                onCancel()
                onIntent(BtFlowIntent.CancelConnect)
            },
            modifier = modifier,
        )

        DeviceConnectStep.Connected -> ConnectedView(
            deviceName = connect.deviceName,
            onContinue = onContinue,
            modifier = modifier,
        )

        DeviceConnectStep.Failed -> ConnectFailedView(
            state = connect,
            onRetry = {
                onRetry()
                dispatch(DeviceConnectIntent.Retry)
            },
            onCancel = {
                onCancel()
                onIntent(BtFlowIntent.CancelConnect)
            },
            modifier = modifier,
        )
    }
}

// ---- v-connecting ----

@Composable
private fun ConnectingView(
    state: DeviceConnectUiState,
    onSimulateSuccess: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page),
    ) {
        DeviceFlowNavBar(TITLE_CONNECTING, onCancel)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                color = QkColor.Primary,
                modifier = Modifier.size(SpinnerSize),
                strokeWidth = SpinnerStroke,
            )
            Text(
                connectingHeadline(state.deviceName),
                modifier = Modifier.padding(top = QkSpacing.Xl),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
            Text(
                CONNECTING_SUBLINE,
                modifier = Modifier.padding(top = QkSpacing.Sm),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
        QkCard(modifier = Modifier.padding(top = QkSpacing.Xl), padding = QkSpacing.Sm) {
            ConnectStage.values().forEach { stage ->
                ConnectStageRow(
                    stage = stage,
                    stageState = connectStageState(stage, state.stage),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            QkButton(
                text = ACTION_SIMULATE_SUCCESS,
                modifier = Modifier.weight(1f),
                style = QkButtonStyle.Secondary,
                enabled = canSimulateSuccess(state),
                onClick = onSimulateSuccess,
            )
            QkButton(
                text = ACTION_CANCEL,
                modifier = Modifier.weight(1f),
                style = QkButtonStyle.Secondary,
                onClick = onCancel,
            )
        }
        Spacer(modifier = Modifier.height(QkSpacing.Lg))
    }
}

/** 行进条一行：行首阶段图标（完成打勾 / 进行中转圈 / 未开始空心）+ 阶段文案。 */
@Composable
private fun ConnectStageRow(
    stage: ConnectStage,
    stageState: ConnectStageState,
) {
    val pending = stageState == ConnectStageState.Pending
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
    ) {
        Box(
            modifier = Modifier
                .size(StageIconSize)
                .clip(CircleShape)
                .background(if (pending) QkColor.Card else QkColor.MintSoft),
            contentAlignment = Alignment.Center,
        ) {
            when (stageState) {
                ConnectStageState.Done -> QkIcon(
                    QkIcons.Habit,
                    tint = QkColor.Primary,
                    size = StageGlyphSize,
                    contentDescription = connectStageLabel(stage),
                )

                ConnectStageState.Active -> CircularProgressIndicator(
                    color = QkColor.Primary,
                    modifier = Modifier.size(RowSpinnerSize),
                    strokeWidth = RowSpinnerStroke,
                )

                ConnectStageState.Pending -> QkIcon(
                    QkIcons.Device,
                    tint = QkColor.Hint,
                    size = StageGlyphSize,
                    contentDescription = connectStageLabel(stage),
                )
            }
        }
        Text(
            connectStageLabel(stage),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = if (pending) QkColor.Hint else QkColor.Fg,
        )
    }
}

// ---- v-connected ----

@Composable
private fun ConnectedView(
    deviceName: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DeviceFlowNavBar(TITLE_CONNECTED, onContinue)
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.size(StateRingSize).clip(CircleShape).background(QkColor.Mint),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(QkIcons.Habit, tint = QkColor.Primary, size = StateGlyphSize)
        }
        Text(
            CONNECTED_TITLE,
            modifier = Modifier.padding(top = QkSpacing.Lg),
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Text(
            connectedSubtitle(deviceName),
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
            textAlign = TextAlign.Center,
        )
        CircularProgressIndicator(
            color = QkColor.Primary,
            modifier = Modifier.padding(top = QkSpacing.Xl).size(StateGlyphSize),
            strokeWidth = SpinnerStroke,
        )
        Spacer(modifier = Modifier.weight(1f))
        QkButton(
            text = ACTION_GO_FIRMWARE,
            modifier = Modifier.fillMaxWidth(),
            onClick = onContinue,
        )
        Spacer(modifier = Modifier.height(QkSpacing.Lg))
    }
}

// ---- 失败分支 ----

@Composable
private fun ConnectFailedView(
    state: DeviceConnectUiState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page),
    ) {
        DeviceFlowNavBar(TITLE_CONNECT_FAILED, onCancel)
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(StateRingSize).clip(CircleShape).background(QkColor.DangerSoft),
                contentAlignment = Alignment.Center,
            ) {
                QkIcon(Icons.Outlined.WarningAmber, tint = QkColor.Danger, size = StateGlyphSize)
            }
            Text(
                CONNECT_FAILED_HEADLINE,
                modifier = Modifier.padding(top = QkSpacing.Lg),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
            Text(
                connectFailureText(state.failureReason),
                modifier = Modifier.padding(top = QkSpacing.Sm),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        QkButton(
            text = ACTION_RETRY,
            modifier = Modifier.fillMaxWidth(),
            onClick = onRetry,
        )
        Spacer(modifier = Modifier.height(QkSpacing.Md))
        QkButton(
            text = ACTION_BACK_TO_CONFIRM,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Ghost,
            onClick = onCancel,
        )
        Spacer(modifier = Modifier.height(QkSpacing.Lg))
    }
}

