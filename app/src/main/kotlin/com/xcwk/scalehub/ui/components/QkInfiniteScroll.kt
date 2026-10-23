package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 无限滚动。对齐原型 26-final-gaps.html `v-infinite-scroll`。
 *
 * 滚到列表尾部自动要下一页；加载中显示转圈与「加载中…」；
 * 没有更多时显示「已加载全部 N 条」。原型把两种页脚画在同一屏上，演示页同时给出。
 */

fun shouldLoadNextPage(
    lastVisibleIndex: Int,
    itemCount: Int,
    isLoading: Boolean,
    endReached: Boolean,
    prefetch: Int = 2,
): Boolean {
    if (isLoading || endReached || itemCount <= 0 || lastVisibleIndex < 0) return false
    val trigger = itemCount - 1 - prefetch.coerceAtLeast(0)
    return lastVisibleIndex >= trigger
}

fun nextLoadedCount(loaded: Int, total: Int, pageSize: Int): Int {
    val safeTotal = total.coerceAtLeast(0)
    val step = pageSize.coerceAtLeast(1)
    return (loaded.coerceAtLeast(0) + step).coerceAtMost(safeTotal)
}

fun infiniteScrollLoadingLabel(): String = "加载中…"

fun infiniteScrollEndLabel(total: Int): String = "已加载全部 $total 条"

@Composable
fun <T> QkInfiniteColumn(
    items: List<T>,
    isLoading: Boolean,
    endReached: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    loadingLabel: String = infiniteScrollLoadingLabel(),
    endLabel: String? = null,
    header: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
    itemKey: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    LaunchedEffect(state, isLoading, endReached, items.size) {
        snapshotFlow {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            shouldLoadNextPage(last, items.size, isLoading, endReached)
        }
            .distinctUntilChanged()
            .collect { if (it) onLoadMore() }
    }
    LazyColumn(state = state, modifier = modifier) {
        if (header != null) {
            item { header() }
        }
        if (itemKey == null) {
            items(items) { itemContent(it) }
        } else {
            items(items, key = itemKey) { itemContent(it) }
        }
        if (isLoading || endReached || footer != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = QkSpacing.Md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = QkColor.Primary, trackColor = QkColor.Mint)
                        Text(loadingLabel, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (endReached && endLabel != null) {
                        Text(
                            endLabel,
                            modifier = Modifier.fillMaxWidth(),
                            color = QkColor.Muted,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                    footer?.invoke()
                }
            }
        }
    }
}

private data class ScrollRow(val time: String, val weight: String)

private val InfiniteDemoRows = listOf(
    ScrollRow("10/21 07:30", "62.4 kg"),
    ScrollRow("10/20 07:28", "62.7 kg"),
    ScrollRow("10/19 07:22", "62.8 kg"),
    ScrollRow("10/18 07:20", "62.9 kg"),
    ScrollRow("10/17 07:30", "63.0 kg"),
    ScrollRow("10/16 07:25", "63.1 kg"),
    ScrollRow("10/15 07:28", "62.9 kg"),
    ScrollRow("10/14 07:22", "63.2 kg"),
    ScrollRow("10/13 07:20", "63.0 kg"),
)

/** 原型 v-infinite-scroll。 */
@Composable
fun QkInfiniteScrollDemo(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    QkInfiniteColumn(
        items = InfiniteDemoRows,
        isLoading = true,
        endReached = true,
        onLoadMore = {},
        modifier = modifier.fillMaxSize(),
        endLabel = infiniteScrollEndLabel(128),
        header = {
            Column(
                modifier = Modifier.padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(QkSpacing.CardGap),
            ) {
                QkPageTitle("无限滚动演示")
                QkCard {
                    Text(
                        "滚动到底部自动加载更多记录。",
                        color = QkColor.PrimaryDark,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        footer = {
            QkButton(
                text = "返回",
                style = QkButtonStyle.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = QkSpacing.Page),
                onClick = onBack,
            )
        },
    ) { row ->
        QkListRow(
            title = row.time,
            modifier = Modifier.padding(horizontal = QkSpacing.Page),
            showDivider = true,
            trailing = {
                Text(row.weight, color = QkColor.Fg, style = MaterialTheme.typography.titleSmall)
            },
        )
    }
}

@Preview
@Composable
private fun QkInfiniteScrollPreview() {
    QkTheme { QkInfiniteScrollDemo() }
}
