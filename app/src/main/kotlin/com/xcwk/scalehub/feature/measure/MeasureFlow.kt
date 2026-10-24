package com.xcwk.scalehub.feature.measure

import java.util.Locale

/**
 * measure — 测量完整状态机（T-077 · 原型 12 `12-measure-full.html`）。
 *
 * ## 原型
 * 八个 view 对应八个呈现态（[MeasureFlowStep]）：
 * - `v-check` 测量前检查：成员/设备/蓝牙/秤面三行就绪项 + 确认下一步
 * - `v-prep` 测量准备：赤脚站上体脂秤 + 三条站姿要点 + 我准备好了
 * - `v-stable` 等待稳定：读数跳动 62.– / 等待稳定… + 稳定了 → 开始分析 + 超时弹窗
 * - `v-calc` 计算成分：62.4 体重已锁定 + 正在计算身体成分 + 四个演示分支（枢纽态）
 * - `v-left` 提前离开：检测到你已离开秤面 + 重新测量 / 返回
 * - `v-invalid` 无效数据：设备返回的数据无效 + 重新测量 / 反馈此问题
 * - `v-savefail` 保存失败：62.4 kg 体脂 28.6% 未保存 + 重试保存 / 稍后再说
 * - `v-success` 保存成功：测量完成 · 已保存 + 62.4 kg ↓0.3 较上次 + 两项指标
 *
 * ## 呈现式边界
 * 本文件只维护呈现态与逐字文案，不接触蓝牙、仓储与系统服务：
 * - 读数、超时、电量、成员名均由调用方经 [MeasureFlowUiState] 注入；
 * - 保存、反馈问题、查看报告、退出测量一律走回调（本文件不写库、不弹系统页）；
 * - 状态推进是纯函数 [measureReduce]，JVM 单测钉死进入条件与非法转移。
 *
 * ## 状态机口径（合法转移表）
 * ```
 * Check  --确认三项就绪--> Prep    （任一项未就绪则忽略）
 * Prep   --我准备好了-->   Stable
 * Stable --读数合理-->     Calc     （越界读数直接进 Invalid）
 * Stable --超时-->         超时弹窗（态不变）
 * Calc   --保存完成-->     Success
 * Calc   --提前离开-->     Left
 * Calc   --数据无效-->     Invalid
 * Calc   --保存失败-->     SaveFail
 * SaveFail --重试保存成功--> Success
 * Left / Invalid / SaveFail --重新测量/稍后--> Check（秤面待确认复位）
 * ```
 * 其余组合一律忽略（返回原状态），弹层只在所属态可见。
 */

// ---- 逐字文案（原型 12；JVM 单测逐条校验，改动即验收失败） ----

object MeasureFlowText {
    // 共用
    const val Back = "返回"

    // v-check
    const val CheckTitle = "测量前检查"
    const val CheckHero = "确认当前成员与设备"
    const val CheckMember = "测量对象：林悦"
    const val CheckDevice = "设备已连接"
    const val CheckDeviceDetail = "轻衡 S1 · 电量 82%"
    const val CheckBluetooth = "蓝牙已开启"
    const val CheckSurface = "秤面干燥平整"
    const val Ready = "就绪"
    const val Pending = "待确认"
    const val CheckConfirm = "确认，下一步"

    // v-prep
    const val PrepTitle = "测量准备"
    const val PrepHero = "赤脚站上体脂秤"
    const val PrepElectrodes = "双脚踩住四片电极"
    const val PrepLegs = "双腿微开，手臂自然下垂"
    const val PrepStill = "站稳后保持静止几秒"
    const val PrepReady = "我准备好了"

    // v-stable
    const val MeasuringTitle = "测量中"
    const val StablePending = "62.–"
    const val StableWaiting = "等待稳定…"
    const val StableKeepStill = "请保持站稳"
    const val StableAutoNext = "读数稳定后自动进入下一步"
    const val StableDone = "稳定了 → 开始分析"
    const val StableTimeoutHint = "读数一直不稳定？→ 超时"
    const val TimeoutTitle = "测量超时"
    const val TimeoutBody = "超过 60 秒体重仍未稳定，可能站姿不稳或秤放置不平。"
    const val GotIt = "知道了"
    const val RetryMeasure = "重新测量"

    // v-calc
    const val CalcLocked = "体重已锁定"
    const val CalcTitle = "正在计算身体成分"
    const val CalcBranchLabel = "触发分支（演示）"
    const val BranchBluetoothLost = "蓝牙中断"
    const val BranchLeftEarly = "提前离开"
    const val BranchInvalid = "无效数据"
    const val BranchSaveFail = "保存失败"
    const val CalcDone = "完成 → 保存成功"
    const val CalcCancelHint = "（或点返回取消）"

