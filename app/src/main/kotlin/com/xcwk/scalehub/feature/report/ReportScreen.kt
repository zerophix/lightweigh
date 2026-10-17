package com.xcwk.scalehub.feature.report

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkDivider
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.components.QkRiskRow
import com.xcwk.scalehub.ui.components.QkScoreRing
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * report — 四件套之 Screen：体测报告（评分/建议/体型/风险/指标全表/控制建议）。
 * tone 上色：0=弱化 1=主色绿 2=注意橙 3=超标红。
 *
 * 版式按 v2 原型「身体报告」屏，并按真实数据口径做了两处修正：
 *  - **头部卡恒在**：原型以评分卡开篇，但手动记录（无阻抗）没有评分。无评分时
 *    头部卡退化为「日期 + 大数字体重 + 增减」——哪次测量、多少公斤必须可见，
 *    不能因为缺评分就让页面失去测量身份。
 *  - **指标表只放体测指标**：BMI / 肥胖度 / 体脂率等十八项是本次测量结果；
 *    「身高 / 实际年龄」是 BodyAnalysis 无条件追加的档案回显行（"已录入"），
 *    属于分析依据而非身体指标，移到指标卡下方的「分析依据」脚注，不混排。
 *    体重在指标表里没有行（BodyAnalysis 不产出），由头部卡的大数字承担。
 */
