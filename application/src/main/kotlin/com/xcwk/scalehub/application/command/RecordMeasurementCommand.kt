package com.xcwk.scalehub.application.command

/**
 * command — 记录一次测量（由 trigger 层组装，只带事实）。
 * impedanceOhm 为 0 表示本次未测出阻抗（纯体重/体重秤）。
 */
data class RecordMeasurementCommand(
    val weightKg: Double,
    val impedanceOhm: Double,
    val mac: String,
    val algorithmId: String,
)
