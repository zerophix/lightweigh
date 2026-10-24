package com.xcwk.scalehub.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** 路由契约（同 TrendRoute 口径）。 */
const val HISTORY_ROUTE_NAME = "history"

/** 装配挂载点：历史记录二级页（由趋势页进入，onBack 返回趋势）。 */
@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    onOpenReport: (Long) -> Unit = {},
    onOpenCompare: () -> Unit = {},
    vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory()),
) {
    // 列表 → 测量详情（T-081）由本层持有，返回即回列表
    var detailTime by rememberSaveable { mutableStateOf<Long?>(null) }
    val time = detailTime
    if (time != null) {
        HistoryDetailRoute(
            time = time,
            onBack = { detailTime = null },
            onOpenReport = onOpenReport,
        )
        return
    }

    val state by vm.state.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onIntent = vm::onIntent,
        onBack = onBack,
        onOpenReport = onOpenReport,
        onOpenCompare = onOpenCompare,
        // 行点击先进轻量详情（同一个 recordId），详情里的「查看指标详情」再开完整体测报告
        onOpenDetail = { detailTime = it },
    )
}
