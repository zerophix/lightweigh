package com.xcwk.scalehub.feature.sync

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.runtime.Composable

/**
 * service-status — 云服务不可用状态页（T-102 · 原型08 v-service）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/08-sync-network-help.html` id="v-service"
 * - 验收：AC-N3「网络与服务状态页」，文案逐字取自原型，禁止改写
 *
 * ## 呈现式约束（AC-N3 原文，硬约束）
 * 1. 只渲染传入状态 [ServiceStatusUiState]；
 * 2. 服务可达性检测经由传入回调 [ServiceAvailabilityProbe]，呈现层不接触系统服务；
 * 3. 颜色/间距一律 Qk token，版式复用同包 [SyncStatusPage]（共版式约束见其 KDoc）。
 */

/** 原型 v-service 文案（逐字，勿改）。 */
object ServiceStatusText {
    const val TopBarTitle = "数据同步"
    const val Title = "云服务暂时不可用"
    const val Description = "我们正在维护服务，数据安全无虞。本地测量与查看不受影响。"
    const val PrimaryAction = "重试连接"
    const val SecondaryAction = "查看服务公告"
}

/**
 * 云服务可达性探测回调：与 [NetworkAvailabilityProbe] 同理，检测实现由调用方注入，
 * UI 只消费结果（重试探测的发起同样走回调，不在呈现层发请求）。
 */
fun interface ServiceAvailabilityProbe {
    fun isAvailable(): Boolean
}

/** 服务状态页输入状态：不可变，只携带呈现所需信息。 */
data class ServiceStatusUiState(
    val serviceAvailable: Boolean = false,
    val topBarTitle: String = ServiceStatusText.TopBarTitle,
) {
    /**
     * 是否需要呈现维护文案。服务恢复后本页不再渲染——原型无「已恢复」文案，
     * 不自造；出栈由调用方监听状态决定。
     */
    val degraded: Boolean get() = !serviceAvailable
}

/** 由注入的探测回调构建状态：检测逻辑留在调用方，UI 只消费结果。 */
fun serviceStatusStateOf(probe: ServiceAvailabilityProbe): ServiceStatusUiState =
    ServiceStatusUiState(serviceAvailable = probe.isAvailable())

/** 服务状态页（原型 v-service）。纯呈现，回调全部来自调用方。 */
@Composable
fun ServiceStatusScreen(
    state: ServiceStatusUiState,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onViewNotice: () -> Unit = {},
) {
    if (!state.degraded) return
    SyncStatusPage(
        topBarTitle = state.topBarTitle,
        icon = Icons.Outlined.CloudOff,
        title = ServiceStatusText.Title,
        description = ServiceStatusText.Description,
        primaryText = ServiceStatusText.PrimaryAction,
        secondaryText = ServiceStatusText.SecondaryAction,
        onBack = onBack,
        onPrimary = onRetry,
        onSecondary = onViewNotice,
    )
}
