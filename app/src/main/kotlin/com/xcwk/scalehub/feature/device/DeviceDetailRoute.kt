package com.xcwk.scalehub.feature.device

import androidx.compose.runtime.Composable

/**
 * device — 设备页「设备详情」入口（T-105 接入波第三批）。
 *
 * ## 接入口径
 * [DeviceDetailScreen] 是呈现层，不知道自己在导航里的位置。入口文案与
 * 「设备页状态 → 详情页初始状态」的派生都写在这里；设备页只引
 * [DEVICE_DETAIL_ENTRY_TITLE]，详情屏只在 [DeviceDetailRoute] 出现一次。
 *
 * ## 最小装配（诚实标注）
 * - 名称 / 连接态 / 协议号取当前使用的绑定设备；
 * - 电量、固件版本、绑定成员数还没有数据源，沿用呈现层默认值——不伪造具体读数来源，
 *   待设备档案与固件服务接入后由本文件替换；
 * - 详情页的重命名 / 固件 / 成员关联三行仍走既有 [DeviceManageRoute] 三屏。
 */

/** 设备页「设备管理」卡里的入口行（原型 04 v-settings 上游入口）。 */
const val DEVICE_DETAIL_ENTRY_TITLE = "设备详情"
const val DEVICE_DETAIL_ENTRY_HINT = "电量、固件版本、绑定成员与解绑"

/** 设备页状态 → 详情页初始状态：优先当前使用的设备，其次任一已绑定设备。 */
fun deviceDetailStateOf(state: DeviceUiState): DeviceDetailUiState {
    val device = state.bound.firstOrNull { it.active } ?: state.bound.firstOrNull()
    return DeviceDetailUiState(
        name = device?.name ?: DEFAULT_DEVICE_NAME,
        connected = device?.active == true,
        protocolId = device?.protocolId ?: DEFAULT_PROTOCOL_ID,
    )
}

/** 详情页未拿到绑定设备时的协议号兜底（原型 v-settings 展示值）。 */
const val DEFAULT_PROTOCOL_ID = "0x11"

/** 设备页 → 设备设置页。返回即回设备页。 */
@Composable
fun DeviceDetailRoute(
    state: DeviceUiState,
    onBack: () -> Unit,
    onRename: () -> Unit = {},
    onOpenFirmware: () -> Unit = {},
    onOpenMemberLink: () -> Unit = {},
    onUnbind: () -> Unit = {},
) {
    DeviceDetailScreen(
        state = deviceDetailStateOf(state),
        onRename = onRename,
        onUnbind = onUnbind,
        onOpenFirmware = onOpenFirmware,
        onOpenMemberLink = onOpenMemberLink,
        onBack = onBack,
    )
}
