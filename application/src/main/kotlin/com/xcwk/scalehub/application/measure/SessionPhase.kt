package com.xcwk.scalehub.application.measure

import com.xcwk.scalehub.domain.protocol.ScaleEvent

/**
 * 测量会话相位（DEC-024 组 E）。
 *
 * 旧实现用 13 个并列可变字段表达同一件事：
 * linkReady / sealed / saved / seenWeight / steppingOff / awaitingConfirm /
 * heldWeightOnly / suppressDisconnect / heldWeight / heldImpedance /
 * pendingImpedance / heldHeart / scaleKind。
 *
 * 这些字段的任意组合都能通过编译，于是「未封存却在等确认」「没见到体重却在
 * 下秤保持」「已落库却未封存」这类状态在类型层面是合法的，只能靠 790 行里的
 * 守卫人工维持。本文件把它们收进一个 sealed 相位：
 *
 *  - `awaitingConfirm` 只存在于 [SessionPhase.Sealed]，没封存就不可能等确认；
 *  - `weightOnly` 只随 `awaitingConfirm` 一起出现；
 *  - `seenWeight` / `steppingOff` / 阻抗累加 / 心率峰值只存在于 [MeasureProgress]，
 *    而 [MeasureProgress] 只挂在 [SessionPhase.Linked] 上；
 *  - `suppressNextDisconnect` 只属于 [SessionPhase.Linking]，链路就绪后自然消失。
 *
 * 转移全部经 [reduce] 完成，非法事件是 no-op 而不是抛异常——蓝牙回调本来就会
 * 在任意时刻乱序到达，静默忽略比崩溃更符合现场。
 */
internal sealed interface SessionPhase {

    /** 尚未开始。 */
    data object Idle : SessionPhase

    /** 扫描/连接中，链路未就绪。 */
    data class Linking(
        /** 是否已选中一台绑定秤（选中后不再重新匹配）。 */
        val picked: Boolean = false,
        /** 吞掉下一次断连回调（reconnect 时旧链路会自己断开一次）。 */
        val suppressNextDisconnect: Boolean = false,
    ) : SessionPhase

    /** 链路就绪，正在本次测量中。 */
    data class Linked(val progress: MeasureProgress = MeasureProgress()) : SessionPhase

    /** 本次结果已封存：要么等确认，要么已落库。 */
    data class Sealed(val result: SealedResult) : SessionPhase

    /** 会话已结束（终态）。 */
    data object Closed : SessionPhase
}

/** 仅在链路就绪期间存在的测量进度。 */
internal data class MeasureProgress(
    /** 本次是否已见到非零体重。 */
    val seenWeight: Boolean = false,
    /** 已见到体重后又回到零（下秤），等待重新上秤。 */
    val steppingOff: Boolean = false,
    /** 过程帧阻抗峰值：结果帧可能不带阻抗，封存时要沿用这个值。 */
    val pendingImpedance: Double = 0.0,
    /** 心率峰值。 */
    val heartPeak: Int = 0,
    /** 本次观察到的秤类型（体重秤 / 体脂秤 / 心率体脂秤）。 */
    val scaleKind: Int = ScaleEvent.KIND_UNKNOWN,
)

/** 封存后的结果。字段一旦构造就不再改变语义，只追加确认/落库标记。 */
internal data class SealedResult(
    val weight: Double,
    val impedance: Double,
    val heartPeak: Int,
    val awaitingConfirm: Boolean = false,
    val weightOnly: Boolean = false,
    val saved: Boolean = false,
)

// ---- 派生只读视图：调用点保持可读，但写入口只有 reduce ----

internal val SessionPhase.linkReady: Boolean get() = this is SessionPhase.Linked
internal val SessionPhase.sealed: Boolean get() = this is SessionPhase.Sealed
internal val SessionPhase.saved: Boolean get() = (this as? SessionPhase.Sealed)?.result?.saved == true
internal val SessionPhase.picked: Boolean get() = (this as? SessionPhase.Linking)?.picked == true
internal val SessionPhase.suppressNextDisconnect: Boolean
    get() = (this as? SessionPhase.Linking)?.suppressNextDisconnect == true

internal val SessionPhase.progress: MeasureProgress
    get() = (this as? SessionPhase.Linked)?.progress ?: MeasureProgress()

internal val SessionPhase.seenWeight: Boolean get() = progress.seenWeight
internal val SessionPhase.steppingOff: Boolean get() = progress.steppingOff
internal val SessionPhase.pendingImpedance: Double get() = progress.pendingImpedance
internal val SessionPhase.scaleKind: Int get() = progress.scaleKind

internal val SessionPhase.sealedResult: SealedResult?
    get() = (this as? SessionPhase.Sealed)?.result

internal val SessionPhase.awaitingConfirm: Boolean get() = sealedResult?.awaitingConfirm == true
internal val SessionPhase.weightOnly: Boolean get() = sealedResult?.weightOnly == true

/** 心率峰值在 Linked 与 Sealed 两段都要可读（封存后仍要写进报告文案）。 */
internal val SessionPhase.heartPeak: Int
    get() = when (this) {
        is SessionPhase.Linked -> progress.heartPeak
        is SessionPhase.Sealed -> result.heartPeak
        else -> 0
    }

/**
 * 已连上且已经见到体重（或正在下秤）——此时链路抖动属正常，不弹重连提示。
 * 单一来源：策略要改只改这里。
 */
internal val SessionPhase.settling: Boolean
    get() = this is SessionPhase.Linked && (progress.seenWeight || progress.steppingOff)

/** 相位转移事件。 */
internal sealed interface SessionEvent {
    /** 链路就绪（订阅成功或收到广播帧）。 */
    data object LinkEstablished : SessionEvent

