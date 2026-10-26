package com.xcwk.scalehub.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 设置模块交付屏的 Compose 预览集合。
 *
 * `SettingsEntriesRoute` / `UnitSettingsScreen` 以 ViewModel 为唯一入参，属接线层，不在预览范围内。
 */

/** 设置主页 · 默认态。 */
@Preview(name = "设置主页 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SettingsScreenPreviewDefault() {
    QkTheme {
        SettingsScreen(state = SettingsUiState(), onIntent = {})
    }
}

/** 设置路由 · 默认态。 */
@Preview(name = "设置路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SettingsRoutePreviewDefault() {
    QkTheme {
        SettingsRoute(onBack = {})
    }
}

/** 数据恢复 · 默认态。 */
@Preview(name = "数据恢复 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DataRecoveryScreenPreviewDefault() {
    QkTheme {
        DataRecoveryScreen()
    }
}

/** 维护公告 · 默认态。 */
@Preview(name = "维护公告 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MaintenanceScreenPreviewDefault() {
    QkTheme {
        MaintenanceScreen()
    }
}

/** 三方 SDK 清单 · 默认态。 */
@Preview(name = "SDK 清单 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SdkListScreenPreviewDefault() {
    QkTheme {
        SdkListScreen()
    }
}

/** SDK 清单路由 · 默认态。 */
@Preview(name = "SDK 清单路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SettingsSdkListRoutePreviewDefault() {
    QkTheme {
        SettingsSdkListRoute(
            state = SdkListUiState(),
            onToggleSdkConsent = { _, _ -> },
            onOpenSdkPolicy = {},
            onBack = {},
        )
    }
}

/** 数据恢复路由 · 默认态。 */
@Preview(name = "数据恢复路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SettingsDataRecoveryRoutePreviewDefault() {
    QkTheme {
        SettingsDataRecoveryRoute(
            state = RecoveryUiState(),
            onStartRecovery = {},
            onCancelRecovery = {},
            onBack = {},
        )
    }
}

/** 维护公告路由 · 默认态。 */
@Preview(name = "维护公告路由 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MaintenanceRoutePreviewDefault() {
    QkTheme {
        MaintenanceRoute(
            state = MaintenanceUiState(),
            onExitMaintenance = {},
            onViewAnnouncement = {},
            onBack = {},
        )
    }
}

/** 隐私设置 · 默认态。 */
@Preview(name = "隐私设置 · 默认", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PrivacySettingsScreenPreviewDefault() {
    QkTheme {
        PrivacySettingsScreen()
    }
}

/** 同步状态 · 已同步态。 */
@Preview(name = "同步状态 · 已同步", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SyncStatusScreenPreviewDefault() {
    QkTheme {
        SyncStatusScreen(lastSyncTime = "2026-10-24 08:00", syncEnabled = true)
    }
}

/** 同步状态 · 从未同步（空态）。 */
@Preview(name = "同步状态 · 从未同步", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SyncStatusScreenPreviewNeverSynced() {
    QkTheme {
        SyncStatusScreen(lastSyncTime = null, syncEnabled = false)
    }
}
