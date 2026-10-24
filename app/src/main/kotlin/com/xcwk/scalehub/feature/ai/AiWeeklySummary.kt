package com.xcwk.scalehub.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.states.QkEmptyState
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * ai — AI 周总结页（T-084 · Wave 1，原型 20 `v-weekly`）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/20-ai-members.html` id="v-weekly"（Figma D05 缺口）
 * - 验收：AC-J1-11「周报与成员详情」——成员与日期区间 + 总结句 + 关键变化指标卡
 *
 * ## 范围（关键约束）
 * 本任务**只新建文件**。既有 `AiScreen` / `AiUiState` / `AiViewModel` 归 Wave 2 的 T-083，
 * 同模块并行改会冲突（GC-prototype-driven-dev「同模块串行」），故本文件自带独立的
 * [AiWeeklySummaryUiState]，不与既有四件套耦合。
 *
 * ## 呈现式约束
 * Screen 纯渲染：全部数据由 [AiWeeklySummaryUiState] 传入，本文件不读 repository、不发请求。
 * 周数据为空时走通用空态 [QkEmptyState]（T-097 / Wave 0），不渲染任何伪造指标，
 * 也不做任何可能除零的计算（AC-H1 / AC-HT1 同口径：空态不出现假数据）。
 * 颜色、圆角、间距一律取 QkColor / QkRadius / QkSpacing token。
 */

/** 周总结「关键变化」的一格指标（原型三格：体重 / 体脂 / 测量天数）。 */
data class WeeklyMetric(
    val value: String,
    val label: String,
    /** 高亮格：原型里体重与体脂两格用品牌色，测量天数为常规墨色。 */
    val highlight: Boolean = false,
)

/** AI 周总结页的渲染状态（不可变，由上层传入；空数据由 [hasData] 判定）。 */
data class AiWeeklySummaryUiState(
    /** 成员名，如「林悦」。 */
    val memberName: String = "",
    /** 日期区间，如「2026年10月14日 — 10月20日」。 */
    val dateRange: String = "",
    /** 总结句（原型 .card.soft 大字）。 */
    val summary: String = "",
    /** 关键变化指标格。 */
    val metrics: List<WeeklyMetric> = emptyList(),
    /** 关键变化下方的口径说明段。 */
    val detail: String = "",
    /** 本周亮点条目。 */
    val highlights: List<String> = emptyList(),
    /** 下周建议条目（渲染时逐行加「· 」前缀）。 */
    val suggestions: List<String> = emptyList(),
    /** 底部说明（AI 生成内容的免责口径）。 */
    val footnote: String = "",
) {
    /**
     * 是否有本周数据：总结句与关键变化至少有一项才渲染正文。
     *
     * 空数据（两者皆空）→ 走通用空态，不展示任何数值，避免「空周报里冒出假指标」。
     */
    val hasData: Boolean
        get() = summary.isNotBlank() || metrics.isNotEmpty()
}

/**
 * AI 周总结页（原型 v-weekly）。
 *
 * @param state 页面状态；[AiWeeklySummaryUiState.hasData] 为 false 时渲染通用空态
 * @param onBack 返回（原型导航栏返回与底部「返回」按钮同一去向）
 * @param onGeneratePlan 「生成下周计划」（原型 data-toast 动作）
 */
@Composable
fun AiWeeklySummaryScreen(
    state: AiWeeklySummaryUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onGeneratePlan: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(QkSpacing.Page),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = onBack)
            QkPageTitle(
                title = "AI 周总结",
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
        }
        if (!state.hasData) {
            // 通用空态（Wave 0 / T-097）：只渲染文案，不产生任何数值
            QkEmptyState(
                title = AiWeeklyEntry.EMPTY_TITLE,
                description = "完成一次测量后，AI 会根据已授权的记录生成周总结。",
                icon = QkIcons.Ai,
                modifier = Modifier.padding(top = QkSpacing.Xl),
            )
        } else {
            WeeklySummaryBody(
                state = state,
                onBack = onBack,
                onGeneratePlan = onGeneratePlan,
                modifier = Modifier.padding(top = QkSpacing.Md),
            )
        }
    }
}

@Composable
private fun WeeklySummaryBody(
    state: AiWeeklySummaryUiState,
    onBack: () -> Unit,
    onGeneratePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 成员 + 日期区间（原型 .hint）
        if (state.memberName.isNotBlank() || state.dateRange.isNotBlank()) {
            Text(
                text = listOf(state.memberName, state.dateRange)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }

        // 总结句（原型 .card.soft）
        if (state.summary.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = QkSpacing.Md)
                    .clip(RoundedCornerShape(QkRadius.Card))
                    .background(QkColor.Mint)
                    .padding(QkSpacing.CardPad),
            ) {
                Text(
                    text = state.summary,
                    style = MaterialTheme.typography.titleLarge,
                    color = QkColor.Fg,
                )
            }
        }

        // 关键变化（原型 .card.lg pad18）
        if (state.metrics.isNotEmpty()) {
            QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
                Text(
                    text = "关键变化",
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Fg,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = QkSpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
                ) {
                    state.metrics.forEach { metric ->
                        MetricTile(metric = metric, modifier = Modifier.weight(1f))
                    }
                }
                if (state.detail.isNotBlank()) {
                    Text(
                        text = state.detail,
                        modifier = Modifier.padding(top = QkSpacing.Md),
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Muted,
                    )
                }
            }
        }

        // 本周亮点（原型 .card.lg pad18）
        if (state.highlights.isNotEmpty()) {
            QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
                Text(
                    text = "本周亮点",
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Fg,
                )
                Column(
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
                ) {
                    state.highlights.forEach { highlight ->
                        HighlightRow(text = highlight)
                    }
                }
            }
        }

        // 下周建议（原型 .card.lg pad18，逐行「· 」前缀）
        if (state.suggestions.isNotEmpty()) {
            QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
                Text(
                    text = "下周建议",
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Fg,
                )
                Column(modifier = Modifier.padding(top = QkSpacing.Sm)) {
                    state.suggestions.forEach { suggestion ->
                        Text(
                            text = "· $suggestion",
                            modifier = Modifier.padding(top = QkSpacing.Xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.Muted,
                        )
                    }
                }
            }
        }

        // 底部说明（原型 .note）
        if (state.footnote.isNotBlank()) {
            Text(
                text = state.footnote,
                modifier = Modifier.padding(top = QkSpacing.Md),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkButton(
                text = "生成下周计划",
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = onGeneratePlan,
            )
            QkButton(
                text = "返回",
                modifier = Modifier.weight(1f),
                onClick = onBack,
            )
        }
    }
}

/** 关键变化的一格：浅底圆角块 + 数值（高亮格主绿）+ 单位标签。 */
@Composable
private fun MetricTile(metric: WeeklyMetric, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(QkRadius.Segment))
            .background(QkColor.MintSoft)
            .padding(vertical = QkSpacing.Md, horizontal = QkSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = metric.value,
            style = MaterialTheme.typography.titleLarge,
            color = if (metric.highlight) QkColor.Primary else QkColor.Fg,
        )
        Text(
            text = metric.label,
            modifier = Modifier.padding(top = QkSpacing.Xs),
            style = MaterialTheme.typography.labelMedium,
            color = QkColor.Muted,
        )
    }
}

/** 亮点条目：主绿对勾 + 正文。 */
@Composable
private fun HighlightRow(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = QkColor.Primary,
            modifier = Modifier.padding(top = QkSpacing.Xs).size(QkSpacing.Lg),
        )
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .padding(start = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AiWeeklySummaryPreview() {
    QkTheme {
        AiWeeklySummaryScreen(
            state = AiWeeklySummaryUiState(
                memberName = "林悦",
                dateRange = "2026年10月14日 — 10月20日",
                summary = "本周体重下降 0.4 kg，趋势稳健，继续保持。",
                metrics = listOf(
                    WeeklyMetric("−0.4", "体重 kg", highlight = true),
                    WeeklyMetric("−0.3", "体脂 %", highlight = true),
                    WeeklyMetric("5/7", "测量天数"),
                ),
                detail = "本周体重从 62.8 kg 下降到 62.4 kg，体脂率从 28.9% 降到 28.6%。" +
                    "两项指标均呈下降趋势，方向与你的减重目标一致。",
                highlights = listOf(
                    "坚持每天同一时段测量，数据可比性好",
                    "肌肉量保持稳定，减重主要来自脂肪",
                    "水分率正常，体脂估算可信度较高",
                ),
                suggestions = listOf(
                    "继续保持每天 7:30 左右测量",
                    "增加 1 次力量训练，维持肌肉量",
                    "注意控制晚餐碳水摄入",
                ),
                footnote = "AI 周总结基于已授权的测量记录生成，仅供参考。",
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AiWeeklySummaryEmptyPreview() {
    QkTheme {
        AiWeeklySummaryScreen(state = AiWeeklySummaryUiState())
    }
}
