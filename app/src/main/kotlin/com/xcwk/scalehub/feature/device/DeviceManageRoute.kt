package com.xcwk.scalehub.feature.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.shell.WiringTarget
import kotlinx.coroutines.delay

/**
 * device — 设备管理三屏的接入装配点（T-079 / T-080 接入波）。
 *
 * 设备页补三行入口（[com.xcwk.scalehub.ui.shell.WiringEntries] 的 device.* 三条），
 * 分别落到固件管理 / 设备重命名 / 成员设备关联。三屏都是呈现式组件，
 * 状态推进由各自的纯函数承担；本文件只做「谁进来、返回去哪」。
 *
 * ```
 * 设备页 ─┬─ 固件更新 ────── FirmwareManager（检查 → 升级中 → 就绪）
 *         ├─ 设备重命名 ──── DeviceRenameScreen
 *         └─ 成员设备关联 ── MemberDeviceLinkScreen
 * ```
 *
 * ## 仍是空回调的地方（诚实标注）
 * - 固件版本检查与真实 OTA：本轮用本地进度模拟升级，接入 ViewModel 后替换
 * - 重命名与成员关联的落库：保存只更新本地状态并返回设备页
 */
@Composable
fun DeviceManageRoute(
    target: WiringTarget,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (target) {
        WiringTarget.Firmware -> FirmwareFlow(onBack = onBack, modifier = modifier)
        WiringTarget.DeviceRename -> RenameFlow(onBack = onBack, modifier = modifier)
        WiringTarget.MemberDeviceLink -> MemberLinkFlow(onBack = onBack, modifier = modifier)
        // 其余入口的宿主不在设备页，防御性忽略（入口清单由宿主页决定）
        else -> Unit
    }
}

/** 固件三态：检查 → 升级中（本地模拟进度）→ 就绪。 */
@Composable
private fun FirmwareFlow(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember { mutableStateOf(FirmwareUiState()) }

    // 本轮没有真实 OTA：进入升级态后本地推进进度；真实进度由 ViewModel 注入后删除本段
    LaunchedEffect(state.step) {
        if (state.step != FirmwareStep.Updating) return@LaunchedEffect
        while (state.progressPercent < 100) {
            delay(SIMULATED_OTA_STEP_MILLIS)
            state = firmwareReduce(state, FirmwareIntent.Progress(state.progressPercent + 10))
        }
        state = firmwareReduce(state, FirmwareIntent.CompleteUpdate)
    }

    FirmwareManager(
        state = state,
        onIntent = { state = firmwareReduce(state, it) },
        modifier = modifier,
        onStartUpdate = { state = firmwareReduce(state, FirmwareIntent.StartUpdate) },
        onDismiss = onBack,
        // 就绪页「开始测量」本轮回设备页（真实链路走测量页签）
        onStartMeasure = onBack,
    )
}

/** 重命名：草稿 + 校验（纯函数）→ 保存后回设备页。 */
@Composable
private fun RenameFlow(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember { mutableStateOf(DeviceRenameUiState()) }

    DeviceRenameScreen(
        state = state,
        onNameChange = { state = deviceRenameInputChanged(state, it) },
        onRenameSaved = { name ->
            // 真实写入设备档案归 ViewModel；本轮只更新本地当前名称
            state = state.copy(currentName = sanitizeDeviceName(name), input = "")
            onBack()
        },
        modifier = modifier,
        onBack = onBack,
    )
}

/** 成员设备关联：勾选集合（纯函数）→ 保存后回设备页。 */
@Composable
private fun MemberLinkFlow(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember { mutableStateOf(MemberDeviceLinkUiState()) }

    MemberDeviceLinkScreen(
        state = state,
        onToggleMember = { memberId -> state = toggleMemberLink(state, memberId) },
        onLinkSaved = { linkedIds ->
            // 关联关系落库归 ViewModel；本轮只更新本地集合
            state = state.copy(linkedIds = linkedIds)
            onBack()
        },
        modifier = modifier,
        onBack = onBack,
        onManageDevice = onBack,
    )
}

/** 模拟 OTA 的进度步长间隔（真实 OTA 接入后随本段一起删除）。 */
private const val SIMULATED_OTA_STEP_MILLIS = 400L
