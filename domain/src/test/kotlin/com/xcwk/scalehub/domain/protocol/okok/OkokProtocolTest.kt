package com.xcwk.scalehub.domain.protocol.okok

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.protocol.ScaleEvent
import org.junit.Test
import java.util.UUID

/**
 * okok-1 连接秤解析黄金向量（M3a，DEC-022）。
 * 帧字节按旧库协议文档手工构造，锁定 MeasureFrame/OkokProtocol 的既有行为，
 * 真机数值对拍仍属 G5 硬件项。
 */
class OkokProtocolTest {

    private fun uuid(shortId: Int): UUID =
        UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", shortId and 0xFFFF))

    private val process = uuid(0xFFF1)
    private val result = uuid(0x2A9C)
    private val battery = uuid(0x2A19)

    /** CF 过程帧：[CF][flags][weight LE][impedance LE(0.1Ω)]。flags=0x40 阻抗测量中。 */
    private fun processFrame(weightRaw: Int, impedanceRaw: Int, flags: Int = 0): ByteArray {
        val data = ByteArray(6)
        data[0] = 0xCF.toByte()
        data[1] = flags.toByte()
        data[2] = weightRaw.toByte()
        data[3] = (weightRaw shr 8).toByte()
        data[4] = impedanceRaw.toByte()
        data[5] = (impedanceRaw shr 8).toByte()
        return data
    }

    /** C4 四电极帧：[C4][flags][..6][weight LE@6][impedance LE@8]，长度 20。 */
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

    @Test
    fun `process frame reports live weight while impedance measuring`() {
        OkokProtocol.INSTANCE.reset()
        val event = OkokProtocol.INSTANCE.parse(process, processFrame(6550, 5000, flags = 0x40))
        requireNotNull(event)
        assertThat(event.weightKg).isEqualTo(65.5)
        assertThat(event.impedanceMeasuring).isTrue()
        assertThat(event.locked).isFalse()
        assertThat(event.impedanceOhm).isEqualTo(500.0)
        assertThat(event.kind).isEqualTo(ScaleEvent.KIND_UNKNOWN)
        assertThat(event.label).isEqualTo("测量过程 过程")
    }

    @Test
    fun `result frame is always final lock with impedance`() {
        OkokProtocol.INSTANCE.reset()
        val event = OkokProtocol.INSTANCE.parse(result, fourFrame(6550, 5000, flags = 0x00))
        requireNotNull(event)
        assertThat(event.locked).isTrue()
        assertThat(event.impedanceMeasuring).isFalse()
        assertThat(event.impedanceOhm).isEqualTo(500.0)
        assertThat(event.kind).isEqualTo(ScaleEvent.KIND_FAT)
    }

    @Test
    fun `fat process frame locks only with weight lock and impedance`() {
        OkokProtocol.INSTANCE.reset()
        // 未锁定（flags=0）：不锁
        assertThat(OkokProtocol.INSTANCE.parse(process, fourFrame(6550, 5000, 0x00))!!.locked).isFalse()
        // 锁定位但阻抗为 0：体脂秤口径下不锁
        assertThat(OkokProtocol.INSTANCE.parse(process, fourFrame(6550, 0, 0x01))!!.locked).isFalse()
        // 锁定位 + 阻抗非 0：锁定
        assertThat(OkokProtocol.INSTANCE.parse(process, fourFrame(6550, 5000, 0x01))!!.locked).isTrue()
    }

    @Test
    fun `c0 weight frame locks on process by weight flag`() {
        OkokProtocol.INSTANCE.reset()
        val data = ByteArray(8)
        data[0] = 0xC0.toByte()
        data[1] = 0x01
        data[6] = 0x86.toByte()   // 6534 → 65.34 kg → 65.3
        data[7] = 0x19
        val event = OkokProtocol.INSTANCE.parse(process, data)
        requireNotNull(event)
        assertThat(event.kind).isEqualTo(ScaleEvent.KIND_WEIGHT)
        assertThat(event.weightKg).isEqualTo(65.3)
        assertThat(event.locked).isTrue()
        assertThat(event.impedanceOhm).isEqualTo(0.0)
    }

    @Test
    fun `battery characteristic yields percent`() {
        OkokProtocol.INSTANCE.reset()
        val event = OkokProtocol.INSTANCE.parse(battery, byteArrayOf(0x55))
        requireNotNull(event)
        assertThat(event.batteryPercent).isEqualTo(85)
        assertThat(event.weightKg).isNull()
    }

