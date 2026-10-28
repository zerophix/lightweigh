package com.xcwk.scalehub.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * diag-screen — 客户端诊断页（T-1017）。
 *
 * 展示设备信息、蓝牙/网络/云服务状态、最近日志。
 * 所有副作用（复制到剪贴板、刷新）通过回调委托给调用方。
 */
@Composable
fun DiagScreen(
    deviceInfo: DiagDeviceInfo,
    bluetoothState: DiagBluetoothState,
    networkState: DiagNetworkState,
    cloudState: DiagCloudState,
    recentLogs: List<String>,
    onCopyDiagnostics: () -> Unit,
    onToggleBluetooth: () -> Unit,
    onRefreshCloud: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = QkSpacing.Page)) {
        // 顶部导航
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("诊断信息", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = QkColor.Fg)
            IconButton(onClick = onCopyDiagnostics) {
                Icon(imageVector = QkIcons.Copy, contentDescription = "复制", tint = QkColor.Primary)
            }
        }

        // 设备信息
        DiagCard(title = "设备信息") {
            DiagInfoRow("型号", deviceInfo.model)
            DiagInfoRow("系统版本", deviceInfo.androidVersion)
            DiagInfoRow("App 版本", deviceInfo.appVersion)
            DiagInfoRow("构建类型", deviceInfo.buildType)
        }

        // 蓝牙状态
        DiagCard(title = "蓝牙状态") {
            DiagInfoRow("状态", if (bluetoothState.enabled) "已开启" else "已关闭")
            DiagInfoRow("已配对设备", bluetoothState.pairedCount.toString())
        }

        // 网络状态
        DiagCard(title = "网络状态") {
            DiagInfoRow("类型", networkState.type)
            DiagInfoRow("是否可用", if (networkState.available) "是" else "否")
            DiagInfoRow("是否稳定", if (networkState.stable) "是" else "否")
        }

        // 云服务
        DiagCard(title = "云服务") {
            DiagInfoRow("连接状态", cloudState.status)
            DiagInfoRow("服务器", cloudState.serverRegion)
            DiagInfoRow("最后上报", cloudState.lastUpload)
        }

        // 诊断摘要
        QkCard(modifier = Modifier.padding(vertical = QkSpacing.Md)) {
            Column(modifier = Modifier.padding(QkSpacing.Md)) {
                Text("诊断摘要", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = QkColor.Fg)
                Text(
                    buildString {
                        append("设备：${deviceInfo.model} | Android ${deviceInfo.androidVersion} | App ${deviceInfo.appVersion}\n")
                        append("蓝牙：${if (bluetoothState.enabled) "开启" else "关闭"} | 已配对 ${bluetoothState.pairedCount} 台\n")
                        append("网络：${networkState.type} | ${if (networkState.available) "可用" else "不可用"}\n")
                        append("云服务：${cloudState.status} | ${cloudState.serverRegion}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
        }

        // 最近日志
        QkCard(modifier = Modifier.padding(bottom = QkSpacing.Md)) {
            Column(modifier = Modifier.padding(QkSpacing.Md)) {
                Text("最近日志 (${recentLogs.size} 条)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = QkColor.Fg)
                recentLogs.forEach { log ->
                    Text(log, style = MaterialTheme.typography.bodySmall, color = QkColor.Muted, modifier = Modifier.padding(vertical = QkSpacing.Xs))
                }
            }
        }

        QkButton(
            text = "复制诊断到剪贴板",
            modifier = Modifier.fillMaxWidth().padding(bottom = QkSpacing.Md),
            onClick = onCopyDiagnostics,
        )
    }
}

@Composable
private fun DiagCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    QkCard(modifier = modifier.fillMaxWidth().padding(vertical = QkSpacing.Xs)) {
        Column(modifier = Modifier.padding(QkSpacing.Md)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = QkColor.Fg)
            Spacer(Modifier.height(QkSpacing.Sm))
            content()
        }
    }
}

@Composable
private fun DiagInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Xs), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = QkColor.Fg)
    }
}

/** 设备信息。 */
data class DiagDeviceInfo(
    val model: String = android.os.Build.MODEL,
    val androidVersion: String = android.os.Build.VERSION.RELEASE,
    val appVersion: String = "",
    val buildType: String = "debug",
)

/** 蓝牙状态。 */
data class DiagBluetoothState(val enabled: Boolean = false, val pairedCount: Int = 0)

/** 网络状态。 */
data class DiagNetworkState(val type: String = "unknown", val available: Boolean = false, val stable: Boolean = false)

/** 云服务状态。 */
data class DiagCloudState(val status: String = "未知", val serverRegion: String = "ap-guangzhou", val lastUpload: String = "从未")
