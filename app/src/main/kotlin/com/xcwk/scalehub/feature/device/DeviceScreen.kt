package com.xcwk.scalehub.feature.device

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.ui.components.BlePermissionGate
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGateCard
import com.xcwk.scalehub.ui.components.QkDeviceHero
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.components.rememberBtEnabled
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * device — 四件套之 Screen：绑定列表 + 扫描发现（旧库 BindActivity 语义）。
 * 运行期权限门在本层（Activity 入口门禁口径，BlePermission 契约）；
 * 蓝牙开关经系统 Intent 唤起（旧库 REQUEST_ENABLE_BT :80-87）。
 *
 * 版式按 v2 原型「设备」屏：主卡 = 蓝牙环 hero（150dp 双内嵌环 + 主绿圆心）+
 * 搜索连接主按钮 + 连接帮助幽灵按钮 + 配对流程三步（pill 标状态）；下方发现/已绑定
 * 两张列表卡用 list-row 语法。
 */
@Composable
fun DeviceScreen(vm: DeviceViewModel = viewModel(factory = DeviceViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    // 蓝牙开关态 + 权限门均由共享组件提供（ui/components/BleSupport.kt）
    val btEnabled = rememberBtEnabled()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showHelp by remember { mutableStateOf(false) }

    // 生命周期 ON_PAUSE 停扫（旧库 BindActivity.onPause → stopScan）
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) vm.onIntent(DeviceIntent.Pause)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // 开蓝牙返回后重查开关并自动继续（旧库 onActivityResult → startScan）
        if (btEnabled) vm.onIntent(DeviceIntent.ToggleScan) else vm.onIntent(DeviceIntent.Refresh)
    }

    // 扫码绑定（旧库 BindActivity btnQr → ScanContract）
    val qrLauncher = rememberLauncherForActivityResult<ScanOptions, ScanIntentResult>(
      ScanContract() as ActivityResultContract<ScanOptions, ScanIntentResult>,
    ) { result ->
        val contents = result.contents
        if (contents != null) {
            val qr = com.xcwk.scalehub.domain.device.ScaleQr.parse(contents)
            if (qr == null) {
                vm.onIntent(DeviceIntent.Refresh)
            } else {
                vm.onIntent(DeviceIntent.QrBind(qr.name, qr.mac))
            }
        }
    }

    // 授权成功即自动开扫（旧库 onRequestPermissionsResult → prepareScan）
    BlePermissionGate(onGranted = { vm.onIntent(DeviceIntent.ToggleScan) }) { granted, requestPermission ->
        DeviceContent(
            state = state,
            permissionGranted = granted,
            bluetoothEnabled = btEnabled,
            onIntent = vm::onIntent,
            onRequestPermission = requestPermission,
            onEnableBluetooth = {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            },
            onScanQr = {
                qrLauncher.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("将体脂秤二维码放入框内")
                        .setBeepEnabled(false)
                        .setOrientationLocked(true),
                )
            },
            onHelp = { showHelp = true },
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("连接帮助") },
            text = {
                Text(
                    "1. 打开手机蓝牙；\n" +
                        "2. 轻踩体脂秤秤面，等屏幕亮起（设备开始广播）；\n" +
                        "3. 回到本页点「搜索并连接设备」，在发现的设备里点一下完成绑定；\n" +
                        "4. 之后每次测量，App 会自动连接已绑定的秤。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("知道了") } },
        )
    }
}