    // v-calc 弹窗
    const val BtDialogTitle = "蓝牙连接中断"
    const val BtDialogBody = "本次数据未保存。请保持手机靠近体脂秤。"
    const val BtDialogCancel = "取消"
    const val BtDialogReconnect = "重新连接"
    const val CancelDialogTitle = "取消本次测量？"
    const val CancelDialogBody = "测量尚未完成，本次数据不会保存。"
    const val CancelDialogKeep = "继续测量"
    const val CancelDialogConfirm = "取消测量"

    // v-left
    const val LeftTitle = "测量未完成"
    const val LeftHeadline = "检测到你已离开秤面"
    const val LeftBody = "身体成分测量需全程站在秤上。\n本次未采集完整数据。"

    // v-invalid
    const val InvalidTitle = "数据异常"
    const val InvalidHeadline = "设备返回的数据无效"
    const val InvalidBody = "读数超出合理范围，可能电极接触不良。\n该数据未保存。"
    const val Feedback = "反馈此问题"

    // v-savefail
    const val SaveFailTitle = "保存失败"
    const val SaveFailDoneHint = "本次测量已完成"
    const val Unsaved = "未保存"
    const val SaveFailBody = "保存时出错，数据暂存本机，可重试，无需重新测量。"
    const val RetrySave = "重试保存"
    const val Later = "稍后再说"

    // v-success
    const val SuccessBanner = "测量完成 · 已保存 · 10月21日 07:30"
    const val SuccessSummary = "整体状态平稳，继续保持。"
    const val SuccessWeightUnit = "kg"
    const val SuccessDiff = "↓0.3 较上次"
    const val BodyFatLabel = "体脂率"
    /** v-savefail 卡片右上角的行内体脂文案前缀（原型「体脂 28.6%」） */
    const val FatInline = "体脂 "
    const val MuscleLabel = "肌肉量"
    const val SuccessDone = "完成"
    const val DuplicateMeasure = "模拟重复测量"
    const val DupTitle = "3 分钟内重复测量"
    const val DupBody = "你刚在 07:30 测量过（62.4 kg），本次读数 62.5 kg。"
    const val DupReplace = "替换上次记录"
    const val DupKeep = "保留为新记录"
    const val DupGiveUp = "放弃本次"

    /** 原型里三处「kg / %」单位与体脂百分数文案。 */
    const val PercentUnit = "%"
    const val WeightUnit = "kg"
}

/**
 * 体重合理性区间（kg）。
 *
 * 原型 `v-invalid` 的说法是「读数超出合理范围，可能电极接触不良」。
 * 判定放在状态机里而不是 UI 层：越界读数必须改变状态（进 [MeasureFlowStep.Invalid]），
 * 若只在渲染时提示，非法数据仍会被当作已锁定体重继续计算。
 */
val PLAUSIBLE_WEIGHT_KG: ClosedFloatingPointRange<Double> = 20.0..300.0

/** 读数是否落在合理区间；越界数据不得进入计算态。 */
fun isPlausibleWeight(weightKg: Double): Boolean = weightKg in PLAUSIBLE_WEIGHT_KG

/** 锁定体重文案（一位小数，恒用 Locale.US，避免区域设置改小数点）。 */
fun formatLockedWeight(weightKg: Double): String = String.format(Locale.US, "%.1f", weightKg)

// ---- 状态机 ----

/** 八个呈现态，与原型八个 view 一一对应。 */
enum class MeasureFlowStep { Check, Prep, Stable, Calc, Left, Invalid, SaveFail, Success }

/** 测量前检查的三项就绪条件（原型 `v-check` 三行）。 */
data class MeasurePrecheckState(
    /** 设备已连接（由蓝牙会话探测后注入） */
    val deviceReady: Boolean = true,
    /** 蓝牙已开启（由系统开关探测后注入） */
    val bluetoothReady: Boolean = true,
    /** 秤面干燥平整：需要用户自己确认，故默认「待确认」 */
    val surfaceConfirmed: Boolean = false,
) {
    /** 三项齐全才允许进入测量准备 */
    val allReady: Boolean get() = deviceReady && bluetoothReady && surfaceConfirmed
}

/** 已完成测量的呈现数据（`v-savefail` 与 `v-success` 共用）。 */
data class MeasureFlowResult(
    val weightText: String = "62.4",
    val fatText: String = "28.6",
    val muscleText: String = "42.3",
    val diffText: String = MeasureFlowText.SuccessDiff,
)

/**
 * 测量流程呈现态。
 *
 * 弹层可见性放在状态里而非 Composable 局部 `remember`：
 * 状态机切换态时必须一并收掉旧态弹层，否则「提前离开」后仍会飘着超时弹窗。
 */
