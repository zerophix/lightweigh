package com.xcwk.scalehub.feature.mine

/** mine — 四件套之 UiState（表单文本态，解析在 ViewModel）。 */
data class MineUiState(
    val loading: Boolean = true,
    val male: Boolean = true,
    val birthYear: String = "",
    val birthMonth: String = "",
    val birthDay: String = "",
    val heightCm: String = "",
    val nickname: String = "",
    /** 汇总行："男 | 23岁 | 175cm"，未设置项显示 --（旧库口径）。 */
    val summary: String = "",
    /** 未设身高提示（需求文档：体脂估算依赖身高）。 */
    val unsetHint: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

sealed interface MineIntent {
    data class MaleChanged(val male: Boolean) : MineIntent
    data class YearChanged(val value: String) : MineIntent
    data class MonthChanged(val value: String) : MineIntent
    data class DayChanged(val value: String) : MineIntent
    data class HeightChanged(val value: String) : MineIntent
    data class NicknameChanged(val value: String) : MineIntent
    data object Save : MineIntent
}
