package com.xcwk.scalehub.feature.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel

/** 路由契约。 */
const val AI_ROUTE_NAME = "ai"

/** 装配挂载点：加号面板/报告页进入，onBack 返回刚才页面（需求口径）。 */
@Composable
fun AiRoute(
    onBack: () -> Unit,
    vm: AiViewModel = viewModel(factory = AiViewModel.Factory),
) {
    val state by vm.state.collectAsState()
    AiScreen(state = state, onIntent = vm::onIntent, onBack = onBack)
}
