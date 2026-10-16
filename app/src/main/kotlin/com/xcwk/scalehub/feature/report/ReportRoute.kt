package com.xcwk.scalehub.feature.report

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** 路由契约：报告以测量时间戳为参数（"report/{time}"，nav 启用后映射）。 */
const val REPORT_ROUTE_NAME = "report"
fun reportRouteOf(time: Long): String = "report/$time"

/** 装配挂载点：由历史行进入，onBack 返回历史。 */
@Composable
fun ReportRoute(
    time: Long,
    onBack: () -> Unit,
    vm: ReportViewModel = viewModel(factory = ReportViewModel.Factory(time)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ReportScreen(state = state, onIntent = vm::onIntent, onBack = onBack)
}
