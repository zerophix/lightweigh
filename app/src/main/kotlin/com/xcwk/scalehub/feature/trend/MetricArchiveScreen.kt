package com.xcwk.scalehub.feature.trend

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.feature.mine.ArchiveGoalProgress
import com.xcwk.scalehub.feature.mine.ArchiveWaistCard
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme
import java.util.Locale

/** 趋势页「身体指标与档案」入口行文案（T-055）。 */
const val METRIC_ARCHIVE_ENTRY_TITLE = "身体指标与档案"

/** 入口行副标题：说清点进去能看到什么。 */
const val METRIC_ARCHIVE_ENTRY_HINT = "全部身体指标、身体围度与目标进度"

/** 指标列表页标题（与 MetricListScreen 同口径）。 */
const val METRIC_ARCHIVE_TITLE = "全部身体指标"

/** 无读数时的占位（与 MetricListScreen 的空态口径一致：不编造数值）。 */
const val METRIC_ARCHIVE_NO_VALUE = "--"

/**
 * 档案侧栏数据（围度 / 目标进度），来源是首页目标查询（与首页进度卡同一事实源）。
 *
 * 任一字段为 null 时对应展示回落：围度显示「--」，目标进度不渲染该卡
 * （[ArchiveGoalProgress] 自带「无目标即隐藏」的口径，不在宿主层伪造目标值）。
 */
data class MetricArchiveUi(
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val currentWeight: Double? = null,
    val targetWeight: Double? = null,
)

/**
 * 由趋势块派生指标读数（纯函数，JVM 单测覆盖）。
 *
 * 口径：
 * - 体重取体重块最后一个点（最新一次），带该块的区间变化与点序；
 * - 其余指标当前没有运行时读数来源，一律给 [METRIC_ARCHIVE_NO_VALUE]——
 *   宁可显示「--」也不拿别的指标冒充。
 * - 顺序即 [METRIC_SPECS] 顺序（原型 v-list 的八项）。
 */
fun metricArchiveReadings(state: TrendUiState): List<MetricReading> =
    METRIC_SPECS.map { spec ->
        if (spec.key != MetricKey.Weight) {
            MetricReading(spec = spec, value = METRIC_ARCHIVE_NO_VALUE)
        } else {
            val block = state.weight
            val last = block?.points?.lastOrNull()
            MetricReading(
                spec = spec,
                value = last?.let { "%.1f".format(Locale.US, it.value) } ?: METRIC_ARCHIVE_NO_VALUE,
                deltaText = block?.changeValue.orEmpty(),
                trendPoints = block?.points.orEmpty(),
            )
        }
    }

/**
 * 解析当前打开的指标详情：`null` / 未知 key 一律回列表。
 *
 * 纯函数，JVM 单测覆盖——「点了不存在的指标」不允许凭空造一个详情页。
 */
fun metricArchiveOpenReading(key: String?, readings: List<MetricReading>): MetricReading? =
    key?.let { k -> readings.firstOrNull { it.spec.key.name == k } }

/**
 * metric-detail 宿主页（T-055）：趋势页 →「身体指标与档案」→ 指标列表 → 单项详情。
 *
 * 承接三块既有呈现式组件：
 * - [MetricDetailScreen]：内脏脂肪等八项指标的详情（原型 `17-metric-details.html`）；
 * - [ArchiveWaistCard]：身体围度（腰围 / 臀围）；
 * - [ArchiveGoalProgress]：目标进度（当前 / 目标 / 差距）。
 *
 * 列表与详情是同一页内的两级：详情返回回列表，列表返回回趋势页——不新开导航栈。
 */
@Composable
fun MetricArchiveScreen(
    state: TrendUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val readings = metricArchiveReadings(state)
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    val openReading = metricArchiveOpenReading(openKey, readings)

    if (openReading != null) {
        MetricDetailScreen(
            reading = openReading,
            modifier = modifier,
            onBack = { openKey = null },
            onSeeList = { openKey = null },
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(modifier = Modifier.height(QkSpacing.Md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = onBack)
            Spacer(modifier = Modifier.width(QkSpacing.Sm))
            Text(
                text = METRIC_ARCHIVE_TITLE,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        QkSectionHead(title = "身体指标", modifier = Modifier.padding(top = QkSpacing.Lg))
        QkCard(modifier = Modifier.padding(top = 2.dp)) {
            readings.forEachIndexed { index, reading ->
                QkListRow(
                    title = reading.spec.title,
                    subtitle = "${reading.value} ${reading.spec.unit}".trimEnd(),
                    showDivider = index != readings.lastIndex,
                    onClick = { openKey = reading.spec.key.name },
                    trailing = {
                        Text(
                            text = TrendText.Chevron,
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.Hint,
                        )
                    },
                )
            }
        }

        ArchiveWaistCard(
            waistCm = state.archive?.waistCm,
            hipCm = state.archive?.hipCm,
        )
        ArchiveGoalProgress(
            currentWeight = state.archive?.currentWeight,
            targetWeight = state.archive?.targetWeight,
        )

        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}

@Preview(name = "身体指标 · 列表", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MetricArchiveScreenPreviewDefault() {
    QkTheme {
        MetricArchiveScreen(
            state = TrendUiState(
                loading = false,
                weight = TrendBlockUi(
                    points = listOf(TrendPointUi(0, 62.4), TrendPointUi(1, 62.1)),
                    caption = "", highValue = "62.4", highDay = "10/20",
                    lowValue = "62.1", lowDay = "10/21",
                    changeValue = "-0.3公斤", emptyText = null,
                ),
                archive = MetricArchiveUi(
                    waistCm = 78.0, hipCm = 92.0,
                    currentWeight = 62.1, targetWeight = 58.0,
                ),
            ),
        )
    }
}
