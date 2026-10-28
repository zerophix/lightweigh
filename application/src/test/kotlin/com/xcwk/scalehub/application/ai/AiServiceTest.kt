package com.xcwk.scalehub.application.ai

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.sync.CloudSessionPort
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * AI 服务行为测试。
 */
class AiServiceTest {

    private class FakeSession : CloudSessionPort {
        override fun isFormalAccount() = true
        override fun accessToken() = "tok-1"
        override fun subject(token: String) = "u-1"
        override fun signOut() {}
    }

    private class FakeAiPort : AiPort {
        var responses = mutableListOf<Pair<Boolean, String>>()
        var index = 0

        override suspend fun ask(turns: List<Pair<String, Boolean>>): Pair<Boolean, String> {
            return responses.getOrElse(index++) { false to "no more responses" }
        }

        override suspend fun weeklySummary(prompt: String): Pair<Boolean, String> {
            return responses.getOrElse(index++) { false to "no more responses" }
        }

        override suspend fun healthAdvice(context: String, query: String): Pair<Boolean, String> {
            return responses.getOrElse(index++) { false to "no more responses" }
        }
    }

    @Test
    fun `ask with guest returns login prompt`() = runTest {
        val session = object : CloudSessionPort {
            override fun isFormalAccount() = false
            override fun accessToken() = null
            override fun subject(token: String) = null
            override fun signOut() {}
        }
        val service = AiService(session, FakeAiPort())

        val result = service.ask(listOf("你好" to true))

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("登录")
    }

    @Test
    fun `ask with empty turns returns input prompt`() = runTest {
        val service = AiService(FakeSession(), FakeAiPort())

        val result = service.ask(listOf("" to true))

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("请输入")
    }

    @Test
    fun `weeklySummary with no data returns empty prompt`() = runTest {
        val service = AiService(FakeSession(), FakeAiPort())

        val result = service.weeklySummary(1000L, 2000L, WeeklyData(emptyList(), emptyList()))

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("暂无数据")
    }

    @Test
    fun `isMaxTurns returns true when exceeds limit`() {
        val service = AiService(FakeSession(), FakeAiPort())
        val turns = List(AiService.MAX_TURNS + 1) { "" to true }

        assertThat(service.isMaxTurns(turns)).isTrue()
    }

    @Test
    fun `isMaxTurns returns false when within limit`() {
        val service = AiService(FakeSession(), FakeAiPort())
        val turns = List(AiService.MAX_TURNS - 1) { "" to true }

        assertThat(service.isMaxTurns(turns)).isFalse()
    }

    @Test
    fun `trimHistory preserves first and last MAX_TURNS`() {
        val service = AiService(FakeSession(), FakeAiPort())
        val turns = (1..30).map { "第${it}轮" to true }

        val trimmed = service.trimHistory(turns)

        assertThat(trimmed.size).isEqualTo(AiService.MAX_TURNS)
        assertThat(trimmed.first().first).isEqualTo("第1轮")
        assertThat(trimmed.last().first).isEqualTo("第30轮")
    }
}
