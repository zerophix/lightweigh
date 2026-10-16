package com.xcwk.scalehub.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel

/** 路由契约（进入态即门槛本身，无参数）。 */
const val AUTH_ROUTE_NAME = "auth"

/** 装配挂载点：首次启动门（entered 由 AuthGateViewModel 读写）。 */
@Composable
fun AuthRoute(vm: AuthGateViewModel = viewModel(factory = AuthGateViewModel.Factory)) {
    val state by vm.state.collectAsState()
    AuthScreen(state = state, onIntent = vm::onIntent)
}
