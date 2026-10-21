package com.xcwk.scalehub.application.measure

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.ble.BleConnectListener
import com.xcwk.scalehub.application.ble.BleIndicateListener
import com.xcwk.scalehub.application.ble.BlePort
import com.xcwk.scalehub.application.ble.BleScanListener
import com.xcwk.scalehub.application.ble.BleWriteListener
import com.xcwk.scalehub.application.service.RecordMeasurementService
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.domain.protocol.ConnectionMode
import com.xcwk.scalehub.domain.protocol.chipsea.ChipseaCloudProtocol
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
        var lastConnectMode: com.xcwk.scalehub.domain.protocol.ConnectionMode? = null
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
        override fun connect(
            mac: String,
            listener: BleConnectListener,
            mode: com.xcwk.scalehub.domain.protocol.ConnectionMode,
        ) {
            connectCalls++; lastConnectMac = mac; lastConnectMode = mode
            connecting = true; connectListener = listener
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
            val algorithm = ScaleDevice.algorithmFor(protocolId, ScaleDevice.ALGORITHM_BUILTIN_1)
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

    private class FakeRepo : MeasurementRepository, com.xcwk.scalehub.application.query.MeasurementQueryPort {
        val saved = mutableListOf<MeasureRecord>()
        override suspend fun save(record: MeasureRecord, memberId: String) { saved += record }
        override suspend fun recent(limit: Int) = saved.reversed().take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) = saved.filter { it.time in fromInc until toExc }
        override suspend fun delete(time: Long) { saved.removeAll { it.time == time } }
    }

    private class FakeProfile : MemberProfileReader {
        override fun male() = true
        override fun ageYears() = 30
        override fun unitPreferences() = com.xcwk.scalehub.domain.model.UserUnitPreferences()
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
            ble, devices, RecordMeasurementService(repo, profile, null, memberId = { "1" }), profile,
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
        // 无绑定设备时给出「去绑定设备」出口（UI 据此渲染直达设备页的按钮）
        assertThat(s.needBind).isTrue()
        assertThat(ble.scans).isEqualTo(0)
    }

    @Test
    fun `disabled bluetooth shows enable hint`() = runTest {
        val ble = FakeBle().apply { enabled = false }
        val session = harness(backgroundScope, FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true))), ble)
        session.start()
        assertThat(session.state.value.title).isEqualTo("请打开蓝牙")
        // 蓝牙未开是另一种等待态，不提供绑定出口
        assertThat(session.state.value.needBind).isFalse()
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

    /**
     * 芯海秤是随机可解析地址——连接必须走 SCANNED_DEVICE（用扫描保留的设备实例）。
     * 若退化成 PUBLIC_ADDRESS，实现层会用 getRemoteDevice(mac) 重建，地址类型判错，
     * 芯海秤永远连不上（上游 BLEManager.kt:188 注释点名的坑）。
     */
    @Test
    fun `chipsea scale connects in scanned device mode`() = runTest {
        val ble = FakeBle()
        val mac = "AA:BB:CC:DD:EE:FF"
        val devices = FakeDevices(
            mutableListOf(ScaleDevice("芯海秤", mac, "2026-10-16", true, ChipseaCloudProtocol.ID, "builtin-1")),
        )
        val session = harness(backgroundScope, devices, ble)
        session.start()
        runCurrent()

        ble.emitFound(mac, byteArrayOf(0x01, 0x02))

        assertThat(ble.connectCalls).isEqualTo(1)
        assertThat(ble.lastConnectMac).isEqualTo(mac)
        assertThat(ble.lastConnectMode).isEqualTo(ConnectionMode.SCANNED_DEVICE)
    }

    /** OKOK 秤是公有地址，仍按地址直连（对照：不能因芯海修复把所有秤都改成扫描实例连接）。 */
    @Test
    fun `okok scale connects in public address mode`() = runTest {
        val ble = FakeBle()
        val mac = "AA:BB:CC:DD:EE:FF"
        val devices = FakeDevices(mutableListOf(ScaleDevice("OKOK 秤", mac, "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble)
        session.start()
        runCurrent()

        ble.emitFound(mac, byteArrayOf(0x01, 0x02))

        assertThat(ble.connectCalls).isEqualTo(1)
        assertThat(ble.lastConnectMode).isEqualTo(ConnectionMode.PUBLIC_ADDRESS)
    }

    @Test
    fun `locked result frame seals and saves with impedance`() = runTest {
        val ble = FakeBle()
        val repo = FakeRepo()
        val profile = FakeProfile()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null, memberId = { "1" }), profile, { 0 }, backgroundScope, { false },
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
            ble, devices, RecordMeasurementService(repo, profile, null, memberId = { "1" }), profile, { 0 }, backgroundScope, { false },
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
            ble, devices, RecordMeasurementService(repo, profile, null, memberId = { "1" }), profile, { 600 }, backgroundScope, { false },
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
            ble, devices, RecordMeasurementService(repo, profile, null, memberId = { "1" }), profile, { 0 }, backgroundScope, { false },
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
            ble, devices, RecordMeasurementService(repo, profile, null, memberId = { "1" }), profile, { 0 }, backgroundScope, { false },
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

    /**
     * 会话结束后迟到的确认不得写库。
     * stop() 只取消定时器、不清 awaitingConfirm，而 finishSave 本身不看 closed——
     * 这条路径编译器不拦，只能靠守卫与测试钉住（DEC-024 P2 步骤 1）。
     */
    @Test
    fun `stop 之后迟到的确认不再写库`() = runTest {
        val ble = FakeBle()
        val repo = FakeRepo()
        val profile = FakeProfile()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null, memberId = { "1" }), profile,
            { 600 }, backgroundScope, { false },
        )
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()
        ble.emit(result2A9C, c0Frame(6550, 0x01))   // 65.5 vs 60.0 → 进入待确认
        runCurrent()
        assertThat(session.state.value.outcome?.kind).isEqualTo(MeasureOutcomeKind.CONFIRM_WEIGHT)

        session.stop()          // 会话结束（页面关闭）
        session.confirmSave()   // 迟到的确认
        runCurrent()
        assertThat(repo.saved).isEmpty()
    }

    @Test
    fun `reconnect 清空上次结果并重新扫描`() = runTest {
        val ble = FakeBle()
        val repo = FakeRepo()
        val profile = FakeProfile()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null, memberId = { "1" }), profile, { 0 }, backgroundScope, { false },
        )
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()
        ble.emit(result2A9C, fourFrame(6550, 5000, 0x00))
        runCurrent()
        assertThat(session.state.value.outcome?.kind).isEqualTo(MeasureOutcomeKind.SAVED)
        assertThat(ble.scans).isEqualTo(1)

        session.reconnect()
        runCurrent()
        assertThat(ble.scans).isEqualTo(2)
        assertThat(session.state.value.outcome).isNull()
        assertThat(session.state.value.phase).isEqualTo(MeasurePhase.WAIT)
    }

    @Test
    fun `retry 在已连接未就绪时重新发起连接`() = runTest {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble)
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        assertThat(ble.connectCalls).isEqualTo(1)
        ble.emitConnectSuccess()   // 已连接但订阅未就绪（linkReady=false）

        session.retry()
        assertThat(ble.connectCalls).isEqualTo(2)
    }

    // ---- 组 E 前置：suppressDisconnect / holdStepOff / 静默重连 ----

    /**
     * reconnect() 会置 suppressDisconnect，用来吞掉「旧链路自己断开」这一次回调。
     * 判据取「是否触发重新扫描」：被吞掉时链路层不该因这次断连再排一次重试；
     * 漏吞则会走 scheduleRetry → retryConnect → watchForScale，扫描计数多一次。
     */
    @Test
    fun `reconnect 后首次断连被吞掉不触发重试扫描`() = runTest {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble)
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()
        assertThat(session.state.value.link).isEqualTo("已连接")

        session.reconnect()          // suppressDisconnect = true
        runCurrent()
        assertThat(ble.scans).isEqualTo(2)   // start() 重新扫描一次

        ble.emitDisconnected()       // 旧链路断开，应被吞掉
        advanceTimeBy(MeasureSession.RETRY_DELAY_MS + 1)
        runCurrent()

        assertThat(ble.scans).isEqualTo(2)   // 没有因这次断连再排一次重试扫描
        assertThat(session.state.value.showRetry).isFalse()
        assertThat(session.state.value.outcome).isNull()
    }

    /**
     * 已连上但还没上秤时断连：延迟 700ms 才弹提示，给锁定结果帧留落地时间。
     * 立即弹会把「结果帧紧跟着断连」的正常时序误判成链路故障。
     */
    @Test
    fun `已连接但未上秤时断连_延迟后弹出重连提示`() = runTest {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble)
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()        // linkReady=true，但还没上秤

        ble.emitDisconnected()
        assertThat(session.state.value.title).isNotEqualTo("连接已断开")   // 先不弹
        advanceTimeBy(MeasureSession.DISCONNECT_PROMPT_DELAY_MS + 1)
        runCurrent()

        val s = session.state.value
        assertThat(s.title).isEqualTo("连接已断开")
        assertThat(s.showRetry).isTrue()
        assertThat(s.link).isEqualTo("已断开")
    }

    /**
     * 已上秤（seenWeight）时订阅失败：走 holdStepOff 回到「请光脚站上秤」，
     * 保持已连接、不弹重连——踩上再拿起时秤常闪断，这是正常时序。
     */
    @Test
    fun `已上秤时订阅失败进入下秤保持而非重连提示`() = runTest {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble)
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()
        ble.emit(fff1, processFrame(6550, 5000, 0x00))   // 站上：65.5kg
        assertThat(session.state.value.phase).isEqualTo(MeasurePhase.LIVE)

        ble.indicate?.onIndicateFailure("订阅中断")

        val s = session.state.value
        assertThat(s.phase).isEqualTo(MeasurePhase.WAIT)
        assertThat(s.title).isEqualTo("请光脚站上秤")
        assertThat(s.notice).isEqualTo("请重新上秤")
        assertThat(s.link).isEqualTo("已连接")
        assertThat(s.outcome).isNull()
    }

    /**
     * 结果已封存（sealed）后的断连不再触发静默重连——
     * 否则保存完成后还会无意义地重连一次，白耗电量并可能与下一次测量抢链路。
     */
    @Test
    fun `密封之后断连不再发起静默重连`() = runTest {
        val ble = FakeBle()
        val repo = FakeRepo()
        val profile = FakeProfile()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null, memberId = { "1" }), profile, { 0 }, backgroundScope, { false },
        )
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()
        ble.emit(result2A9C, fourFrame(6550, 5000, 0x00))
        runCurrent()
        assertThat(session.state.value.outcome?.kind).isEqualTo(MeasureOutcomeKind.SAVED)
        val callsAfterSave = ble.connectCalls

        ble.emitDisconnected()
        advanceTimeBy(MeasureSession.QUIET_RETRY_DELAY_MS + 1)
        runCurrent()

        assertThat(ble.connectCalls).isEqualTo(callsAfterSave)
        assertThat(session.state.value.outcome?.kind).isEqualTo(MeasureOutcomeKind.SAVED)
    }

    // ---- 组 E 前置：阻抗累加与多帧锁定时序 ----

    /**
     * 过程帧只累加阻抗（取峰值），最终帧的阻抗可能是 0——此时必须沿用累加值。
     * 若把累加写成「后帧覆盖」，结果帧的 0 会把峰值抹掉，整次测量退化成无体成分。
     */
    @Test
    fun `过程帧阻抗累加取峰值_结果帧无阻抗时沿用累加值`() = runTest {
        val ble = FakeBle()
        val repo = FakeRepo()
        val profile = FakeProfile()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null, memberId = { "1" }), profile, { 0 }, backgroundScope, { false },
        )
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()

        ble.emit(fff1, processFrame(6550, 4800, 0x40))   // 480Ω
        ble.emit(fff1, processFrame(6550, 5200, 0x40))   // 520Ω（峰值）
        ble.emit(fff1, processFrame(6550, 4900, 0x40))   // 490Ω（回落）
        assertThat(session.state.value.phase).isEqualTo(MeasurePhase.LIVE)
        assertThat(repo.saved).isEmpty()

        ble.emit(result2A9C, fourFrame(6550, 0, 0x00))   // 结果帧本身不带阻抗
        runCurrent()

        assertThat(session.state.value.outcome?.kind).isEqualTo(MeasureOutcomeKind.SAVED)
        assertThat(repo.saved.single().impedance).isEqualTo(520.0)
    }

    /**
     * 过程帧只刷新实时体重，不得提前封存；封存后再来的过程帧不得二次落库。
     * 判据同时盯「落库次数」与「结果体重」：封存过早会让第一次过程帧就写库，
     * 封存后不设防则迟到帧会覆盖已展示的结果。
     */
    @Test
    fun `过程帧只刷新实时值_封存后迟到帧不再落库`() = runTest {
        val ble = FakeBle()
        val repo = FakeRepo()
        val profile = FakeProfile()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null, memberId = { "1" }), profile, { 0 }, backgroundScope, { false },
        )
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()

        ble.emit(fff1, processFrame(6500, 5000, 0x40))
        ble.emit(fff1, processFrame(6600, 5100, 0x40))
        assertThat(session.state.value.phase).isEqualTo(MeasurePhase.LIVE)
        assertThat(session.state.value.weightText).isEqualTo("66.0")
        assertThat(repo.saved).isEmpty()

        ble.emit(result2A9C, fourFrame(6600, 5100, 0x00))
        runCurrent()
        assertThat(repo.saved).hasSize(1)
        val sealedWeight = requireNotNull(session.state.value.outcome).weight

        ble.emit(fff1, processFrame(7000, 5200, 0x40))   // 封存后的迟到过程帧
        runCurrent()

        assertThat(repo.saved).hasSize(1)
        assertThat(session.state.value.outcome?.weight).isEqualTo(sealedWeight)
    }

    /**
     * DEC-038 组D：开扫前先取上次体重——首包用户信息必须已带上次体重（0.1kg LE @7-8）。
     * 旧路径 refreshLastWeight 异步竞态，快速链路（广播秤）下用户帧可能发 0（无效）。
     */
    @Test
    fun `首包用户信息携带上次体重`() = runTest {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble, lastRaw = 600)
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()   // 写时间 → 写用户 → 读电量

        val user = ble.writePayloads.last()
        assertThat(user[0]).isEqualTo(0xA0.toByte())
        assertThat(user[7]).isEqualTo(0x58.toByte())   // 600 LE 低字节
        assertThat(user[8]).isEqualTo(0x02)            // 600 LE 高字节
    }

    /**
     * DEC-038 组A：可选特征（电量）读失败不得弹断开提示。
     * 旧路径 read() 失败复用 onIndicateFailure → linkReady → 700ms 假「连接已断开」。
     */
    @Test
    fun `读失败只提示notice不弹断开`() = runTest {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble)
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()
        assertThat(session.state.value.link).isEqualTo("已连接")

        ble.indicate?.onReadFailure("没有特征 00002a19")
        advanceTimeBy(MeasureSession.DISCONNECT_PROMPT_DELAY_MS * 2 + 1)
        runCurrent()

        val s = session.state.value
        assertThat(s.notice).isEqualTo("没有特征 00002a19")
        assertThat(s.title).isNotEqualTo("连接已断开")
        assertThat(s.showRetry).isFalse()
        assertThat(s.link).isEqualTo("已连接")
    }

    /** DEC-038 组A兜底：链路存活时的订阅失败误报同样不弹断开（isConnected 守卫）。 */
    @Test
    fun `链路存活时订阅失败误报不弹断开`() = runTest {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble)
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()   // FakeBle 保持 connectedMac 非空 → isConnected()==true

        ble.indicate?.onIndicateFailure("CCCD 写失败")
        advanceTimeBy(MeasureSession.DISCONNECT_PROMPT_DELAY_MS * 2 + 1)
        runCurrent()

        assertThat(session.state.value.title).isNotEqualTo("连接已断开")
        assertThat(session.state.value.showRetry).isFalse()
    }

    /**
     * DEC-038 组B：断连提示弹出后「重新连接」必须真正重连。
     * 旧路径 phase 停在 Linked，retryConnect 被 linkReady 守卫拦成死按钮。
     */
    @Test
    fun `断连提示后重试真正发起重连`() = runTest {
        val ble = FakeBle()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = harness(backgroundScope, devices, ble)
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()
        assertThat(ble.connectCalls).isEqualTo(1)

        ble.emitDisconnected()   // 已连接未上秤时断连
        advanceTimeBy(MeasureSession.DISCONNECT_PROMPT_DELAY_MS + 1)
        runCurrent()
        assertThat(session.state.value.showRetry).isTrue()

        session.retry()
        runCurrent()
        assertThat(ble.connectCalls).isEqualTo(2)   // 死按钮修复判据
    }

    /** DEC-038 组C：确认保存连点只落库一次（sealed.saved 守卫）。 */
    @Test
    fun `确认保存连点只落库一次`() = runTest {
        val ble = FakeBle()
        val repo = FakeRepo()
        val profile = FakeProfile()
        val devices = FakeDevices(mutableListOf(ScaleDevice("秤", "AA:BB:CC:DD:EE:FF", "2026-10-16", true)))
        val session = MeasureSession(
            ble, devices, RecordMeasurementService(repo, profile, null, memberId = { "1" }), profile,
            { 600 }, backgroundScope, { false },
        )
        session.start(); runCurrent()
        ble.emitFound("AA:BB:CC:DD:EE:FF", byteArrayOf(0x01))
        ble.emitConnectSuccess()
        ble.indicateSuccess()
        ble.emit(result2A9C, c0Frame(6550, 0x01))   // 65.5 vs 60.0 → 待确认
        runCurrent()
        assertThat(session.state.value.outcome?.kind).isEqualTo(MeasureOutcomeKind.CONFIRM_WEIGHT)

        session.confirmSave()
        session.confirmSave()   // 连点第二次
        runCurrent()

        assertThat(repo.saved).hasSize(1)
        assertThat(session.state.value.outcome?.kind).isEqualTo(MeasureOutcomeKind.SAVED)
    }
}