data class MeasureFlowUiState(
    val step: MeasureFlowStep = MeasureFlowStep.Check,
    val precheck: MeasurePrecheckState = MeasurePrecheckState(),
    /** Stable 阶段的跳动读数 */
    val liveWeightText: String = MeasureFlowText.StablePending,
    /** Calc 及之后锁定的体重（kg）；Stable 阶段为 null */
    val lockedWeightKg: Double? = null,
    /** 已完成测量的呈现数据 */
    val result: MeasureFlowResult = MeasureFlowResult(),
    val timeoutDialogVisible: Boolean = false,
    val cancelDialogVisible: Boolean = false,
    val bluetoothDialogVisible: Boolean = false,
    val duplicateDialogVisible: Boolean = false,
) {
    /** 三项就绪（Check 态的按钮可用性直接取这里） */
    val ready: Boolean get() = precheck.allReady

    /** 当前展示的体重文案：已锁定取锁定值，否则取跳动读数 */
    val weightText: String get() = lockedWeightKg?.let(::formatLockedWeight) ?: liveWeightText
}

/** 流程内意图。外部副作用（落库、反馈、跳报告、退出）走回调，不进 reduce。 */
sealed interface MeasureFlowIntent {
    // v-check
    data class SetDeviceReady(val ready: Boolean) : MeasureFlowIntent
    data class SetBluetoothReady(val ready: Boolean) : MeasureFlowIntent
    data object ConfirmSurface : MeasureFlowIntent
    data object ConfirmPrecheck : MeasureFlowIntent

    // 通用
    data object Back : MeasureFlowIntent

    // v-prep
    data object ReadyToStand : MeasureFlowIntent

    // v-stable
    data class ReadingStable(val weightKg: Double) : MeasureFlowIntent
    data object Timeout : MeasureFlowIntent
    data object DismissTimeout : MeasureFlowIntent
    data object RestartAfterTimeout : MeasureFlowIntent

    // v-calc
    data object CalcFinished : MeasureFlowIntent
    data object DeviceLeft : MeasureFlowIntent
    data object DataInvalid : MeasureFlowIntent
    data object SaveFailed : MeasureFlowIntent
    data object RequestCancel : MeasureFlowIntent
    data object DismissCancel : MeasureFlowIntent
    data object ConfirmCancel : MeasureFlowIntent
    data object BluetoothLost : MeasureFlowIntent
    data object ReconnectBluetooth : MeasureFlowIntent
    data object CancelAfterBluetoothLost : MeasureFlowIntent

    // v-left / v-invalid / v-savefail
    data object RetryMeasure : MeasureFlowIntent
    data object Feedback : MeasureFlowIntent
    data object RetrySave : MeasureFlowIntent
    data object Later : MeasureFlowIntent

    // v-success
    data object Finish : MeasureFlowIntent
    data object RequestDuplicateMeasure : MeasureFlowIntent
    data object ReplaceLastRecord : MeasureFlowIntent
    data object KeepAsNewRecord : MeasureFlowIntent
    data object GiveUpDuplicate : MeasureFlowIntent
}

/** 收掉全部弹层（换态/取消时统一走这里）。 */
private fun MeasureFlowUiState.closeDialogs(): MeasureFlowUiState = copy(
    timeoutDialogVisible = false,
    cancelDialogVisible = false,
    bluetoothDialogVisible = false,
    duplicateDialogVisible = false,
)

/**
 * 回到测量前检查：设备/蓝牙的就绪结论沿用，秤面确认复位
 * （原型每轮都要求重新确认秤面干燥平整）。
 */
private fun MeasureFlowUiState.restartFlow(): MeasureFlowUiState = MeasureFlowUiState(
    step = MeasureFlowStep.Check,
    precheck = precheck.copy(surfaceConfirmed = false),
)

/**
 * 纯呈现态转移 —— JVM 单测钉死的部分。
 *
 * 非法转移一律返回 `state` 本身（不抛异常、不静默落到别的态），
 * 这样迟到的事件（例如离开秤面后到达的「读数稳定」）不会把用户拽回计算态。
 */
