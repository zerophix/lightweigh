package com.xcwk.scalehub.feature.report

/** report — 四件套之 UiState（评分/建议/体型/风险/控制来自 BodyAnalysis 全套规则）。 */
data class ReportMetricUi(
    val name: String,
    val valueText: String,
    val unit: String,
    val status: String,
    val tone: Int,        // 0未录入 1标准 2注意 3超标
    val delta: String?,
)

data class ReportRiskUi(val name: String, val levelText: String, val tone: Int)

/** 报告分析 tab 目标达成行（体重/BMI/体脂率/肌肉率）。 */
data class ReportGoalRow(
    val name: String,
    val valueText: String,
    val achieved: Boolean,
    val rangeText: String,
)

data class ReportUiState(
    val loading: Boolean = true,
    val found: Boolean = false,
    val timeText: String = "",
    val weightText: String = "",
    /** "比上次  体重 +1.5 公斤  体脂率 -0.8 %"（Pages:663 口径）。 */
    val deltaLine: String = "",
    val score: Int? = null,
    /** 评分较上次变化（原型评分卡 pill）：两端都有评分才给，否则 null。 */
    val scoreDelta: Int? = null,
    val advice: String? = null,
    val bodyShape: String? = null,
    val risks: List<ReportRiskUi> = emptyList(),
    val bmiText: String = "--",
    val bmiLabel: String? = null,
    val metrics: List<ReportMetricUi> = emptyList(),
    val fatControlText: String? = null,
    val muscleControlText: String? = null,
    val standardWeightText: String? = null,
    /** 缺档提示行（"完善「身高」档案…"）。 */
    val missingText: String? = null,
    val disclaimer: String = "",
    /** AI 解读可用（正式账号口径，M4 联动）。 */
    val aiEnabled: Boolean = false,
    // ---- 报告分析 tab（DEC-043-A）----
    /** BMI 分档 0-4（BodyFigure 身宽 + BmiScaleBar 游标色）。 */
    val bmiLevel: Int = 1,
    /** 近期体重序列（最多 11 条，时间正序，用于折线图）。 */
    val recentWeights: List<Double> = emptyList(),
    /** 近期体脂率序列（过滤 -1 无效值）。 */
    val recentFats: List<Double> = emptyList(),
    /** 目标达成行（体重/BMI/体脂率/肌肉率）。 */
    val goalRows: List<ReportGoalRow> = emptyList(),
)

sealed interface ReportIntent {

}
