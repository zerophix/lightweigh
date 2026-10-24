package com.xcwk.scalehub.feature.settings

import androidx.compose.runtime.Composable

/** 路由契约。 */
const val SETTINGS_ROUTE_NAME = "settings"

/**
 * 装配挂载点：由我的页进入，onBack 返回。
 *
 * 设置区内部的子屏（SDK 清单 / 数据恢复 / 维护模式 / 帮助中心）由
 * [SettingsEntriesRoute] 在设置页内部切栈，返回即回到设置主页，不再回调上层。
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenUnitSettings: () -> Unit = {},
    onOpenPrivacySettings: () -> Unit = {},
    onOpenSyncStatus: () -> Unit = {},
    onOpenSetPassword: () -> Unit = {},
    maintenanceState: MaintenanceUiState = MaintenanceUiState(active = false),
) {
    SettingsEntriesRoute(
        onBack = onBack,
        onOpenUnitSettings = onOpenUnitSettings,
        onOpenPrivacySettings = onOpenPrivacySettings,
        onOpenSyncStatus = onOpenSyncStatus,
        onOpenSetPassword = onOpenSetPassword,
        maintenanceState = maintenanceState,
    )
}
