@file:OptIn(ExperimentalMaterial3Api::class)

package com.xcwk.scalehub.feature.trend

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.application.query.TrendRange
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---- 趋势扩展（T-048，DEC-043-E）----
//
// 原型 05-history-trend.html · v-trend 逐字文案（本文件为唯一真源，页面只引用常量）：
//   看见每一步改变 / 林悦 · 09/21 — 10/21 / 周 月 年 / 体重 ▾ / 时间范围 / 近30天 /
//   全部测量记录 / 选择指标 / 体重 体脂率 BMI 肌肉量 / 近 7 天 / 近 30 天 / 近一年 /
//   自定义… / 取消
//
// 口径：
//  - 周/月/年 = 聚合粒度（AC-T048 when），落成趋势范围的三档粗调：周→近 7 天、
//    月→近 30 天、年→全部（应用层 TrendRange 现有最大档，近一年无独立档，见任务汇报偏离）；
//  - 扩展指标（骨骼肌/水分/基础代谢/肌肉/蛋白质）来自 DEC-043-E 与原型
//    17-metric-details.html 的指标清单；本任务只到呈现层，序列数据仍由 TrendQuery 的
//    WEIGHT/FAT 两路提供，其余指标无数据时给空态，不伪造曲线。

/** v-trend 的单句文案（改一个字即触发单测红灯）。 */
object TrendText {
    const val HeroTitle = "看见每一步改变"
    const val MemberFallback = "林悦"

    /** 聚合粒度（原型 .seg 的三档）。 */
    const val GranularityWeek = "周"
    const val GranularityMonth = "月"
    const val GranularityYear = "年"

    /** 卡头指标选择器后缀（原型「体重 ▾」）。 */
    const val MetricCaret = " ▾"

    /** 二级行与弹层标题（原型同一句「时间范围」两处复用）。 */
    const val RangeRowTitle = "时间范围"
    const val RangeSheetTitle = "时间范围"
    const val MetricSheetTitle = "选择指标"
    const val Cancel = "取消"
    const val AllRecords = "全部测量记录"

    /** 时间范围弹层的四个选项（原型逐字，含空格与省略号）。 */
    const val Range7 = "近 7 天"
    const val Range30 = "近 30 天"
    const val RangeYear = "近一年"
    const val RangeCustom = "自定义…"

    /** 行尾/卡头使用的紧凑范围标签（原型「近30天」不带空格）。 */
    const val Today = "今天"
    const val Recent7 = "近7天"
    const val Recent30 = "近30天"
    const val All = "全部"

    /** 行尾箭头（原型 .chev）。 */
    const val Chevron = "›"

    /** 加载中占位（沿用既有页面文案）。 */
    const val Loading = "正在加载…"
}

/**
 * 聚合粒度 —— 原型 v-trend 的「周 / 月 / 年」三段。
 *
 * 与 [TrendRange] 的映射放在纯函数里，Screen 只做转发，单测可脱离 Compose 断言。
 */
enum class TrendAggregation(val label: String) {
    WEEK(TrendText.GranularityWeek),
    MONTH(TrendText.GranularityMonth),
    YEAR(TrendText.GranularityYear),
}

/** 当前范围落在哪一档聚合粒度上（今天视作周档）。 */
fun trendAggregationFor(range: TrendRange): TrendAggregation = when (range) {
    TrendRange.TODAY, TrendRange.LAST_7 -> TrendAggregation.WEEK
    TrendRange.LAST_30 -> TrendAggregation.MONTH
    TrendRange.ALL -> TrendAggregation.YEAR
}

/** 选择聚合粒度后切换到哪个范围（应用层无「近一年」独立档，年档取全部）。 */
fun trendRangeFor(aggregation: TrendAggregation): TrendRange = when (aggregation) {
    TrendAggregation.WEEK -> TrendRange.LAST_7
    TrendAggregation.MONTH -> TrendRange.LAST_30
    TrendAggregation.YEAR -> TrendRange.ALL
}

/**
 * 一个可展示的身体成分指标。
 *
 * [hasSeries] 表示 TrendQuery 当前是否有该指标的序列（体重/体脂两路）；
 * 其余扩展指标只有文案与空态，等应用层补齐序列后自动可画。
 */
data class TrendMetric(
    val id: String,
    val name: String,
    val unit: String,
    val color: Color,
    val hasSeries: Boolean = false,
)

/** 指标清单：原型 v-trend 的 4 个（体重/体脂率/BMI/肌肉量）+ DEC-043-E 的扩展指标。 */
val TREND_METRICS = listOf(
    TrendMetric("weight", "体重", "kg", QkColor.Primary, hasSeries = true),
    TrendMetric("fat", "体脂率", "%", QkColor.Blue, hasSeries = true),
    TrendMetric("bmi", "BMI", "", QkColor.BmiNormal),
    TrendMetric("muscle", "肌肉量", "kg", QkColor.Purple),
    TrendMetric("skeletal", "骨骼肌", "%", QkColor.Accent),
    TrendMetric("water", "水分", "%", QkColor.BmiThin),
    TrendMetric("bmr", "基础代谢", "kcal", QkColor.AmberInk),
    TrendMetric("protein", "蛋白质", "%", QkColor.Danger),
)

