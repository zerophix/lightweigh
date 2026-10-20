package com.xcwk.scalehub.feature.device

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.ble.BlePort
import com.xcwk.scalehub.application.ble.BleScanListener
import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.domain.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 设备页扫描与绑定行为（M3d，DEC-022）：
 * 协议过滤（只有 Protocols 认出的广播进列表）、已绑定标记、绑定切当前、15s 超时停止。
 */
class DeviceViewModelTest {

    companion object {
        private lateinit var scheduler: kotlinx.coroutines.test.TestCoroutineScheduler

        @org.junit.BeforeClass
        @JvmStatic
        fun setupMain() {
            val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
            scheduler = dispatcher.scheduler
            Dispatchers.setMain(dispatcher)
        }

        @org.junit.AfterClass
        @JvmStatic
        fun tearDownMain() {
            Dispatchers.resetMain()
        }
    }

    private class FakeBle : BlePort {
        var listener: BleScanListener? = null
        var scans = 0
        var stops = 0
        override fun isBluetoothEnabled() = true
        override fun scan(listener: BleScanListener) { scans++; this.listener = listener }
        override fun stopScan() {
            stops++
            listener?.onScanStop()   // 与 AndroidBleLink 契约一致：停止即回调 onScanStop
            listener = null
        }
        override fun connect(
            mac: String,
            listener: com.xcwk.scalehub.application.ble.BleConnectListener,
            mode: com.xcwk.scalehub.domain.protocol.ConnectionMode,
        ) = Unit
        override fun isConnected() = false
        override fun isConnecting() = false
        override fun disconnect() = Unit
        override fun subscribe(
            characteristics: List<java.util.UUID>,
            required: java.util.UUID?,
            missingMessage: String,
            listener: com.xcwk.scalehub.application.ble.BleIndicateListener,
        ) = Unit

        override fun write(characteristic: java.util.UUID?, data: ByteArray, listener: com.xcwk.scalehub.application.ble.BleWriteListener?) = Unit
        override fun read(characteristic: java.util.UUID) = Unit
    }

    private class FakeDevices(var list: MutableList<ScaleDevice> = mutableListOf()) : DeviceRepository {
        override fun devices() = list.toList()
        override fun contains(mac: String) = list.any { it.mac.equals(mac, ignoreCase = true) }
        override fun device(mac: String) = list.firstOrNull { it.mac.equals(mac, ignoreCase = true) }
        override fun bind(name: String, mac: String, protocolId: String): ScaleDevice? {
            val existing = device(mac)
            val updated = ScaleDevice(name, mac, existing?.boundAt ?: "2026-10-16", true, protocolId, "builtin-1")
            list = list.map { if (it.mac.equals(mac, true)) updated else it.withActive(false) }.toMutableList()
            if (existing == null) list.add(updated)
            return updated
        }

        override fun activate(mac: String) {
            list = list.map { it.withActive(it.mac.equals(mac, true)) }.toMutableList()
        }

        override fun unbind(mac: String) {
            list = list.filterNot { it.mac.equals(mac, true) }.toMutableList()
        }

        override fun restoreAll(devices: List<ScaleDevice>) {
            list = devices.toMutableList()
        }
    }

    /** okok 广播字段：[0x10][0xFF][C0 .. weight BE@4..5 flags@10]。 */
    private fun okokRecord(weightRaw: Int = 655, flags: Int = 0x01): ByteArray {
        val record = ByteArray(17)
        record[0] = 0x10
        record[1] = 0xFF.toByte()
        record[2] = 0xC0.toByte()
        record[4] = (weightRaw shr 8).toByte()
        record[5] = weightRaw.toByte()
        record[10] = flags.toByte()
        return record
    }

    @Test
    fun `scan keeps only protocol recognized broadcasts and marks bound`() = runTest(scheduler) {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("旧秤", "66:55:44:33:22:11", "2026-10-01", true)))
        val vm = DeviceViewModel(ble, devices)
        vm.onIntent(DeviceIntent.ToggleScan)
        runCurrent()

        ble.listener?.onDeviceFound("噪声设备", "11:22:33:44:55:66", -70, byteArrayOf(0x01, 0x02))
        ble.listener?.onDeviceFound("已绑秤", "66:55:44:33:22:11", -50, okokRecord())
        ble.listener?.onDeviceFound("新秤", "AA:BB:CC:DD:EE:FF", -40, okokRecord())

        val found = vm.state.value.found
        assertThat(found).hasSize(2)   // 噪声被协议层过滤
        assertThat(found.first { it.mac == "66:55:44:33:22:11" }.alreadyBound).isTrue()
        assertThat(found.first { it.mac == "AA:BB:CC:DD:EE:FF" }.alreadyBound).isFalse()
        assertThat(found.first().protocolId).isEqualTo("okok-broadcast-1")
        assertThat(vm.state.value.notice).isEqualTo("发现 2 台体脂秤")
    }

    @Test
    fun `bind switches current device and reports toast text`() = runTest(scheduler) {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("旧秤", "66:55:44:33:22:11", "2026-10-01", true)))
        val vm = DeviceViewModel(ble, devices)
        val found = FoundScale("新秤", "AA:BB:CC:DD:EE:FF", -40, "65.5 kg", "okok-broadcast-1", false)
        vm.onIntent(DeviceIntent.Bind(found))
        assertThat(vm.state.value.message).isEqualTo("已绑定 新秤")
        assertThat(devices.device("AA:BB:CC:DD:EE:FF")!!.active).isTrue()
        assertThat(devices.device("66:55:44:33:22:11")!!.active).isFalse()
        assertThat(vm.state.value.bound).hasSize(2)
    }

    @Test
    fun `scan timeout stops scanning after fifteen seconds`() = runTest(scheduler) {
        val ble = FakeBle()
        val vm = DeviceViewModel(ble, FakeDevices())
        vm.onIntent(DeviceIntent.ToggleScan)
        runCurrent()
        assertThat(vm.state.value.scanning).isTrue()

        advanceTimeBy(DeviceViewModel.SCAN_TIMEOUT_MS + 1)
        runCurrent()
        assertThat(ble.stops).isEqualTo(1)
        assertThat(vm.state.value.scanning).isFalse()
    }

    @Test
    fun `toggle again stops scan immediately`() = runTest(scheduler) {
        val ble = FakeBle()
        val vm = DeviceViewModel(ble, FakeDevices())
        vm.onIntent(DeviceIntent.ToggleScan)
        runCurrent()
        vm.onIntent(DeviceIntent.ToggleScan)
        assertThat(vm.state.value.scanning).isFalse()
        assertThat(ble.stops).isEqualTo(1)
    }

    @Test
    fun `unbind removes and activate switches`() = runTest(scheduler) {
        val devices = FakeDevices(
            mutableListOf(
                ScaleDevice("甲", "AA:BB:CC:DD:EE:FF", "2026-10-01", true),
                ScaleDevice("乙", "66:55:44:33:22:11", "2026-10-02", false),
            ),
        )
        val vm = DeviceViewModel(FakeBle(), devices)
        vm.onIntent(DeviceIntent.Activate("66:55:44:33:22:11"))
        assertThat(vm.state.value.bound.first { it.mac == "66:55:44:33:22:11" }.active).isTrue()
        vm.onIntent(DeviceIntent.Unbind("66:55:44:33:22:11"))
        assertThat(vm.state.value.bound).hasSize(1)
    }
}
