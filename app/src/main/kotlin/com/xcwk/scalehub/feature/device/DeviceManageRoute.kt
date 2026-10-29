package com.xcwk.scalehub.feature.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.domain.repository.DeviceRepository
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
 * ## T-133 接入波 Batch2：已接入真实仓储能力
 * - 固件版本检查与 OTA：调用 DeviceRepository.checkFirmware / startOta
 * - 重命名落库：调用 DeviceRepository.renameDevice
 * - 成员关联落库：调用 DeviceRepository.updateMemberLinks
 */
@Composable
fun DeviceManageRoute(
    target: WiringTarget,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    activeDeviceMac: String,
    deviceRepository: DeviceRepository,
) {
    when (target) {
        WiringTarget.Firmware -> FirmwareFlow(
            onBack = onBack,
            modifier = modifier,
            deviceMac = activeDeviceMac,
            deviceRepository = deviceRepository,
        )
        WiringTarget.DeviceRename -> RenameFlow(
            onBack = onBack,
            modifier = modifier,
            deviceMac = activeDeviceMac,
            deviceRepository = deviceRepository,
        )
        WiringTarget.MemberDeviceLink -> MemberLinkFlow(
            onBack = onBack,
            modifier = modifier,
            deviceMac = activeDeviceMac,
            deviceRepository = deviceRepository,
        )
        // 其余入口的宿主不在设备页，防御性忽略（入口清单由宿主页决定）
        else -> Unit
    }
}

/** 固件三态：检查 → 升级中 → 就绪。 */
@Composable
private fun FirmwareFlow(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    deviceMac: String,
    deviceRepository: DeviceRepository,
) {
    var state by remember { mutableStateOf(FirmwareUiState(deviceName = getDeviceName(deviceMac, deviceRepository))) }

    // 进入时请求一次固件检查
    LaunchedEffect(Unit) {
        val info = deviceRepository.checkFirmware(deviceMac)
        info?.let {
            state = firmwareReduce(state, FirmwareIntent.CheckResult(
                hasUpdate = it.hasUpdate,
                currentVersion = it.currentVersion,
                latestVersion = it.latestVersion,
            ))
        }
    }

    // OTA 进度由外部（ViewModel/BlePort）通过 FirmwareIntent.Progress 回填
    // 本组件不再模拟本地进度；真实进度事件经 ViewModel 注入

    FirmwareManager(
        state = state,
        onIntent = { intent ->
            when (intent) {
                FirmwareIntent.StartUpdate -> {
                    deviceRepository.startOta(deviceMac)
                    state = firmwareReduce(state, intent)
                }
                else -> state = firmwareReduce(state, intent)
            }
        },
        modifier = modifier,
        onStartUpdate = { /* 由 onIntent 处理 */ },
        onDismiss = onBack,
        // 就绪页「开始测量」本轮回设备页（真实链路走测量页签）
        onStartMeasure = onBack,
    )
}

/** 重命名：草稿 + 校验（纯函数）→ 保存落库后回设备页。 */
@Composable
private fun RenameFlow(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    deviceMac: String,
    deviceRepository: DeviceRepository,
) {
    val initialName = getDeviceName(deviceMac, deviceRepository)
    var state by remember { mutableStateOf(DeviceRenameUiState(currentName = initialName)) }

    DeviceRenameScreen(
        state = state,
        onNameChange = { state = deviceRenameInputChanged(state, it) },
        onRenameSaved = { name ->
            val updated = deviceRepository.renameDevice(deviceMac, name)
            updated?.let { state = state.copy(currentName = it.name, input = "") }
            onBack()
        },
        modifier = modifier,
        onBack = onBack,
    )
}

/** 成员设备关联：勾选集合（纯函数）→ 保存落库后回设备页。 */
@Composable
private fun MemberLinkFlow(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    deviceMac: String,
    deviceRepository: DeviceRepository,
) {
    var state by remember { mutableStateOf(MemberDeviceLinkUiState(deviceName = getDeviceName(deviceMac, deviceRepository))) }

    MemberDeviceLinkScreen(
        state = state,
        onToggleMember = { memberId -> state = toggleMemberLink(state, memberId) },
        onLinkSaved = { linkedIds ->
            deviceRepository.updateMemberLinks(deviceMac, linkedIds)
            state = state.copy(linkedIds = linkedIds)
            onBack()
        },
        modifier = modifier,
        onBack = onBack,
        onManageDevice = onBack,
    )
}

/** 从仓储获取设备名称（不存在时回退默认值）。 */
private fun getDeviceName(mac: String, repo: DeviceRepository): String =
    repo.device(mac)?.name ?: "轻衡 S1"

/** 模拟 OTA 的进度步长间隔（真实 OTA 接入后随本段一起删除）。 */
private const val SIMULATED_OTA_STEP_MILLIS = 400L