@Composable
fun ReportScreen(
    state: ReportUiState,
    onAskAi: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    // 档案回显行（身高/实际年龄）不进指标列表，攒成脚注
    val profileEcho = listOfNotNull(
        state.metrics.firstOrNull { it.name == "身高" }
            ?.takeIf { it.valueText != "--" }
            ?.let { "身高 ${it.valueText} ${it.unit}" },
        state.metrics.firstOrNull { it.name == "实际年龄" }
            ?.takeIf { it.valueText != "--" }
            ?.let { "年龄 ${it.valueText} 岁" },
    ).joinToString(" · ")
    val bodyMetrics = state.metrics.filter { it.name != "身高" && it.name != "实际年龄" }

    if (!state.loading && !state.found) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = QkSpacing.Page),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QkBackButton(onClick = onBack)
                QkPageTitle(
                    title = "身体报告",
                    modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
                )
            }
            QkCard { QkEmptyHint("找不到这次测量记录") }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(
                title = "身体报告",
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
        }

        // 头部卡：有评分走原型评分卡；无评分（手动记录）退化为体重主卡
        QkCard {
            Text(
                if (state.score != null) "${state.timeText} · 综合评估" else state.timeText,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
            if (state.score != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QkScoreRing(score = state.score, color = toneColor(state.scoreTone()))
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 17.dp),
                    ) {
                        Text(
                            scoreTitle(state.score),
                            style = MaterialTheme.typography.titleMedium,
                            color = QkColor.Fg,
                        )
                        state.advice?.let {
                            Text(
                                it,
                                modifier = Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = QkColor.Muted,
                            )
                        }
                        // 较上次评分 pill（原型评分卡口径）：0 分不显示
                        val delta = state.scoreDelta
                        if (delta != null && delta != 0) {
                            QkPill(
                                "较上次 ${if (delta > 0) "+" else ""}$delta 分",
                                if (delta > 0) QkPillTone.Green else QkPillTone.Amber,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    state.weightText,
                    style = if (state.score != null) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.displayMedium
                    },
                    color = QkColor.Fg,
                )
                Text(
                    " 公斤",
                    modifier = Modifier.padding(bottom = if (state.score != null) 3.dp else 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
            Text(
                state.deltaLine,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }

        // 关键身体指标：原型 section-head + list-rows（值 + 状态胶囊在行尾）
        QkSectionHead(
            title = "关键身体指标",
            caption = "本次测量",
            modifier = Modifier.padding(top = QkSpacing.Lg),
        )
        QkCard(modifier = Modifier.padding(top = 2.dp)) {
            if (bodyMetrics.isEmpty()) {
                QkEmptyHint("这次测量还没有可分析的指标")
            } else {
                bodyMetrics.forEachIndexed { index, m ->
                    if (index > 0) QkDivider()
                    QkListRow(
                        title = m.name,
                        subtitle = metricSubtitle(m, state.bodyShape),
                        icon = metricIcon(m.name),
                        tone = metricTone(m.tone),
                        glyphSize = 34.dp,
                        showDivider = false,
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${m.valueText} ${m.unit}".trim(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = toneColor(m.tone),
                                )
                                if (m.status != "--") {
                                    QkPill(
                                        m.status,
                                        pillTone(m.tone),
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
        if (profileEcho.isNotBlank()) {
            Text(
                "分析依据：$profileEcho",
                modifier = Modifier.padding(top = 6.dp, start = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Hint,
            )
        }

        state.missingText?.let { text ->
            QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) { QkEmptyHint(text) }
        }

        // 风险评估（原型 risk 行：状态点 + 名称 + 档位胶囊）
        if (state.risks.isNotEmpty()) {
            QkSectionHead(title = "风险评估", modifier = Modifier.padding(top = QkSpacing.Lg))
            QkCard(modifier = Modifier.padding(top = 2.dp)) {
                state.risks.forEachIndexed { index, risk ->
                    if (index > 0) QkDivider()
                    QkRiskRow(
                        name = risk.name,
                        levelText = risk.levelText,
                        tone = pillTone(risk.tone),
                        dotColor = dotColor(risk.tone),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        // 控制建议（fatControl/muscleControl/standardWeight）
        if (state.fatControlText != null || state.muscleControlText != null || state.standardWeightText != null) {
            QkSectionHead(title = "控制建议", modifier = Modifier.padding(top = QkSpacing.Lg))
            QkCard(modifier = Modifier.padding(top = 2.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    ControlCell("脂肪控制", state.fatControlText, Modifier.weight(1f))
                    ControlCell("肌肉控制", state.muscleControlText, Modifier.weight(1f))
                    ControlCell("标准体重", state.standardWeightText?.let { "$it 公斤" }, Modifier.weight(1f))
                }
            }
        }

        Text(
            state.disclaimer,
            modifier = Modifier.padding(top = QkSpacing.Lg),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
        QkButton(
            text = if (state.aiEnabled) "AI 解读报告" else "AI 解读（需手机号登录）",
            enabled = state.aiEnabled,
            style = QkButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            onClick = onAskAi,
        )
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun ControlCell(title: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        Text(
            value ?: "--",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            color = QkColor.Fg,
        )
    }
}

/** 评分的状态标题（原型 86 分档口径）：分高字好，分低提醒。 */
private fun scoreTitle(score: Int): String = when {
    score >= 86 -> "身体状态良好"
    score >= 70 -> "整体尚可，仍有提升空间"
    else -> "需要关注身体信号"
}

/**
 * 指标行副标题：增减delta + BMI 行的体型（BodyAnalysis 的 bodyShape 只对 BMI 有意义）。
 * 两项都没有就返 null，行内不留空行。
 */
private fun metricSubtitle(m: ReportMetricUi, bodyShape: String?): String? {
    val parts = buildList {
        m.delta?.let { add("较上次 $it") }
        if (m.name == "BMI" && bodyShape != null) add("体型 $bodyShape")
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

/** 指标名 → 图标（原型每行一个 glyph；映射不到就给通用图标，不空行）。 */
private fun metricIcon(name: String): ImageVector = when {
    name.contains("BMI", ignoreCase = true) -> QkIcons.Bmi
    name.contains("肥胖度") -> QkIcons.BodyFat
    name.contains("体脂") -> QkIcons.BodyFat
    name.contains("体重") -> QkIcons.Weight
    name.contains("去脂") -> QkIcons.Weight
    name.contains("骨骼肌") -> QkIcons.Rope
    name.contains("肌肉") -> QkIcons.Rope
    name.contains("骨") -> QkIcons.Waist
    name.contains("内脏") -> QkIcons.BloodPressure
    name.contains("水分") || name.contains("水含量") -> QkIcons.Water
    name.contains("蛋白") -> QkIcons.Protein
    name.contains("代谢") -> QkIcons.Exercise
    name.contains("年龄") -> QkIcons.Age
    else -> QkIcons.More
}

private fun metricTone(tone: Int): QkGlyphTone = when (tone) {
    2 -> QkGlyphTone.Orange
    3 -> QkGlyphTone.Gray
    else -> QkGlyphTone.Green
}

/** 评分档位着色：86+ 绿 / 70-85 橙 / <70 红（展示口径，规则分值本身随 BodyMetrics）。 */
private fun ReportUiState.scoreTone(): Int = score?.let { if (it >= 86) 1 else if (it >= 70) 2 else 3 } ?: 0

@Composable
private fun toneColor(tone: Int): Color = when (tone) {
    1 -> QkColor.Primary
    2 -> QkColor.Accent
    3 -> QkColor.Danger
    else -> QkColor.Muted
}

/** tone → 胶囊色调（0 灰 / 1 绿 / 2 琥珀 / 3 红）。 */
private fun pillTone(tone: Int): QkPillTone = when (tone) {
    1 -> QkPillTone.Green
    2 -> QkPillTone.Amber
    3 -> QkPillTone.Red
    else -> QkPillTone.Gray
}

/** tone → 状态点颜色（原型 risk-dot：绿 / 橙 / 红）。 */
private fun dotColor(tone: Int): Color = when (tone) {
    1 -> QkColor.Primary
    2 -> QkColor.Accent
    3 -> QkColor.Danger
    else -> QkColor.Muted
}

@Preview(showBackground = true)
@Composable
private fun ReportScreenPreview() {
    QkTheme {
        ReportScreen(
            state = ReportUiState(
                loading = false,
                found = true,
                timeText = "2026/10/15 08:00",
                weightText = "72.0",
                deltaLine = "比上次  体重 -1.5 公斤  体脂率 -0.8 %",
                score = 87,
                advice = "身体数据处于标准区间，继续保持当前饮食与运动习惯。",
                bodyShape = "苗条型",
                risks = listOf(
                    ReportRiskUi("肥胖症", "低风险", 1),
                    ReportRiskUi("脂肪肝", "中风险", 2),
                    ReportRiskUi("高血压", "低风险", 1),
                ),
                bmiText = "23.5",
                bmiLabel = "标准",
                metrics = listOf(
                    ReportMetricUi("体重", "72.0", "公斤", "标准", 1, "-1.5"),
                    ReportMetricUi("BMI", "23.5", "", "标准", 1, "+0.3"),
                    ReportMetricUi("体脂率", "19.1", "%", "标准", 1, null),
                    ReportMetricUi("肌肉率", "33.0", "%", "不足", 2, null),
                    ReportMetricUi("内脏脂肪", "7", "级", "标准", 1, null),
                    ReportMetricUi("身高", "175", "cm", "已录入", 1, null),
                    ReportMetricUi("实际年龄", "30", "岁", "已录入", 1, null),
                ),
                fatControlText = "-1.2",
                muscleControlText = "+0.8",
                standardWeightText = "68.0",
                disclaimer = "体重、体脂率为实测记录；其余指标基于公认公式估算，仅供参考，不构成医疗建议",
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportScreenNoScorePreview() {
    QkTheme {
        ReportScreen(
            state = ReportUiState(
                loading = false,
                found = true,
                timeText = "2026/10/16 21:07",
                weightText = "123.0",
                deltaLine = "比上次  体重 +54.5 公斤  体脂率 -- %",
                score = null,
                advice = null,
                bodyShape = null,
                risks = listOf(
                    ReportRiskUi("肥胖症", "高风险", 3),
                    ReportRiskUi("高血压", "中风险", 2),
                ),
                bmiText = "40.2",
                bmiLabel = "重度肥胖",
                metrics = listOf(
                    ReportMetricUi("BMI", "40.2", "", "重度肥胖", 3, "+17.8"),
                    ReportMetricUi("肥胖度", "85.0", "", "肥胖", 2, "+82.0"),
                    ReportMetricUi("身高", "175", "cm", "已录入", 1, null),
                    ReportMetricUi("实际年龄", "30", "岁", "已录入", 1, null),
                ),
                disclaimer = "体重、体脂率为实测记录；其余指标基于公认公式估算，仅供参考，不构成医疗建议",
            ),
        )
    }
}
