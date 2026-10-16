package com.xcwk.scalehub.domain.protocol.packet

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.protocol.HexUtil
import org.junit.Test

/**
 * 下发报文黄金向量（M3a，DEC-022）：用户信息帧字节布局与时间帧往返。
 */
class OutgoingPacketTest {

    @Test
    fun `user info packet follows legacy byte layout`() {
        // slot=1、男、30 岁、175cm、上次体重 655（65.5kg）
        val data = UserInfoPacket.build(1, true, 30, 175, 655)
        assertThat(data).isEqualTo(
            byteArrayOf(
                0xA0.toByte(), 0x01, 0x00, 0x00, 0x00,
                0x9E.toByte(),   // 0x80 男 | 30 岁
                0xAF.toByte(),   // 175
                0x8F.toByte(), 0x02,   // 655 低字节在前
            ),
        )
    }

    @Test
    fun `user info clamps age and encodes female`() {
        val data = UserInfoPacket.build(0x01020304, false, 999, 0, 0)
        assertThat(data[1]).isEqualTo(0x04)
        assertThat(data[2]).isEqualTo(0x03)
        assertThat(data[3]).isEqualTo(0x02)
        assertThat(data[4]).isEqualTo(0x01)
        assertThat(data[5]).isEqualTo(127)   // 年龄上限，无性别位
        assertThat(data[6]).isEqualTo(0)
        assertThat(data[7]).isEqualTo(0)
        assertThat(data[8]).isEqualTo(0)
    }

    @Test
    fun `time packet carries seconds big endian within call window`() {
        val before = System.currentTimeMillis() / 1000L
        val data = TimePacket.now()
        val after = System.currentTimeMillis() / 1000L
        assertThat(data.size).isEqualTo(4)
        val seconds = ((data[0].toLong() and 0xFF) shl 24) or
            ((data[1].toLong() and 0xFF) shl 16) or
            ((data[2].toLong() and 0xFF) shl 8) or
            (data[3].toLong() and 0xFF)
        assertThat(seconds).isAtLeast(before)
        assertThat(seconds).isAtMost(after)
    }

    @Test
    fun `hex util renders uppercase with zero padding`() {
        assertThat(HexUtil.toHex(byteArrayOf(0x00, 0x0F, 0xFF.toByte()))).isEqualTo("000FFF")
        assertThat(HexUtil.toHex(null)).isEqualTo("")
        assertThat(HexUtil.readUInt16Le(byteArrayOf(0x8F.toByte(), 0x02), 0)).isEqualTo(655)
        assertThat(HexUtil.readUInt16Be(byteArrayOf(0x02, 0x8F.toByte()), 0)).isEqualTo(655)
    }
}
