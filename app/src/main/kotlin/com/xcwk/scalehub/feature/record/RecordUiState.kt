package com.xcwk.scalehub.feature.record

/** 8 类记录的面板标题（壳的加号面板与表单共用单一事实源）。 */
val RECORD_TYPE_TITLES = mapOf(
    "weight" to "添加体重",
    "diet" to "记饮食",
    "exercise" to "记运动",
    "water" to "去喝水",
    "waist" to "记体围",
    "glucose" to "记血糖",
    "blood_pressure" to "记血压",
    "habit" to "记习惯",
)

/** record — 四件套之 UiState（表单字段语义随旧库 RecordScreen Pages.kt:466-530）。 */
data class RecordUiState(
    val type: String = "",
    val title: String = "",
    val primaryLabel: String = "",
    val primaryValue: String = "",
    val extraLabel: String? = null,
    val extraValue: String = "",
    val presets: List<String> = emptyList(),
    val selectedPreset: String? = null,
    val quickValues: List<Int> = emptyList(),
    val isHabit: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

sealed interface RecordIntent {
    data class ValueChanged(val value: String) : RecordIntent
    data class ExtraChanged(val value: String) : RecordIntent
    data class PresetPicked(val preset: String) : RecordIntent
    data class QuickPicked(val value: Int) : RecordIntent
    data object Save : RecordIntent
}
