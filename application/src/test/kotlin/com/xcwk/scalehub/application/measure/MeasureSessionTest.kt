package com.xcwk.scalehub.application.measure

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.ble.BleConnectListener
import com.xcwk.scalehub.application.ble.BleIndicateListener
import com.xcwk.scalehub.application.ble.BlePort
import com.xcwk.scalehub.application.ble.BleScanListener
import com.xcwk.scalehub.application.ble.BleWriteListener
import com.xcwk.scalehub.application.service.RecordMeasurementService
import com.xcwk.scalehub.domain.algorithm.OkokBia
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.domain.repository.DeviceRepository
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

/**
 * 测量会话编排行为（M3b，DEC-022）—— 假 BlePort 驱动旧库 MeasureActivity 状态机：
 * 识别绑定秤/连接订阅/时间用户下发/锁定封存/确认流/超时归零/拿起重连。
 */
class MeasureSessionTest {

    private class FakeBle : BlePort {
        var enabled = true
        var scanListener: BleScanListener? = null
        var scans = 0
        var connectListener: BleConnectListener? = null
        var connecting = false
        var connectedMac: String? = null
        var lastConnectMac: String? = null
        var connectCalls = 0
        var subscribed: List<UUID> = emptyList()
        var required: UUID? = null
        var indicate: BleIndicateListener? = null
        val writes = mutableListOf<UUID?>()
        var writePayloads = mutableListOf<ByteArray>()
        val reads = mutableListOf<UUID>()
        var disconnects = 0

        override fun isBluetoothEnabled() = enabled
        override fun scan(listener: BleScanListener) { scans++; scanListener = listener }
        override fun stopScan() { scanListener = null }
        override fun connect(mac: String, listener: BleConnectListener) {
            connectCalls++; lastConnectMac = mac; connecting = true; connectListener = listener
        }

        override fun isConnected() = connectedMac != null
        override fun isConnecting() = connecting
        override fun disconnect() { disconnects++; connectedMac = null; connecting = false }
        override fun subscribe(
            characteristics: List<UUID>,
            required: UUID?,
            missingMessage: String,
            listener: BleIndicateListener,
        ) {
            subscribed = characteristics; this.required = required; indicate = listener
        }

        override fun write(characteristic: UUID?, data: ByteArray, listener: BleWriteListener?) {
            writes += characteristic; writePayloads += data; listener?.onWriteSuccess()
        }

        override fun read(characteristic: UUID) { reads += characteristic }

        fun emitFound(mac: String, record: ByteArray) {
            scanListener?.onDeviceFound("智能秤", mac, -60, record)
        }

        fun emitConnectSuccess() {
            connecting = false
            connectedMac = lastConnectMac
            connectListener?.onConnectSuccess(lastConnectMac ?: "", emptyList())
        }

        fun emitDisconnected() {
            val mac = connectedMac ?: lastConnectMac ?: ""
            connectedMac = null; connecting = false
            connectListener?.onDisConnected(mac)
        }

        fun emit(char: UUID, data: ByteArray) = indicate?.onCharacteristicChanged(char, data)
        fun indicateSuccess() = indicate?.onIndicateSuccess()
    }

    private class FakeDevices(var list: MutableList<ScaleDevice> = mutableListOf()) : DeviceRepository {
        override fun devices() = list.toList()
        override fun contains(mac: String) = list.any { it.mac.equals(mac, ignoreCase = true) }
        override fun device(mac: String) = list.firstOrNull { it.mac.equals(mac, ignoreCase = true) }
        override fun bind(name: String, mac: String, protocolId: String): ScaleDevice? {
            if (mac.isEmpty()) return null
            val algorithm = if (OkokBia.uses(protocolId)) OkokBia.ID else ScaleDevice.ALGORITHM_BUILTIN_1
            val existing = device(mac)
            val boundAt = if (existing == null || existing.boundAt.isEmpty()) "2026-10-16" else existing.boundAt
            val updated = ScaleDevice(name, mac, boundAt, true, protocolId, algorithm)
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

    private class FakeRepo : MeasurementRepository {
        val saved = mutableListOf<MeasureRecord>()
        override suspend fun save(record: MeasureRecord) { saved += record }
        override suspend fun recent(limit: Int) = saved.reversed().take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) = saved.filter { it.time in fromInc until toExc }
        override suspend fun delete(time: Long) { saved.removeAll { it.time == time } }
    }

    private class FakeProfile : MemberProfileReader {
        override fun male() = true
        override fun ageYears() = 30
        override fun heightCm() = 175
        override fun birthYear() = 1996
        override fun birthMonth() = 5
        override fun birthDay() = 20
        override fun nickname() = "测试"
    }

    private fun uuid(shortId: Int): UUID =
        UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", shortId and 0xFFFF))

    private val fff1 = uuid(0xFFF1)
    private val result2A9C = uuid(0x2A9C)
    private val battery2A19 = uuid(0x2A19)
    private val time2A08 = uuid(0x2A08)
    private val userFFF2 = uuid(0xFFF2)

