package com.xcwk.scalehub.feature.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 设备页「连接向导」入口 —— 入口登记 + 真实扫描结果到确认页的映射。
 *
 * 钉的是「不拿原型示例值冒充真机」：扫描阶段只有名字与广播强度，
 * 型号 / 序列号 / 电量必须如实写「连接后读取」，电量哨兵值不得伪装成 0%。
 */
class DeviceSetupEntryTest {

    private fun found(rssi: Int = -60) = FoundScale(
        name = "轻衡 S1 智能体脂秤",
        mac = "AA:BB:CC:DD:EE:FF",
        rssi = rssi,
        summary = "65.5 kg 未锁定 协议0x11",
        protocolId = "0x11",
        alreadyBound = false,
    )

    @Test
    fun `入口文案指向首次连接向导`() {
        assertThat(DEVICE_SETUP_ENTRY_TITLE).isEqualTo("连接向导")
        assertThat(DEVICE_SETUP_ENTRY_HINT).contains("权限")
        assertThat(DEVICE_SETUP_SEARCHING).contains("搜索")
    }

    @Test
    fun `扫描结果只带真实读到的名字与信号`() {
        val device = discoveredDeviceOf(found(rssi = -60))

        assertThat(device.name).isEqualTo("轻衡 S1 智能体脂秤")
        assertThat(device.shortName).isEqualTo("轻衡 S1 智能体脂秤")
        assertThat(device.signalBars).isEqualTo(3)
        assertThat(device.model).isEmpty()
        assertThat(device.serialMasked).isEmpty()
        assertThat(device.batteryPercent).isEqualTo(BATTERY_UNKNOWN)
    }

    @Test
    fun `型号与电量如实写连接后读取`() {
        val device = discoveredDeviceOf(found())

        assertThat(deviceModelLine(device)).isEqualTo("型号与序列号$SPEC_UNKNOWN_TEXT")
        assertThat(deviceBatteryLabel(device)).isEqualTo(SPEC_UNKNOWN_TEXT)
    }

    @Test
    fun `原型默认设备的规格行保持不变`() {
        assertThat(deviceModelLine(DEFAULT_DISCOVERED_DEVICE)).isEqualTo("型号 XHS-01 · 序列号 ****8A2F")
        assertThat(deviceBatteryLabel(DEFAULT_DISCOVERED_DEVICE)).isEqualTo("82%")
    }

    @Test
    fun `广播强度分档落到四格信号`() {
        assertThat(signalBarsOf(-50)).isEqualTo(4)
        assertThat(signalBarsOf(-60)).isEqualTo(3)
        assertThat(signalBarsOf(-70)).isEqualTo(2)
        assertThat(signalBarsOf(-80)).isEqualTo(1)
        assertThat(signalBarsOf(-95)).isEqualTo(0)
    }

    @Test
    fun `确认页在未授权时不得跳过权限`() {
        val denied = BtFlowUiState(step = BtFlowStep.Permission, device = discoveredDeviceOf(found()))
        val next = btFlowReduce(denied, BtFlowIntent.ConfirmDevice)

        assertThat(next.step).isEqualTo(BtFlowStep.Permission)
    }

    @Test
    fun `确认后进入连接中且用真实设备名`() {
        val state = BtFlowUiState(
            step = BtFlowStep.ConfirmDevice,
            permission = BtPermissionStatus.Granted,
            device = discoveredDeviceOf(found()),
        )
        val next = btFlowReduce(state, BtFlowIntent.ConfirmDevice)

        assertThat(next.step).isEqualTo(BtFlowStep.Connect)
        assertThat(next.connect.step).isEqualTo(DeviceConnectStep.Connecting)
        assertThat(next.connect.deviceName).isEqualTo("轻衡 S1 智能体脂秤")
    }

    @Test
    fun `取消连接回确认页而不是退出向导`() {
        val connecting = btFlowReduce(
            BtFlowUiState(permission = BtPermissionStatus.Granted, step = BtFlowStep.ConfirmDevice, device = discoveredDeviceOf(found())),
            BtFlowIntent.ConfirmDevice,
        )
        val back = btFlowReduce(connecting, BtFlowIntent.CancelConnect)

        assertThat(back.step).isEqualTo(BtFlowStep.ConfirmDevice)
        assertThat(back.device).isNotNull()
    }
}
