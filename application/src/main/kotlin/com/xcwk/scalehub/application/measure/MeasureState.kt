package com.xcwk.scalehub.application.measure

/** 测量页相位（旧库 phaseWait/phaseLive/phaseDone 三态）。 */
enum class MeasurePhase { WAIT, LIVE, DONE }

enum class MeasureOutcomeKind {
    /** 已写入本机。 */
    SAVED,
    /** 低于 10 公斤不写入。 */
    TOO_LIGHT,
    /** 与最近一次相差较大，需确认后写入。 */
    CONFIRM_WEIGHT,
    /** 体脂秤只锁定了体重，没测出阻抗。 */
    NO_COMPOSITION,
    /** 30 秒内没有拿到数据。 */
    NO_DATA,
    /** 保存时发现地址不在绑定列表。 */
    NOT_BOUND,
}

data class MeasureOutcome(
    val kind: MeasureOutcomeKind,
    val title: String,
    val extra: String,
    val weight: String = "",
    val fat: String = "--",
    val impedanceOhm: Double = 0.0,
    val heartRate: Int = 0,
    val confirmable: Boolean = false,
    /** SAVED 时为落库记录时间戳（查看报告入口，M4）。 */
    val recordTime: Long? = null,
)

data class MeasureState(
    val phase: MeasurePhase = MeasurePhase.WAIT,
    val title: String = "请光脚站上秤",
    val desc: String = "",
    val weightText: String = "0.0",
    val notice: String = "",
    /** 连接芯片：连接中/已连接/已断开/未连接。 */
    val link: String = "未连接",
    val countdown: Int = 30,
    val showCountdown: Boolean = false,
    val showRetry: Boolean = false,
    val outcome: MeasureOutcome? = null,
    /** 本机无绑定设备（WAIT 相专属出口：渲染「去绑定设备」直达设备页）。 */
    val needBind: Boolean = false,
)
