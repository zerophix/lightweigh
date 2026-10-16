package com.xcwk.scalehub.feature.trend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 路由契约：M4 导航壳按此常量注册（DEC-021 假设 A3——navigation 库启用前
 * 以类型常量先行，启用后映射为类型安全 Route）。
 */
const val TREND_ROUTE_NAME = "trend"
const val TREND_ROUTE_PATTERN = "trend/{range}/{segment}"

/** 装配挂载点：接 ViewModel 与无状态 Screen（导航壳接入的唯一入口）。 */
@Composable
fun TrendRoute(vm: TrendViewModel = viewModel(factory = TrendViewModel.Factory)) {
    // 宿主级二级导航态（历史记录），归 Route 层持有——同 MainShell 例外域声明
    var showHistory by rememberSaveable { mutableStateOf(false) }
    if (showHistory) {
        com.xcwk.scalehub.feature.history.HistoryRoute(onBack = { showHistory = false })
    } else {
        val state by vm.state.collectAsStateWithLifecycle()
        TrendScreen(state = state, onIntent = vm::onIntent, onOpenHistory = { showHistory = true })
    }
}
