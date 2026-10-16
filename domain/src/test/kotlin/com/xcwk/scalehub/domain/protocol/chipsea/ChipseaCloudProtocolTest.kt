package com.xcwk.scalehub.domain.protocol.chipsea

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.protocol.ScaleEvent
import org.junit.Test
import java.util.UUID

/**
 * chipsea-cloud-1 解析黄金向量（M3a，DEC-022）。
 * 锁定标记识别（FF F0/F0 FF + 版本 0x02、OKOK 0x10/0x11 排除）、
 * own MAC 正反序、20 字节测量帧与表一单位换算。
 */
class ChipseaCloudProtocolTest {

    private fun uuid(shortId: Int): UUID =
        UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", shortId and 0xFFFF))

    private val process = uuid(0xFFF1)
    private val result = uuid(0x2A9C)

    /** AD 结构广播：[0x15][0xFF][20 字节厂商数据：FF F0 02 property weightLE .. MAC×6]。 */
    private fun advertisement(property: Int, weightRaw: Int, mac: ByteArray): ByteArray {
        val record = ByteArray(2 + 22)
        record[0] = 0x15
        record[1] = 0xFF.toByte()
        record[2] = 0xFF.toByte()
        record[3] = 0xF0.toByte()
        record[4] = 0x02
        record[5] = property.toByte()
        record[6] = weightRaw.toByte()
        record[7] = (weightRaw shr 8).toByte()
        System.arraycopy(mac, 0, record, 2 + 2 + 12, 6)   // 厂商字段第 14 字节起
        return record
    }

    /** 20 字节测量帧：flags LE@0、电阻1 LE@9、体重 LE@11、电阻2 LE@13、表一 @15。 */
    private fun frame(flagsLe: Int, resistance1: Int, weightRaw: Int, resistance2: Int, property: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = flagsLe.toByte()
        data[1] = (flagsLe shr 8).toByte()
        data[9] = resistance1.toByte()
        data[10] = (resistance1 shr 8).toByte()
        data[11] = weightRaw.toByte()
        data[12] = (weightRaw shr 8).toByte()
        data[13] = resistance2.toByte()
        data[14] = (resistance2 shr 8).toByte()
        data[15] = property.toByte()
        return data
    }

    @Test
    fun `match recognizes chipsea advertisement with kilograms summary`() {
        val mac = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val match = ChipseaCloudProtocol.INSTANCE.match(advertisement(0x00, 6550, mac))
        requireNotNull(match)
        assertThat(match.protocolId).isEqualTo("chipsea-cloud-1")
        assertThat(match.summary).isEqualTo("65.5 kg  云端版")
    }

    @Test
    fun `okok vendor version 0x10 and 0x11 are never chipsea`() {
        val mac = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        for (version in listOf(0x10, 0x11)) {
            val record = advertisement(0x00, 6550, mac)
            record[4] = version.toByte()
            assertThat(ChipseaCloudProtocol.INSTANCE.match(record)).isNull()
        }
    }

    @Test
    fun `owns accepts forward and reverse mac`() {
        val macBytes = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66.toByte())
        val record = advertisement(0x00, 6550, macBytes)
        assertThat(ChipseaCloudProtocol.INSTANCE.owns(record, "11:22:33:44:55:66")).isTrue()
        assertThat(ChipseaCloudProtocol.INSTANCE.owns(record, "66:55:44:33:22:11")).isTrue()
        assertThat(ChipseaCloudProtocol.INSTANCE.owns(record, "AA:BB:CC:DD:EE:FF")).isFalse()
    }

    @Test
    fun `process frame parses weight and first resistance`() {
        // flags 0x0306：双电阻体脂；体重 6550=65.5kg；电阻1 5000=500.0Ω
        val event = ChipseaCloudProtocol.INSTANCE.parse(process, frame(0x0306, 5000, 6550, 4900, 0x00))
        requireNotNull(event)
        assertThat(event.weightKg).isEqualTo(65.5)
        assertThat(event.impedanceOhm).isEqualTo(500.0)
        assertThat(event.kind).isEqualTo(ScaleEvent.KIND_FAT)
        assertThat(event.locked).isFalse()
        assertThat(event.impedanceMeasuring).isTrue()
    }

    @Test
    fun `result frame is locked when weight positive`() {
        val event = ChipseaCloudProtocol.INSTANCE.parse(result, frame(0x0306, 5000, 6550, 0, 0x00))
        requireNotNull(event)
        assertThat(event.locked).isTrue()
        assertThat(event.impedanceMeasuring).isFalse()
        assertThat(event.label).startsWith("锁定 ")
    }

    @Test
    fun `weight only flags give weight kind without impedance`() {
        val event = ChipseaCloudProtocol.INSTANCE.parse(process, frame(0x0002, 0, 6550, 0, 0x00))
        requireNotNull(event)
        assertThat(event.kind).isEqualTo(ScaleEvent.KIND_WEIGHT)
        assertThat(event.impedanceOhm).isEqualTo(0.0)
    }

    @Test
    fun `table one units convert jin and pounds`() {
        // unit=1（斤）decimalCode=0→2 位：6550=65.50 斤 → 32.75 → 32.8 kg
        val jin = ChipseaCloudProtocol.INSTANCE.parse(process, frame(0x0006, 0, 6550, 0, 0x08))
        requireNotNull(jin)
        assertThat(jin.weightKg).isEqualTo(32.8)
        // unit=2（磅）2 位：10000=100.00 lb → 45.359 → 45.4 kg
        val pound = ChipseaCloudProtocol.INSTANCE.parse(process, frame(0x0006, 0, 10000, 0, 0x10))
        requireNotNull(pound)
        assertThat(pound.weightKg).isEqualTo(45.4)
    }

    @Test
    fun `unit switch writes kilogram bytes`() {
        assertThat(ChipseaCloudProtocol.INSTANCE.userPacket(1, true, 30, 175, 655))
            .isEqualTo(byteArrayOf(0x01, 0x00))
        assertThat(ChipseaCloudProtocol.INSTANCE.id()).isEqualTo("chipsea-cloud-1")
        assertThat(ChipseaCloudProtocol.INSTANCE.missingRequiredMessage())
            .isEqualTo("没有非锁定数据特征 0xFFF1")
    }
}
