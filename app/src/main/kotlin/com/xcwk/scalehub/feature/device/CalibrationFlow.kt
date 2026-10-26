package com.xcwk.scalehub.feature.device

/**
 * 设备校准引导（T-062）。
 *
 * 只做**引导状态机**：真实校准指令属于厂商协议（OKOK/Chipsea 文档未包含），
 * 因此这里不伪造"已校准成功"的设备交互，只保证用户每一步看到正确指引，
 * 且非法事件不会让状态机跳步。
 */
enum class CalibrationStep {
    /** 未开始。 */
    Idle,

    /** 把秤放在硬质平整地面，确认后继续。 */
    PlaceOnFloor,

    /** 空秤状态下站上去，等待读数。 */
    StepOn,

    /** 下秤，等待归零。 */
    StepOff,

    /** 引导完成（不代表设备已写入校准值）。 */
    Done,
}

sealed interface CalibrationEvent {
    data object Start : CalibrationEvent
    data object FloorConfirmed : CalibrationEvent
    data object SteppedOn : CalibrationEvent
    data object SteppedOff : CalibrationEvent
    data object Cancel : CalibrationEvent
    data object Restart : CalibrationEvent
}

/**
 * 纯状态转移。
 *
 * 规则：
 * - 只能顺序前进，跳步事件被忽略（返回原状态）；
 * - [CalibrationEvent.Cancel] 从任何非终态回到 [CalibrationStep.Idle]；
 * - [CalibrationEvent.Restart] 只在 [CalibrationStep.Done] 生效。
 */
fun calibrationNext(step: CalibrationStep, event: CalibrationEvent): CalibrationStep = when (event) {
    CalibrationEvent.Cancel -> if (step == CalibrationStep.Done) step else CalibrationStep.Idle
    CalibrationEvent.Restart -> if (step == CalibrationStep.Done) CalibrationStep.PlaceOnFloor else step
    CalibrationEvent.Start -> if (step == CalibrationStep.Idle) CalibrationStep.PlaceOnFloor else step
    CalibrationEvent.FloorConfirmed ->
        if (step == CalibrationStep.PlaceOnFloor) CalibrationStep.StepOn else step
    CalibrationEvent.SteppedOn ->
        if (step == CalibrationStep.StepOn) CalibrationStep.StepOff else step
    CalibrationEvent.SteppedOff ->
        if (step == CalibrationStep.StepOff) CalibrationStep.Done else step
}

/** 该步的引导文案（时间顺序固定，不随设备型号变化）。 */
fun calibrationHint(step: CalibrationStep): String = when (step) {
    CalibrationStep.Idle -> "把秤放在硬质平整地面，避开地毯与斜坡"
    CalibrationStep.PlaceOnFloor -> "确认地面平整后点「下一步」"
    CalibrationStep.StepOn -> "双脚站上秤，保持静止直到读数稳定"
    CalibrationStep.StepOff -> "下秤，等待显示归零"
    CalibrationStep.Done -> "引导完成；如需重新开始可点「重新校准」"
}
