package com.xcwk.scalehub.infrastructure.cloud

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.infrastructure.cloud.CloudBaseGateway.Companion.chunked40
import com.xcwk.scalehub.infrastructure.cloud.CloudBaseGateway.Companion.parseRowsJson
import org.junit.Test

/** 网关纯函数：行解析与分块（HTTP 本体属集成测试，需真实环境——随部署回填）。 */
class CloudBaseGatewayTest {

    @Test
    fun `行解析_类型保留与NULL归空`() {
        val rows = parseRowsJson(
            """[{"measured_at":1700000000000,"weight":70.5,"male":true,"nickname":null,"mac":"AA:BB"}]""",
        )!!
        assertThat(rows).hasSize(1)
        val f = rows[0].fields
        assertThat(f["measured_at"]).isEqualTo(1700000000000L)
        assertThat((f["weight"] as Number).toDouble()).isWithin(1e-9).of(70.5)
        assertThat(f["male"]).isEqualTo(true)
        assertThat(f["nickname"]).isNull()
        assertThat(f["mac"]).isEqualTo("AA:BB")
    }

    @Test
    fun `行解析_空数组与坏JSON`() {
        assertThat(parseRowsJson("[]")).isEmpty()
        assertThat(parseRowsJson("not json")).isNull()
        assertThat(parseRowsJson("")).isNull()
    }

    @Test
    fun `分块40_边界与余数`() {
        assertThat(emptyList<Int>().chunked40()).isEmpty()
        assertThat(List(39) { it }.chunked40().map { it.size }).containsExactly(39)
        assertThat(List(40) { it }.chunked40().map { it.size }).containsExactly(40)
        assertThat(List(85) { it }.chunked40().map { it.size }).containsExactly(40, 40, 5)
    }
}
