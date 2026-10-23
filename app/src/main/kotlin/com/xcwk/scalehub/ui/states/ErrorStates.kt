package com.xcwk.scalehub.ui.states

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * 通用错误与边界状态（T-097 · Wave 0）
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/15-states-errors.html`（16 个 view）
 * - 验收：AC-H4「PartialError 不整页白屏」、AC-HT1「Empty 历史无假数据行」、
 *   AC-H1「EmptyNoData 无「较上次」数值」
 * - 模块：主 PRD `M18 通用状态与组件`
 *
 * ## 范围（关键约束）
 * 本任务**只新建文件**，不改动任何 feature 屏。既有 HistoryScreen / TrendScreen /
 * MeasureScreen 等各自持有的空错实现属后续迁移切片，与 Wave 2 的 T-072/076/077/083/086
 * 改同一批文件，并行改会冲突（见 DOC-并发开发约定「同模块串行」）。
 * 因此这里提供**通用状态原语**，新代码可直接使用，旧代码迁移另开任务。
 *
 * ## 与原型 16 个 view 的关系
 * 原型该文件多为**具体业务屏**而非状态页，故以 [PROTOTYPE_STATE_MAP] 显式分类：
 * 归入 [QkStateKind.Generic] 的由本文件实现，其余标注为业务屏（明确不适用本任务）。
 * 分类表可被单测校验，见 `tests/unit/test_error_states_map.py` 的镜像断言。
 */

/** 状态种类：Generic 表示由本文件提供通用实现，SpecificScreen 表示属业务屏、不在本任务范围。 */
enum class QkStateKind { Generic, SpecificScreen }

/** 通用状态的细分类型。 */
enum class QkGenericState { Empty, LoadError, PartialError, Offline, FullScreenLoading, InlineLoading }

/** 原型单个 view 的归属记录。 */
data class QkPrototypeState(
    val viewId: String,
    val kind: QkStateKind,
    val state: QkGenericState?,
    val note: String,
)

/**
 * 原型 `15-states-errors.html` 全部 16 个 view 的归属分类。
 *
 * `state == null` 即「明确标注不适用本任务」——该 view 是业务屏或导航枢纽。
 * 修改本表必须同步 `tests/unit/test_error_states_map.py`（单测校验 16 个 view 一个不少）。
 */
val PROTOTYPE_STATE_MAP: List<QkPrototypeState> = listOf(
    QkPrototypeState("v-hub", QkStateKind.SpecificScreen, null, "导航枢纽，非状态页"),
    QkPrototypeState("v-rec-edit", QkStateKind.SpecificScreen, null, "历史记录编辑屏，数值不可改属业务规则"),
    QkPrototypeState("v-export", QkStateKind.SpecificScreen, null, "导出结果屏；其失败子块可复用 [QkLoadErrorState]"),
    QkPrototypeState("v-score", QkStateKind.SpecificScreen, null, "身体评分说明屏"),
    QkPrototypeState("v-anomaly", QkStateKind.SpecificScreen, null, "趋势异常/缺测的图表语义，属图表能力而非状态页"),
    QkPrototypeState("v-multimetric", QkStateKind.SpecificScreen, null, "多指标趋势对比屏"),
    QkPrototypeState("v-pair-timeout", QkStateKind.Generic, QkGenericState.LoadError, "配对超时 30 秒未建立连接 + 重新配对"),
    QkPrototypeState("v-report-stale", QkStateKind.SpecificScreen, null, "AI 报告过期提示属业务屏，保留旧报告不覆盖"),
    QkPrototypeState("v-sync-fail", QkStateKind.Generic, QkGenericState.PartialError, "1 条失败其余 127 条已同步 — 局部失败不清空，对应 AC-H4"),
    QkPrototypeState("v-fb-fail", QkStateKind.Generic, QkGenericState.LoadError, "反馈提交失败且填写内容已保留 + 重新提交"),
    QkPrototypeState("v-connect-guide", QkStateKind.SpecificScreen, null, "连接指南步骤屏"),
    QkPrototypeState("v-troubleshoot", QkStateKind.SpecificScreen, null, "故障排查选择屏"),
    QkPrototypeState("v-privacy-qa", QkStateKind.SpecificScreen, null, "隐私答疑屏"),
    QkPrototypeState("v-notif-landing", QkStateKind.SpecificScreen, null, "通知落地页，属首页变体"),
    QkPrototypeState("v-fullscreen-load", QkStateKind.Generic, QkGenericState.FullScreenLoading, "全屏加载 128/128 进度"),
    QkPrototypeState("v-longpress", QkStateKind.SpecificScreen, null, "长按菜单，属 T-099 QkLongPressMenu"),
)

/** 按原型 view id 查归属；未知 view 返回 null（调用方须显式处理，不得默认当作状态页）。 */
fun stateKindOf(viewId: String): QkPrototypeState? = PROTOTYPE_STATE_MAP.find { it.viewId == viewId }

// ---------------------------------------------------------------------------
// 通用状态实现
//
// 硬性约束（GOV-PTASK / GC-prototype-driven-dev）：
// 一律使用 QkColor / QkRadius / QkSpacing token，禁止 Color(0x...) 与裸 dp。
// 空态禁止渲染伪造数据（AC-H1、AC-HT1）；局部错误禁止整页白屏（AC-H4）。
// ---------------------------------------------------------------------------

/**
 * 空态：无数据时居中图标 + 标题 + 说明 + 可选主行动。
 *
 * AC 约束：不得出现伪造数值或「较上次数值」（AC-H1、AC-HT1）——
 * 本组件只接收调用方给定文案，不自行生成任何测量数字。
 */
@Composable
fun QkEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = QkIcons.History,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = QkSpacing.Xl, vertical = QkSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QkIconBubble(icon = icon, tone = QkGlyphTone.Gray, size = QkSpacing.Xl)
        Spacer(Modifier.height(QkSpacing.Lg))
        Text(
            text = title,
            color = QkColor.Fg,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (!description.isNullOrEmpty()) {
            Spacer(Modifier.height(QkSpacing.Sm))
            Text(
                text = description,
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(QkSpacing.Xl))
            QkButton(text = actionText, onClick = onAction)
        }
    }
}

/**
 * 加载/提交失败态：整块错误 + 重试（原型 v-pair-timeout、v-fb-fail 的版式）。
 *
 * 与 [QkPartialErrorBar] 的区别：本组件表示「这一块整体失败」，
 * [QkPartialErrorBar] 表示「一部分失败、其余仍可用」。
 */
@Composable
fun QkLoadErrorState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Outlined.ErrorOutline,
    retryText: String = "重试",
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = QkSpacing.Xl, vertical = QkSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QkIconBubble(icon = icon, tone = QkGlyphTone.Orange, size = QkSpacing.Xl)
        Spacer(Modifier.height(QkSpacing.Lg))
        Text(
            text = title,
            color = QkColor.Fg,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (!description.isNullOrEmpty()) {
            Spacer(Modifier.height(QkSpacing.Sm))
            Text(
                text = description,
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(QkSpacing.Xl))
            QkButton(text = retryText, style = QkButtonStyle.Secondary, onClick = onRetry)
        }
    }
}

/**
 * 局部错误条：**AC-H4 的落地组件** —— 部分数据失败时只在局部提示并可重试，
 * 不清空、不整页白屏（原型 v-sync-fail：1 条失败、其余 127 条照常展示）。
 *
 * 必须叠加在已有内容之上使用，禁止用它替换整块内容。
 */
@Composable
fun QkPartialErrorBar(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryText: String = "重试",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(QkColor.DangerSoft, RoundedCornerShape(QkRadius.Field))
            .padding(horizontal = QkSpacing.Md, vertical = QkSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkIcon(icon = Icons.Outlined.ErrorOutline, tint = QkColor.Danger, size = QkSpacing.Lg)
        Spacer(Modifier.width(QkSpacing.Sm))
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = QkColor.Fg,
            style = MaterialTheme.typography.bodySmall,
        )
        if (onRetry != null) {
            Spacer(Modifier.width(QkSpacing.Sm))
            Text(
                text = retryText,
                color = QkColor.Primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .background(QkColor.MintSoft, RoundedCornerShape(QkRadius.SegmentItem))
                    .padding(horizontal = QkSpacing.Md, vertical = QkSpacing.Xs),
            )
        }
    }
}

/** 离线态：可读本地数据但云端不可达，不阻断主路径（AC-H5 / PRD 弱网与离线）。 */
@Composable
fun QkOfflineState(
    modifier: Modifier = Modifier,
    title: String = "当前离线",
    description: String = "已显示本地数据，联网后自动同步。",
    onRetry: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(QkColor.MintSoft, RoundedCornerShape(QkRadius.Field))
            .padding(horizontal = QkSpacing.Md, vertical = QkSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkIcon(icon = Icons.Outlined.CloudOff, tint = QkColor.Primary, size = QkSpacing.Lg)
        Spacer(Modifier.width(QkSpacing.Sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = QkColor.Fg, style = MaterialTheme.typography.labelLarge)
            Text(text = description, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (onRetry != null) {
            QkIcon(
                icon = Icons.Outlined.Refresh,
                tint = QkColor.Primary,
                size = QkSpacing.Lg,
                modifier = Modifier
                    .background(QkColor.Card, RoundedCornerShape(QkRadius.SegmentItem))
                    .padding(QkSpacing.Xs),
            )
        }
    }
}

/**
 * 全屏加载（原型 v-fullscreen-load）：居中进度 + 文案，可选进度数。
 *
 * 首次登录同步全部记录等长任务使用；普通列表加载请用 [QkInlineLoading]。
 */
@Composable
fun QkFullScreenLoading(
    label: String,
    modifier: Modifier = Modifier,
    progressLabel: String? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().background(QkColor.Bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = QkColor.Primary,
                modifier = Modifier.size(QkSpacing.Xl),
                strokeWidth = QkSpacing.Xs,
            )
            Spacer(Modifier.height(QkSpacing.Lg))
            Text(text = label, color = QkColor.Fg, style = MaterialTheme.typography.bodyMedium)
            if (progressLabel != null) {
                Spacer(Modifier.height(QkSpacing.Sm))
                Text(text = progressLabel, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 行内/局部加载：列表或卡片内占位，不接管整屏。 */
@Composable
fun QkInlineLoading(
    label: String = "加载中…",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = QkSpacing.Lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = QkColor.Primary,
            modifier = Modifier.size(QkSpacing.Md),
            strokeWidth = QkSpacing.Xs,
        )
        Spacer(Modifier.width(QkSpacing.Sm))
        Text(text = label, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
    }
}
