package com.xcwk.scalehub.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.states.QkEmptyState
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import kotlin.math.abs
import kotlin.math.round

/**
 * AI 周总结（T-084 · Wave 1 · 原型 20 `v-weekly`）。
 *
 * 呈现式组件：读数由调用方传入，本文件不读 repository、不新建 ViewModel
 * （AiScreen / AiViewModel 属 Wave 2 的 T-083）。
 *
 * 成员与日期区间示例：林悦 · 2026年10月14日 — 10月20日。
 * 周数据缺失或非有限数值时只走 [QkEmptyState]，不估算、不除零。
 */
data class WeeklySummaryInput(
    val memberName: String,
    val rangeLabel: String,
    val startWeightKg: Double?,
    val endWeightKg: Double?,
    val startFatPercent: Double?,
    val endFatPercent: Double?,
    val measuredDays: Int,
    val plannedDays: Int,
    /** null 时使用原型亮点文案；空列表则不渲染该段。 */
    val highlights: List<String>? = null,
    /** null 时使用原型下周建议；空列表则不渲染该段。 */
    val suggestions: List<String>? = null,
)

internal object WeeklyCopy {
    const val TITLE = "AI 周总结"
    const val SECTION_CHANGES = "关键变化"
    const val LABEL_WEIGHT = "体重 kg"
    const val LABEL_FAT = "体脂 %"
    const val LABEL_DAYS = "测量天数"
    const val SECTION_HIGHLIGHTS = "本周亮点"
    const val SECTION_NEXT = "下周建议"
    const val DISCLAIMER = "AI 周总结基于已授权的测量记录生成，仅供参考。"
    const val BUTTON_PLAN = "生成下周计划"
    const val BUTTON_BACK = "返回"
    const val SAMPLE_MEMBER = "林悦"
    const val SAMPLE_RANGE = "2026年10月14日 — 10月20日"
    val HIGHLIGHTS = listOf(
        "坚持每天同一时段测量，数据可比性好",
        "肌肉量保持稳定，减重主要来自脂肪",
        "水分率正常，体脂估算可信度较高",
    )
    val SUGGESTIONS = listOf(
        "继续保持每天 7:30 左右测量",
        "增加 1 次力量训练，维持肌肉量",
        "注意控制晚餐碳水摄入",
    )
    const val EMPTY_TITLE = "本周还没有可总结的测量"
    const val EMPTY_BODY = "有测量记录后才会生成周总结。这里不会用空数据估算体重或体脂。"
}

sealed interface WeeklyView {
    data object Empty : WeeklyView
    data class Ready(
        val memberLine: String,
        val headline: String,
        val weightChange: String,
        val fatChange: String,
        val measuredLabel: String,
        val narrative: String,
        val highlights: List<String>,
        val suggestions: List<String>,
    ) : WeeklyView
}

/** 原型样例输入。空周不走这条，避免把样例数字当成真实读数。 */
internal fun prototypeWeeklyInput(): WeeklySummaryInput = WeeklySummaryInput(
    memberName = WeeklyCopy.SAMPLE_MEMBER,
    rangeLabel = WeeklyCopy.SAMPLE_RANGE,
    startWeightKg = 62.8,
    endWeightKg = 62.4,
    startFatPercent = 28.9,
    endFatPercent = 28.6,
    measuredDays = 5,
    plannedDays = 7,
)

internal fun resolveWeekly(input: WeeklySummaryInput): WeeklyView {
    val startW = input.startWeightKg
    val endW = input.endWeightKg
    val startF = input.startFatPercent
    val endF = input.endFatPercent
    if (startW == null || endW == null || startF == null || endF == null) return WeeklyView.Empty
    if (!startW.isFinite() || !endW.isFinite() || !startF.isFinite() || !endF.isFinite()) {
        return WeeklyView.Empty
    }
    val weightDelta = endW - startW
    val fatDelta = endF - startF
    val highlights = input.highlights ?: WeeklyCopy.HIGHLIGHTS
    val suggestions = input.suggestions ?: WeeklyCopy.SUGGESTIONS
    return WeeklyView.Ready(
        memberLine = "${input.memberName} · ${input.rangeLabel}",
        headline = weeklyHeadline(weightDelta),
        weightChange = formatSignedChange(weightDelta),
        fatChange = formatSignedChange(fatDelta),
        measuredLabel = measuredDaysLabel(input.measuredDays, input.plannedDays),
        narrative = weeklyNarrative(startW, endW, startF, endF),
        highlights = highlights,
        suggestions = suggestions,
    )
}

/**
 * 日均变化。测量天数为 0 时返回 null：不除零，也不回填 0 冒充有数据。
 * 周总结页本身不展示该值，供调用方在有天数时再决定是否使用。
 */
internal fun averageDailyChange(totalDelta: Double, measuredDays: Int): Double? {
    if (measuredDays <= 0 || !totalDelta.isFinite()) return null
    return totalDelta / measuredDays
}

/** 测量天数标签。计划天数为 0 时只显示已测天数，不做除法。 */
internal fun measuredDaysLabel(measured: Int, planned: Int): String {
    val safeMeasured = measured.coerceAtLeast(0)
    if (planned <= 0) return safeMeasured.toString()
    return "$safeMeasured/$planned"
}

internal fun weeklyHeadline(weightDeltaKg: Double): String {
    val magnitude = formatOneDecimal(abs(weightDeltaKg))
    return when {
        weightDeltaKg < 0 -> "本周体重下降 $magnitude kg，趋势稳健，继续保持。"
        weightDeltaKg > 0 -> "本周体重上升 $magnitude kg。"
        else -> "本周体重持平，趋势稳健，继续保持。"
    }
}