    /** 观察到秤类型（每帧都调，类型只升不降）。 */
    data class KindObserved(val kind: Int) : SessionEvent

    /** 见到非零体重。 */
    data class WeightSeen(val weightKg: Double, val impedanceOhm: Double, val heartRate: Int) : SessionEvent

    /** 见到零体重帧（人还站着，但秤面读数归零）。 */
    data object ZeroSeen : SessionEvent

    /** 确认已下秤：清空体重与阻抗累加，进入等待重上。 */
    data object StepOff : SessionEvent

    /** 重新开始一次站立（订阅恢复）。 */
    data object Reseat : SessionEvent

    /** 封存本次结果。 */
    data class Seal(val weightKg: Double) : SessionEvent

    /** 倒计时结束且没有数据。 */
    data object Timeout : SessionEvent

    /** 进入待确认。 */
    data class AwaitConfirm(val weightOnly: Boolean) : SessionEvent

    /** 确认已被消费（落库开始前调用，避免重复确认重复落库）。 */
    data object ConfirmConsumed : SessionEvent

    /** 落库完成。 */
    data object SaveCommitted : SessionEvent

    /** 重新开始连接（start 内部），保留「吞一次断连」的意图。 */
    data object ResetForConnect : SessionEvent

    /** 已选中绑定秤。 */
    data object MarkPicked : SessionEvent

    /** 清掉「吞一次断连」标记。 */
    data object ClearSuppress : SessionEvent

    /** 会话结束。 */
    data object Close : SessionEvent
}

/** 纯函数转移：同一输入必得同一输出，非法事件原样返回。 */
internal fun SessionPhase.reduce(event: SessionEvent): SessionPhase = when (event) {
    SessionEvent.LinkEstablished -> when (this) {
        is SessionPhase.Linking -> SessionPhase.Linked()
        else -> this
    }

    is SessionEvent.KindObserved -> when (this) {
        is SessionPhase.Linked -> copy(progress = progress.copy(scaleKind = mergeScaleKind(progress.scaleKind, event.kind)))
        else -> this
    }

    is SessionEvent.WeightSeen -> when (this) {
        is SessionPhase.Linked -> copy(
            progress = progress.copy(
                seenWeight = true,
                steppingOff = false,
                pendingImpedance = maxOf(progress.pendingImpedance, event.impedanceOhm),
                heartPeak = maxOf(progress.heartPeak, event.heartRate),
            ),
        )
        // 迟到帧意味着链路其实已通，按就绪处理（旧实现靠后续回调补齐，行为等价）
        is SessionPhase.Linking -> SessionPhase.Linked().reduce(event)
        else -> this
    }

    SessionEvent.ZeroSeen -> when (this) {
        is SessionPhase.Linked -> if (progress.seenWeight) copy(progress = progress.copy(steppingOff = true)) else this
        else -> this
    }

    SessionEvent.StepOff -> when (this) {
        is SessionPhase.Linked -> copy(progress = progress.copy(seenWeight = false, steppingOff = true, pendingImpedance = 0.0))
        else -> this
    }

    SessionEvent.Reseat -> when (this) {
        SessionPhase.Closed, SessionPhase.Idle -> this
        else -> SessionPhase.Linking(picked = true)
    }

    is SessionEvent.Seal -> when (this) {
        is SessionPhase.Linked -> SessionPhase.Sealed(
            SealedResult(
                weight = event.weightKg,
                impedance = progress.pendingImpedance,
                heartPeak = progress.heartPeak,
            ),
        )
        else -> this
    }

    SessionEvent.Timeout -> when (this) {
        is SessionPhase.Linking -> SessionPhase.Sealed(SealedResult(weight = 0.0, impedance = 0.0, heartPeak = 0))
        else -> this
    }

    is SessionEvent.AwaitConfirm -> when (this) {
        is SessionPhase.Sealed ->
            if (result.saved) this
            else copy(result = result.copy(awaitingConfirm = true, weightOnly = event.weightOnly))
        else -> this
    }

    SessionEvent.ConfirmConsumed -> when (this) {
        is SessionPhase.Sealed -> if (result.awaitingConfirm) copy(result = result.copy(awaitingConfirm = false)) else this
        else -> this
    }

    SessionEvent.SaveCommitted -> when (this) {
        is SessionPhase.Sealed -> copy(result = result.copy(saved = true, awaitingConfirm = false))
        else -> this
    }

    SessionEvent.ResetForConnect -> when (this) {
        // 终态不可复活：start() 已有 closed 守卫，这里再钉一层，防止将来新增入口绕过
        SessionPhase.Closed -> this
        else -> SessionPhase.Linking(
            picked = false,
            suppressNextDisconnect = (this as? SessionPhase.Linking)?.suppressNextDisconnect ?: false,
        )
    }

    SessionEvent.MarkPicked -> when (this) {
        is SessionPhase.Linking -> copy(picked = true)
        else -> this
    }

    SessionEvent.ClearSuppress -> when (this) {
        is SessionPhase.Linking -> copy(suppressNextDisconnect = false)
        else -> this
    }

    SessionEvent.Close -> SessionPhase.Closed
}

/** 秤类型只升不降：心率体脂 > 体脂 > 体重 > 未知（旧实现 noteKind 的等价表达）。 */
internal fun mergeScaleKind(current: Int, observed: Int): Int = when {
    observed == ScaleEvent.KIND_FAT_HR -> observed
    observed == ScaleEvent.KIND_FAT && current != ScaleEvent.KIND_FAT_HR -> observed
    observed == ScaleEvent.KIND_WEIGHT && current == ScaleEvent.KIND_UNKNOWN -> observed
    else -> current
}
