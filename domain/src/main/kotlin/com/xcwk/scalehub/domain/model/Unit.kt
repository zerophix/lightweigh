package com.xcwk.scalehub.domain.model

/**
 * 重量单位。
 * - KG: 千克（公制，默认）
 * - JIN: 斤（中国传统，1 斤 = 0.5 kg）
 * - LB: 磅（英制，1 lb ≈ 0.45359237 kg）
 */
enum class WeightUnit(val factorToKg: Double, val symbol: String, val symbolZh: String) {
    KG(1.0, "kg", "公斤"),
    JIN(0.5, "jin", "斤"),
    LB(0.45359237, "lb", "磅");

    /** 任意单位 → kg */
    fun toKg(value: Double): Double = value * factorToKg
    /** kg → 任意单位 */
    fun fromKg(kg: Double): Double = kg / factorToKg
}

/**
 * 长度单位。
 * - CM: 厘米（公制，默认）
 * - IN: 英寸（英制，1 in = 2.54 cm）
 */
enum class LengthUnit(val factorToCm: Double, val symbol: String, val symbolZh: String) {
    CM(1.0, "cm", "厘米"),
    IN(2.54, "in", "英寸");

    fun toCm(value: Double): Double = value * factorToCm
    fun fromCm(cm: Double): Double = cm / factorToCm
}

/** 用户偏好设置（持久化实体）。 */
data class UserUnitPreferences(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val lengthUnit: LengthUnit = LengthUnit.CM,
    /** 是否在 BMI/体脂率等衍生指标也按单位换算（默认 true） */
    val applyToDerived: Boolean = true
)