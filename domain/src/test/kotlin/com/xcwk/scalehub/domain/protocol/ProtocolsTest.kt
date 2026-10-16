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
}
