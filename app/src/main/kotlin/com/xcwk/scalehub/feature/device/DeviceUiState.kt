package com.xcwk.scalehub.feature.device

import com.xcwk.scalehub.domain.model.ScaleDevice

/** device — 四件套之 UiState（绑定列表 + 扫描发现）。 */
data class FoundScale(
    val name: String,
    val mac: String,
    val rssi: Int,
    val summary: String,
    val protocolId: String,
    val alreadyBound: Boolean,
)

data class DeviceUiState(
    val bound: List<ScaleDevice> = emptyList(),
    val found: List<FoundScale> = emptyList(),
    val scanning: Boolean = false,
    val notice: String = "",
    val message: String? = null,
    /** 配对流程态；null = 不在配对流程（停留设备列表页）。 */
    val pairing: PairingState? = null,
)

// ---- 配对状态机（T-104 · 原型 04-device.html 的 v-pairing/v-pair-success/v-pair-fail） ----

/**
 * 配对流程三态。原型三屏共用同一台秤名，故每个态都携带 [deviceName]。
 * 状态机只管「呈现走到哪一步」，不发起真实 GATT 连接（连接由 BlePort 层负责）。
 */
sealed interface PairingState {
    val deviceName: String

    /** v-pairing：正在连接，可模拟成功/失败。 */
    data class Pairing(override val deviceName: String = DEFAULT_PAIRING_DEVICE) : PairingState

    /** v-pair-success：连接成功，终态（不可回退到配对中）。 */
    data class Success(override val deviceName: String = DEFAULT_PAIRING_DEVICE) : PairingState

    /** v-pair-fail：连接失败/超时，可重试。 */
    data class Fail(override val deviceName: String = DEFAULT_PAIRING_DEVICE) : PairingState
}

/** 配对流程的呈现态转移事件。 */
sealed interface PairingIntent {
    /** 从设备列表进入配对中（仅当当前不在配对流程内）。 */
    data class Start(val deviceName: String) : PairingIntent
    data object Succeeded : PairingIntent
    data object Failed : PairingIntent
    /** 失败页「重新搜索」：重走配对中。 */
    data object Retry : PairingIntent
    /** 离开配对流程，回到设备列表页（成功/失败页的返回）。 */
    data object Exit : PairingIntent
}

/**
 * 纯呈现态转移 —— JVM 单测钉死的部分。
 *
 * 口径：
 * - 进入只能从设备列表（null）发起；已在成功页时 [PairingIntent.Start] 不生效（成功不回退）
 * - 配对中只会走向成功或失败；成功/失败页再收到 Succeeded/Failed 一律忽略
 * - [PairingIntent.Retry] 只对失败态生效，把流程拉回配对中；对成功态无效
 * - [PairingIntent.Exit] 是离开流程（等同返回键），不属于状态回退
 */
fun pairingReduce(state: PairingState?, intent: PairingIntent): PairingState? = when (intent) {
    is PairingIntent.Start -> if (state == null) PairingState.Pairing(intent.deviceName) else state
    PairingIntent.Succeeded ->
        if (state is PairingState.Pairing) PairingState.Success(state.deviceName) else state
    PairingIntent.Failed ->
        if (state is PairingState.Pairing) PairingState.Fail(state.deviceName) else state
    PairingIntent.Retry ->
        if (state is PairingState.Fail) PairingState.Pairing(state.deviceName) else state
    PairingIntent.Exit -> null
}

// ---- 三 view 逐字文案（取自原型，单测逐字断言） ----

const val DEFAULT_PAIRING_DEVICE = "轻衡 S1"

// v-pairing
const val PAIRING_TITLE = "连接设备"
const val PAIRING_KEEP_NEAR = "请保持手机靠近体脂秤"
const val PAIRING_SIMULATE_FAIL = "模拟失败/超时"
const val PAIRING_SIMULATE_SUCCESS = "模拟成功"

// v-pair-success
const val PAIRING_SUCCESS_TITLE = "连接成功"
const val PAIRING_START_MEASURE = "开始测量"
const val PAIRING_BACK_TO_DEVICE = "返回设备管理"

// v-pair-fail
const val PAIRING_FAIL_TITLE = "连接失败"
const val PAIRING_RETRY = "重新搜索"
const val PAIRING_BACK = "返回"

/** v-pairing 主文案：「正在连接 轻衡 S1…」。 */
fun pairingConnectingText(deviceName: String): String = "正在连接 $deviceName…"

/** v-pair-success 正文两行（原型 <br> 换行）。 */
fun pairingSuccessBody(deviceName: String): String =
    "「$deviceName」已连接。\n现在可以开始第一次测量。"

/** v-pair-fail 正文两行（原型 <br> 换行）。 */
fun pairingFailBody(deviceName: String): String =
    "未能连接到「$deviceName」。\n请确认秤已唤醒、距离 1 米内。"

sealed interface DeviceIntent {
    /** 切换扫描：扫描中→停止；空闲→开始（实现层自取前台 Activity，UI 无需关心 Context）。 */
    data object ToggleScan : DeviceIntent
    /** 生命周期 ON_PAUSE：离开页面即停扫（旧库 BindActivity.onPause → stopScan）。 */
    data object Pause : DeviceIntent
    data class Bind(val found: FoundScale) : DeviceIntent

    /** 扫码绑定（旧库 ScaleQr 路径：二维码只带 MAC/名称，默认 okok-1 协议）。 */
    data class QrBind(val name: String, val mac: String) : DeviceIntent
    data class Unbind(val mac: String) : DeviceIntent
    data class Activate(val mac: String) : DeviceIntent
    data object Refresh : DeviceIntent

    /** 配对流程事件（状态机见 [pairingReduce]）。 */
    data class Pairing(val intent: PairingIntent) : DeviceIntent
}
