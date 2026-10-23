package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.states.QkInlineLoading
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 无限滚动：是否该触发加载更多的**纯判定**（T-098 · Wave 0）。
 *
 * 抽成纯函数是为了能被 JVM 单测覆盖 —— 滚动触发阈值若写在组合逻辑里，
 * 只能靠真机手测，回归时无人会发现（治理设计约束 Q3）。
 *
 * @param lastVisibleIndex 当前可见的最后一项下标（未布局时为 -1）
 * @param totalItems 列表总项数（含加载占位）
 * @param threshold 距底部多少项内提前触发，避免用户滚到底才开始请求
 */
fun shouldLoadMore(lastVisibleIndex: Int, totalItems: Int, threshold: Int = 3): Boolean {
    if (totalItems <= 0 || lastVisibleIndex < 0 || threshold < 0) return false
    return lastVisibleIndex >= totalItems - 1 - threshold
}

/**
 * 无限滚动列表（T-098 · Wave 0）。
 *
 * ## 原型
 * `design/prototypes/interactive/26-final-gaps.html` → `v-infinite-scroll`：
 * > 滚动到底部自动加载更多记录。　　（列表中段显示「加载中…」）
 *
 * ## 验收关联（PRD-历史与趋势 状态机）
 * > 分页加载失败 → Ready + 底部重试（已有项保留）
 *
 * 本组件**永不移除已渲染项**：[loading] 只在底部追加一个加载占位，
 * 失败与否由调用方决定是否继续 [hasMore]，已有项不会被清空。
 * 需要「底部重试」时把 [onLoadMore] 传入重试动作、并保持 [hasMore]=true 即可。
 *
 * @param loading 是否正在加载下一页（控制占位与防抖）
 * @param hasMore 是否还有下一页；为 false 时触底不再回调
 * @param onLoadMore 触达阈值时回调；调用方负责去重（组件以 [loading] 防抖）
 */
@Composable
fun QkInfiniteScroll(
    loading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    loadingLabel: String = "加载中…",
    endLabel: String = "没有更多了",
    content: LazyListScope.() -> Unit,
) {
    val currentOnLoadMore = rememberUpdatedState(onLoadMore)
    val currentLoading = rememberUpdatedState(loading)
    val currentHasMore = rememberUpdatedState(hasMore)

    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (!currentLoading.value && currentHasMore.value &&
                    shouldLoadMore(lastVisible, total)
                ) {
                    currentOnLoadMore.value()
                }
            }
    }

    LazyColumn(state = state, modifier = modifier) {
        content()
        item(key = QK_LOAD_MORE_KEY) {
            QkLoadMoreFooter(
                loading = loading,
                hasMore = hasMore,
                loadingLabel = loadingLabel,
                endLabel = endLabel,
            )
        }
    }
}

/** 底部加载占位的固定 key，避免调用方 key 冲突导致的状态错乱。 */
const val QK_LOAD_MORE_KEY: String = "qk-load-more-footer"

/**
 * 底部状态行：加载中 / 没有更多了。
 *
 * 复用 [QkInlineLoading]，与全屏/局部加载同一套视觉，不另造指示器
 * （GOV-CODE 会把跨文件相似实现判为重复）。
 */
@Composable
fun QkLoadMoreFooter(
    loading: Boolean,
    hasMore: Boolean,
    modifier: Modifier = Modifier,
    loadingLabel: String = "加载中…",
    endLabel: String = "没有更多了",
) {
    when {
        loading -> QkInlineLoading(label = loadingLabel, modifier = modifier.fillMaxWidth())
        !hasMore -> Text(
            text = endLabel,
            modifier = modifier.fillMaxWidth().padding(vertical = QkSpacing.Lg),
            color = QkColor.Muted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
