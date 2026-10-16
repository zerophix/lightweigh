package com.xcwk.scalehub.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** 路由契约（同 TrendRoute 口径）。 */
const val HISTORY_ROUTE_NAME = "history"

/** 装配挂载点：历史记录二级页（由趋势页进入，onBack 返回趋势）。 */
@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory()),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    HistoryScreen(state = state, onIntent = vm::onIntent, onBack = onBack)
}
