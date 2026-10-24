package com.xcwk.scalehub.feature.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * device — 首次连接向导的接线层（T-103 两屏的宿主）。
 *
 * ## 与既有配对流程的关系（取舍写在这里）
 * 设备页原有两条活路径：发现行直接绑定（[DeviceIntent.Bind]，真实落库）、
 * 发现行「连接」进 T-104 配对三态（[PairingFlow]，原型演示态机）。二者都保留不动。
 * 本向导是把 T-103 的**权限说明 → 确认设备 → 分步连接**串成一条「解释型首次连接」
 * 路径：权限为什么需要、确认的是哪台秤、连接卡在哪一步。它不是第二套连接实现——
 * 确认动作仍调用既有 [DeviceIntent.Bind]（真实绑定），进度页与配对三态同属原型演示态，
 * 真实 GATT 连接一直在测量会话（[MeasureSession]）里。
 *
 * ## 数据口径（不造规格）
 * 确认设备页只展示本机确实读到的信息：设备名（广播）与信号格数（RSSI 分档）。
 * 型号 / 序列号 / 电量要连上后才读得到，扫描阶段如实写「连接后读取」（[SPEC_UNKNOWN_TEXT]），
 * 不拿原型示例值冒充真机。
 */

/** 设备页「设备管理」分组的向导入口文案（纯数据，JVM 单测校验）。 */
const val DEVICE_SETUP_ENTRY_TITLE: String = "连接向导"
const val DEVICE_SETUP_ENTRY_HINT: String = "权限说明 → 确认设备 → 连接进度"

/** 向导内部等待扫描结果的文案。 */
const val DEVICE_SETUP_SEARCHING: String = "正在搜索附近的体脂秤…"

/**
 * 首次连接向导装配点。
 *
 * @param permissionGranted 系统「附近的设备」权限当前是否已授予（由 BlePermissionGate 提供）
 * @param scanning 是否正在扫描（真实状态）
 * @param found 已发现的设备（真实扫描结果，取第一台进入确认页）
 * @param onRequestPermission 发起系统权限请求
 * @param onStartScan 开始扫描
 * @param onStopScan 停止扫描
 * @param onBind 确认后绑定该设备（复用既有 DeviceIntent.Bind）
 * @param onBack 退出向导，回设备页
 * @param onOpenFirmware 连接成功「继续」→ 固件检查（T-079）
 */
@Composable
fun DeviceSetupRoute(
    permissionGranted: Boolean,
    scanning: Boolean,
    found: List<FoundScale>,
    onRequestPermission: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onBind: (FoundScale) -> Unit,
    onBack: () -> Unit = {},
    onOpenFirmware: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var flow by remember {
        mutableStateOf(
            BtFlowUiState(permission = if (permissionGranted) BtPermissionStatus.Granted else BtPermissionStatus.Unknown),
        )
    }
    // 待确认的真实设备（确认动作要用它做真实绑定）
    val pending = found.firstOrNull()

    // 系统权限结果回填：允许就进确认页（真源仍是 gate，不自己判断权限）
    LaunchedEffect(permissionGranted) {
        if (permissionGranted && flow.permission != BtPermissionStatus.Granted) {
            flow = btFlowReduce(flow, BtFlowIntent.PermissionResult(true))
        }
    }
    // 扫描结果回填：确认页还没拿到设备时，取真实扫描到的第一台
    LaunchedEffect(pending?.mac) {
        if (pending != null && flow.step == BtFlowStep.ConfirmDevice && flow.device == null) {
            flow = btFlowReduce(flow, BtFlowIntent.DeviceFound(discoveredDeviceOf(pending)))
        }
    }
    // 确认页在等设备就开扫；离开确认页就停扫（不常驻扫描）
    LaunchedEffect(flow.step) {
        val waiting = flow.step == BtFlowStep.ConfirmDevice && flow.device == null
        if (waiting && !scanning) onStartScan()
        if (!waiting && scanning) onStopScan()
    }

    when {
        // 确认页但还没发现设备 → 如实呈现「正在搜索」，不拿原型默认设备占位
        flow.step == BtFlowStep.ConfirmDevice && flow.device == null -> DeviceSetupSearchingView(
            onBack = {
                onStopScan()
                onBack()
            },
            modifier = modifier,
        )

        else -> BtPermissionFlow(
            state = flow,
            onIntent = { flow = btFlowReduce(flow, it) },
            modifier = modifier,
            onRequestBtPermission = onRequestPermission,
            onConfirmDevice = {
                // 真实绑定（既有路径），进度页只是把「连接中」讲清楚
                pending?.let(onBind)
            },
            onRetry = {},
            onOpenSettings = {},
            onCancel = {
                onStopScan()
                onBack()
            },
            onContinue = {
                onStopScan()
                onOpenFirmware()
            },
        )
    }
}

/** 确认页等设备时的中间态：标题 + 转圈 + 取消（原型未画，按真实等待态补，不造设备规格）。 */
@Composable
private fun DeviceSetupSearchingView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = QkSpacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DeviceFlowNavBar(TITLE_CONFIRM_DEVICE, onBack)
        Spacer(Modifier.weight(1f))
        QkIcon(QkIcons.Bluetooth, tint = QkColor.Primary, size = QkSpacing.Xl * 2)
        Text(
            text = DEVICE_SETUP_SEARCHING,
            modifier = Modifier.padding(top = QkSpacing.Lg),
            style = MaterialTheme.typography.titleMedium,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "轻踩秤面唤醒设备，发现后会自动跳到确认页",
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
            textAlign = TextAlign.Center,
        )
        CircularProgressIndicator(
            color = QkColor.Primary,
            modifier = Modifier.padding(top = QkSpacing.Xl).height(QkSpacing.Xl),
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = QkSpacing.Xl),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkButton(
                text = ACTION_CANCEL,
                modifier = Modifier.weight(1f),
                style = QkButtonStyle.Secondary,
                onClick = onBack,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceSetupRoutePreview() {
    QkTheme {
        DeviceSetupRoute(
            permissionGranted = true,
            scanning = false,
            found = emptyList(),
            onRequestPermission = {},
            onStartScan = {},
            onStopScan = {},
            onBind = {},
        )
    }
}