@Composable
fun DeviceContent(
    state: DeviceUiState,
    permissionGranted: Boolean,
    bluetoothEnabled: Boolean,
    onIntent: (DeviceIntent) -> Unit,
    onRequestPermission: () -> Unit = {},
    onEnableBluetooth: () -> Unit = {},
    onScanQr: () -> Unit = {},
    onHelp: () -> Unit = {},
) {
    val (heroStatus, heroSub) = heroText(
        permissionGranted = permissionGranted,
        bluetoothEnabled = bluetoothEnabled,
        state = state,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        QkCard(modifier = Modifier.padding(top = QkSpacing.Md)) {
            QkDeviceHero(status = heroStatus, sub = heroSub)
            QkButton(
                text = if (state.scanning) "停止搜索" else "搜索并连接设备",
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                onClick = { onIntent(DeviceIntent.ToggleScan) },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                QkButton(
                    text = "连接帮助",
                    style = QkButtonStyle.Ghost,
                    modifier = Modifier.weight(1f),
                    onClick = onHelp,
                )
                QkButton(
                    text = "扫码绑定",
                    style = QkButtonStyle.Ghost,
                    modifier = Modifier.weight(1f),
                    onClick = onScanQr,
                )
            }

            // 配对流程三步（原型 device 卡内 list-row）：pill 状态随蓝牙/扫描态派生
            QkSectionHead(
                title = "设备配对流程",
                modifier = Modifier.padding(top = QkSpacing.Lg),
            )
            QkListRow(
                title = "开启蓝牙",
                subtitle = "确保手机蓝牙功能已开启",
                glyph = "1",
                showDivider = true,
                trailing = {
                    QkPill(
                        if (bluetoothEnabled) "已开启" else "准备",
                        if (bluetoothEnabled) QkPillTone.Green else QkPillTone.Gray,
                    )
                },
            )
            QkListRow(
                title = "唤醒体脂秤",
                subtitle = "轻踩秤面，等待设备广播",
                glyph = "2",
                showDivider = true,
                trailing = {
                    QkPill(
                        if (state.scanning) "广播中" else "待完成",
                        if (state.scanning) QkPillTone.Blue else QkPillTone.Gray,
                    )
                },
            )
            QkListRow(
                title = "开始测量",
                subtitle = "赤脚站上秤面并保持稳定",
                glyph = "3",
                showDivider = false,
                trailing = { QkPill("待完成", QkPillTone.Gray) },
            )

            if (state.notice.isNotBlank()) {
                Text(
                    state.notice,
                    modifier = Modifier.padding(top = QkSpacing.Md),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
            Text(
                "本页直接扫描并连接真实蓝牙体脂秤；二维码绑定请对准秤身或包装上的二维码。",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Hint,
            )
        }

        // 权限 / 蓝牙开关门（原型没有这两态，但 Android 端必须显式处理）
        if (!permissionGranted) {
            QkGateCard(
                title = "需要蓝牙权限",
                body = "允许「附近的设备」权限后才能搜索和连接体脂秤。",
                action = "授权蓝牙权限",
                onAction = onRequestPermission,
                modifier = Modifier.padding(top = QkSpacing.CardGap),
            )
        } else if (!bluetoothEnabled) {
            QkGateCard(
                title = "请打开蓝牙",
                body = "打开手机蓝牙后才能搜索体脂秤。",
                action = "打开蓝牙",
                onAction = onEnableBluetooth,
                modifier = Modifier.padding(top = QkSpacing.CardGap),
            )
        }

        if (state.found.isNotEmpty()) {
            QkSectionHead(
                title = "发现的设备",
                modifier = Modifier.padding(top = QkSpacing.Lg),
            )
            QkCard(modifier = Modifier.padding(top = 2.dp)) {
                state.found.forEachIndexed { index, found ->
                    QkListRow(
                        title = found.name,
                        subtitle = "${found.mac}\n${found.summary}",
                        icon = QkIcons.Weight,
                        showDivider = index != state.found.lastIndex,
                        onClick = { onIntent(DeviceIntent.Bind(found)) },
                        trailing = {
                            QkPill(
                                if (found.alreadyBound) "已绑定" else "点击绑定",
                                if (found.alreadyBound) QkPillTone.Gray else QkPillTone.Green,
                            )
                        },
                    )
                }
            }
        }

        QkSectionHead(
            title = "已绑定设备",
            modifier = Modifier.padding(top = QkSpacing.Lg),
        )
        QkCard(modifier = Modifier.padding(top = 2.dp)) {
            if (state.bound.isEmpty()) {
                Text(
                    "还没有绑定体脂秤。踩亮秤屏后点「搜索并连接设备」。",
                    modifier = Modifier.padding(vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            } else {
                state.bound.forEachIndexed { index, device ->
                    QkListRow(
                        title = device.name + if (device.active) "（当前）" else "",
                        subtitle = "${device.mac}\n协议 ${device.protocolId} · 绑定于 ${device.boundAt}",
                        icon = QkIcons.Weight,
                        tone = if (device.active) QkGlyphTone.Green else QkGlyphTone.Gray,
                        showDivider = index != state.bound.lastIndex,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!device.active) {
                                    TextButton(onClick = { onIntent(DeviceIntent.Activate(device.mac)) }) {
                                        Text("切到这台", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                TextButton(onClick = { onIntent(DeviceIntent.Unbind(device.mac)) }) {
                                    Text(
                                        "解绑",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = QkColor.Danger,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
        state.message?.let {
            Text(
                it,
                modifier = Modifier.padding(top = QkSpacing.Md),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Primary,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

/** hero 文案随权限/蓝牙/扫描/绑定态派生——环本身恒静态（原型即无动画）。 */
private fun heroText(
    permissionGranted: Boolean,
    bluetoothEnabled: Boolean,
    state: DeviceUiState,
): Pair<String, String> = when {
    !permissionGranted -> "连接蓝牙体脂秤" to "请允许「附近的设备」权限后搜索"
    !bluetoothEnabled -> "请打开蓝牙" to "打开手机蓝牙后才能搜索体脂秤"
    state.bound.any { it.active } ->
        // 只是「当前使用的绑定设备」标签——此前硬编码「已连接」是 UI 谎言：
        // 绑定页从不发起 GATT 连接（DeviceViewModel 纯扫描），该字样曾把真机排查
        // 全程引向「已经连上了」的错误方向（DEC-038 真机追因实录）
        (state.bound.first { it.active }.name + " 使用中") to state.bound.first { it.active }.mac
    state.scanning -> "正在搜索体脂秤" to "轻踩秤面唤醒设备，等待广播出现"
    else -> "连接蓝牙体脂秤" to "请打开体脂秤并靠近手机"
}

@Preview(showBackground = true)
@Composable
private fun DeviceScreenPreview() {
    QkTheme {
        DeviceContent(
            state = DeviceUiState(
                notice = "发现 2 台体脂秤",
                scanning = true,
                found = listOf(
                    FoundScale("智能体脂秤", "AA:BB:CC:DD:EE:FF", -58, "65.5 kg 未锁定 协议0x11", "okok-1", false),
                ),
            ),
            permissionGranted = true,
            bluetoothEnabled = true,
            onIntent = {},
        )
    }
}
