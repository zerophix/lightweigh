package com.xcwk.scalehub.feature.device

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
