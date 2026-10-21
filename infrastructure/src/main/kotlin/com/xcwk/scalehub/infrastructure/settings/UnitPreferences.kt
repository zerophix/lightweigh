package com.xcwk.scalehub.infrastructure.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 单位偏好（DEC-043-C）：重量 kg/斤/磅、长度 cm/in。
 * 存储层统一 kg/cm，展示层按此换算。
 */
data class UnitPrefs(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val lengthUnit: LengthUnit = LengthUnit.CM,
)

enum class WeightUnit(val label: String, val factor: Double) {
    KG("公斤", 1.0),
    JIN("斤", 2.0),
    LB("磅", 2.20462),
}

enum class LengthUnit(val label: String, val factor: Double) {
    CM("厘米", 1.0),
    IN("英寸", 0.393701),
}

class UnitPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("unit_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UnitPrefs> = _state.asStateFlow()

    private fun load() = UnitPrefs(
        weightUnit = WeightUnit.entries.getOrElse(prefs.getInt("weight", 0)) { WeightUnit.KG },
        lengthUnit = LengthUnit.entries.getOrElse(prefs.getInt("length", 0)) { LengthUnit.CM },
    )

    fun setWeightUnit(unit: WeightUnit) {
        prefs.edit().putInt("weight", unit.ordinal).apply()
        _state.value = _state.value.copy(weightUnit = unit)
    }

    fun setLengthUnit(unit: LengthUnit) {
        prefs.edit().putInt("length", unit.ordinal).apply()
        _state.value = _state.value.copy(lengthUnit = unit)
    }

    /** kg → 展示值。 */
    fun formatWeight(kg: Double): String {
        val unit = _state.value.weightUnit
        return "%.1f".format(kg * unit.factor)
    }

    /** cm → 展示值。 */
    fun formatLength(cm: Double): String {
        val unit = _state.value.lengthUnit
        return "%.1f".format(cm * unit.factor)
    }

    /** 展示单位标签。 */
    fun weightLabel(): String = _state.value.weightUnit.label
    fun lengthLabel(): String = _state.value.lengthUnit.label
}