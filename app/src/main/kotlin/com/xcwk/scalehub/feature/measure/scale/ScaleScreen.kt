package com.xcwk.scalehub.feature.measure.scale

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.application.measure.MeasureOutcome
import com.xcwk.scalehub.application.measure.MeasureOutcomeKind
import com.xcwk.scalehub.application.measure.MeasurePhase
import com.xcwk.scalehub.application.measure.MeasureState
import com.xcwk.scalehub.ui.components.BlePermissionGate
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkGateCard
import com.xcwk.scalehub.ui.components.rememberBtEnabled
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkTheme

private val LinkGreen = QkColor.Primary
private val LinkGray = QkColor.Muted
private val LinkRed = QkColor.Danger

/**
 * scale — 四件套之 Screen：渲染 MeasureSession 三相六态。
 * 权限门与蓝牙开关在本层守卫后再进会话（BlePermission 契约）。
 */
@Composable
fun ScaleRoute(
    onBack: () -> Unit,
    onOpenReport: (Long) -> Unit = {},
    onGoBind: () -> Unit = {},
    vm: ScaleViewModel = viewModel(factory = ScaleViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // 权限门与蓝牙开关态均由共享组件提供（ui/components/BleSupport.kt）
    val btEnabled = rememberBtEnabled()

    // 会话生命周期锚定 overlay（DEC-042）：ScaleViewModel 是 Activity 级的，
    // overlay 弹出不会触发 onCleared；不在这里显式 stop，扫描/连接/倒计时
    // 会话会脱离 UI 在后台继续跑，且残留状态污染下次进入。
    DisposableEffect(Unit) {
        onDispose { vm.stop() }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.start() }

    // 授权成功即启动会话（旧库 onRequestPermissionsResult → startSession）
    BlePermissionGate(onGranted = { vm.start() }) { granted, requestPermission ->
        // 门槛全过才启动（旧库 startSession 同款：权限 + 蓝牙开关都满足）
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
            onRequestPermission = requestPermission,
            onEnableBluetooth = {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            },
            onGoBind = onGoBind,
        )
    }
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
    onGoBind: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("体脂秤测量", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            LinkChip(state.link)
        }

        when {
            !permissionGranted -> QkGateCard(
                title = "需要蓝牙权限",
                body = "允许附近的设备权限后才能连接体脂秤。",
                action = "授权蓝牙权限",
                onAction = onRequestPermission,
                modifier = Modifier.padding(top = 24.dp),
            )
            !bluetoothEnabled -> QkGateCard(
                title = "请打开蓝牙",
                body = "打开手机蓝牙后才能连接体脂秤。",
                action = "打开蓝牙",
                onAction = onEnableBluetooth,
                modifier = Modifier.padding(top = 24.dp),
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
                        QkButton(
                            text = "重新连接",
                            modifier = Modifier.padding(top = 16.dp),
                            onClick = { onIntent(ScaleIntent.Retry) },
                        )
                    }
                    if (state.needBind) {
                        // 无绑定设备的等待态给出明确出口，直达设备页绑定
                        QkButton(
                            text = "去绑定设备",
                            modifier = Modifier.padding(top = 16.dp),
                            onClick = onGoBind,
                        )
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
                                QkButton(
                                    text = "查看报告",
                                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                    onClick = { onOpenReport(time) },
                                )
                                QkButton(
                                    text = "完成",
                                    style = QkButtonStyle.Secondary,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    onClick = onBack,
                                )
                            } else {
                                QkButton(
                                    text = "完成",
                                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                    onClick = onBack,
                                )
                            }
                        }
                        MeasureOutcomeKind.CONFIRM_WEIGHT, MeasureOutcomeKind.NO_COMPOSITION -> {
                            QkButton(
                                text = if (outcome.kind == MeasureOutcomeKind.NO_COMPOSITION) "保存当前数据" else "确认保存",
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                onClick = { onIntent(ScaleIntent.ConfirmSave) },
                            )
                            QkButton(
                                text = "重新测量",
                                style = QkButtonStyle.Secondary,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                onClick = { onIntent(ScaleIntent.Reconnect) },
                            )
                        }
                        MeasureOutcomeKind.TOO_LIGHT, MeasureOutcomeKind.NO_DATA -> {
                            QkButton(
                                text = "重新测量",
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                onClick = { onIntent(ScaleIntent.Reconnect) },
                            )
                            QkButton(
                                text = "返回",
                                style = QkButtonStyle.Ghost,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                onClick = onBack,
                            )
                        }
                        MeasureOutcomeKind.NOT_BOUND -> {
                            // 未绑定态给明确的下一步：直达设备页绑定（壳层接线切页签并清覆盖栈），
                            // 不再让按钮文案承载操作说明
                            QkButton(
                                text = "去绑定设备",
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                onClick = onGoBind,
                            )
                        }
                    }
                }
            }
        }
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
                outcome = MeasureOutcome(
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
