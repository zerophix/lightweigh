package com.xcwk.scalehub.feature.mine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** 路由契约（同 TrendRoute 口径，导航壳启用后映射类型安全 Route）。 */
const val MINE_ROUTE_NAME = "mine"

/** 装配挂载点：我的资料页唯一入口。 */
@Composable
fun MineRoute(vm: MineViewModel = viewModel(factory = MineViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    MineScreen(state = state, onIntent = vm::onIntent)
}
