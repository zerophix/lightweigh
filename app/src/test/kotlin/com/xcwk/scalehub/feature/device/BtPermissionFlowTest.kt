package com.xcwk.scalehub.feature.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-103 蓝牙权限与确认设备两页的单测。
 *
 * 覆盖验收中**唯一可自动判定**的部分：
 * - 逐字文案（原型 `18-device-setup.html` 的 v-bt-permission / v-confirm-device）
 * - 状态机（[btFlowReduce]）：权限 → 确认设备 → 连接，含权限被拒分支
 *
 * 视觉布局不在此测（需真机/截图）。
 */
class BtPermissionFlowTest {

    private fun granted(device: DiscoveredDevice? = DEFAULT_DISCOVERED_DEVICE) = BtFlowUiState(
        step = BtFlowStep.ConfirmDevice,
        permission = BtPermissionStatus.Granted,
        device = device,
    )

    // ---- v-bt-permission 文案 ----

    @Test
    fun `权限页文案逐字对齐原型`() {
        assertThat(TITLE_BT_PERMISSION).isEqualTo("蓝牙权限")
        assertThat(BT_PERMISSION_HEADLINE).isEqualTo("需要蓝牙权限\n才能连接体脂秤")
        assertThat(BT_PERMISSION_SUBLINE).isEqualTo(
            "Android 12+ 需要「附近设备」权限。\n我们只用它发现和连接体脂秤，\n不会读取你的位置信息。",
        )
        assertThat(BT_PERMISSION_BANNER)
            .isEqualTo("点击「继续」后系统会弹出权限请求，你可以拒绝，但连接体脂秤需要此权限。")
        assertThat(ACTION_CONTINUE).isEqualTo("继续")
    }

    @Test
    fun `权限页三项用途逐字对齐原型`() {
        assertThat(BT_PERMISSION_ITEMS).hasSize(3)
        assertThat(BT_PERMISSION_ITEMS[0].title).isEqualTo("搜索附近体脂秤")
        assertThat(BT_PERMISSION_ITEMS[0].desc).isEqualTo("发现可连接的轻衡设备")
        assertThat(BT_PERMISSION_ITEMS[1].title).isEqualTo("传输测量数据")
        assertThat(BT_PERMISSION_ITEMS[1].desc).isEqualTo("读取体重和体成分结果")
        assertThat(BT_PERMISSION_ITEMS[2].title).isEqualTo("读取设备信息")
        assertThat(BT_PERMISSION_ITEMS[2].desc).isEqualTo("电量、固件版本、序列号")
    }

    // ---- v-confirm-device 文案 ----

    @Test
    fun `确认设备页文案逐字对齐原型`() {
        assertThat(TITLE_CONFIRM_DEVICE).isEqualTo("确认设备")
        assertThat(CONFIRM_HEADLINE).isEqualTo("发现一台设备")
        assertThat(CONFIRM_SUBLINE).isEqualTo("请确认是否为你要连接的体脂秤")
        assertThat(LABEL_SIGNAL).isEqualTo("信号强度")
        assertThat(LABEL_BATTERY).isEqualTo("电量")
        assertThat(CONFIRM_BANNER).isEqualTo("如果不是你的设备，请轻踩秤面唤醒后重新搜索。")
        assertThat(ACTION_CONFIRM_PAIR).isEqualTo("确认，开始配对")
        assertThat(ACTION_RESCAN).isEqualTo("重新搜索")
    }

    @Test
    fun `设备规格行与电量逐字对齐原型`() {
        assertThat(DEFAULT_DISCOVERED_DEVICE.name).isEqualTo("轻衡 S1 智能体脂秤")
        assertThat(DEFAULT_DISCOVERED_DEVICE.shortName).isEqualTo("轻衡 S1")
        assertThat(deviceModelLine(DEFAULT_DISCOVERED_DEVICE)).isEqualTo("型号 XHS-01 · 序列号 ****8A2F")
        assertThat(deviceBatteryLabel(DEFAULT_DISCOVERED_DEVICE)).isEqualTo("82%")
        assertThat(DEFAULT_DISCOVERED_DEVICE.signalBars).isEqualTo(3)
        assertThat(DEFAULT_DISCOVERED_DEVICE.batteryPercent).isEqualTo(82)
    }

