package com.xcwk.scalehub.infrastructure.member

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.Member
import org.junit.Test

/** DEC-S04：scalehub_members JSON 与成员列表互转。 */
class MemberCodecTest {
    @Test
    fun `空与坏行被跳过`() {
        assertThat(MemberCodec.parse(null)).isEmpty()
        assertThat(MemberCodec.parse("")).isEmpty()
        assertThat(MemberCodec.parse("""[{"slot":1}]""")).isEmpty()
    }

    @Test
    fun `往返保留 id slot nickname`() {
        val raw = """[{"id":"1","slot":1,"nickname":"我"},{"id":"2","slot":3,"nickname":"爸爸"}]"""
        val parsed = MemberCodec.parse(raw)
        assertThat(parsed).containsExactly(
            Member("1", 1, "我"),
            Member("2", 3, "爸爸"),
        ).inOrder()
        val again = MemberCodec.parse(MemberCodec.toJson(parsed))
        assertThat(again).isEqualTo(parsed)
    }
}
