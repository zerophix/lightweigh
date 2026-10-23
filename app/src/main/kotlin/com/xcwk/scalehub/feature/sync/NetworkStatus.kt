package com.xcwk.scalehub.feature.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * 网络不可用（T-102 · Wave 1）。
 *
 * 原型 `design/prototypes/interactive/08-sync-network-help.html` → `v-network`：
 * 「网络连接不可用」
 * 「测量与记录不受影响，数据先存本机，联网后自动同步。」
 * 按钮「检查网络设置」「继续离线使用」。
 *
 * 呈现式：只渲染文案并把点击交给回调。
 * 本文件不读取系统网络服务；是否联网由 [onCheckNetworkSettings] 的调用方决定。
 */
internal object NetworkStatusCopy {
    const val NAV_TITLE = "数据同步"
    const val TITLE = "网络连接不可用"
    const val BODY = "测量与记录不受影响，\n数据先存本机，联网后自动同步。"
    const val CHECK_SETTINGS = "检查网络设置"
    const val CONTINUE_OFFLINE = "继续离线使用"
}

enum class NetworkStatusAction { CheckSettings, ContinueOffline }

internal fun dispatchNetworkStatus(
    action: NetworkStatusAction,
    onCheckNetworkSettings: () -> Unit,
    onContinueOffline: () -> Unit,
) {
    when (action) {
        NetworkStatusAction.CheckSettings -> onCheckNetworkSettings()
        NetworkStatusAction.ContinueOffline -> onContinueOffline()
    }
}

@Composable
fun NetworkStatusScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onCheckNetworkSettings: () -> Unit = {},
    onContinueOffline: () -> Unit = {},
) {
    SyncOutagePage(
        navTitle = NetworkStatusCopy.NAV_TITLE,
        headline = NetworkStatusCopy.TITLE,
        body = NetworkStatusCopy.BODY,
        primaryText = NetworkStatusCopy.CHECK_SETTINGS,
        secondaryText = NetworkStatusCopy.CONTINUE_OFFLINE,
        icon = Icons.Outlined.WifiOff,
        onBack = onBack,
        onPrimary = {
            dispatchNetworkStatus(
                NetworkStatusAction.CheckSettings,
                onCheckNetworkSettings,
                onContinueOffline,
            )
        },
        onSecondary = {
            dispatchNetworkStatus(
                NetworkStatusAction.ContinueOffline,
                onCheckNetworkSettings,
                onContinueOffline,
            )
        },
        modifier = modifier.background(QkColor.Bg),
    )
}

/**
 * 同步入口下的不可用状态页（网络 / 云服务共用版式）。
 * 图标与按钮文案由调用方传入，本组件不探测网络。
 */
@Composable
internal fun SyncOutagePage(
    navTitle: String,
    headline: String,
    body: String,
    primaryText: String,
    secondaryText: String,
    icon: ImageVector,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Sm),
        ) {
            QkBackButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
            Text(
                text = navTitle,
                modifier = Modifier.align(Alignment.Center),
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = QkSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            QkIconBubble(
                icon = icon,
                tone = QkGlyphTone.Orange,
                size = QkSpacing.Xl * 3 + QkSpacing.Lg,
                contentDescription = headline,
            )
            Spacer(Modifier.height(QkSpacing.Xl - QkSpacing.Xs))
            Text(
                text = headline,
                modifier = Modifier.fillMaxWidth(),
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(QkSpacing.Sm + QkSpacing.Xs / 2))
            Text(
                text = body,
                modifier = Modifier.fillMaxWidth(),
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(QkSpacing.Xl))
            QkButton(
                text = primaryText,
                modifier = Modifier.fillMaxWidth(),
                onClick = onPrimary,
            )
            Spacer(Modifier.height(QkSpacing.Md))
            QkButton(
                text = secondaryText,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Ghost,
                onClick = onSecondary,
            )
        }
    }
}
