package com.xcwk.scalehub.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-117 VerifyCodeScreen 单测：
 * - 文案逐字对齐原型 v-verify
 * - 表单校验逻辑
 * - 状态转移
 */
class VerifyCodeScreenTest {

    // ---- 原型文案逐字 ----

    @Test
    fun `验证码页文案逐字对齐原型 v-verify`() {
        assertThat(VerifyCodeText.NavTitle).isEqualTo("输入验证码")
        assertThat(VerifyCodeText.Headline).isEqualTo("输入验证码")
        assertThat(VerifyCodeText.SublinePrefix).isEqualTo("验证码已发送至 ")
        assertThat(VerifyCodeText.CodeLabel).isEqualTo("验证码")
        assertThat(VerifyCodeText.CodePlaceholder).isEqualTo("6 位验证码")
        assertThat(VerifyCodeText.ResendPrefix).isEqualTo("重新发送（")
        assertThat(VerifyCodeText.ResendSuffix).isEqualTo("s）")
        assertThat(VerifyCodeText.CantReceive).isEqualTo("收不到验证码？")
        assertThat(VerifyCodeText.Submit).isEqualTo("登录")
    }

    // ---- 表单校验 ----

    @Test
    fun `验证码为空时 codeEmpty 为真`() {
        assertThat(VerifyCodeUiState(code = "").codeEmpty).isTrue()
        assertThat(VerifyCodeUiState(code = "   ").codeEmpty).isTrue()
        assertThat(VerifyCodeUiState(code = "123456").codeEmpty).isFalse()
    }

    @Test
    fun `验证码格式校验：6 位数字通过`() {
        assertThat(VerifyCodeUiState(code = "123456").codeValid).isTrue()
        assertThat(VerifyCodeUiState(code = "000000").codeValid).isTrue()
        assertThat(VerifyCodeUiState(code = "12345").codeValid).isFalse()
        assertThat(VerifyCodeUiState(code = "1234567").codeValid).isFalse()
        assertThat(VerifyCodeUiState(code = "abc123").codeValid).isFalse()
    }

    @Test
    fun `只有提交过才展示字段错误`() {
        assertThat(VerifyCodeUiState().showErrors).isFalse()
        assertThat(VerifyCodeUiState(submitAttempted = true).showErrors).isTrue()
    }

    // ---- 状态转移 ----

    @Test
    fun `输入验证码清除提交标记`() {
        val state = VerifyCodeUiState(submitAttempted = true)
        val next = verifyCodeReduce(state, VerifyCodeIntent.CodeChanged("123456"))
        assertThat(next.code).isEqualTo("123456")
        assertThat(next.submitAttempted).isFalse()
    }

    @Test
    fun `点击重发开启倒计时`() {
        val next = verifyCodeReduce(VerifyCodeUiState(), VerifyCodeIntent.Resend)
        assertThat(next.countdown).isEqualTo(60)
    }

    @Test
    fun `点击登录标记尝试提交`() {
        val next = verifyCodeReduce(VerifyCodeUiState(), VerifyCodeIntent.Submit)
        assertThat(next.submitAttempted).isTrue()
    }

    @Test
    fun `点击收不到验证码不改变表单状态`() {
        val state = VerifyCodeUiState(code = "123456")
        val next = verifyCodeReduce(state, VerifyCodeIntent.CantReceive)
        assertThat(next).isEqualTo(state)
    }
}
