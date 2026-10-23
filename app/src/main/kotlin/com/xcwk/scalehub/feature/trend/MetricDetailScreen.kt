package com.xcwk.scalehub.feature.trend

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.components.QkAreaChart
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.states.QkEmptyState
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/** 八个体成分指标（原型 `17-metric-details.html` 的 8 个详情 view）。 */
enum class MetricKey { Weight, Bmi, Muscle, Water, Visceral, Metabolism, Score, Bone }

/** 分级区间在视觉上的归类：偏低 / 正常 / 偏高。 */
enum class MetricTone { Low, Normal, High }

/** 一条分级说明（原型「标准 48–58 kg」「正常 18.5–23.9」等）。 */
data class MetricLevel(val name: String, val range: String, val tone: MetricTone)

/**
 * 指标的**静态文案与分级**——完全来自原型，不含任何测量值。
 *
 * 与 [MetricReading] 分离的原因：文案是产品事实（PRD + 原型），测量值是运行时数据；
 * 混在一起会导致"改个测量值顺手改了参考区间"这类事实漂移。
 */
data class MetricSpec(
    val key: MetricKey,
    val title: String,
    val unit: String,
    /** 解释段（原型正文，逐字照抄） */
    val explainer: String,
    /** 参考范围标题，如「参考范围 · 女 20–39 岁」 */
    val referenceLabel: String = "",
    val levels: List<MetricLevel> = emptyList(),
    /** 公式或口径说明（BMI 除法公式、内脏等级区间等） */
    val formula: String = "",
    /** 免责或补充说明（评分为估算参考…） */
    val note: String = "",
    /** 标签 + 数值 的补充行（评分为构成占比、代谢为三档摄入） */
    val composition: List<Pair<String, String>> = emptyList(),
)

/** 一次测量下的指标读数（运行时数据，由调用方汇总）。 */
data class MetricReading(
    val spec: MetricSpec,
    val value: String,
    val deltaText: String = "",
    val trendPoints: List<TrendPointUi> = emptyList(),
    val trendDates: List<String> = emptyList(),
)

/**
 * 趋势可绘制状态（**AC-HT5 的可判定形式**）。
 *
 * - [Empty]：没有任何测量
 * - [InsufficientData]：**仅 1 条测量** → 不画假趋势
 * - [Plottable]：≥2 条 → 可画
 */
enum class TrendState { Empty, InsufficientData, Plottable }

/**
 * 依据数据点数量判定趋势状态（AC-HT5 原文：仅 1 条测量时为 InsufficientData，不画假趋势）。
 *
 * 纯函数，JVM 单测覆盖 —— 这是「不编造趋势」这条验收唯一可自动判定的部分。
 */
fun trendStateOf(pointCount: Int): TrendState = when {
    pointCount <= 0 -> TrendState.Empty
    pointCount == 1 -> TrendState.InsufficientData
    else -> TrendState.Plottable
}

/**
 * 把可能含缺日的序列切成**连续分段**（AC-HT4 原文：趋势中间缺一天时不出现跨空洞插值线）。
 *
 * `null` 表示该天无记录。切分后每段单独绘制，段与段之间不连线，
 * 因此图表结构上无法产生"跨空洞的插值线"。
 *
 * ```
 * segmentSeries(listOf(1.0, 2.0, null, 3.0))  → [[1.0, 2.0], [3.0]]
 * segmentSeries(listOf(1.0, null))            → [[1.0]]
 * segmentSeries(emptyList())                 → []
 * ```
 */
fun segmentSeries(values: List<Double?>): List<List<Double>> {
    val segments = mutableListOf<MutableList<Double>>()
    var current = mutableListOf<Double>()
    for (v in values) {
        if (v == null) {
            if (current.isNotEmpty()) {
                segments += current
                current = mutableListOf()
            }
        } else {
            current += v
        }
    }
    if (current.isNotEmpty()) segments += current
    return segments
}

/**
 * 八个指标的静态文案（逐字取自原型 `17-metric-details.html`）。
 *
 * 值字段留空 —— 测量值属 [MetricReading]，此处只固定产品事实。
 */