    // ---- 状态机：权限 → 确认 ----

    @Test
    fun `初始停在权限页且权限未询问`() {
        val state = BtFlowUiState()
        assertThat(state.step).isEqualTo(BtFlowStep.Permission)
        assertThat(state.permission).isEqualTo(BtPermissionStatus.Unknown)
        assertThat(state.awaitingPermission).isFalse()
    }

    @Test
    fun `未授权时继续只挂起等待系统结果不前进`() {
        // 「继续」先唤起系统弹窗，页面必须原地不动，等外部回填结果
        val next = btFlowReduce(BtFlowUiState(), BtFlowIntent.Continue)
        assertThat(next.step).isEqualTo(BtFlowStep.Permission)
        assertThat(next.awaitingPermission).isTrue()
    }

    @Test
    fun `已授权时继续直接进确认页`() {
        val state = BtFlowUiState(permission = BtPermissionStatus.Granted)
        val next = btFlowReduce(state, BtFlowIntent.Continue)
        assertThat(next.step).isEqualTo(BtFlowStep.ConfirmDevice)
        assertThat(next.awaitingPermission).isFalse()
    }

    @Test
    fun `权限允许后进确认页并记已授权`() {
        val next = btFlowReduce(BtFlowUiState(awaitingPermission = true), BtFlowIntent.PermissionResult(granted = true))
        assertThat(next.step).isEqualTo(BtFlowStep.ConfirmDevice)
        assertThat(next.permission).isEqualTo(BtPermissionStatus.Granted)
        assertThat(next.awaitingPermission).isFalse()
    }

    // ---- 状态机：权限被拒分支 ----

    @Test
    fun `权限被拒停留权限页并记拒绝态`() {
        val next = btFlowReduce(BtFlowUiState(awaitingPermission = true), BtFlowIntent.PermissionResult(granted = false))
        assertThat(next.step).isEqualTo(BtFlowStep.Permission)
        assertThat(next.permission).isEqualTo(BtPermissionStatus.Denied)
        assertThat(next.awaitingPermission).isFalse()
        assertThat(next.discovering).isFalse()
    }

    @Test
    fun `权限被拒后继续可再次请求但仍不前进`() {
        val denied = BtFlowUiState(permission = BtPermissionStatus.Denied)
        val next = btFlowReduce(denied, BtFlowIntent.Continue)
        assertThat(next.step).isEqualTo(BtFlowStep.Permission)
        assertThat(next.awaitingPermission).isTrue()
        assertThat(next.permission).isEqualTo(BtPermissionStatus.Denied)
    }

    @Test
    fun `去系统设置后回到未询问以便返回重新判断`() {
        val denied = BtFlowUiState(permission = BtPermissionStatus.Denied)
        val next = btFlowReduce(denied, BtFlowIntent.OpenSettings)
        assertThat(next.permission).isEqualTo(BtPermissionStatus.Unknown)
        assertThat(next.awaitingPermission).isFalse()
        assertThat(next.step).isEqualTo(BtFlowStep.Permission)
    }

    @Test
    fun `未授权时确认配对无效`() {
        // 不变量 1：不得跳过权限直接配对
        val state = BtFlowUiState(step = BtFlowStep.Permission, device = DEFAULT_DISCOVERED_DEVICE)
        val next = btFlowReduce(state, BtFlowIntent.ConfirmDevice)
        assertThat(next.step).isEqualTo(BtFlowStep.Permission)
    }

