package com.xcwk.scalehub.feature.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel

/** 路由契约：记录表单以类型为参数（"record/{type}"）。 */
const val RECORD_ROUTE_NAME = "record"
fun recordRouteOf(type: String): String = "record/$type"

/** 装配挂载点：保存成功后自动关闭（旧库 RecordScreen onSaved→close 口径）。 */
@Composable
fun RecordRoute(
    type: String,
    onDone: () -> Unit,
    preset: String = "",
    vm: RecordViewModel = viewModel(factory = RecordViewModel.Factory(type, preset)),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }
    RecordScreen(state = state, onIntent = vm::onIntent, onBack = onDone)
}
