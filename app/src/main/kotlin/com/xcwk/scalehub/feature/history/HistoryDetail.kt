package com.xcwk.scalehub.feature.history

import androidx.compose.foundation.clickable
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
import com.xcwk.scalehub.ui.components.QkSectionHead
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
    /** 顶部信息条：成员 · 时间 · 同步状态（原型「林悦 · 10月21日 07:30 · 已同步」） */
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
    /** 同步状态为 false 时展示本地标记（AC-HT6 允许飞行模式读本地详情） */
    val synced: Boolean = true,
)

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

        Spacer(Modifier.height(QkSpacing.Sm))
        Text(
            text = detail.metaText + if (detail.synced) "" else " · 本地",
            color = QkColor.Muted,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(QkSpacing.Lg))
        QkCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(QkSpacing.CardPad)) {
                Text(
                    text = "体重",
                    color = QkColor.Muted,
                    style = MaterialTheme.typography.labelMedium,
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
                        Spacer(Modifier.width(QkSpacing.Md))
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
        QkListRow(title = "体脂率", subtitle = buildDelta(detail.fatRateValue, "%", detail.fatRateDelta))
        QkListRow(title = "肌肉量", subtitle = buildDelta(detail.muscleValue, "kg", detail.muscleDelta))
        QkListRow(title = "身体评分", subtitle = buildDelta(detail.scoreValue, detail.scoreSuffix, ""))

        Spacer(Modifier.height(QkSpacing.Lg))
        QkSectionHead("与上次对比")

        Spacer(Modifier.height(QkSpacing.CardGap))
        QkButton(
            text = "查看指标详情",
            style = QkButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenMetricDetail,
        )

        Spacer(Modifier.height(QkSpacing.Xl))
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
