package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * 左滑删除容器（T-098 · Wave 0）。
 *
 * ## 原型
 * `design/prototypes/interactive/26-final-gaps.html` → `v-swipe-delete`：
 * > 在真实设备上：左滑记录行 → 露出删除按钮。
 * 弹层文案：「删除这条记录？删除后本地与云端同步移除，7 天内可恢复。」（取消 / 删除）
 *
 * ## 验收关联（AC-HT2「删确认取消后记录仍在」）
 * 本组件**滑到底只发起请求、不移除任何数据**：
 * - 滑到阈值 → 触发 [onRequestDelete]，并立即回弹（[confirmValueChange] 返回 `false`）
 * - 是否真删由调用方弹确认框决定，取消则什么都不发生
 *
 * 这样设计使「确认才删、取消保留」由数据层保证，组件层不可能绕过确认直接移除，
 * 结构上就违反不了 AC-HT2。
 *
 * 右滑（StartToEnd）一律回弹：原型只定义了左滑删除，右滑没有语义。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QkSwipeDelete(
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    deleteLabel: String = "删除",
    content: @Composable RowScope.() -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    if (enabled) onRequestDelete()
                    // false = 回弹到未删除状态；未确认前记录必须仍在
                    false
                }
                // 右滑无语义，直接回弹，避免停在半开状态
                SwipeToDismissBoxValue.StartToEnd -> false
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(QkColor.DangerSoft)
                    .padding(horizontal = QkSpacing.Lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = deleteLabel,
                    color = QkColor.Danger,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        content = { content() },
    )
}
