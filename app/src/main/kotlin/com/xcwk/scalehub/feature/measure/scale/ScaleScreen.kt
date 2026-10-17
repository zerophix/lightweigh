package com.xcwk.scalehub.feature.measure.scale

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.application.measure.MeasureOutcomeKind
import com.xcwk.scalehub.application.measure.MeasurePhase
import com.xcwk.scalehub.application.measure.MeasureState
import com.xcwk.scalehub.BleGate
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkTheme

private val LinkGreen = Color(0xFF2D8B56)
private val LinkGray = Color(0xFF76797E)
private val LinkRed = Color(0xFFDC4C4C)

/**
 * scale — 四件套之 Screen：渲染 MeasureSession 三相六态。
 * 权限门与蓝牙开关在本层守卫后再进会话（BlePermission 契约）。
 */
@Composable
fun ScaleRoute(
    onBack: () -> Unit,
    onOpenReport: (Long) -> Unit = {},
    vm: ScaleViewModel = viewModel(factory = ScaleViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { if (it.values.all { granted -> granted }) vm.start() }
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.start() }

    val granted = activity?.let { BleGate.granted(it) } ?: false
    val btEnabled = rememberBtEnabled()

    LaunchedEffect(granted, btEnabled) {
        if (granted && btEnabled) vm.start()
    }

    ScaleContent(
        state = state,
        permissionGranted = granted,
        bluetoothEnabled = btEnabled,
        onIntent = vm::onIntent,
        onBack = onBack,
        onOpenReport = onOpenReport,
        onRequestPermission = {
            activity?.let { BleGate.request(it, 100) }
                ?: permissionLauncher.launch(BleGate.required())
        },
        onEnableBluetooth = {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        },
    )
}

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
fun ScaleContent(
    state: MeasureState,
    permissionGranted: Boolean,
    bluetoothEnabled: Boolean,
    onIntent: (ScaleIntent) -> Unit,
    onBack: () -> Unit = {},
    onOpenReport: (Long) -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onEnableBluetooth: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("体脂秤测量", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            LinkChip(state.link)
        }

        when {
            !permissionGranted -> GateCard(
                title = "需要蓝牙权限",
                desc = "允许附近的设备权限后才能连接体脂秤。",
                action = "授权蓝牙权限",
                onAction = onRequestPermission,
            )
            !bluetoothEnabled -> GateCard(
                title = "请打开蓝牙",
                desc = "打开手机蓝牙后才能连接体脂秤。",
                action = "打开蓝牙",
                onAction = onEnableBluetooth,
            )
            state.outcome == null -> {
                // WAIT / LIVE 相
                Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.title, style = MaterialTheme.typography.headlineSmall)
                    Text(state.desc, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.showCountdown) {
                        Text("${state.countdown}s", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (state.phase == MeasurePhase.LIVE) {
                        Text(state.weightText, modifier = Modifier.padding(top = 24.dp), style = MaterialTheme.typography.displayLarge)
                        Text("公斤", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.notice.takeIf { it.isNotEmpty() }?.let {
                        Text(it, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.showRetry) {
                        Button(onClick = { onIntent(ScaleIntent.Retry) }, modifier = Modifier.padding(top = 16.dp)) { Text("重新连接") }
                    }
                }
            }
            else -> {
                val outcome = requireNotNull(state.outcome)
                Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (outcome.weight.isNotEmpty()) {
                        Text(outcome.weight, style = MaterialTheme.typography.displayMedium)
                        Text("公斤", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(outcome.title, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.headlineSmall)
                    if (outcome.fat.isNotEmpty() && outcome.fat != "--") {
                        Text("体脂 ${outcome.fat}", modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        outcome.extra,
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (outcome.kind) {
                        MeasureOutcomeKind.SAVED -> {
                            val time = outcome.recordTime
                            if (time != null) {
                                Button(onClick = { onOpenReport(time) }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("查看报告") }
                                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("完成") }
                            } else {
                                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("完成") }
                            }
                        }
                        MeasureOutcomeKind.CONFIRM_WEIGHT, MeasureOutcomeKind.NO_COMPOSITION -> {
                            Button(onClick = { onIntent(ScaleIntent.ConfirmSave) }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                                Text(if (outcome.kind == MeasureOutcomeKind.NO_COMPOSITION) "保存当前数据" else "确认保存")
                            }
                            OutlinedButton(onClick = { onIntent(ScaleIntent.Reconnect) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("重新测量") }
                        }
                        MeasureOutcomeKind.TOO_LIGHT, MeasureOutcomeKind.NO_DATA -> {
                            Button(onClick = { onIntent(ScaleIntent.Reconnect) }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("重新测量") }
                            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("返回") }
                        }
                        MeasureOutcomeKind.NOT_BOUND -> {
                            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("去设备页绑定（返回后切换设备页签）") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GateCard(title: String, desc: String, action: String, onAction: () -> Unit) {
    QkCard(modifier = Modifier.padding(top = 24.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(desc, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(action) }
    }
}

@Composable
private fun LinkChip(label: String) {
    val color = when (label) {
        "已连接" -> LinkGreen
        "已断开", "未连接" -> LinkRed
        else -> LinkGray
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Preview(showBackground = true)
@Composable
private fun ScaleWaitPreview() {
    QkTheme {
        ScaleContent(
            state = MeasureState(desc = "双脚尽量覆盖所有电极片", countdown = 23, showCountdown = true, link = "连接中"),
            permissionGranted = true,
            bluetoothEnabled = true,
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScaleOutcomePreview() {
    QkTheme {
        ScaleContent(
            state = MeasureState(
                phase = MeasurePhase.DONE,
                link = "已连接",
                outcome = com.xcwk.scalehub.application.measure.MeasureOutcome(
                    kind = MeasureOutcomeKind.CONFIRM_WEIGHT,
                    title = "请确认这次体重",
                    extra = "和最近一次相差 5.5 公斤。确认后才写入。",
                    weight = "65.5",
                    confirmable = true,
                ),
            ),
            permissionGranted = true,
            bluetoothEnabled = true,
            onIntent = {},
        )
    }
}