internal fun weeklyNarrative(
    startWeightKg: Double,
    endWeightKg: Double,
    startFatPercent: Double,
    endFatPercent: Double,
): String {
    val weight = changeClause(
        prefix = "本周体重从",
        start = startWeightKg,
        end = endWeightKg,
        unit = "kg",
        downVerb = "下降到",
    )
    val fat = changeClause(
        prefix = "体脂率从",
        start = startFatPercent,
        end = endFatPercent,
        unit = "%",
        downVerb = "降到",
        unitBeforeNumber = false,
    )
    val trend = when {
        endWeightKg < startWeightKg && endFatPercent < startFatPercent ->
            "两项指标均呈下降趋势，方向与你的减重目标一致。"
        endWeightKg > startWeightKg && endFatPercent > startFatPercent ->
            "两项指标均呈上升趋势。"
        else -> "两项指标变化方向不一致。"
    }
    return "$weight，$fat。$trend"
}

private fun changeClause(
    prefix: String,
    start: Double,
    end: Double,
    unit: String,
    downVerb: String,
    unitBeforeNumber: Boolean = true,
): String {
    val verb = when {
        end < start -> downVerb
        end > start -> "升到"
        else -> "持平于"
    }
    val startText = withUnit(formatOneDecimal(start), unit, unitBeforeNumber)
    val endText = withUnit(formatOneDecimal(end), unit, unitBeforeNumber)
    return "$prefix $startText $verb $endText"
}

private fun withUnit(number: String, unit: String, spaced: Boolean): String =
    if (spaced) "$number $unit" else "$number$unit"

internal fun formatOneDecimal(value: Double): String {
    val scaled = round(value * 10.0).toLong()
    val sign = if (scaled < 0) "-" else ""
    val absScaled = abs(scaled)
    val whole = absScaled / 10
    val frac = absScaled % 10
    return "$sign$whole.$frac"
}

internal fun formatSignedChange(delta: Double): String {
    val magnitude = formatOneDecimal(abs(delta))
    return when {
        delta < 0 -> "−$magnitude"
        delta > 0 -> "+$magnitude"
        else -> magnitude
    }
}

@Composable
fun AiWeeklySummary(
    input: WeeklySummaryInput?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onGeneratePlan: () -> Unit = {},
) {
    val view = if (input == null) WeeklyView.Empty else resolveWeekly(input)
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
                text = WeeklyCopy.TITLE,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(QkSpacing.Sm))
        when (view) {
            WeeklyView.Empty -> QkEmptyState(
                title = WeeklyCopy.EMPTY_TITLE,
                description = WeeklyCopy.EMPTY_BODY,
            )
            is WeeklyView.Ready -> WeeklyReadyBody(
                view = view,
                onBack = onBack,
                onGeneratePlan = onGeneratePlan,
            )
        }
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

@Composable
private fun WeeklyReadyBody(
    view: WeeklyView.Ready,
    onBack: () -> Unit,
    onGeneratePlan: () -> Unit,
) {
    Text(
        text = view.memberLine,
        color = QkColor.Muted,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(QkSpacing.Md))
    QkCard {
        Text(
            text = view.headline,
            color = QkColor.Fg,
            style = MaterialTheme.typography.titleLarge,
        )
    }
    Spacer(Modifier.height(QkSpacing.CardGap))
    QkCard {
        Text(
            text = WeeklyCopy.SECTION_CHANGES,
            color = QkColor.Fg,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(QkSpacing.Sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.CardGap),
        ) {
            ChangeCell(view.weightChange, WeeklyCopy.LABEL_WEIGHT, emphasize = true, Modifier.weight(1f))
            ChangeCell(view.fatChange, WeeklyCopy.LABEL_FAT, emphasize = true, Modifier.weight(1f))
            ChangeCell(view.measuredLabel, WeeklyCopy.LABEL_DAYS, emphasize = false, Modifier.weight(1f))
        }
        Spacer(Modifier.height(QkSpacing.Md))
        Text(
            text = view.narrative,
            color = QkColor.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (view.highlights.isNotEmpty()) {
        Spacer(Modifier.height(QkSpacing.CardGap))
        QkCard {
            Text(
                text = WeeklyCopy.SECTION_HIGHLIGHTS,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleSmall,
            )
            view.highlights.forEach { line ->
                Spacer(Modifier.height(QkSpacing.Sm))
                Text(text = line, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (view.suggestions.isNotEmpty()) {
        Spacer(Modifier.height(QkSpacing.CardGap))
        QkCard {
            Text(
                text = WeeklyCopy.SECTION_NEXT,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(QkSpacing.Sm))
            Text(
                text = view.suggestions.joinToString(separator = "\n") { "· $it" },
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Spacer(Modifier.height(QkSpacing.Md))
    Text(
        text = WeeklyCopy.DISCLAIMER,
        color = QkColor.Muted,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(QkSpacing.Lg))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
    ) {
        QkButton(
            text = WeeklyCopy.BUTTON_PLAN,
            style = QkButtonStyle.Secondary,
            modifier = Modifier.weight(1f),
            onClick = onGeneratePlan,
        )
        QkButton(
            text = WeeklyCopy.BUTTON_BACK,
            modifier = Modifier.weight(1f),
            onClick = onBack,
        )
    }
}

@Composable
private fun ChangeCell(
    value: String,
    label: String,
    emphasize: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(QkColor.Secondary)
            .padding(QkSpacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = if (emphasize) QkColor.Primary else QkColor.Fg,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(QkSpacing.Xs))
        Text(
            text = label,
            color = QkColor.Muted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
