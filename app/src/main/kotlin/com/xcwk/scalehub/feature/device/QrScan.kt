package com.xcwk.scalehub.feature.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * 扫码绑定状态机（T-058）。
 *
 * 现状：仓库没有相机依赖（CameraX/ML Kit），外部扫码 App 在权限被拒时会把
 * 用户丢进别人的报错页。因此这里把扫码抽成**状态机 + 端口**：
 * - 有相机实现时走 [QrScanState.Scanning]；
 * - 没有时明确进入 [QrScanState.Unsupported]，并保留手动输入 MAC 的路径。
 * 不伪造"扫到了"的结果。
 */
sealed interface QrScanState {
    data object Idle : QrScanState
    data object RequestingPermission : QrScanState
    data object Scanning : QrScanState
    data class Result(val raw: String) : QrScanState
    data class Failed(val reason: String) : QrScanState
    /** 无相机能力：只能手动输入。 */
    data object Unsupported : QrScanState
}

sealed interface QrScanEvent {
    data object Start : QrScanEvent
    data object PermissionGranted : QrScanEvent
    data object PermissionDenied : QrScanEvent
    data class Scanned(val raw: String) : QrScanEvent
    data class Failed(val reason: String) : QrScanEvent
    data object Cancel : QrScanEvent
}

/**
 * 纯状态转移。规则：
 * - 只有 [QrScanEvent.Start] 能从 Idle 进入，且相机不可用时直接落到 Unsupported；
 * - 取消从任何状态回到 Idle；
 * - 重复的授权回调不改变终态。
 */
fun qrScanNext(state: QrScanState, event: QrScanEvent, cameraAvailable: Boolean = true): QrScanState =
    when (event) {
        QrScanEvent.Cancel -> QrScanState.Idle
        QrScanEvent.Start -> when {
            state !is QrScanState.Idle -> state
            !cameraAvailable -> QrScanState.Unsupported
            else -> QrScanState.RequestingPermission
        }
        QrScanEvent.PermissionGranted ->
            if (state is QrScanState.RequestingPermission) QrScanState.Scanning else state
        QrScanEvent.PermissionDenied ->
            if (state is QrScanState.RequestingPermission) {
                QrScanState.Failed("相机权限被拒绝，可改用手动输入设备码")
            } else {
                state
            }
        is QrScanEvent.Scanned ->
            if (state is QrScanState.Scanning) QrScanState.Result(event.raw) else state
        is QrScanEvent.Failed ->
            if (state is QrScanState.Scanning) QrScanState.Failed(event.reason) else state
    }


/** 每个状态给一句用户能执行的话，不出现"扫码中"却没有相机的情况。 */
fun qrScanMessage(state: QrScanState): String = when (state) {
    QrScanState.Idle -> "点「开始扫码」拉起相机"
    QrScanState.RequestingPermission -> "正在请求相机权限"
    QrScanState.Scanning -> "对准秤身或包装上的二维码"
    is QrScanState.Result -> "已识别：${state.raw}"
    is QrScanState.Failed -> state.reason
    QrScanState.Unsupported -> "当前版本未集成相机扫码，请输入秤身/包装上的 MAC"
}

/**
 * 扫码/手动输入弹层。
 *
 * 有相机时展示扫描态；没有相机时直接展示手动输入，不把用户丢进别人的报错页
 * （这正是替换系统 CaptureActivity 的目的）。
 */
@Composable
fun QrScanSheet(
    state: QrScanState,
    manualValue: String,
    onManualChange: (String) -> Unit,
    canBind: Boolean,
    onBind: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加体脂秤") },
        text = {
            Column {
                Text(
                    text = qrScanMessage(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = QkColor.Muted,
                )
                QkTextField(
                    value = manualValue,
                    onValueChange = onManualChange,
                    label = "设备 MAC",
                    placeholder = "AA:BB:CC:DD:EE:FF",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = QkSpacing.Sm),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canBind, onClick = onBind) { Text("绑定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
