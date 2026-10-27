package com.xcwk.scalehub.feature.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 诊断页 Compose 预览。
 *
 * 所有状态通过数据类传入，便于 IDE 预览与断点调试。
 */

@Preview(name = "诊断页 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DiagScreenPreviewDefault() {
    QkTheme {
        DiagScreen(
            deviceInfo = DiagDeviceInfo(
                model = "Pixel 8",
                androidVersion = "14",
                appVersion = "1.2.3",
                buildType = "release",
            ),
            bluetoothState = DiagBluetoothState(
                enabled = true,
                pairedCount = 2,
            ),
            networkState = DiagNetworkState(
                type = "WIFI",
                available = true,
                stable = true,
            ),
            cloudState = DiagCloudState(
                status = "已连接",
                serverRegion = "ap-guangzhou",
                lastUpload = "2 分钟前",
            ),
            recentLogs = listOf(
                "measure_start mac=AA:BB:CC:DD:EE:FF",
                "ble_connect success",
                "measure_data received 512 bytes",
                "sync_start table=user_profiles",
                "sync_complete records=3",
            ),
            onCopyDiagnostics = {},
            onToggleBluetooth = {},
            onRefreshCloud = {},
            onBack = {},
        )
    }
}

@Preview(name = "诊断页 · 蓝牙关闭", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DiagScreenPreviewBluetoothOff() {
    QkTheme {
        DiagScreen(
            deviceInfo = DiagDeviceInfo(),
            bluetoothState = DiagBluetoothState(enabled = false, pairedCount = 0),
            networkState = DiagNetworkState(type = "CELLULAR", available = true, stable = false),
            cloudState = DiagCloudState(status = "断开", serverRegion = "ap-guangzhou", lastUpload = "从未"),
            recentLogs = listOf(
                "ble_scan timeout",
                "retry_connect failed",
            ),
            onCopyDiagnostics = {},
            onToggleBluetooth = {},
            onRefreshCloud = {},
            onBack = {},
        )
    }
}
