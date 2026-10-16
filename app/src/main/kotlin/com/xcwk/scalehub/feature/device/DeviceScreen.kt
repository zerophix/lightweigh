package com.xcwk.scalehub.feature.device

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.BleGate
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * device — 四件套之 Screen：绑定列表 + 扫描发现（旧库 BindActivity 语义）。
 * 运行期权限门在本层（Activity 入口门禁口径，BlePermission 契约）；
 * 蓝牙开关经系统 Intent 唤起（旧库 REQUEST_ENABLE_BT :80-87）。
 */
@Composable
fun DeviceScreen(vm: DeviceViewModel = viewModel(factory = DeviceViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // 授权结果后回到扫描准备（旧库 onRequestPermissionsResult → prepareScan）
        if (it.values.all { granted -> granted }) vm.onIntent(DeviceIntent.Refresh)
    }
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.onIntent(DeviceIntent.Refresh) }

    val granted = activity?.let { BleGate.granted(it) } ?: false
    val btEnabled = rememberBtEnabled()

    DeviceContent(
        state = state,
        permissionGranted = granted,
        bluetoothEnabled = btEnabled,
        onIntent = vm::onIntent,
        onRequestPermission = {
            activity?.let { BleGate.request(it, REQUEST_PERMISSION) }
                ?: permissionLauncher.launch(BleGate.required())
        },
        onEnableBluetooth = {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        },
    )
}

private const val REQUEST_PERMISSION = 100

@Composable
private fun rememberBtEnabled(): Boolean {
    val context = LocalContext.current
    return androidx.compose.runtime.remember {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        manager?.adapter?.isEnabled == true
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun DeviceContent(
    state: DeviceUiState,
    permissionGranted: Boolean,
    bluetoothEnabled: Boolean,
    onIntent: (DeviceIntent) -> Unit,
    onRequestPermission: () -> Unit = {},
    onEnableBluetooth: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("设备", style = MaterialTheme.typography.headlineSmall)

        if (!permissionGranted) {
            QkCard(modifier = Modifier.padding(top = 12.dp)) {
                Text("需要蓝牙权限", style = MaterialTheme.typography.titleSmall)
                Text(
                    "允许附近的设备权限后才能搜索和连接体脂秤。",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text("授权蓝牙权限")
                }
            }
        } else if (!bluetoothEnabled) {
            QkCard(modifier = Modifier.padding(top = 12.dp)) {
                Text("请打开蓝牙", style = MaterialTheme.typography.titleSmall)
                Text(
                    "打开手机蓝牙后才能搜索体脂秤。",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onEnableBluetooth, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text("打开蓝牙")
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.notice, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onIntent(DeviceIntent.ToggleScan) }) {
                    Text(if (state.scanning) "停止搜索" else "开始搜索设备")
                }
            }
        }

        if (state.found.isNotEmpty()) {
            Text("发现的设备", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            state.found.forEach { found ->
                QkCard(modifier = Modifier.padding(top = 8.dp).clickable { onIntent(DeviceIntent.Bind(found)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(found.name, style = MaterialTheme.typography.titleSmall)
                            Text(found.mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(found.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            if (found.alreadyBound) "已绑定" else "点击绑定",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Text("已绑定设备", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
        if (state.bound.isEmpty()) {
            Text(
                "还没有绑定体脂秤。踩亮秤屏后点「开始搜索设备」。",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.bound.forEach { device ->
            QkCard(modifier = Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            device.name + if (device.active) "（当前）" else "",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(device.mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "协议 ${device.protocolId} · 绑定于 ${device.boundAt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!device.active) {
                        TextButton(onClick = { onIntent(DeviceIntent.Activate(device.mac)) }) { Text("切到这台") }
                    }
                    TextButton(onClick = { onIntent(DeviceIntent.Unbind(device.mac)) }) { Text("解绑") }
                }
            }
        }
        state.message?.let {
            Text(it, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceScreenPreview() {
    QkTheme {
        DeviceContent(
            state = DeviceUiState(
                notice = "发现 2 台体脂秤",
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