val METRIC_SPECS: List<MetricSpec> = listOf(
    MetricSpec(
        key = MetricKey.Weight,
        title = "体重",
        unit = "kg",
        explainer = "体重是身体成分的基础指标。建议结合体脂率、肌肉量一起看，比单看体重更能了解身体变化。",
        referenceLabel = "健康参考范围 · 女 20–39 岁",
        levels = listOf(
            MetricLevel("偏轻", "", MetricTone.Low),
            MetricLevel("标准", "48–58 kg", MetricTone.Normal),
            MetricLevel("偏重", "", MetricTone.High),
        ),
    ),
    MetricSpec(
        key = MetricKey.Bmi,
        title = "BMI",
        unit = "",
        explainer = "BMI 不区分肌肉与脂肪，运动员可能偏高但体脂正常。",
        levels = listOf(
            MetricLevel("偏瘦", "<18.5", MetricTone.Low),
            MetricLevel("正常", "18.5–23.9", MetricTone.Normal),
            MetricLevel("超重", "24–27.9", MetricTone.High),
        ),
        formula = "BMI = 体重(kg) ÷ 身高(m)²",
    ),
    MetricSpec(
        key = MetricKey.Muscle,
        title = "肌肉量",
        unit = "kg",
        explainer = "肌肉量是骨骼肌等肌肉总重量。肌肉量越高，基础代谢通常越高。建议结合运动与蛋白质摄入保持肌肉量。",
        referenceLabel = "参考范围 · 女 20–39 岁",
        levels = listOf(
            MetricLevel("偏低", "", MetricTone.Low),
            MetricLevel("标准", "36–46 kg", MetricTone.Normal),
            MetricLevel("偏高", "", MetricTone.High),
        ),
    ),
    MetricSpec(
        key = MetricKey.Water,
        title = "水分率",
        unit = "%",
        explainer = "水分率是体内水分占比。脱水会明显影响体脂率等其他读数，建议测量前正常饮水。",
        referenceLabel = "参考范围 · 女 20–39 岁",
        levels = listOf(
            MetricLevel("偏低", "", MetricTone.Low),
            MetricLevel("标准", "45–60%", MetricTone.Normal),
            MetricLevel("偏高", "", MetricTone.High),
        ),
    ),
    MetricSpec(
        key = MetricKey.Visceral,
        title = "内脏脂肪等级",
        unit = "级",
        explainer = "内脏脂肪等级 1–9 为标准范围。内脏脂肪过高与代谢风险相关，建议保持均衡饮食与规律运动。",
        levels = listOf(
            MetricLevel("标准", "(1–9)", MetricTone.Normal),
            MetricLevel("偏高", "(10–14)", MetricTone.High),
            MetricLevel("高", "(15+)", MetricTone.High),
        ),
        formula = "等级可视化（1–30）",
    ),
    MetricSpec(
        key = MetricKey.Metabolism,
        title = "基础代谢",
        unit = "kcal",
        explainer = "基础代谢是静息状态下消耗的热量。肌肉量越高通常越高。日常总消耗 = 基础代谢 × 活动系数。",
        composition = listOf("减重 kcal" to "", "维持 kcal" to "", "增重 kcal" to ""),
        note = "减重建议每日摄入 ≈ 基础代谢 × 0.8，健康节奏比速度更重要。",
    ),
    MetricSpec(
        key = MetricKey.Score,
        title = "身体评分",
        unit = "分",
        explainer = "评分如何得出",
        levels = listOf(
            MetricLevel("需改善", "", MetricTone.Low),
            MetricLevel("良好", "80–90", MetricTone.Normal),
            MetricLevel("优秀", "", MetricTone.High),
        ),
        composition = listOf(
            "体脂率参考区间" to "30%",
            "BMI" to "25%",
            "肌肉量与水分率" to "25%",
            "内脏脂肪等级" to "20%",
        ),
        note = "评分为估算参考，非医疗诊断。",
    ),
    MetricSpec(
        key = MetricKey.Bone,
        title = "骨量",
        unit = "kg",
        explainer = "骨量是骨骼中矿物质含量的估算值。建议保持钙质摄入与负重运动。",
        referenceLabel = "参考范围 · 女 20–39 岁",
        levels = listOf(
            MetricLevel("偏低", "", MetricTone.Low),
            MetricLevel("标准", "2.5–3.2 kg", MetricTone.Normal),
            MetricLevel("偏高", "", MetricTone.High),
        ),
    ),
)

/** 按 key 取指标文案；未知 key 返回 null（调用方显式处理，不给默认值防编造）。 */
fun metricSpec(key: MetricKey): MetricSpec? = METRIC_SPECS.find { it.key == key }

/**
 * 全部身体指标列表（T-082 · Wave 1，原型 `v-list`）。
 *
 * > 全部身体指标 | 林悦 · 10月21日 07:30 测量 | 体重 62.4 kg 标准范围 › | BMI 22.1 …
 *
 * 行文案由 [MetricSpec] 与读数合成，不含任何写死的测量值。
 */
