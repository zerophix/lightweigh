package com.xcwk.scalehub.domain.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 二维码解析黄金向量（旧库 ScaleQr 三形态：纯 MAC/JSON/链接）。 */
class ScaleQrParserTest {

    @Test
    fun `plain mac with separators`() {
        val qr = ScaleQr.parse("AA:BB:CC:DD:EE:FF")
        requireNotNull(qr)
        assertThat(qr.mac).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(qr.name).isEmpty()
    }

    @Test
    fun `plain hex12 without separators`() {
        val qr = ScaleQr.parse("aabbccddeeff")
        requireNotNull(qr)
        assertThat(qr.mac).isEqualTo("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `json payload extracts mac and name`() {
        val qr = ScaleQr.parse("""{"name":"卧室秤","deviceMac":"aa-bb-cc-dd-ee-ff"}""")
        requireNotNull(qr)
        assertThat(qr.name).isEqualTo("卧室秤")
        assertThat(qr.mac).isEqualTo("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `link payload reads query parameters`() {
        val qr = ScaleQr.parse("https://example.com/bind?deviceName=客厅秤&device=112233445566")
        requireNotNull(qr)
        assertThat(qr.name).isEqualTo("客厅秤")
        assertThat(qr.mac).isEqualTo("11:22:33:44:55:66")
    }

    @Test
    fun `junk text is rejected`() {
        assertThat(ScaleQr.parse("hello world")).isNull()
        assertThat(ScaleQr.parse("")).isNull()
        assertThat(ScaleQr.parse(null)).isNull()
    }
}