    @Test
    fun `已授权但尚未发现设备时确认配对无效`() {
        val next = btFlowReduce(granted(device = null), BtFlowIntent.ConfirmDevice)
        assertThat(next.step).isEqualTo(BtFlowStep.ConfirmDevice)
    }

    // ---- 状态机：确认 → 连接 ----

    @Test
    fun `发现设备后进确认页`() {
        val next = btFlowReduce(BtFlowUiState(permission = BtPermissionStatus.Granted), BtFlowIntent.DeviceFound(DEFAULT_DISCOVERED_DEVICE))
        assertThat(next.step).isEqualTo(BtFlowStep.ConfirmDevice)
        assertThat(next.device).isEqualTo(DEFAULT_DISCOVERED_DEVICE)
        assertThat(next.discovering).isFalse()
    }

    @Test
    fun `确认配对进连接中并以短名开始`() {
        val next = btFlowReduce(granted(), BtFlowIntent.ConfirmDevice)
        assertThat(next.step).isEqualTo(BtFlowStep.Connect)
        assertThat(next.connect.step).isEqualTo(DeviceConnectStep.Connecting)
        assertThat(next.connect.deviceName).isEqualTo("轻衡 S1")
        assertThat(next.connect.stage).isEqualTo(ConnectStage.Establishing)
    }

    @Test
    fun `重新搜索回权限页清空设备但保留授权`() {
        val next = btFlowReduce(granted(), BtFlowIntent.Rescan)
        assertThat(next.step).isEqualTo(BtFlowStep.Permission)
        assertThat(next.device).isNull()
        assertThat(next.discovering).isTrue()
        assertThat(next.permission).isEqualTo(BtPermissionStatus.Granted)
    }

    @Test
    fun `取消连接回确认页且不改权限`() {
        val connecting = btFlowReduce(granted(), BtFlowIntent.ConfirmDevice)
        val next = btFlowReduce(connecting, BtFlowIntent.CancelConnect)
        assertThat(next.step).isEqualTo(BtFlowStep.ConfirmDevice)
        assertThat(next.permission).isEqualTo(BtPermissionStatus.Granted)
        assertThat(next.device).isEqualTo(DEFAULT_DISCOVERED_DEVICE)
    }

    @Test
    fun `连接阶段事件在非连接态被忽略`() {
        val next = btFlowReduce(granted(), BtFlowIntent.ConnectEvent(DeviceConnectIntent.Succeeded))
        assertThat(next.step).isEqualTo(BtFlowStep.ConfirmDevice)
    }

    @Test
    fun `连接阶段事件在连接态转交子状态机`() {
        val connecting = btFlowReduce(granted(), BtFlowIntent.ConfirmDevice)
        val progressed = btFlowReduce(connecting, BtFlowIntent.ConnectEvent(DeviceConnectIntent.StageChanged(ConnectStage.SyncingInfo)))
        assertThat(progressed.connect.stage).isEqualTo(ConnectStage.SyncingInfo)
        val done = btFlowReduce(progressed, BtFlowIntent.ConnectEvent(DeviceConnectIntent.Succeeded))
        assertThat(done.connect.step).isEqualTo(DeviceConnectStep.Connected)
        assertThat(done.step).isEqualTo(BtFlowStep.Connect)
    }

    @Test
    fun `连接失败后再取消回确认页`() {
        val connecting = btFlowReduce(granted(), BtFlowIntent.ConfirmDevice)
        val failed = btFlowReduce(connecting, BtFlowIntent.ConnectEvent(DeviceConnectIntent.Failed("蓝牙已断开")))
        assertThat(failed.connect.step).isEqualTo(DeviceConnectStep.Failed)
        val next = btFlowReduce(failed, BtFlowIntent.CancelConnect)
        assertThat(next.step).isEqualTo(BtFlowStep.ConfirmDevice)
        assertThat(next.connect.step).isEqualTo(DeviceConnectStep.Connecting)
    }
}
