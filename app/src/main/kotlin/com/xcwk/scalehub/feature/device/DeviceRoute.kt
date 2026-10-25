package com.xcwk.scalehub.feature.device

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

/** 路由契约。 */
const val DEVICE_ROUTE_NAME = "device"

/** 装配挂载点：设备页签宿主。 */
@Composable
fun DeviceRoute(
    vm: DeviceViewModel = viewModel(factory = DeviceViewModel.Factory),
    onManualRecord: () -> Unit = {},
) {
    DeviceScreen(vm = vm, onManualRecord = onManualRecord)
}