fun measureReduce(state: MeasureFlowUiState, intent: MeasureFlowIntent): MeasureFlowUiState =
    when (state.step) {
        MeasureFlowStep.Check -> when (intent) {
            is MeasureFlowIntent.SetDeviceReady -> state.copy(
                precheck = state.precheck.copy(deviceReady = intent.ready),
            )
            is MeasureFlowIntent.SetBluetoothReady -> state.copy(
                precheck = state.precheck.copy(bluetoothReady = intent.ready),
            )
            MeasureFlowIntent.ConfirmSurface -> state.copy(
                precheck = state.precheck.copy(surfaceConfirmed = true),
            )
            // 三项未齐时确认无效：原型要求先看到三个「就绪」
            MeasureFlowIntent.ConfirmPrecheck -> if (state.ready) {
                state.copy(step = MeasureFlowStep.Prep)
            } else {
                state
            }
            else -> state
        }

        MeasureFlowStep.Prep -> when (intent) {
            MeasureFlowIntent.ReadyToStand -> state.copy(
                step = MeasureFlowStep.Stable,
                liveWeightText = MeasureFlowText.StablePending,
                lockedWeightKg = null,
            )
            MeasureFlowIntent.Back -> state.copy(step = MeasureFlowStep.Check)
            else -> state
        }

        MeasureFlowStep.Stable -> when (intent) {
            is MeasureFlowIntent.ReadingStable -> if (isPlausibleWeight(intent.weightKg)) {
                state.copy(step = MeasureFlowStep.Calc, lockedWeightKg = intent.weightKg)
            } else {
                // 越界读数不进计算，直接落无效数据
                state.copy(step = MeasureFlowStep.Invalid, lockedWeightKg = null)
            }
            MeasureFlowIntent.Timeout -> state.copy(timeoutDialogVisible = true)
            MeasureFlowIntent.DismissTimeout -> state.copy(timeoutDialogVisible = false)
            MeasureFlowIntent.RestartAfterTimeout -> state.copy(
                step = MeasureFlowStep.Stable,
                liveWeightText = MeasureFlowText.StablePending,
                lockedWeightKg = null,
                timeoutDialogVisible = false,
            )
            MeasureFlowIntent.Back -> state.copy(step = MeasureFlowStep.Check)
            else -> state
        }

        MeasureFlowStep.Calc -> when (intent) {
            MeasureFlowIntent.CalcFinished -> state.copy(
                step = MeasureFlowStep.Success,
                result = state.result.copy(weightText = state.weightText),
            ).closeDialogs()
            // 提前离开：本次数据不完整，锁定读数一并作废，避免下游误当有效数据
            MeasureFlowIntent.DeviceLeft -> state.copy(
                step = MeasureFlowStep.Left,
                lockedWeightKg = null,
            ).closeDialogs()
            MeasureFlowIntent.DataInvalid -> state.copy(
                step = MeasureFlowStep.Invalid,
                lockedWeightKg = null,
            ).closeDialogs()
            MeasureFlowIntent.SaveFailed -> state.copy(
                step = MeasureFlowStep.SaveFail,
                result = state.result.copy(weightText = state.weightText),
            ).closeDialogs()
            MeasureFlowIntent.RequestCancel -> state.copy(cancelDialogVisible = true)
            MeasureFlowIntent.DismissCancel -> state.copy(cancelDialogVisible = false)
            MeasureFlowIntent.ConfirmCancel -> state.restartFlow()
            // 原型 v-calc 的返回键弹「取消本次测量？」
            MeasureFlowIntent.Back -> state.copy(cancelDialogVisible = true)
            MeasureFlowIntent.BluetoothLost -> state.copy(bluetoothDialogVisible = true)
            MeasureFlowIntent.ReconnectBluetooth -> state.copy(bluetoothDialogVisible = false)
            MeasureFlowIntent.CancelAfterBluetoothLost -> state.restartFlow()
            else -> state
        }

        MeasureFlowStep.Left -> when (intent) {
            MeasureFlowIntent.RetryMeasure, MeasureFlowIntent.Back -> state.restartFlow()
            else -> state
        }

        MeasureFlowStep.Invalid -> when (intent) {
            MeasureFlowIntent.RetryMeasure, MeasureFlowIntent.Back -> state.restartFlow()
            // 反馈走宿主回调，呈现态不变
            MeasureFlowIntent.Feedback -> state
            else -> state
        }

        MeasureFlowStep.SaveFail -> when (intent) {
            MeasureFlowIntent.RetrySave -> state.copy(step = MeasureFlowStep.Success).closeDialogs()
            MeasureFlowIntent.Later, MeasureFlowIntent.Back -> state.restartFlow()
            else -> state
        }

        MeasureFlowStep.Success -> when (intent) {
            MeasureFlowIntent.RequestDuplicateMeasure -> state.copy(duplicateDialogVisible = true)
            MeasureFlowIntent.ReplaceLastRecord,
            MeasureFlowIntent.KeepAsNewRecord,
            MeasureFlowIntent.GiveUpDuplicate -> state.restartFlow()
            // 退出由宿主完成（onExit 回调），呈现态不再变化
            MeasureFlowIntent.Finish -> state
            else -> state
        }
    }
