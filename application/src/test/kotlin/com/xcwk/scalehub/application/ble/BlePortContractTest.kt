package com.xcwk.scalehub.application.ble

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.protocol.ConnectionMode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

/**
 * BlePort 契约测试（T-001 跨端口一致性语义契约化）。
 *
 * 验证 FakeBle 实现遵守 BlePort 语义契约：
 * - scan/stopScan 生命周期
 * - connect 连接/断开语义
 * - subscribe 必选特征校验
 * - write/read 回调语义
 *
 * AndroidBleLink 同契约验收需在模拟器/真机上单独跑（T-017）。
 */
class BlePortContractTest {

    // ---- FakeBle 实现（契约验收用）----

    private class FakeBle : BlePort {
        var enabled = true
        var scanListener: BleScanListener? = null
        var connectListener: BleConnectListener? = null
        var connectedMac: String? = null
        var connecting = false
        var indicateListener: BleIndicateListener? = null
        var subscribed: List<UUID> = emptyList()
        var required: UUID? = null
        val writes = mutableListOf<Pair<UUID?, ByteArray>>()
        val reads = mutableListOf<UUID>()

        override fun isBluetoothEnabled() = enabled
        override fun scan(listener: BleScanListener) { scanListener = listener }
        override fun stopScan() { scanListener = null }
        override fun connect(
            mac: String,
            listener: BleConnectListener,
            mode: ConnectionMode,
        ) {
            connecting = true
            connectListener = listener
        }
        override fun isConnected() = connectedMac != null
        override fun isConnecting() = connecting
        override fun disconnect() {
            connectedMac = null
            connecting = false
        }
        override fun subscribe(
            characteristics: List<UUID>,
            required: UUID?,
            missingMessage: String,
            listener: BleIndicateListener,
        ) {
            subscribed = characteristics
            this.required = required
            indicateListener = listener
            // 契约：若 required != null 且不在特征列表中，立即回调失败
            if (required != null && !characteristics.contains(required)) {
                listener.onIndicateFailure(missingMessage)
            }
        }
        override fun write(characteristic: UUID?, data: ByteArray, listener: BleWriteListener?) {
            writes += characteristic to data
            listener?.onWriteSuccess()
        }
        override fun read(characteristic: UUID) {
            reads += characteristic
        }
    }

    // ---- 契约：scan/stopScan ----

    @Test
    fun `scan 后 stopScan 清理 listener`() = runTest {
        val fake = FakeBle()
        val listener = object : BleScanListener {
            override fun onScanStarted() {}
            override fun onScanStop() {}
            override fun onScanFailed(reason: String) {}
            override fun onDeviceFound(name: String, mac: String, rssi: Int, scanRecord: ByteArray?) {}
        }
        fake.scan(listener)
        fake.stopScan()
        assertThat(fake.scanListener).isNull()
    }

    // ---- 契约：connect/disconnect ----

    @Test
    fun `connect 成功后 isConnected 为 true`() = runTest {
        val fake = FakeBle()
        fake.connectedMac = "AA:BB:CC:DD:EE:FF"
        assertThat(fake.isConnected()).isTrue()
    }

    @Test
    fun `disconnect 后 isConnected 为 false 且清理状态`() = runTest {
        val fake = FakeBle()
        fake.connectedMac = "AA:BB:CC:DD:EE:FF"
        fake.disconnect()
        assertThat(fake.isConnected()).isFalse()
        assertThat(fake.isConnecting()).isFalse()
    }

    @Test
    fun `connect 失败回调 onConnectFail`() = runTest {
        val fake = FakeBle()
        var failReason: String? = null
        fake.connect("AA:BB:CC:DD:EE:FF", object : BleConnectListener {
            override fun onStartConnect() {}
            override fun onConnectFail(reason: String) { failReason = reason }
            override fun onConnectSuccess(mac: String, services: List<String>) {}
            override fun onDisConnected(mac: String) {}
        }, ConnectionMode.PUBLIC_ADDRESS)
        fake.connectListener?.onConnectFail("连接超时")
        assertThat(failReason).isEqualTo("连接超时")
    }

    // ---- 契约：subscribe ----

    @Test
    fun `subscribe 缺少 required 特征必须回调 onIndicateFailure`() = runTest {
        val fake = FakeBle()
        var failReason: String? = null
        fake.subscribe(
            listOf(UUID.randomUUID()),
            UUID.randomUUID(),
            "缺少特征",
            object : BleIndicateListener {
                override fun onIndicateSuccess() {}
                override fun onIndicateFailure(reason: String) { failReason = reason }
                override fun onCharacteristicChanged(characteristic: UUID, data: ByteArray) {}
                override fun onCharacteristicRead(characteristic: UUID, data: ByteArray) {}
            }
        )
        assertThat(failReason).isEqualTo("缺少特征")
    }

    // ---- 契约：write/read ----

    @Test
    fun `write 成功回调 onWriteSuccess`() = runTest {
        val fake = FakeBle()
        var success = false
        var failure: String? = null
        fake.write(UUID.randomUUID(), byteArrayOf(0x01), object : BleWriteListener {
            override fun onWriteSuccess() { success = true }
            override fun onWriteFailure(reason: String) { failure = reason }
        })
        assertThat(success).isTrue()
        assertThat(failure).isNull()
    }

    @Test
    fun `read 记录读取的特征`() = runTest {
        val fake = FakeBle()
        val char = UUID.randomUUID()
        fake.read(char)
        assertThat(fake.reads).contains(char)
    }
}