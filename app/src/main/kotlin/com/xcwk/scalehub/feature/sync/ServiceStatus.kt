package com.xcwk.scalehub.feature.sync

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.theme.QkColor

/**
 * 云服务不可用（T-102 · Wave 1）。
 *
 * 原型 `08-sync-network-help.html` → `v-service`：
 * 「云服务暂时不可用」
 * 「我们正在维护服务，数据安全无虞。本地测量与查看不受影响。」
 * 按钮「重试连接」「查看服务公告」。
 *
 * 呈现式，版式复用 [SyncOutagePage]。重试与公告都走回调。
 * 本文件不访问系统网络服务，也不发起连接。
 */
internal object ServiceStatusCopy {
    const val NAV_TITLE = "数据同步"
    const val TITLE = "云服务暂时不可用"
    const val BODY = "我们正在维护服务，数据安全无虞。\n本地测量与查看不受影响。"
    const val RETRY = "重试连接"
    const val ANNOUNCEMENT = "查看服务公告"
}

enum class ServiceStatusAction { Retry, ViewAnnouncement }

internal fun dispatchServiceStatus(
    action: ServiceStatusAction,
    onRetryConnection: () -> Unit,
    onViewAnnouncement: () -> Unit,
) {
    when (action) {
        ServiceStatusAction.Retry -> onRetryConnection()
        ServiceStatusAction.ViewAnnouncement -> onViewAnnouncement()
    }
}

@Composable
fun ServiceStatusScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onRetryConnection: () -> Unit = {},
    onViewAnnouncement: () -> Unit = {},
) {
    SyncOutagePage(
        navTitle = ServiceStatusCopy.NAV_TITLE,
        headline = ServiceStatusCopy.TITLE,
        body = ServiceStatusCopy.BODY,
        primaryText = ServiceStatusCopy.RETRY,
        secondaryText = ServiceStatusCopy.ANNOUNCEMENT,
        icon = Icons.Outlined.CloudOff,
        onBack = onBack,
        onPrimary = {
            dispatchServiceStatus(
                ServiceStatusAction.Retry,
                onRetryConnection,
                onViewAnnouncement,
            )
        },
        onSecondary = {
            dispatchServiceStatus(
                ServiceStatusAction.ViewAnnouncement,
                onRetryConnection,
                onViewAnnouncement,
            )
        },
        modifier = modifier.background(QkColor.Bg),
    )
}
