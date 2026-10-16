package com.xcwk.scalehub.application.ai

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.sync.CloudSessionPort
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** AI 问答编排行为（旧库 CloudSync.ask 语义：游客拦截/空输入/网络文案, M2c DEC-022）。 */
class AiServiceTest {

    private class FakeSession(var formal: Boolean) : CloudSessionPort {
        override fun isFormalAccount() = formal
        override fun accessToken() = if (formal) "tok" else null
        override fun subject(token: String) = "u-1"
        override fun signOut() { formal = false }
    }

    private class FakeAi(
        private val response: Pair<Boolean, String> = true to "回答正文",
        private val error: RuntimeException? = null,
    ) : AiPort {
        var lastTurns: List<Pair<String, Boolean>> = emptyList()
        override suspend fun ask(turns: List<Pair<String, Boolean>>): Pair<Boolean, String> {
            lastTurns = turns
            error?.let { throw it }
            return response
        }
    }

    @Test
    fun `guest is blocked with login hint`() = runTest {
        val ai = FakeAi()
        val out = AiService(FakeSession(formal = false), ai).ask(listOf("你好" to true))
        assertThat(out.ok).isFalse()
        assertThat(out.message).isEqualTo("请先使用手机号登录后再提问")
        assertThat(ai.lastTurns).isEmpty()
    }

    @Test
    fun `blank question is rejected`() = runTest {
        val out = AiService(FakeSession(formal = true), FakeAi()).ask(listOf("   " to true))
        assertThat(out.ok).isFalse()
        assertThat(out.message).isEqualTo("请输入问题")
    }

    @Test
    fun `formal account passes turns through and returns text`() = runTest {
        val ai = FakeAi(response = true to "减脂期晚餐建议…")
        val out = AiService(FakeSession(formal = true), ai)
            .ask(listOf("减脂期晚餐怎么吃？" to true))
        assertThat(out.ok).isTrue()
        assertThat(out.message).isEqualTo("减脂期晚餐建议…")
        assertThat(ai.lastTurns).containsExactly("减脂期晚餐怎么吃？" to true)
    }

    @Test
    fun `port failure surfaces network message`() = runTest {
        val ai = FakeAi(error = RuntimeException("boom"))
        val out = AiService(FakeSession(formal = true), ai).ask(listOf("在吗" to true))
        assertThat(out.ok).isFalse()
        assertThat(out.message).isEqualTo("当前网络不可用，请联网后使用AI功能")
    }

    @Test
    fun `cloud rejection message is relayed verbatim`() = runTest {
        val ai = FakeAi(response = false to "AI 服务暂时不可用")
        val out = AiService(FakeSession(formal = true), ai).ask(listOf("在吗" to true))
        assertThat(out.ok).isFalse()
        assertThat(out.message).isEqualTo("AI 服务暂时不可用")
    }
}