@Composable
fun MetricListScreen(
    readings: List<MetricReading>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpen: (MetricKey) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(Modifier.height(QkSpacing.Md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = onBack)
            Spacer(Modifier.width(QkSpacing.Sm))
            Text(
                text = "全部身体指标",
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (readings.isEmpty()) {
            QkEmptyState(
                title = "还没有测量记录",
                description = "连接体脂秤后开始第一次测量。",
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        Spacer(Modifier.height(QkSpacing.Lg))
        readings.forEach { reading ->
            val rangeLabel = reading.spec.levels
                .firstOrNull { it.tone == MetricTone.Normal }?.takeIf { it.range.isNotEmpty() }
                ?.let { "标准范围 ${it.range}" }
                ?: "标准范围"
            QkListRow(
                title = reading.spec.title,
                subtitle = "${reading.value} ${reading.spec.unit}".trimEnd() + " · $rangeLabel ›",
                onClick = { onOpen(reading.spec.key) },
            )
        }

        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/**
 * 单个指标详情（T-082 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/17-metric-details.html` →
 * `v-weight` / `v-bmi` / `v-muscle` / `v-water` / `v-visceral` / `v-metabolism` / `v-score` / `v-bone`
 *
 * ## 验收
 * - **AC-HT4 趋势中间缺一天时不出现跨空洞插值线** → 由 [segmentSeries] 切段、逐段绘制，
 *   结构上不可能产生跨空洞连线
 * - **AC-HT5 仅 1 条测量时为 InsufficientData，不画假趋势** → 由 [trendStateOf] 判定，
 *   非 [TrendState.Plottable] 时**不渲染任何趋势线**，只显示文字说明
 */
@Composable
fun MetricDetailScreen(
    reading: MetricReading,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSeeList: () -> Unit = {},
) {
    val spec = reading.spec
    val state = trendStateOf(reading.trendPoints.size)
    // 缺日切段：每段独立绘制，段间不连线（AC-HT4）
    val segments = segmentSeries(reading.trendPoints.map { it.value })

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(Modifier.height(QkSpacing.Md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = onBack)
            Spacer(Modifier.width(QkSpacing.Sm))
            Text(
                text = spec.title,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Spacer(Modifier.height(QkSpacing.Lg))
        QkCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(QkSpacing.CardPad)) {
                Text(
                    text = "本次测量",
                    color = QkColor.Muted,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(QkSpacing.Xs))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = reading.value,
                        color = QkColor.Fg,
                        style = MaterialTheme.typography.displaySmall,
                    )
                    if (spec.unit.isNotEmpty()) {
                        Spacer(Modifier.width(QkSpacing.Xs))
                        Text(
                            text = spec.unit,
                            color = QkColor.Muted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = QkSpacing.Xs),
                        )
                    }
                    if (reading.deltaText.isNotEmpty()) {
                        Spacer(Modifier.width(QkSpacing.Md))
                        Text(
                            text = reading.deltaText,
                            color = QkColor.Primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(bottom = QkSpacing.Xs),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(QkSpacing.CardGap))
        Text(
            text = spec.explainer,
            color = QkColor.Muted,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (spec.formula.isNotEmpty()) {
            Spacer(Modifier.height(QkSpacing.Sm))
            Text(
                text = spec.formula,
                color = QkColor.Fg,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (spec.levels.isNotEmpty()) {
            Spacer(Modifier.height(QkSpacing.Lg))
            QkSectionHead(if (spec.referenceLabel.isEmpty()) "标准范围" else spec.referenceLabel)
            Spacer(Modifier.height(QkSpacing.CardGap))
            QkCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(QkSpacing.CardPad)) {
                    spec.levels.forEach { level -> LevelRow(level) }
                }
            }
        }

        if (spec.composition.isNotEmpty()) {
            Spacer(Modifier.height(QkSpacing.Lg))
            QkSectionHead(if (spec.key == MetricKey.Score) "评分如何得出" else "减重参考（每日摄入）")
            Spacer(Modifier.height(QkSpacing.CardGap))
            QkCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(QkSpacing.CardPad)) {
                    spec.composition.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = QkSpacing.Xs),
                        ) {
                            Text(
                                text = label,
                                color = QkColor.Muted,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = value,
                                color = QkColor.Fg,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(QkSpacing.Lg))
        QkSectionHead("趋势")
        Spacer(Modifier.height(QkSpacing.CardGap))
        when (state) {
            TrendState.Empty -> Text(
                text = "还没有测量记录，暂无趋势。",
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
            )

            TrendState.InsufficientData -> Text(
                text = "仅 1 条测量记录，数据不足以绘制趋势。",
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
            )

            TrendState.Plottable -> segments.forEachIndexed { index, segment ->
                if (index > 0) {
                    Spacer(Modifier.height(QkSpacing.Sm))
                    Text(
                        text = "缺测区间，分段展示",
                        color = QkColor.Hint,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                QkAreaChart(
                    points = segment.mapIndexed { i, v -> TrendPointUi(i.toLong(), v) },
                    modifier = Modifier.fillMaxWidth(),
                    xLabels = reading.trendDates.take(segment.size),
                )
            }
        }

        if (spec.note.isNotEmpty()) {
            Spacer(Modifier.height(QkSpacing.Sm))
            Text(
                text = spec.note,
                color = QkColor.Hint,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(QkSpacing.Lg))
        QkButton(
            text = "查看全部指标",
            style = QkButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
            onClick = onSeeList,
        )

        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/** 分级行：名称 + 区间，按 [MetricTone] 上色。 */
@Composable
private fun LevelRow(level: MetricLevel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = QkSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = level.name,
            color = when (level.tone) {
                MetricTone.Low -> QkColor.Blue
                MetricTone.Normal -> QkColor.Primary
                MetricTone.High -> QkColor.Accent
            },
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(QkSpacing.Xl),
        )
        Text(
            text = level.range,
            color = QkColor.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
