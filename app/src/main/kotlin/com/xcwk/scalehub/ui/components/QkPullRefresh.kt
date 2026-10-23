package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 下拉刷新容器（T-098 · Wave 0）。
 *
 * ## 原型
 * `design/prototypes/interactive/26-final-gaps.html` → `v-pull-refresh`：
 * > 下拉刷新在真实设备上：列表顶部下拉 → 出现刷新图标 → 松手触发。
 *
 * ## 验收关联
 * PRD-首页与空态 `H-R5`：**下拉刷新失败不得 Cleared 已有本地最新体重**。
 * 本组件只负责「触发一次 [onRefresh] 回调」，不持有也不清空任何数据，
 * 故该约束由调用方 ViewModel 承担，对组件天然成立 —— 组件层无法违反它。
 *
 * ## 用法
 * ```
 * QkPullRefresh(refreshing = state.refreshing, onRefresh = vm::refresh) {
 *     // 列表内容；刷新失败时保留原有内容，由 ViewModel 决定
 * }
 * ```
 *
 * 复用 Material3 `PullToRefreshBox`（BOM 2024.09 → material3 1.3），不自研手势：
 * 自研手势会在嵌套滚动容器上出现抢焦点与滚动冲突。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QkPullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        content = content,
    )
}
