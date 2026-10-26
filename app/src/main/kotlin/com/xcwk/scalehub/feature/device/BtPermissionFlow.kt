package com.xcwk.scalehub.feature.device

import com.xcwk.scalehub.ui.components.QkScreenHeader
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * device — 蓝牙权限与设备连接流程（T-103 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/18-device-setup.html` 的前四个 view：
 * - `v-bt-permission` 权限说明：为什么需要「附近设备」权限 + 三项用途 + 系统弹窗预告 + 「继续」
 * - `v-confirm-device` 确认设备：发现到的设备（型号 / 序列号 / 信号 / 电量）+ 误判提示 + 「确认，开始配对」「重新搜索」
 * - `v-connecting` 连接中（见 [DeviceConnectFlow]）：四步进度 + 「模拟成功」「取消」
 * - `v-connected` 连接成功（见 [DeviceConnectFlow]）：「已连接，正在检查固件版本…」+「继续」
 *
 * ## 验收（AC-BLE-03 蓝牙权限与连接流程）
 * - 权限页说明「Android 12+ 需要「附近设备」权限」，且不索取位置信息
 * - 权限结果由外部注入：允许才进入确认设备页，拒绝则留在本页并给出「去系统设置」出口
 * - 确认设备页展示设备型号 / 序列号 / 信号强度 / 电量，可确认配对或重新搜索
 * - 配对阶段（连接中 → 已连接 / 失败）由 [DeviceConnectFlow] 承担
 *
 * ## 边界（呈现式组件）
 * 本文件**不申请权限、不扫描蓝牙、不建立连接**：
 * - 权限结果经 [BtFlowIntent.PermissionResult] 注入（系统弹窗在外部）
 * - 扫描结果经 [BtFlowIntent.DeviceFound] 注入
 * - 连接进度经 [BtFlowIntent.ConnectEvent] 转交 [deviceConnectReduce]
 *
 * 组件只在用户动作时通过回调请求外部做事：
 * - [BtPermissionFlow.onRequestBtPermission] 权限页「继续」且尚未授权 → 外部发起系统权限请求
 * - [BtPermissionFlow.onOpenSettings] 权限被拒 → 外部跳系统设置
 * - [BtPermissionFlow.onConfirmDevice] 确认配对 → 外部发起 GATT 连接
 * - [BtPermissionFlow.onCancel] 取消 → 外部出栈
 * - [BtPermissionFlow.onRetry] 连接失败重试 → 外部重新连接
 * - [BtPermissionFlow.onContinue] 连接成功「继续」→ 交给固件检查流程（T-079）
 *
 * ## 不变量（JVM 单测钉死）
 * 1. 权限未允许时 [BtFlowIntent.ConfirmDevice] 无效——不得跳过权限直接配对；
 * 2. 权限被拒后停留 [BtFlowStep.Permission]（不前进、不回到初始态），并记录
 *    [BtPermissionStatus.Denied] 以呈现「去系统设置」出口；
 * 3. 重新搜索回到权限页并清空发现结果，但**保留**已授予的权限（不重复弹窗）；
 * 4. 取消连接只回到确认设备页，不影响已授予的权限。
 */

// ---- 权限与流程状态 ----

/** 蓝牙权限状态。三态而非布尔：未询问过与用户明确拒绝需要不同的出口。 */
enum class BtPermissionStatus { Unknown, Granted, Denied }

/** 流程的呈现阶段（连接中 / 已连接 / 失败三个子态收在 [BtFlowUiState.connect] 内）。 */
enum class BtFlowStep { Permission, ConfirmDevice, Connect }

/**
 * 发现到的一台设备（扫描结果由外部注入）。
 *
 * @param name 完整名称，原型「轻衡 S1 智能体脂秤」
 * @param shortName 连接文案里用的短名，原型「轻衡 S1」
 * @param model 型号，原型「XHS-01」
 * @param serialMasked 打码序列号，原型「****8A2F」
 * @param signalBars 信号格数，原型画 4 格亮 3 格
 * @param batteryPercent 电量百分比，原型「82%」
 */
data class DiscoveredDevice(
    val name: String = DEFAULT_DEVICE_FULL_NAME,
    val shortName: String = DEFAULT_DEVICE_SHORT_NAME,
    val model: String = DEFAULT_DEVICE_MODEL,
    val serialMasked: String = DEFAULT_DEVICE_SERIAL_MASKED,
    val signalBars: Int = DEFAULT_DEVICE_SIGNAL_BARS,
    val batteryPercent: Int = DEFAULT_DEVICE_BATTERY_PERCENT,
) {
    init {
        require(signalBars in 0..SIGNAL_BAR_COUNT) { "signalBars 必须落在 0..$SIGNAL_BAR_COUNT，收到 $signalBars" }
        // -1 = 扫描阶段读不到电量（真实扫描只给名字与广播强度），确认页如实写「连接后读取」
        require(batteryPercent in -1..100) { "batteryPercent 必须落在 -1..100（-1=未知），收到 $batteryPercent" }
    }
}

/** 扫描阶段拿不到设备规格时，确认页的如实替代文案（不拿原型默认值冒充真机）。 */
const val SPEC_UNKNOWN_TEXT = "连接后读取"

/** 电量未知（真实扫描阶段）的哨兵值。 */
const val BATTERY_UNKNOWN: Int = -1

/**
 * 真实扫描结果 → 确认设备页的入参：只填本机确实读到的两项（名称、广播强度），
 * 型号 / 序列号 / 电量留空，由 [deviceModelLine] / [deviceBatteryLabel] 如实说明。
 */
fun discoveredDeviceOf(found: FoundScale): DiscoveredDevice = DiscoveredDevice(
    name = found.name,
    shortName = found.name,
    model = "",
    serialMasked = "",
    signalBars = signalBarsOf(found.rssi),
    batteryPercent = BATTERY_UNKNOWN,
)

/** 广播强度 → 信号格数（原型四格；RSSI 分档，越界夹到两端）。 */
fun signalBarsOf(rssi: Int): Int = when {
    rssi >= -55 -> 4
    rssi >= -65 -> 3
    rssi >= -75 -> 2
    rssi >= -85 -> 1
    else -> 0
}

/** 权限 → 确认 → 连接（连接子态见 [DeviceConnectUiState]）。 */
data class BtFlowUiState(
    val step: BtFlowStep = BtFlowStep.Permission,
    val permission: BtPermissionStatus = BtPermissionStatus.Unknown,
    /** 已向系统发起权限请求、等待结果（期间按钮置灰，防重复弹窗） */
    val awaitingPermission: Boolean = false,
    /** 正在扫描（结果由外部注入） */
    val discovering: Boolean = false,
    val device: DiscoveredDevice? = null,
    val connect: DeviceConnectUiState = DeviceConnectUiState(),
)

/** 流程内的呈现态转移。外部副作用（弹权限、扫蓝牙、连接、跳设置）走回调，不进 reduce。 */
sealed interface BtFlowIntent {
    /** 权限页「继续」。已授权则直接进确认页，否则等外部权限结果。 */
    data object Continue : BtFlowIntent

    /** 系统权限结果回填（外部弹窗结束后调用）。 */
    data class PermissionResult(val granted: Boolean) : BtFlowIntent

    /** 已跳系统设置，重置为「未询问」以便返回后重新判断。 */
    data object OpenSettings : BtFlowIntent

    /** 确认设备页「重新搜索」：回到权限页并清空发现结果。 */
    data object Rescan : BtFlowIntent

    /** 扫描结果回填（外部发现设备后调用）。 */
    data class DeviceFound(val device: DiscoveredDevice) : BtFlowIntent

    /** 确认设备页「确认，开始配对」。 */
    data object ConfirmDevice : BtFlowIntent

    /** 连接子流程事件（进度 / 成功 / 失败 / 重试），转交 [deviceConnectReduce]。 */
    data class ConnectEvent(val intent: DeviceConnectIntent) : BtFlowIntent

    /** 连接中「取消」：回到确认设备页。 */
    data object CancelConnect : BtFlowIntent
}

/**
 * 纯呈现态转移 —— JVM 单测钉死的部分。
 *
 * 口径：
 * - [BtFlowIntent.Continue]：`Granted` 直接进确认页；`Unknown` / `Denied` 只置
 *   [BtFlowUiState.awaitingPermission]，真正的推进等 [BtFlowIntent.PermissionResult]
 * - [BtFlowIntent.PermissionResult]：允许才进确认页；拒绝留在权限页并记 `Denied`
 * - [BtFlowIntent.ConfirmDevice]：仅「已授权 + 已有设备」才进连接态（双保险，防越权）
 * - [BtFlowIntent.Rescan]：权限保留、设备清空、仍在权限页
 * - [BtFlowIntent.CancelConnect]：回确认页并把连接子态复位
 */
fun btFlowReduce(state: BtFlowUiState, intent: BtFlowIntent): BtFlowUiState = when (intent) {
    BtFlowIntent.Continue -> when {
        state.permission == BtPermissionStatus.Granted ->
            state.copy(step = BtFlowStep.ConfirmDevice, awaitingPermission = false)

        else -> state.copy(awaitingPermission = true)
    }

    is BtFlowIntent.PermissionResult -> if (intent.granted) {
        state.copy(
            step = BtFlowStep.ConfirmDevice,
            permission = BtPermissionStatus.Granted,
            awaitingPermission = false,
            discovering = state.device == null,
        )
    } else {
        state.copy(
            step = BtFlowStep.Permission,
            permission = BtPermissionStatus.Denied,
            awaitingPermission = false,
            discovering = false,
        )
    }

    BtFlowIntent.OpenSettings -> state.copy(
        permission = BtPermissionStatus.Unknown,
        awaitingPermission = false,
    )

    BtFlowIntent.Rescan -> state.copy(
        step = BtFlowStep.Permission,
        discovering = true,
        device = null,
    )

    is BtFlowIntent.DeviceFound ->
        if (state.step == BtFlowStep.Connect) {
            state
        } else {
            state.copy(
                step = BtFlowStep.ConfirmDevice,
                device = intent.device,
                discovering = false,
            )
        }

    BtFlowIntent.ConfirmDevice ->
        if (state.permission == BtPermissionStatus.Granted && state.device != null) {
            state.copy(
                step = BtFlowStep.Connect,
                connect = DeviceConnectUiState(
                    step = DeviceConnectStep.Connecting,
                    deviceName = requireNotNull(state.device).shortName,
                    stage = ConnectStage.Establishing,
                ),
            )
        } else {
            state
        }

    is BtFlowIntent.ConnectEvent ->
        if (state.step == BtFlowStep.Connect) {
            state.copy(connect = deviceConnectReduce(state.connect, intent.intent))
        } else {
            state
        }

    BtFlowIntent.CancelConnect ->
        if (state.step == BtFlowStep.Connect) {
            state.copy(
                step = BtFlowStep.ConfirmDevice,
                connect = DeviceConnectUiState(deviceName = state.device?.shortName ?: DEFAULT_DEVICE_SHORT_NAME),
            )
        } else {
            state
        }
}

// ---- 静态文案与规格（逐字取自原型，JVM 单测校验） ----

const val TITLE_BT_PERMISSION = "蓝牙权限"
const val TITLE_CONFIRM_DEVICE = "确认设备"

// v-bt-permission
const val BT_PERMISSION_HEADLINE = "需要蓝牙权限\n才能连接体脂秤"
const val BT_PERMISSION_SUBLINE = "Android 12+ 需要「附近设备」权限。\n我们只用它发现和连接体脂秤，\n不会读取你的位置信息。"
const val BT_PERMISSION_BANNER = "点击「继续」后系统会弹出权限请求，你可以拒绝，但连接体脂秤需要此权限。"
const val ACTION_CONTINUE = "继续"

// v-bt-permission 被拒分支（原型未覆盖，本流程补的出口；不索取位置信息）
const val BT_PERMISSION_DENIED_HINT = "未获得附近设备权限，无法搜索体脂秤。可在系统设置中开启后重试。"
const val ACTION_OPEN_SETTINGS = "去系统设置"

// v-confirm-device
const val CONFIRM_HEADLINE = "发现一台设备"
const val CONFIRM_SUBLINE = "请确认是否为你要连接的体脂秤"
const val LABEL_SIGNAL = "信号强度"
const val LABEL_BATTERY = "电量"
const val CONFIRM_BANNER = "如果不是你的设备，请轻踩秤面唤醒后重新搜索。"
const val ACTION_CONFIRM_PAIR = "确认，开始配对"
const val ACTION_RESCAN = "重新搜索"

// ---- 设备规格默认值（原型 v-confirm-device / v-connecting / v-connected 的同一台秤） ----

const val DEFAULT_DEVICE_FULL_NAME = "轻衡 S1 智能体脂秤"
const val DEFAULT_DEVICE_SHORT_NAME = "轻衡 S1"
const val DEFAULT_DEVICE_MODEL = "XHS-01"
const val DEFAULT_DEVICE_SERIAL_MASKED = "****8A2F"
const val DEFAULT_DEVICE_SIGNAL_BARS = 3
const val DEFAULT_DEVICE_BATTERY_PERCENT = 82

/** 信号格总数（原型画 4 格，亮 3 格）。 */
const val SIGNAL_BAR_COUNT = 4

/** 原型里那台秤；扫描结果未回填时确认页以此兜底呈现，避免空白。 */
val DEFAULT_DISCOVERED_DEVICE = DiscoveredDevice()

/** 权限页一条用途：[title] 主文案 + [desc] 副文案（原型 v-bt-permission 三行）。 */
data class BtPermissionItem(val title: String, val desc: String)

/** 权限页三项用途（原型三行，顺序与原文一致）。 */
val BT_PERMISSION_ITEMS = listOf(
    BtPermissionItem("搜索附近体脂秤", "发现可连接的轻衡设备"),
    BtPermissionItem("传输测量数据", "读取体重和体成分结果"),
    BtPermissionItem("读取设备信息", "电量、固件版本、序列号"),
)

// ---- 原型实测尺寸（一律由 Qk token 组合，页面不出现裸 dp 字面量） ----

/** 权限页大图标圆底（原型 .bigicon.ring 96px）。 */
private val HeroRingSize: Dp = QkSpacing.Xl * 4

/** 权限页大图标本体（圆底的一半）。 */
private val HeroGlyphSize: Dp = QkSpacing.Xl * 2

/** 确认设备页大图标圆底（原型 .bigicon 88px）。 */
private val DeviceRingSize: Dp = QkSpacing.Xl * 3 + QkSpacing.Lg

/** 确认设备页大图标本体。 */
private val DeviceGlyphSize: Dp = QkSpacing.Xl + QkSpacing.Sm

/** 提示横幅图标（原型 .banner .bic 18px）。 */
private val BannerIconSize: Dp = QkSpacing.Lg + QkSpacing.Xs / 2

/** 信号格单格宽（原型 8px）。 */
private val SignalBarWidth: Dp = QkSpacing.Sm

/** 信号格单格高（原型 12px）。 */
private val SignalBarHeight: Dp = QkSpacing.Md

/** 用途行勾选标记（原型 .chk 22px）。 */
private val CheckMarkSize: Dp = QkSpacing.Md + QkSpacing.Sm

/** 信号与电量之间的竖分隔线高度（原型 1px 线，视觉高度 28px）。 */
private val SignalDividerHeight: Dp = QkSpacing.Xl + QkSpacing.Xs

// ---- 文案派生（纯函数，单测可见） ----

/** 确认设备页型号行：原型「型号 XHS-01 · 序列号 ****8A2F」；扫描阶段读不到时如实说明。 */
fun deviceModelLine(device: DiscoveredDevice): String =
    if (device.model.isBlank() && device.serialMasked.isBlank()) {
        "型号与序列号$SPEC_UNKNOWN_TEXT"
    } else {
        "型号 ${device.model} · 序列号 ${device.serialMasked}"
    }

/** 电量文案：原型「82%」；扫描阶段读不到电量时如实说明（电量要连上后才读得到）。 */
fun deviceBatteryLabel(device: DiscoveredDevice): String =
    if (device.batteryPercent < 0) SPEC_UNKNOWN_TEXT else "${device.batteryPercent}%"

// ---- 四态 / 两页路由 ----

/**
 * 蓝牙权限与设备连接流程入口：按 [BtFlowUiState.step] 呈现对应 view。
 *
 * 连接中 / 已连接 / 失败三态交给 [DeviceConnectFlow]（同包，共享 [BtFlowUiState]）。
 */
@Composable
fun BtPermissionFlow(
    state: BtFlowUiState,
    onIntent: (BtFlowIntent) -> Unit,
    modifier: Modifier = Modifier,
    onRequestBtPermission: () -> Unit = {},
    onConfirmDevice: (DiscoveredDevice) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCancel: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    when (state.step) {
        BtFlowStep.Permission -> BtPermissionView(
            state = state,
            onIntent = onIntent,
            onRequestBtPermission = onRequestBtPermission,
            onOpenSettings = onOpenSettings,
            onBack = onCancel,
            modifier = modifier,
        )

        BtFlowStep.ConfirmDevice -> BtConfirmDeviceView(
            state = state,
            onIntent = onIntent,
            onConfirmDevice = onConfirmDevice,
            onBack = onCancel,
            modifier = modifier,
        )

        BtFlowStep.Connect -> DeviceConnectFlow(
            state = state,
            onIntent = onIntent,
            onRetry = onRetry,
            onCancel = onCancel,
            onContinue = onContinue,
            modifier = modifier,
        )
    }
}

// ---- v-bt-permission ----

@Composable
private fun BtPermissionView(
    state: BtFlowUiState,
    onIntent: (BtFlowIntent) -> Unit,
    onRequestBtPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val denied = state.permission == BtPermissionStatus.Denied && !state.awaitingPermission
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page),
    ) {
        DeviceFlowNavBar(TITLE_BT_PERMISSION, onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(HeroRingSize).clip(CircleShape).background(QkColor.Mint),
                contentAlignment = Alignment.Center,
            ) {
                QkIcon(QkIcons.Bluetooth, tint = QkColor.Primary, size = HeroGlyphSize)
            }
            Text(
                BT_PERMISSION_HEADLINE,
                modifier = Modifier.padding(top = QkSpacing.Lg),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
            Text(
                BT_PERMISSION_SUBLINE,
                modifier = Modifier.padding(top = QkSpacing.Sm),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
                textAlign = TextAlign.Center,
            )
        }
        QkCard(modifier = Modifier.padding(top = QkSpacing.Md), padding = QkSpacing.Sm) {
            BT_PERMISSION_ITEMS.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                        Text(
                            item.desc,
                            modifier = Modifier.padding(top = QkSpacing.Xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.Muted,
                        )
                    }
                    BtCheckMark()
                }
            }
        }
        BtBanner(
            icon = Icons.Outlined.Info,
            text = if (denied) BT_PERMISSION_DENIED_HINT else BT_PERMISSION_BANNER,
            modifier = Modifier.padding(top = QkSpacing.Md),
        )
        Spacer(modifier = Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            QkButton(
                text = ACTION_CONTINUE,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.awaitingPermission,
            ) {
                if (state.permission != BtPermissionStatus.Granted) onRequestBtPermission()
                onIntent(BtFlowIntent.Continue)
            }
            if (denied) {
                QkButton(
                    text = ACTION_OPEN_SETTINGS,
                    modifier = Modifier.fillMaxWidth(),
                    style = QkButtonStyle.Secondary,
                ) {
                    onOpenSettings()
                    onIntent(BtFlowIntent.OpenSettings)
                }
            }
        }
        Spacer(modifier = Modifier.height(QkSpacing.Lg))
    }
}

// ---- v-confirm-device ----

@Composable
private fun BtConfirmDeviceView(
    state: BtFlowUiState,
    onIntent: (BtFlowIntent) -> Unit,
    onConfirmDevice: (DiscoveredDevice) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val device = state.device ?: DEFAULT_DISCOVERED_DEVICE
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page),
    ) {
        DeviceFlowNavBar(TITLE_CONFIRM_DEVICE, onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(DeviceRingSize).clip(CircleShape).background(QkColor.Mint),
                contentAlignment = Alignment.Center,
            ) {
                QkIcon(QkIcons.Device, tint = QkColor.Primary, size = DeviceGlyphSize)
            }
            Text(
                CONFIRM_HEADLINE,
                modifier = Modifier.padding(top = QkSpacing.Lg),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
            Text(
                CONFIRM_SUBLINE,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
        QkCard(modifier = Modifier.padding(top = QkSpacing.Md), padding = QkSpacing.Lg) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
                Text(
                    deviceModelLine(device),
                    modifier = Modifier.padding(top = QkSpacing.Xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Muted,
                )
                Row(
                    modifier = Modifier.padding(top = QkSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QkSpacing.Lg),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(LABEL_SIGNAL, style = MaterialTheme.typography.labelMedium, color = QkColor.Sub)
                        BtSignalBars(
                            bars = device.signalBars,
                            modifier = Modifier.padding(top = QkSpacing.Xs),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(QkStrokeWidth)
                            .height(SignalDividerHeight)
                            .background(QkColor.Border),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(LABEL_BATTERY, style = MaterialTheme.typography.labelMedium, color = QkColor.Sub)
                        Text(
                            deviceBatteryLabel(device),
                            modifier = Modifier.padding(top = QkSpacing.Xs),
                            style = MaterialTheme.typography.labelLarge,
                            color = QkColor.Fg,
                        )
                    }
                }
            }
        }
        BtBanner(
            icon = Icons.Outlined.Info,
            text = CONFIRM_BANNER,
            modifier = Modifier.padding(top = QkSpacing.Md),
        )
        Spacer(modifier = Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            QkButton(
                text = ACTION_CONFIRM_PAIR,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.discovering,
            ) {
                onIntent(BtFlowIntent.ConfirmDevice)
                onConfirmDevice(device)
            }
            QkButton(
                text = ACTION_RESCAN,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Secondary,
            ) {
                onIntent(BtFlowIntent.Rescan)
            }
        }
        Spacer(modifier = Modifier.height(QkSpacing.Lg))
    }
}

// ---- 两页共享的呈现原语 ----

/** 竖分隔线宽度（原型 1px）。 */
private val QkStrokeWidth: Dp = QkSpacing.Xs / 4

/** 压栈页导航条：返回圆钮 + 页标题（原型 .navbar）。两页与连接阶段共用。 */
@Composable
internal fun DeviceFlowNavBar(title: String, onBack: () -> Unit) {
    QkScreenHeader(title, onBack)
}

/** 提示横幅（原型 .banner：薄荷底圆角条 + 左侧线性图标 + 小字）。 */
@Composable
private fun BtBanner(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(QkColor.Mint)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(icon, tint = QkColor.Primary, size = BannerIconSize)
        Text(
            text,
            modifier = Modifier.padding(start = QkSpacing.Sm).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.GhostInk,
        )
    }
}

/** 用途行勾选标记（原型 .chk.on：主绿圆角方块 + 白勾）。 */
@Composable
private fun BtCheckMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(CheckMarkSize)
            .clip(RoundedCornerShape(QkRadius.Field))
            .background(QkColor.Primary),
        contentAlignment = Alignment.Center,
    ) {
        Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/** 信号格（原型 4 格，前 [bars] 格亮主绿）。 */
@Composable
private fun BtSignalBars(bars: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Xs / 2),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(SIGNAL_BAR_COUNT) { index ->
            Box(
                modifier = Modifier
                    .width(SignalBarWidth)
                    .height(SignalBarHeight)
                    .clip(RoundedCornerShape(QkStrokeWidth * 2))
                    .background(if (index < bars) QkColor.Primary else QkColor.Border),
            )
        }
    }
}
