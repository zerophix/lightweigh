package com.xcwk.scalehub.feature.measure.scale

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.application.measure.MeasureOutcome
import com.xcwk.scalehub.application.measure.MeasureOutcomeKind
import com.xcwk.scalehub.application.measure.MeasurePhase
import com.xcwk.scalehub.application.measure.MeasureState
import com.xcwk.scalehub.ui.components.BlePermissionGate
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkGateCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.rememberBtEnabled
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkComponentSize
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme
import kotlin.math.PI

private val LinkGreen = QkColor.Primary
private val LinkGray = QkColor.Muted
private val LinkRed = QkColor.Danger

/**
 * scale — 四件套之 Screen：渲染 MeasureSession 三相六态。
 * 权限门与蓝牙开关在本层守卫后再进会话（BlePermission 契约）。
 *
 * T-118：会话 SAVED 后由 MeasureFlowViewModel 接管 v-result / v-ai-report 覆盖层；
 * 覆盖层不隐藏测量中画面（保持原位渲染），避免 ScaleContent 离开组合触发会话 stop。
 */
@Composable
fun ScaleRoute(
    onBack: () -> Unit,
    onOpenReport: (Long) -> Unit = {},
    onGoBind: () -> Unit = {},
    vm: ScaleViewModel = viewModel(factory = ScaleViewModel.Factory),
    flowVm: MeasureFlowViewModel = viewModel(factory = MeasureFlowViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val unitPrefs by vm.unitPrefs.collectAsStateWithLifecycle()
    val flow by flowVm.flow.collectAsStateWithLifecycle()
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

    // 保存即进入结果视图：只把终态事件喂给覆盖层 VM，不反向持有会话。
    LaunchedEffect(state.outcome) {
        val outcome = state.outcome
        if (outcome != null && outcome.kind == MeasureOutcomeKind.SAVED) {
            val time = outcome.recordTime ?: return@LaunchedEffect
            flowVm.onSaved(time, outcome.weight.toDoubleOrNull() ?: 0.0)
        }
    }

    // 授权成功即启动会话（旧库 onRequestPermissionsResult → startSession）
    BlePermissionGate(onGranted = { vm.start() }) { granted, requestPermission ->
        // 门槛全过才启动（旧库 startSession 同款：权限 + 蓝牙开关都满足）
        LaunchedEffect(granted, btEnabled) {
            if (granted && btEnabled) vm.start()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            ScaleContent(
                state = state,
                unitPrefs = unitPrefs,
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
            if (flow.timeMillis > 0L && state.outcome?.kind == MeasureOutcomeKind.SAVED) {
                when (flow.step) {
                    MeasureFlowStep.Result -> MeasureResultContent(
                        snap = flow,
                        unitPrefs = unitPrefs,
                        onAskAi = flowVm::openReport,
                        onDone = { flowVm.closeFlow(); onBack() },
                        onOpenReport = { flowVm.closeFlow(); onOpenReport(flow.timeMillis) },
                        onKeepDup = { },
                        onReplaceDup = flowVm::replacePrev,
                    )
                    MeasureFlowStep.Report -> MeasureReportContent(
                        snap = flow,
                        onBack = flowVm::backToResult,
                        onDone = { flowVm.closeFlow(); onBack() },
                        onRetry = flowVm::retryReport,
                    )
                }
            }
        }
    }
}

@Composable
fun ScaleContent(
    state: MeasureState,
    unitPrefs: com.xcwk.scalehub.domain.model.UserUnitPreferences,
    permissionGranted: Boolean,
    bluetoothEnabled: Boolean,
    onIntent: (ScaleIntent) -> Unit,
    onBack: () -> Unit = {},
    onOpenReport: (Long) -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onEnableBluetooth: () -> Unit = {},
    onGoBind: () -> Unit = {},
) {
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }
    val weightLocked = state.phase == MeasurePhase.LIVE &&
        state.outcome == null && state.weightText.toDoubleOrNull() != null
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.phase == MeasurePhase.LIVE) {
                // 原型 v-measuring 页头：返回先经取消确认，标题「测量中」
                QkBackButton(onClick = { showCancelDialog = true })
                Text(
                    "测量中",
                    modifier = Modifier.padding(start = QkSpacing.Sm).weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Text("体脂秤测量", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            }
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
                if (state.phase == MeasurePhase.LIVE) {
                    // v-measuring（T-118）：环形进度 + 锁定读数 + 计算提示
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MeasuringRing(
                            weightDisplay = com.xcwk.scalehub.application.format.QkFormat.weightStr(
                                state.weightText.toDoubleOrNull() ?: 0.0,
                                unitPrefs,
                            ),
                            locked = weightLocked,
                        )
                        Text(
                            if (weightLocked) "正在计算身体成分" else state.title,
                            modifier = Modifier.padding(top = 20.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            if (weightLocked) "请勿下秤，生物电阻抗分析中…" else state.desc,
                            modifier = Modifier.padding(top = QkSpacing.Sm),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Box(modifier = Modifier.weight(1f))
                        state.notice.takeIf { it.isNotEmpty() }?.let {
                            Text(it, modifier = Modifier.padding(bottom = QkSpacing.Sm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.showRetry) {
                            QkButton(
                                text = "重新连接",
                                modifier = Modifier.padding(top = QkSpacing.Sm),
                                onClick = { onIntent(ScaleIntent.Retry) },
                            )
                        }
                    }
                } else {
                    // WAIT 相：站上秤 / 倒计时 / 去绑定（保持原语义）
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.title, style = MaterialTheme.typography.headlineSmall)
                        Text(state.desc, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.showCountdown) {
                            Text("${state.countdown}s", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
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
            }
            else -> {
                val outcome = requireNotNull(state.outcome)
                Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (outcome.weight.isNotEmpty()) {
                        val weightKg = outcome.weight.toDoubleOrNull() ?: 0.0
                        Text(
                            com.xcwk.scalehub.application.format.QkFormat.weightStr(weightKg, unitPrefs),
                            style = MaterialTheme.typography.displayMedium
                        )
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
    // 原型 v-measuring dlg-cancel：取消需二次确认；确认即退出（数据本就不落库）
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            icon = { QkIcon(QkIcons.Add, tint = QkColor.AmberInk, size = 22.dp) },
            title = { Text("取消本次测量？") },
            text = { Text("测量尚未完成，取消后本次数据不会保存。") },
            confirmButton = {
                QkButton(text = "取消测量", style = QkButtonStyle.Ghost, onClick = onBack)
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("继续测量") }
            },
        )
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

/**
 * v-measuring 环形进度（原型 .hero 双弧环）：未锁定时循环生长，
 * 锁定后停在接近满环并把读数标为「体重已锁定」。
 */
@Composable
private fun MeasuringRing(weightDisplay: String, locked: Boolean) {
    val transition = rememberInfiniteTransition(label = "measuring")
    val swept by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val fraction = if (locked) 0.95f else swept
    Box(
        modifier = Modifier.size(QkComponentSize.MeasureRing),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.055f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = QkColor.ScoreTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = QkColor.Primary,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val angle = (-90f + 360f * fraction) * PI.toFloat() / 180f
            drawCircle(
                color = QkColor.Primary,
                radius = stroke * 0.6f,
                center = Offset(
                    size.width / 2f + (size.width / 2f - stroke) * kotlin.math.cos(angle.toDouble()).toFloat(),
                    size.height / 2f + (size.height / 2f - stroke) * kotlin.math.sin(angle.toDouble()).toFloat(),
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                weightDisplay,
                style = MaterialTheme.typography.displayMedium,
                color = QkColor.Fg,
            )
            if (locked) {
                Text(
                    "体重已锁定",
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                    style = MaterialTheme.typography.labelMedium,
                    color = QkColor.Primary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ScaleWaitPreview() {
    QkTheme {
        ScaleContent(
            state = MeasureState(desc = "双脚尽量覆盖所有电极片", countdown = 23, showCountdown = true, link = "连接中"),
            unitPrefs = com.xcwk.scalehub.domain.model.UserUnitPreferences(),
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
            unitPrefs = com.xcwk.scalehub.domain.model.UserUnitPreferences(),
            permissionGranted = true,
            bluetoothEnabled = true,
            onIntent = {},
        )
    }
}
