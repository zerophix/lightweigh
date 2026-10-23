package com.xcwk.scalehub.feature.history

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.states.QkEmptyState
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * 测量详情页数据（一次快照，全部字段来自同一 [recordId]）。
 *
 * ## 与验收的关系
 * **AC-HT3「详情指标与报告同源（同 recordId）」** 由本类型结构保证：
 * 页面只接收**一个** [HistoryDetailUi]，其内部所有指标共用同一个 [recordId]，
 * 不存在"详情读一套、报告读另一套"的可能；调用方若传入不同源数据，
 * 层级上也已在 ViewModel 汇总为单条记录。
 *
 * @property recordId 该快照的唯一来源记录；编辑时须回传同一值（见 [RecordEditModel.recordId]）
 */
data class HistoryDetailUi(
    val recordId: Long,
    /**
     * 成员与时间，不含同步词。
     * 同步词由 [detailMetaLine] 追加，避免调用方和组件各写一遍「已同步」。
     */
    val metaText: String,
    val weightValue: String,
    val weightUnit: String = "kg",
    val weightDelta: String = "",
    val fatRateValue: String,
    val fatRateDelta: String = "",
    val muscleValue: String,
    val muscleDelta: String = "",
    val scoreValue: String,
    val scoreSuffix: String = "分",
    /** 同步状态为 false 时展示「本地」（AC-HT6：飞行模式仍可读已有详情） */
    val synced: Boolean = true,
)

/**
 * 详情顶栏文案。已同步写「已同步」，未同步写「本地」，不发网络请求。
 */
fun detailMetaLine(memberAndTime: String, synced: Boolean): String {
    val status = if (synced) "已同步" else "本地"
    return if (memberAndTime.isBlank()) status else "$memberAndTime · $status"
}

/**
 * 详情与报告是否同一条记录（AC-HT3：详情指标与报告同源，同 recordId）。
 * 任一侧非正数视为还没有记录，不能当成同源。
 */
fun detailMatchesReport(detailRecordId: Long, reportRecordId: Long): Boolean =
    detailRecordId > 0L && detailRecordId == reportRecordId

/**
 * 飞行模式下能否打开历史详情（AC-HT6）。
 * 只看本地是否已有快照；[networkAvailable] 不参与判定，断网不得挡已有记录。
 */
fun canOpenHistoryOffline(hasLocalSnapshot: Boolean, networkAvailable: Boolean): Boolean {
    // 联网与断网同一条件：有本地快照即可打开。把网络并进去会让飞行模式打不开详情。
    return hasLocalSnapshot || (networkAvailable && hasLocalSnapshot)
}

/**
 * 历史记录测量详情（T-081 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/05-history-trend.html` → `v-detail`
 * > 测量详情 · 编辑 | 林悦 · 10月21日 07:30 · 已同步 | 62.4 kg ↓0.3
 * > 体脂率 28.6 % ↓0.2 | 肌肉量 42.3 kg ↑0.1 | 身体评分 分 | 与上次对比 | 查看指标详情
 *
 * ## 验收
 * - **AC-HT3**：详情各项指标与报告同源（同 recordId）→ 见 [HistoryDetailUi] 结构说明
 * - **AC-HT6**：飞行模式下可打开已有历史详情 → 本组件**只渲染传入的快照**，
 *   不发起任何网络请求，离线可显示由数据层保证；[HistoryDetailUi.synced]=false
 *   时展示本地标记而非错误页
 *
 * ## 用法
 * 呈现式组件：状态由 `HistoryViewModel`（既有）汇总后传入，本文件不持有 ViewModel，
 * 也不新建四件套（任务范围仅为本 Screen，避免与 Wave 2 的 T-077 等抢文件）。
 */
@Composable
fun HistoryDetailScreen(
    detail: HistoryDetailUi,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onCompare: () -> Unit = {},
    onOpenMetricDetail: () -> Unit = {},
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
                text = "测量详情",
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "编辑",
                color = QkColor.Primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(end = QkSpacing.Sm)
                    .clickable(onClick = onEdit),
            )
        }

        Spacer(Modifier.height(QkSpacing.Lg))
        QkCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(QkSpacing.CardPad)) {
                Text(
                    text = detailMetaLine(detail.metaText, detail.synced),
                    color = QkColor.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(QkSpacing.Xs))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = detail.weightValue,
                        color = QkColor.Fg,
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Spacer(Modifier.width(QkSpacing.Xs))
                    Text(
                        text = detail.weightUnit,
                        color = QkColor.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = QkSpacing.Xs),
                    )
                    if (detail.weightDelta.isNotEmpty()) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = detail.weightDelta,
                            color = QkColor.Primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(bottom = QkSpacing.Xs),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(QkSpacing.CardGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.CardGap),
        ) {
            MetricStatCell(
                label = "体脂率",
                value = detail.fatRateValue,
                unit = "%",
                delta = detail.fatRateDelta,
                modifier = Modifier.weight(1f),
            )
            MetricStatCell(
                label = "肌肉量",
                value = detail.muscleValue,
                unit = "kg",
                delta = detail.muscleDelta,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(QkSpacing.CardGap))
        QkListRow(
            title = "身体评分",
            trailing = {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = detail.scoreValue,
                        color = QkColor.Primary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.width(QkSpacing.Xs))
                    Text(
                        text = detail.scoreSuffix,
                        color = QkColor.Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        )

        Spacer(Modifier.height(QkSpacing.Lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
        ) {
            QkButton(
                text = "与上次对比",
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = onCompare,
            )
            QkButton(
                text = "查看指标详情",
                style = QkButtonStyle.Primary,
                modifier = Modifier.weight(1f),
                onClick = onOpenMetricDetail,
            )
        }

        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

@Composable
private fun MetricStatCell(
    label: String,
    value: String,
    unit: String,
    delta: String,
    modifier: Modifier = Modifier,
) {
    QkCard(modifier = modifier) {
        Column(modifier = Modifier.padding(QkSpacing.CardPad)) {
            Text(
                text = label,
                color = QkColor.Muted,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(QkSpacing.Xs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = QkColor.Fg,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.width(QkSpacing.Xs))
                Text(
                    text = unit,
                    color = QkColor.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (delta.isNotEmpty()) {
                Spacer(Modifier.height(QkSpacing.Xs))
                Text(
                    text = delta,
                    color = QkColor.Primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** 「28.6 %（↓0.2）」式副标题；无差值时不带括号后缀。 */
internal fun buildDelta(value: String, unit: String, delta: String): String =
    if (delta.isEmpty()) "$value $unit" else "$value $unit（$delta）"

/**
 * 指标为空时的占位（空态复用 [QkEmptyState]，不渲染伪造数值 —— AC-HT1）。
 */
@Composable
fun HistoryDetailEmpty(modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(QkSpacing.Md))
        QkBackButton(onClick = onBack)
        QkEmptyState(
            title = "还没有测量记录",
            description = "连接体脂秤后开始第一次测量。",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
