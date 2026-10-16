package com.xcwk.scalehub.application.command

/**
 * command — 记录一条健康记录（由 trigger 层组装，只带事实）。
 * type 为线格式："diet" / "exercise" / "water" / "waist" / "glucose" /
 * "blood_pressure" / "habit"（"weight" 不接受，见 HealthLogType 注释）。
 * value2 缺省 0.0 —— 旧库表单副值可空即存 0。
 */
data class RecordHealthLogCommand(
    val type: String,
    val value: Double,
    val value2: Double = 0.0,
    val label: String = "",
    val note: String = "",
)
