package com.xcwk.scalehub.feature.mine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** 路由契约（同 TrendRoute 口径，导航壳启用后映射类型安全 Route）。 */
const val MINE_ROUTE_NAME = "mine"

/** 装配挂载点：我的资料页唯一入口。 */
@Composable
fun MineRoute(vm: MineViewModel = viewModel(factory = MineViewModel.Factory)) {
    // 设置为我的页二级页（宿主导航态，例外域同 MainShell 声明）
    var showSettings by rememberSaveable { mutableStateOf(false) }
    if (showSettings) {
        com.xcwk.scalehub.feature.settings.SettingsRoute(onBack = { showSettings = false })
    } else {
        val state by vm.state.collectAsStateWithLifecycle()
        MineScreen(
            state = state,
            onIntent = vm::onIntent,
            onOpenSettings = { showSettings = true },
        )
    }
}