    private fun harness(
        scope: kotlinx.coroutines.CoroutineScope,
        devices: FakeDevices,
        ble: FakeBle = FakeBle(),
        lastRaw: Int = 0,
        skipPrompt: Boolean = false,
    ): MeasureSession {
        val repo = FakeRepo()
        val profile = FakeProfile()
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null), profile,
            { lastRaw }, scope, { skipPrompt },
        )
        return session
    }

    /** C4 四电极帧（20 字节）：flags/体重 LE@6/阻抗 LE@8。 */
    private fun fourFrame(weightRaw: Int, impedanceRaw: Int, flags: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xC4.toByte()
        data[1] = flags.toByte()
        data[6] = weightRaw.toByte()
        data[7] = (weightRaw shr 8).toByte()
        data[8] = impedanceRaw.toByte()
        data[9] = (impedanceRaw shr 8).toByte()
        return data
    }

    /** C0 体重秤帧（8 字节）：体重 LE@6。 */
    private fun c0Frame(weightRaw: Int, flags: Int): ByteArray {
        val data = ByteArray(8)
        data[0] = 0xC0.toByte()
        data[1] = flags.toByte()
        data[6] = weightRaw.toByte()
        data[7] = (weightRaw shr 8).toByte()
        return data
    }

    /** CF 过程帧（6 字节）：flags/体重 LE/阻抗 LE。 */
    private fun processFrame(weightRaw: Int, impedanceRaw: Int, flags: Int): ByteArray {
        val data = ByteArray(6)
        data[0] = 0xCF.toByte()
        data[1] = flags.toByte()
        data[2] = weightRaw.toByte()
        data[3] = (weightRaw shr 8).toByte()
        data[4] = impedanceRaw.toByte()
        data[5] = (impedanceRaw shr 8).toByte()
        return data
    }

    /** okok-broadcast-1 广播：[0x10][0xFF][C0 .. weight BE@2..3 电阻 BE@4..5 flags@8]。 */
    private fun broadcastRecord(weightRaw: Int, resistanceRaw: Int, flags: Int): ByteArray {
        val record = ByteArray(17)
        record[0] = 0x10
        record[1] = 0xFF.toByte()
        record[2] = 0xC0.toByte()
        record[4] = (weightRaw shr 8).toByte()
        record[5] = weightRaw.toByte()
        record[6] = (resistanceRaw shr 8).toByte()
        record[7] = resistanceRaw.toByte()
        record[10] = flags.toByte()
        return record
    }

    @Test
    fun `empty device list shows bind hint without scanning`() = runTest {
        val ble = FakeBle()
        val session = harness(backgroundScope, FakeDevices(), ble)
        session.start()
        val s = session.state.value
        assertThat(s.title).isEqualTo("无法连接")
        assertThat(s.desc).isEqualTo("请先绑定体脂秤")
        assertThat(s.link).isEqualTo("未连接")
        assertThat(ble.scans).isEqualTo(0)
    }

    @Test
    fun `disabled bluetooth shows enable hint`() = runTest {
        val ble = FakeBle().apply { enabled = false }
        val session = harness(backgroundScope, FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true))), ble)
        session.start()
        assertThat(session.state.value.title).isEqualTo("请打开蓝牙")
        assertThat(ble.scans).isEqualTo(0)
    }

    @Test
    fun `bound scale connects subscribes and writes time then user`() = runTest {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble)
        session.start()
        runCurrent()

        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01, 0x02))
        assertThat(ble.connectCalls).isEqualTo(1)
        assertThat(ble.lastConnectMac).isEqualTo("AA:BB:CC:DD:EE:FF")

        ble.emitConnectSuccess()
        assertThat(ble.subscribed).containsExactly(fff1, result2A9C, battery2A19).inOrder()
        assertThat(ble.required).isEqualTo(fff1)

        ble.indicateSuccess()
        // 旧库时序：先写时间 2A08，再写用户 FFF2，成功后读电量 2A19（:263-302）
        assertThat(ble.writes).containsExactly(time2A08, userFFF2).inOrder()
        assertThat(ble.reads).containsExactly(battery2A19).inOrder()
        assertThat(session.state.value.link).isEqualTo("已连接")
        assertThat(session.state.value.phase).isEqualTo(MeasurePhase.LIVE)
    }

    @Test
    fun `locked result frame seals and saves with impedance`() = runTest {
        val ble = FakeBle()
        val repo = FakeRepo()
        val profile = FakeProfile()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null), profile, { 0 }, backgroundScope, { false },
        )
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()

        ble.emit(result2A9C, fourFrame(6550, 5000, 0x00))   // 65.5kg + 500Ω 锁定结果
        runCurrent()
        val outcome = requireNotNull(session.state.value.outcome)
        assertThat(outcome.kind).isEqualTo(MeasureOutcomeKind.SAVED)
        assertThat(outcome.weight).isEqualTo("65.5")
        val record = repo.saved.single()
        assertThat(record.weight).isEqualTo(65.5)
        assertThat(record.impedance).isEqualTo(500.0)
        assertThat(record.fat).isAtLeast(0.0)
        assertThat(record.mac).isEqualTo("AA:BB:CC:DD:EE:FF")
        // M4：查看报告入口需要落库时间戳随 outcome 传出
        assertThat(outcome.recordTime).isEqualTo(record.time)
    }

    @Test
    fun `weight below ten kilograms is rejected`() = runTest {
        val ble = FakeBle()
        val repo = FakeRepo()
        val profile = FakeProfile()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null), profile, { 0 }, backgroundScope, { false },
        )
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()

        ble.emit(result2A9C, fourFrame(500, 0, 0x01))   // 5.0kg
        runCurrent()
        assertThat(session.state.value.outcome?.kind).isEqualTo(MeasureOutcomeKind.TOO_LIGHT)
        assertThat(repo.saved).isEmpty()
    }

    @Test
    fun `weight scale far delta awaits confirm then saves`() = runTest {
        val ble = FakeBle()
        val repo = FakeRepo()
        val profile = FakeProfile()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null), profile, { 600 }, backgroundScope, { false },
        )
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()

        ble.emit(result2A9C, c0Frame(6550, 0x01))   // 65.5 vs 上次 60.0 → 相差 5.5kg
        runCurrent()
        val confirm = requireNotNull(session.state.value.outcome)
        assertThat(confirm.kind).isEqualTo(MeasureOutcomeKind.CONFIRM_WEIGHT)
        assertThat(confirm.confirmable).isTrue()
        assertThat(confirm.extra).contains("5.5")
        assertThat(repo.saved).isEmpty()

        session.confirmSave()
        runCurrent()
        assertThat(session.state.value.outcome?.kind).isEqualTo(MeasureOutcomeKind.SAVED)
        assertThat(repo.saved.single().impedance).isEqualTo(0.0)   // 体重-only
    }

    @Test
    fun `fat scale locked without impedance awaits confirm as weight only`() = runTest {
        val ble = FakeBle()
        val repo = FakeRepo()
        val profile = FakeProfile()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null), profile, { 0 }, backgroundScope, { false },
        )
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()

        ble.emit(result2A9C, fourFrame(6550, 0, 0x01))   // 锁定但无阻抗
        runCurrent()
        assertThat(session.state.value.outcome?.kind).isEqualTo(MeasureOutcomeKind.NO_COMPOSITION)
        session.confirmSave()
        runCurrent()
        assertThat(repo.saved.single().impedance).isEqualTo(0.0)
        assertThat(repo.saved.single().fat).isEqualTo(-1.0)   // 未计算哨兵
    }

    @Test
    fun `countdown timeout shows no data and disconnects`() = runTest {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble)
        session.start()
        advanceTimeBy(31_000)
        runCurrent()
        assertThat(session.state.value.outcome?.kind).isEqualTo(MeasureOutcomeKind.NO_DATA)
        assertThat(ble.disconnects).isAtLeast(1)
    }

    @Test
    fun `zero frame steps off and quiet reconnect runs in background`() = runTest {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble)
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()

        ble.emit(fff1, processFrame(6550, 5000, 0x00))   // 站上：65.5kg
        assertThat(session.state.value.phase).isEqualTo(MeasurePhase.LIVE)
        ble.emit(fff1, processFrame(0, 0, 0x00))          // 拿起
        advanceTimeBy(MeasureSession.ZERO_DELAY_MS + 1)
        runCurrent()
        val s = session.state.value
        assertThat(s.phase).isEqualTo(MeasurePhase.WAIT)
        assertThat(s.notice).isEqualTo("请重新上秤")

        ble.emitDisconnected()                            // 闪断 → holdStepOff，不弹重连提示
        assertThat(session.state.value.outcome).isNull()
        advanceTimeBy(MeasureSession.QUIET_RETRY_DELAY_MS + 1)
        runCurrent()
        assertThat(ble.connectCalls).isEqualTo(2)         // 后台把链路接回来
    }

    @Test
    fun `broadcast-only scale saves without connecting`() = runTest {
        val ble = FakeBle()
        val repo = FakeRepo()
        val profile = FakeProfile()
        val devices = FakeDevices(mutableListOf(ScaleDevice("广播秤", "66:55:44:33:22:11", "2026-10-16", true, "okok-broadcast-1", "builtin-1")))
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null), profile, { 0 }, backgroundScope, { false },
        )
        session.start(); runCurrent()

        val record = broadcastRecord(655, 5000, 0x21)   // 65.5kg 锁定 + 电阻
        ble.emitFound("66:55:44:33:22:11", record)
        runCurrent()
        assertThat(ble.connectCalls).isEqualTo(0)       // 纯广播秤不连接
        assertThat(session.state.value.outcome?.kind).isEqualTo(MeasureOutcomeKind.SAVED)
        assertThat(repo.saved.single().weight).isEqualTo(65.5)
    }

    @Test
    fun `scan failure surfaces notice`() = runTest {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble)
        session.start(); runCurrent()
        ble.scanListener?.onScanFailed("没有蓝牙权限")
        assertThat(session.state.value.notice).isEqualTo("没有蓝牙权限")
    }
}
