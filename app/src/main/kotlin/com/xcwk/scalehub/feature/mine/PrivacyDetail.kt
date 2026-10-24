package com.xcwk.scalehub.feature.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.components.QkVSpace
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * mine — 隐私与数据管理详情页（原型07 v-privacy）。
 *
 * 静态文案逐字取自原型；四个条目行 + 删除确认弹层。删除确认为纯函数门控
 * （[canConfirmDelete]：确认按钮仅在弹层打开时可用），回调由调用方注入，
 * 组件不触数据库/网络（呈现式，DEC-061 口径）。
 */

// ---- 静态文案（逐字取自原型 07 v-privacy） ----

const val TITLE_PRIVACY_DETAIL = "隐私与数据管理"
const val ROW_AI_DATA = "AI 数据设置"
const val ROW_AI_DATA_CAPTION = "撤回授权 · 关闭个性化"
const val ROW_EXPORT_DATA = "导出健康数据"
const val ROW_EXPORT_DATA_CAPTION = "CSV · 加密链接 24h 有效"
const val ROW_SYNC = "数据同步"
const val ROW_SYNC_CAPTION = "端到端加密"
const val ROW_PERSONALIZATION = "个性化分析"
const val ROW_DELETE_ALL = "删除全部健康数据"
const val ROW_DELETE_ALL_CAPTION = "不可恢复"
const val HINT_DELETE_VS_CLOSURE = "删除、注销是不同操作，详见「数据删除与保留规则」。"
const val CONFIRM_DELETE_TITLE = "删除全部健康数据？"
const val CONFIRM_DELETE_BODY = "全部测量记录与报告将被删除，"
const val CONFIRM_DELETE_IRREVERSIBLE = "不可恢复"
const val CONFIRM_DELETE_ADVICE = "。建议先导出。"
const val ACTION_CANCEL_DELETE = "取消"
const val ACTION_CONFIRM_DELETE = "确认删除"

/** 隐私页四个条目行（顺序即原型顺序）。 */
val PRIVACY_ROWS = listOf(
    ROW_AI_DATA to ROW_AI_DATA_CAPTION,
    ROW_EXPORT_DATA to ROW_EXPORT_DATA_CAPTION,
    ROW_SYNC to ROW_SYNC_CAPTION,
    ROW_PERSONALIZATION to null,
)

/** 删除确认弹层正文拼接（三段原文 + 建议）。 */
fun deleteConfirmBody(): String =
    CONFIRM_DELETE_BODY + CONFIRM_DELETE_IRREVERSIBLE + CONFIRM_DELETE_ADVICE

/** 确认删除仅在弹层打开时可用（纯函数门控，单测钉死）。 */
fun canConfirmDelete(dialogOpen: Boolean): Boolean = dialogOpen

/**
 * 隐私详情页（无状态）：四条目 + 删除提示 + 删除入口 + 确认弹层。
 *
 * [deleteDialogOpen] / [onDeleteAll] / [onConfirmDelete] / [onCancelDelete]
 * 与条目行点击 [onRowClick] 全部由调用方持有；本组件零系统依赖。
 */
@Composable
fun PrivacyDetailScreen(
    deleteDialogOpen: Boolean,
    onRowClick: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(TITLE_PRIVACY_DETAIL, style = MaterialTheme.typography.titleLarge)
        QkVSpace(QkSpacing.Sm)
        QkCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                PRIVACY_ROWS.forEach { (title, caption) ->
                    QkListRow(
                        title = title,
                        subtitle = caption,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        QkVSpace(QkSpacing.Sm)
        Text(
            HINT_DELETE_VS_CLOSURE,
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            modifier = Modifier.fillMaxWidth(),
        )
        QkVSpace(QkSpacing.Md)
        QkSectionHead(ROW_DELETE_ALL)
        QkVSpace(QkSpacing.Xs)
        Text(
            ROW_DELETE_ALL_CAPTION,
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Danger,
        )
        QkVSpace(QkSpacing.Sm)
        QkButton(
            text = ROW_DELETE_ALL,
            style = QkButtonStyle.Ghost,
            onClick = onDeleteAll,
            modifier = Modifier.fillMaxWidth(),
        )

        if (deleteDialogOpen) {
            QkVSpace(QkSpacing.Md)
            QkCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(CONFIRM_DELETE_TITLE, style = MaterialTheme.typography.titleMedium)
                    QkVSpace(QkSpacing.Sm)
                    Text(
                        deleteConfirmBody(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = QkColor.Muted,
                    )
                    QkVSpace(QkSpacing.Md)
                    QkButton(
                        text = ACTION_CONFIRM_DELETE,
                        enabled = canConfirmDelete(deleteDialogOpen),
                        onClick = onConfirmDelete,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = onCancelDelete) {
                        Text(ACTION_CANCEL_DELETE, color = QkColor.Muted)
                    }
                }
            }
        }
    }
}
