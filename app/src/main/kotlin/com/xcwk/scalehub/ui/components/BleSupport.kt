package com.xcwk.scalehub.ui.components

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.xcwk.scalehub.BleGate

/**
 * ble — 跨页面共用的平台感知小工具。放在 ui/components 供各 feature 复用，
 * 避免每个页面各自实现一份（DeviceScreen / ScaleScreen 曾各有一份私有拷贝）。
 */

/**
 * 蓝牙权限门：首次进入自动请求一次；拒绝后给引导出口（还能再问→重新授权；
 * 永久拒绝→去系统设置）；全部授予则回调 [onGranted]。拒绝引导对话框由本组件渲染，
 * 页面只需拿 [granted] 决定展示、拿 [request] 响应用户主动授权。
 *
 * 统一走 Activity Result API——`BleGate.request` 的 `onRequestPermissionsResult`
 * 在 Compose 单 Activity 下无人接收，授权结果会丢。
 *
 * @param onGranted 授权完成后的自动继续动作（旧库 onRequestPermissionsResult → prepareScan/startSession）
 */
@Composable
fun BlePermissionGate(
    onGranted: () -> Unit,
    content: @Composable (granted: Boolean, request: () -> Unit) -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val granted = activity?.let { BleGate.granted(it) } ?: false
    /** null=未拒绝过；true=还能再问；false=永久拒绝 */
    var denied by remember { mutableStateOf<Boolean?>(null) }
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) onGranted()
        else denied = activity?.let { BleGate.canAskAgain(it) } ?: false
    }

    // 首次进入未授权即自动请求一次（旧库 prepareScan/startSession 的 askedPermission 门）
    LaunchedEffect(Unit) {
        if (!granted && activity != null && !asked) {
            asked = true
            launcher.launch(BleGate.required())
        }
    }

    content(granted) { launcher.launch(BleGate.required()) }

    denied?.let { canAskAgain ->
        AlertDialog(
            onDismissRequest = { denied = null },
            title = { Text("开启设备权限") },
            text = {
                Text(
                    if (canAskAgain) {
                        "已拒绝授权。请点击「重新授权」允许附近的设备权限。"
                    } else {
                        "需要附近的设备权限才能搜索和连接体脂秤。请到系统设置中允许蓝牙和附近的设备。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    denied = null
                    if (canAskAgain) launcher.launch(BleGate.required())
                    else activity?.openAppSettings()
                }) { Text(if (canAskAgain) "重新授权" else "去系统设置开启") }
            },
            dismissButton = { TextButton(onClick = { denied = null }) { Text("稍后再说") } },
        )
    }
}

/** 跳到本应用的系统设置页（权限被永久拒绝后的出口）。 */
fun Activity.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ),
    )
}

/**
 * 蓝牙开关实时态：监听 [BluetoothAdapter.ACTION_STATE_CHANGED] 广播。
 * 旧库每个 Activity 靠 onActivityResult 重查开关；v2 是单 Activity 常驻页，
 * 没有该回调可依赖，必须广播驱动——否则用户在系统弹窗里打开蓝牙返回后，
 * 页面仍显示"请打开蓝牙"，测量会话也永远不会自动启动。
 */
@Composable
fun rememberBtEnabled(): Boolean {
    val context = LocalContext.current
    val enabled = remember { mutableStateOf(context.isBluetoothOn()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                enabled.value = context.isBluetoothOn()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return enabled.value
}

/** 从任意 Context 向上找到宿主 Activity（Compose 内容可能被包在 ContextWrapper 里）。 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.isBluetoothOn(): Boolean =
    (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
