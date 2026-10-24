package com.xcwk.scalehub.feature.settings

import androidx.compose.runtime.Composable

/**
 * T-092 三个页面的导航挂载点（Route 壳，无业务逻辑）。
 *
 * 本任务只新建呈现式页面，不接入既有 SettingsScreen；此处提供最小的路由契约，
 * 供后续「接入波」在导航图中直接引用。状态与回调全部由宿主（ViewModel / 上层
 * 导航回调）注入，Route 壳不新建状态、不调用网络或存储。
 */

/** SDK 清单页路由名。 */
const val SETTINGS_SDK_LIST_ROUTE_NAME = "settings/sdk-list"

/** 数据恢复页路由名。 */
const val SETTINGS_DATA_RECOVERY_ROUTE_NAME = "settings/data-recovery"

/** 服务维护模式页路由名。 */
const val MAINTENANCE_ROUTE_NAME = "maintenance"

/** SDK 清单页挂载点。 */
@Composable
fun SettingsSdkListRoute(
    state: SdkListUiState,
    onToggleSdkConsent: (String, Boolean) -> Unit,
    onOpenSdkPolicy: (String) -> Unit,
    onBack: () -> Unit,
) {
    SdkListScreen(
        state = state,
        onToggleSdkConsent = onToggleSdkConsent,
        onOpenSdkPolicy = onOpenSdkPolicy,
        onBack = onBack,
    )
}

/** 数据恢复页挂载点。 */
@Composable
fun SettingsDataRecoveryRoute(
    state: RecoveryUiState,
    onStartRecovery: () -> Unit,
    onCancelRecovery: () -> Unit,
    onBack: () -> Unit,
    onCustomRangeChange: (String, String) -> Unit = { _, _ -> },
    onScopeSelected: (RecoveryScopeOption) -> Unit = {},
) {
    DataRecoveryScreen(
        state = state,
        onStartRecovery = onStartRecovery,
        onCancelRecovery = onCancelRecovery,
        onCustomRangeChange = onCustomRangeChange,
        onScopeSelected = onScopeSelected,
        onBack = onBack,
    )
}

/** 服务维护模式页挂载点。 */
@Composable
fun MaintenanceRoute(
    state: MaintenanceUiState,
    onExitMaintenance: () -> Unit,
    onViewAnnouncement: () -> Unit,
    onBack: () -> Unit,
) {
    MaintenanceScreen(
        state = state,
        onExitMaintenance = onExitMaintenance,
        onViewAnnouncement = onViewAnnouncement,
        onBack = onBack,
    )
}
