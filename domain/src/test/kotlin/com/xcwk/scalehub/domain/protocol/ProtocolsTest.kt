package com.xcwk.scalehub.domain.protocol

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.protocol.chipsea.ChipseaCloudProtocol
import com.xcwk.scalehub.domain.protocol.okok.OkokBroadcastProtocol
import com.xcwk.scalehub.domain.protocol.okok.OkokProtocol
import org.junit.Test

/**
 * 协议注册表与识别优先级（旧库 Protocols.java：chipsea > okok > okok-broadcast）。
 */
class ProtocolsTest {

    @Test
    fun `of maps ids with legacy fallbacks`() {
        assertThat(Protocols.of(ChipseaCloudProtocol.ID)).isSameInstanceAs(ChipseaCloudProtocol.INSTANCE)
        assertThat(Protocols.of(OkokBroadcastProtocol.ID)).isSameInstanceAs(OkokBroadcastProtocol.INSTANCE)
        assertThat(Protocols.of(OkokProtocol.ID)).isSameInstanceAs(OkokProtocol.INSTANCE)
        assertThat(Protocols.of("okok")).isSameInstanceAs(OkokProtocol.INSTANCE)
        assertThat(Protocols.of(null)).isSameInstanceAs(OkokProtocol.INSTANCE)
        assertThat(Protocols.of("")).isSameInstanceAs(OkokProtocol.INSTANCE)
        assertThat(Protocols.of("unknown-9")).isNull()
    }

    private fun chipseaAdvertisement(): ByteArray {
        val record = ByteArray(24)
        record[0] = 0x15
        record[1] = 0xFF.toByte()
        record[2] = 0xFF.toByte()
        record[3] = 0xF0.toByte()
        record[4] = 0x02
        record[6] = 0x86.toByte()
        record[7] = 0x19
        return record
    }

    @Test
    fun `match prefers chipsea over okok for version 02`() {
        val match = Protocols.match(chipseaAdvertisement())
        requireNotNull(match)
        assertThat(match.protocolId).isEqualTo(ChipseaCloudProtocol.ID)
    }

    @Test
    fun `match falls back to broadcast scale`() {
        val record = ByteArray(17)
        record[0] = 0x10
        record[1] = 0xFF.toByte()
        record[2] = 0xC0.toByte()
        record[4] = 0x02.toByte()
        record[5] = 0x8F.toByte()
        record[10] = 0x01
        val match = Protocols.match(record)
        requireNotNull(match)
        assertThat(match.protocolId).isEqualTo(OkokBroadcastProtocol.ID)
    }

    @Test
    fun `match returns null for unknown bytes`() {
        assertThat(Protocols.match(byteArrayOf(0x00, 0x01, 0x02))).isNull()
    }

    /** Chipsea_BLE 混合设备: 版本 0x10 + 名称含 "Chipsea" → 应匹配为 chipsea-cloud-1 */
    private fun hybridChipseaAdvertisement(version: Int, name: String): ByteArray {
        val mfr = byteArrayOf(0xFF.toByte(), 0xF0.toByte(), version.toByte(), 0x00.toByte(),
            0x86.toByte(), 0x19.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        // 记录: [mfr AD 结构] + [name AD 结构]
        val record = ByteArray(1 + 1 + mfr.size + 1 + 1 + nameBytes.size)
        var pos = 0
        record[pos++] = (mfr.size + 1).toByte()
        record[pos++] = 0xFF.toByte()
        System.arraycopy(mfr, 0, record, pos, mfr.size)
        pos += mfr.size
        record[pos++] = (nameBytes.size + 1).toByte()
        record[pos++] = 0x09.toByte()
        System.arraycopy(nameBytes, 0, record, pos, nameBytes.size)
        return record
    }

    @Test
    fun `match recognizes hybrid chipsea device with okok version 0x10 and chipsea name`() {
        val record = hybridChipseaAdvertisement(0x10, "Chipsea_BLE")
        val match = Protocols.match(record)
        requireNotNull(match)
        assertThat(match.protocolId).isEqualTo(ChipseaCloudProtocol.ID)
    }

    @Test
    fun `match does not recognize plain okok device as chipsea`() {
        val record = hybridChipseaAdvertisement(0x10, "OKOK-SCALE")
        val match = Protocols.match(record)
        // OKOK 设备版本 0x10 会被 OkokProtocol 识别, 但不应被 ChipseaCloudProtocol 识别
        requireNotNull(match)
        assertThat(match.protocolId).isNotEqualTo(ChipseaCloudProtocol.ID)
        assertThat(match.protocolId).isEqualTo("okok-1")
    }
}