    @Test
    fun `c9 two packet frame assembles eight electrode heart result`() {
        OkokProtocol.INSTANCE.reset()
        val first = ByteArray(20)
        first[0] = 0xC9.toByte()
        first[1] = 0x20          // count=2 index=0（高 4 位 count、低 4 位 index）
        first[2] = 0x01          // flags 锁定
        val weightRaw = 6550
        first[7] = weightRaw.toByte(); first[8] = (weightRaw shr 8).toByte()
        val z34 = 5000
        first[9] = z34.toByte(); first[10] = (z34 shr 8).toByte()
        val second = ByteArray(20)
        second[0] = 0xC9.toByte()
        second[1] = 0x21         // count=2 index=1
        val z24 = 5300
        second[2] = z24.toByte(); second[3] = (z24 shr 8).toByte()
        second[4] = 72           // 心率

        assertThat(OkokProtocol.INSTANCE.parse(process, first)).isNull()   // 收集中
        val event = OkokProtocol.INSTANCE.parse(process, second)
        requireNotNull(event)
        assertThat(event.kind).isEqualTo(ScaleEvent.KIND_FAT_HR)
        assertThat(event.weightKg).isEqualTo(65.5)
        assertThat(event.impedanceOhm).isEqualTo(500.0)
        assertThat(event.heartRate).isEqualTo(72)
        assertThat(event.label).contains("八电极心率")
    }

    @Test
    fun `c9 with wrong count is discarded`() {
        OkokProtocol.INSTANCE.reset()
        val bad = ByteArray(20)
        bad[0] = 0xC9.toByte()
        bad[1] = 0x31   // count=3 index=1：C9 只认 count=2
        assertThat(OkokProtocol.INSTANCE.parse(process, bad)).isNull()
    }

    @Test
    fun `broadcast advertisement yields weight and impedance measuring flag`() {
        OkokProtocol.INSTANCE.reset()
        // 前缀 3 字节 + 标记 FF F0 起 20 字节
        val record = ByteArray(23)
        record[3] = 0xFF.toByte()
        record[4] = 0xF0.toByte()
        record[5] = 0x11          // 版本 0x11（设备计算部分体成分）
        record[6] = 0x41          // bit6 阻抗测量中 + 锁定位 0
        record[7] = 0x86.toByte() // 6534 → 65.3
        record[8] = 0x19
        val match = OkokProtocol.INSTANCE.match(record)
        requireNotNull(match)
        assertThat(match.protocolId).isEqualTo("okok-1")
        assertThat(match.summary).contains("设备计算部分体成分")
        val event = OkokProtocol.INSTANCE.broadcast(record)
        requireNotNull(event)
        assertThat(event.weightKg).isEqualTo(65.3)
        assertThat(event.impedanceMeasuring).isTrue()
    }

    @Test
    fun `broadcast version 0x02 is not okok`() {
        OkokProtocol.INSTANCE.reset()
        val record = ByteArray(23)
        record[3] = 0xFF.toByte()
        record[4] = 0xF0.toByte()
        record[5] = 0x02          // 芯海云端版保留
        record[6] = 0x01
        record[7] = 0x86.toByte()
        record[8] = 0x19
        assertThat(OkokProtocol.INSTANCE.match(record)).isNull()
    }

    @Test
    fun `required characteristic and subscribe list follow legacy`() {
        assertThat(OkokProtocol.INSTANCE.requiredCharacteristic()).isEqualTo(process)
        assertThat(OkokProtocol.INSTANCE.subscribe().toList()).containsExactly(
            process, result, battery,
        )
        assertThat(OkokProtocol.INSTANCE.missingRequiredMessage()).isEqualTo("没有测量过程特征 0xFFF1")
    }

    @Test
    fun `reset clears partial multi packet state`() {
        OkokProtocol.INSTANCE.reset()
        val first = ByteArray(20)
        first[0] = 0xC9.toByte()
        first[1] = 0x20
        assertThat(OkokProtocol.INSTANCE.parse(process, first)).isNull()
        OkokProtocol.INSTANCE.reset()
        // reset 后旧的 index=1 尾包不应被接受
        val second = ByteArray(20)
        second[0] = 0xC9.toByte()
        second[1] = 0x21
        assertThat(OkokProtocol.INSTANCE.parse(process, second)).isNull()
    }
}
