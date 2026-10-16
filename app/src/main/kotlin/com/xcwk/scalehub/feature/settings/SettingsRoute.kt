package com.xcwk.scalehub.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel

/** 路由契约。 */
const val SETTINGS_ROUTE_NAME = "settings"

/** 装配挂载点：由我的页进入，onBack 返回。 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by vm.state.collectAsState()
    SettingsScreen(state = state, onIntent = vm::onIntent, onBack = onBack)
}
