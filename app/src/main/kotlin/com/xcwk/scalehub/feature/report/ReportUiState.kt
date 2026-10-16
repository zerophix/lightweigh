package com.xcwk.scalehub.feature.report

/** report — 四件套之 UiState。 */
data class ReportMetricUi(val name: String, val valueText: String, val unit: String)

data class ReportUiState(
    val loading: Boolean = true,
    val found: Boolean = false,
    val timeText: String = "",
    val weightText: String = "",
    /** "比上次  体重 +1.5 公斤  体脂率 -0.8 %"，无上次或缺项给 --（Pages:663）。 */
    val deltaLine: String = "",
    val bmiText: String = "--",
    val bmiLabel: String? = null,
    val metrics: List<ReportMetricUi> = emptyList(),
    /** 缺档提示行（"完善「身高」档案…"），空列表不显示。 */
    val missingText: String? = null,
    val disclaimer: String = "",
)

sealed interface ReportIntent {
    data object AiExplain : ReportIntent   // AI 解读（M2 云接入前仅占位）
}
