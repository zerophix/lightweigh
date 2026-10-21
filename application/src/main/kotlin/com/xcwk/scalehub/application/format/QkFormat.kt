package com.xcwk.scalehub.application.format

import com.xcwk.scalehub.domain.model.LengthUnit
import com.xcwk.scalehub.domain.model.UserUnitPreferences
import com.xcwk.scalehub.domain.model.WeightUnit

/**
 * 统一格式化工具（单位感知）。
 * 所有 UI 体重/身高/衍生指标渲染统一走这里，禁止硬编码 "kg"/"cm"。
 *
 * 存储层统一用 kg/cm，展示层按 UserUnitPreferences 换算。
 */
object QkFormat {

    // ===== 重量 =====

    /** kg → 用户偏好单位（保留 1 位小数，斤/磅保留 1 位） */
    fun weight(kg: Double, prefs: UserUnitPreferences): Double =
        prefs.weightUnit.fromKg(kg)

    /** kg → 用户偏好单位字符串（含单位后缀） */
    fun weightStr(kg: Double, prefs: UserUnitPreferences): String {
        val v = weight(kg, prefs)
        return when (prefs.weightUnit) {
            WeightUnit.KG -> String.format("%.1f%s", v, prefs.weightUnit.symbolZh)
            WeightUnit.JIN -> String.format("%.1f%s", v, prefs.weightUnit.symbolZh)
            WeightUnit.LB -> String.format("%.1f%s", v, prefs.weightUnit.symbol)
        }
    }

    /** 仅数值部分（供输入框/编辑器用） */
    fun weightValue(kg: Double, prefs: UserUnitPreferences): Double =
        prefs.weightUnit.fromKg(kg)

    /** 用户输入值 → kg（存储） */
    fun weightToKg(value: Double, prefs: UserUnitPreferences): Double =
        prefs.weightUnit.toKg(value)

    // ===== 身高/长度 =====

    /** cm → 用户偏好单位 */
    fun height(cm: Double, prefs: UserUnitPreferences): Double =
        prefs.lengthUnit.fromCm(cm)

    /** cm → 用户偏好单位字符串 */
    fun heightStr(cm: Double, prefs: UserUnitPreferences): String {
        val v = height(cm, prefs)
        return when (prefs.lengthUnit) {
            LengthUnit.CM -> String.format("%.0f%s", v, prefs.lengthUnit.symbolZh)
            LengthUnit.IN -> String.format("%.1f%s", v, prefs.lengthUnit.symbol)
        }
    }

    /** 身高仅数值 */
    fun heightValue(cm: Double, prefs: UserUnitPreferences): Double =
        prefs.lengthUnit.fromCm(cm)

    /** 用户输入值 → cm（存储） */
    fun heightToCm(value: Double, prefs: UserUnitPreferences): Double =
        prefs.lengthUnit.toCm(value)

    // ===== 衍生指标（BMI、体脂率等）=====

    /** BMI 始终按 kg/m² 计算；展示时若 applyToDerived=false 则不加单位后缀 */
    fun bmiStr(bmi: Double, prefs: UserUnitPreferences): String =
        if (prefs.applyToDerived) String.format("%.1f", bmi) else String.format("%.1f", bmi)

    /** 体脂率等百分比指标 */
    fun percentStr(value: Double, prefs: UserUnitPreferences): String =
        String.format("%.1f%%", value)

    /** 基础代谢等整数指标 */
    fun intStr(value: Int, prefs: UserUnitPreferences): String = value.toString()

    /** 千焦/千卡等能量单位（存储 kJ，展示按偏好） */
    fun energyKjStr(kj: Double, prefs: UserUnitPreferences): String =
        String.format("%.0f kJ", kj)

    // ===== 单位标签（用于设置页、输入框 hint）=====

    fun weightUnitLabel(prefs: UserUnitPreferences): String = prefs.weightUnit.symbolZh
    fun lengthUnitLabel(prefs: UserUnitPreferences): String = prefs.lengthUnit.symbolZh

    /** 所有可选重量单位 */
    val allWeightUnits = listOf(WeightUnit.KG, WeightUnit.JIN, WeightUnit.LB)
    /** 所有可选长度单位 */
    val allLengthUnits = listOf(LengthUnit.CM, LengthUnit.IN)
}