/** 按 id 取指标；未知 id 回落到体重（选择器不会产生未知 id）。 */
fun trendMetricOf(id: String): TrendMetric =
    TREND_METRICS.firstOrNull { it.id == id } ?: TREND_METRICS.first()

/** 无序列指标的空态文案（沿用体重/体脂既有句式「这段时间还没有…数据」）。 */
fun trendMetricEmptyText(metric: TrendMetric): String = "这段时间还没有${metric.name}数据"

/** 范围在行尾/卡头显示的紧凑标签（原型「近30天」口径）。 */
fun trendRangeLabel(range: TrendRange): String = when (range) {
    TrendRange.TODAY -> TrendText.Today
    TrendRange.LAST_7 -> TrendText.Recent7
    TrendRange.LAST_30 -> TrendText.Recent30
    TrendRange.ALL -> TrendText.All
}

/** 范围在时间弹层里显示的选项文案（原型「近 30 天」带空格口径）。 */
fun trendRangeSheetLabel(range: TrendRange): String = when (range) {
    TrendRange.TODAY, TrendRange.LAST_7 -> TrendText.Range7
    TrendRange.LAST_30 -> TrendText.Range30
    TrendRange.ALL -> TrendText.RangeYear
}

/**
 * 首屏副标题：`林悦 · 09/21 — 10/21`（原型 v-trend .hero 逐字结构）。
 *
 * 成员名来自调用方（默认沿用原型示例「林悦」）；日期取当前指标首末两点，
 * 无数据时只显示成员名，不编造区间。
 */
fun trendHeroSubtitle(
    memberName: String,
    points: List<TrendPointUi>,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (points.isEmpty()) return memberName
    val fmt = DateTimeFormatter.ofPattern("MM/dd").withZone(zone)
    val start = fmt.format(Instant.ofEpochMilli(points.first().time))
    val end = fmt.format(Instant.ofEpochMilli(points.last().time))
    return "$memberName · $start — $end"
}

/**
 * 指标选择弹层（原型 #sheet-metric「选择指标」）。
 */
@Composable
fun TrendMetricSheet(
    selectedId: String,
    onSelect: (TrendMetric) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = QkColor.Card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QkSpacing.Page)
                .padding(bottom = QkSpacing.Xl),
        ) {
            TrendSheetHead(TrendText.MetricSheetTitle, onDismiss)
            TREND_METRICS.forEach { metric ->
                TrendPickRow(
                    label = metric.name,
                    selected = metric.id == selectedId,
                    onClick = { onSelect(metric) },
                )
            }
        }
    }
}

/**
 * 时间范围弹层（原型 #sheet-date「时间范围」）。
 *
 * 近 7 天 / 近 30 天 / 近一年 直接切到对应 [TrendRange]；自定义…沿用原型
 *「演示」口径，只记录选择、不伪造区间查询。
 */
@Composable
fun TrendRangeSheet(
    selectedLabel: String,
    onSelectRange: (TrendRange) -> Unit,
    onSelectCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = QkColor.Card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QkSpacing.Page)
                .padding(bottom = QkSpacing.Xl),
        ) {
            TrendSheetHead(TrendText.RangeSheetTitle, onDismiss)
            TrendPickRow(TrendText.Range7, selectedLabel == TrendText.Range7) {
                onSelectRange(TrendRange.LAST_7)
            }
            TrendPickRow(TrendText.Range30, selectedLabel == TrendText.Range30) {
                onSelectRange(TrendRange.LAST_30)
            }
            TrendPickRow(TrendText.RangeYear, selectedLabel == TrendText.RangeYear) {
                onSelectRange(TrendRange.ALL)
            }
            TrendPickRow(TrendText.RangeCustom, selectedLabel == TrendText.RangeCustom) {
                onSelectCustom()
            }
        }
    }
}

/** 弹层标题行：标题 + 右侧「取消」（原型 .shead）。 */
@Composable
private fun TrendSheetHead(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = QkColor.Fg,
        )
        Text(
            text = TrendText.Cancel,
            modifier = Modifier.clickable(onClick = onDismiss),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
    }
}

/** 单选项右侧圆点直径（原型 .radio 视觉尺寸）。 */
private val TrendPickDot = 20.dp

/** 单选项行：文案 + 右侧圆点（原型 .pick / .radio）。 */
@Composable
private fun TrendPickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    QkListRow(
        title = label,
        onClick = onClick,
        trailing = {
            Box(
                modifier = Modifier
                    .size(TrendPickDot)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 6.dp else 1.dp,
                        color = if (selected) QkColor.Primary else QkColor.FieldBorder,
                        shape = CircleShape,
                    ),
            )
        },
    )
}
