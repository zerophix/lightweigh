package com.xcwk.scalehub.feature.device

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.shell.WiringTarget
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 设备模块交付屏的 Compose 预览集合。
 *
 * `DeviceRoute` / `DeviceScreen` 以 ViewModel 为唯一入参，属接线层，不在预览范围内。
 */

/** 设备详情页 · 默认态。 */
@Preview(name = "设备详情 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DeviceDetailScreenPreviewDefault() {
    QkTheme {
        DeviceDetailScreen(state = DeviceDetailUiState())
    }
}

/** 设备详情路由 · 默认态。 */
@Preview(name = "设备详情路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DeviceDetailRoutePreviewDefault() {
    QkTheme {
        DeviceDetailRoute(state = DeviceUiState(), onBack = {})
    }
}

/** 设备重命名页 · 默认态。 */
@Preview(name = "设备重命名 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DeviceRenameScreenPreviewDefault() {
    QkTheme {
        DeviceRenameScreen(
            state = DeviceRenameUiState(),
            onNameChange = {},
            onRenameSaved = {},
        )
    }
}

/** 成员与设备绑定页 · 默认态。 */
@Preview(name = "成员设备绑定 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MemberDeviceLinkScreenPreviewDefault() {
    QkTheme {
        MemberDeviceLinkScreen(
            state = MemberDeviceLinkUiState(),
            onToggleMember = {},
            onLinkSaved = {},
        )
    }
}

/** 蓝牙权限与扫描流程 · 默认态。 */
@Preview(name = "蓝牙权限流程 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun BtPermissionFlowPreviewDefault() {
    QkTheme {
        BtPermissionFlow(state = BtFlowUiState(), onIntent = {})
    }
}

/** 设备连接流程 · 默认态。 */
@Preview(name = "设备连接流程 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DeviceConnectFlowPreviewDefault() {
    QkTheme {
        DeviceConnectFlow(state = BtFlowUiState(), onIntent = {})
    }
}

/** 设备管理路由 · 重命名入口。 */
@Preview(name = "设备管理路由 · 重命名", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DeviceManageRoutePreviewRename() {
    QkTheme {
        DeviceManageRoute(target = WiringTarget.DeviceRename, onBack = {})
    }
}
