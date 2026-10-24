package com.xcwk.scalehub.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * network-status — 网络不可用状态页（T-102 · 原型08 v-network）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/08-sync-network-help.html` id="v-network"
 * - 验收：AC-N3「网络与服务状态页」，文案逐字取自原型，禁止改写
 *
 * ## 呈现式约束（AC-N3 原文，硬约束）
 * 1. 只渲染传入状态 [NetworkStatusUiState]，不自建状态、不读系统状态；
 * 2. 网络检测经由传入回调 [NetworkAvailabilityProbe]——检测实现由调用方注入，
 *    本文件不 import 系统网络服务（android.net 全家），单测见
 *    `NetworkServiceStatusTest` 的源码守卫；
 * 3. 颜色/间距一律 Qk token，按钮/图标泡复用 ui/components 的 Qk* 组件。
 */

/** 原型 v-network 文案（逐字，勿改）。 */
object NetworkStatusText {
    const val TopBarTitle = "数据同步"
    const val Title = "网络连接不可用"
    const val Description = "测量与记录不受影响，数据先存本机，联网后自动同步。"
    const val PrimaryAction = "检查网络设置"
    const val SecondaryAction = "继续离线使用"
}

/**
 * 网络可达性探测回调：呈现层不接触系统网络服务，检测实现由调用方（Route/ViewModel
 * 或宿主）注入。UI 只消费 [isAvailable] 的结果。
 */
fun interface NetworkAvailabilityProbe {
    fun isAvailable(): Boolean
}

/** 网络状态页输入状态：不可变，只携带呈现所需信息。 */
data class NetworkStatusUiState(
    val networkAvailable: Boolean = false,
    val topBarTitle: String = NetworkStatusText.TopBarTitle,
) {
    /**
     * 是否需要呈现离线文案。探测恢复后本页不再渲染离线内容——
     * 原型没有「已恢复联网」的文案，不自造；出栈由调用方监听状态决定。
     */
    val offline: Boolean get() = !networkAvailable
}

/** 由注入的探测回调构建状态：检测逻辑留在调用方，UI 只消费结果。 */
fun networkStatusStateOf(probe: NetworkAvailabilityProbe): NetworkStatusUiState =
    NetworkStatusUiState(networkAvailable = probe.isAvailable())

/** 网络状态页（原型 v-network）。纯呈现，回调全部来自调用方。 */
@Composable
fun NetworkStatusScreen(
    state: NetworkStatusUiState,
    onBack: () -> Unit = {},
    onCheckNetworkSettings: () -> Unit = {},
    onContinueOffline: () -> Unit = {},
) {
    if (!state.offline) return
    SyncStatusPage(
        topBarTitle = state.topBarTitle,
        icon = Icons.Outlined.WifiOff,
        title = NetworkStatusText.Title,
        description = NetworkStatusText.Description,
        primaryText = NetworkStatusText.PrimaryAction,
        secondaryText = NetworkStatusText.SecondaryAction,
        onBack = onBack,
        onPrimary = onCheckNetworkSettings,
        onSecondary = onContinueOffline,
    )
}

/**
 * v-network / v-service 共用版式（原型 `.statepage`：图标泡 → 标题 → 副文案 →
 * 主按钮 → 幽灵按钮，行间距对齐原型 24px/12px 档，取 QkSpacing.Xl / .Md）。
 *
 * 放在本文件是 T-102 的建文件约束所致：本任务只允许新建 NetworkStatus.kt 与
 * ServiceStatus.kt 两个文件，故共用版式落在这里，ServiceStatus.kt 同包复用。
 */
@Composable
internal fun SyncStatusPage(
    topBarTitle: String,
    icon: ImageVector,
    title: String,
    description: String,
    primaryText: String,
    secondaryText: String,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = QkSpacing.Page)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(
                title = topBarTitle,
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = QkSpacing.Xl, vertical = QkSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 原型 .statepage .sic 88px 圆泡（warn 色调）→ Xl 的 3 倍档；图标随泡缩放内建
            QkIconBubble(
                icon = icon,
                tone = QkGlyphTone.Orange,
                size = QkSpacing.Xl * 3,
                contentDescription = title,
            )
            Spacer(Modifier.height(QkSpacing.Lg))
            Text(
                text = title,
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(QkSpacing.Md))
            Text(
                text = description,
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
