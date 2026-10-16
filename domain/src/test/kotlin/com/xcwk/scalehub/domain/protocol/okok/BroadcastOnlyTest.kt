package com.xcwk.scalehub.domain.protocol.okok

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.protocol.packet.BroadcastOnly
import org.junit.Test

/**
 * 单向广播秤（okok-broadcast-1）报文黄金向量（M3a，DEC-022）。
 * AD 结构字段、裸 C0 前缀与 FF C0 前缀三种形态；表 2.3 小数位与 BE 字节序。
 */
class BroadcastOnlyTest {

    /** AD 形态：[0x10][0xFF][15 字节字段：C0 version .. weight BE@2..3 resistance BE@4..5 flags@8]。 */
    private fun advertisement(weightRaw: Int, resistanceRaw: Int, flags: Int): ByteArray {
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
    fun `advertisement field parses weight in big endian`() {
        // flags decimalCode=0→表2.3 为 1 位；655 → 65.5 kg；bit5 体脂秤 + 锁定
        val info = BroadcastOnly.parse(advertisement(655, 5000, 0x21))
        requireNotNull(info)
        assertThat(info.weight.value).isEqualTo(65.5)
        assertThat(info.weight.kilograms()).isEqualTo(65.5)
        assertThat(info.flags.weightLocked).isTrue()
        // 行为锁定：广播电阻原样透传旧库口径（BroadcastOnly.java:28 不做 /10）
        assertThat(info.resistanceOhm).isEqualTo(5000.0)
        assertThat(info.summary()).contains("体脂秤")
        assertThat(info.summary()).contains("锁定")
    }

    @Test
    fun `raw C0 prefix is accepted without ad structure`() {
        val field = ByteArray(15)
        field[0] = 0xC0.toByte()
        field[2] = 0x02.toByte()   // BE 655 → 65.5
        field[3] = 0x8F.toByte()
        field[8] = 0x01            // 体重秤、锁定、kg、1 位
        val info = BroadcastOnly.parse(field)
        requireNotNull(info)
        assertThat(info.weight.kilograms()).isEqualTo(65.5)
        assertThat(info.summary()).contains("体重秤")
    }

    @Test
    fun `FF C0 prefix is accepted`() {
        val data = ByteArray(16)
        data[0] = 0xFF.toByte()
        data[1] = 0xC0.toByte()
        data[3] = 0x02.toByte()
        data[4] = 0x8F.toByte()
        data[9] = 0x01
        val info = BroadcastOnly.parse(data)
        requireNotNull(info)
        assertThat(info.weight.kilograms()).isEqualTo(65.5)
    }

    @Test
    fun `unrelated record is rejected`() {
        assertThat(BroadcastOnly.parse(byteArrayOf(0x02, 0x01, 0x06))).isNull()
        assertThat(BroadcastOnly.parse(null)).isNull()
    }

    @Test
    fun `broadcast protocol exposes no connection characteristics`() {
        assertThat(OkokBroadcastProtocol.INSTANCE.subscribe()).isEmpty()
        assertThat(OkokBroadcastProtocol.INSTANCE.requiredCharacteristic()).isNull()
        assertThat(OkokBroadcastProtocol.INSTANCE.missingRequiredMessage())
            .isEqualTo("这台秤只通过广播发送体重")
        val match = OkokBroadcastProtocol.INSTANCE.match(advertisement(655, 0, 0x21))
        requireNotNull(match)
        assertThat(match.protocolId).isEqualTo("okok-broadcast-1")
    }
}
