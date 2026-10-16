package com.xcwk.scalehub.application.command

/**
 * command — 记录一次测量（由 trigger 层组装，只带事实）。
 * impedanceOhm 为 0 表示本次未测出阻抗（纯体重/体重秤）。
 * manualFat：手动录入体重时用户可选填的体脂率（旧库 saveManualWeight
 * 的 extra 口径：可空，缺省 -1 哨兵；仅在无阻抗路径生效，
 * 越界由 MeasureRecord 构造守卫拒绝）。
 */
data class RecordMeasurementCommand(
    val weightKg: Double,
    val impedanceOhm: Double,
    val mac: String,
    val algorithmId: String,
    val manualFat: Double? = null,
)
