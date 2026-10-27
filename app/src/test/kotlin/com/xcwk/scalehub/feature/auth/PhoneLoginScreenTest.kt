package com.xcwk.scalehub.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-117 PhoneLoginScreen 单测：
 * - 文案逐字对齐原型 v-phone
 * - 表单校验逻辑
 * - 状态转移
 */
class PhoneLoginScreenTest {

    // ---- 原型文案逐字 ----

    @Test
    fun `手机号登录页文案逐字对齐原型 v-phone`() {
        assertThat(PhoneLoginText.NavTitle).isEqualTo("手机号登录")
        assertThat(PhoneLoginText.Headline).isEqualTo("输入手机号")
        assertThat(PhoneLoginText.Subline).isEqualTo("未注册的号码将自动创建轻衡账号")
        assertThat(PhoneLoginText.PhoneLabel).isEqualTo("手机号")
        assertThat(PhoneLoginText.PhonePlaceholder).isEqualTo("请输入手机号")
        assertThat(PhoneLoginText.PhonePrefix).isEqualTo("+86")
        assertThat(PhoneLoginText.CodeLabel).isEqualTo("验证码")
        assertThat(PhoneLoginText.CodePlaceholder).isEqualTo("6 位验证码")
        assertThat(PhoneLoginText.GetCodeAction).isEqualTo("获取验证码")
        assertThat(PhoneLoginText.Submit).isEqualTo("登录 / 注册")
        assertThat(PhoneLoginText.OtherMethods).isEqualTo("其他方式登录 ›")
        assertThat(PhoneLoginText.AgreementPrefix).isEqualTo("登录即代表同意 ")
        assertThat(PhoneLoginText.AgreementTerms).isEqualTo("《用户协议》")
        assertThat(PhoneLoginText.AgreementAnd).isEqualTo(" 与 ")
        assertThat(PhoneLoginText.AgreementPrivacy).isEqualTo("《隐私政策》")
    }

    // ---- 表单校验 ----

    @Test
    fun `手机号为空时 phoneEmpty 为真`() {
        assertThat(PhoneLoginUiState(phone = "").phoneEmpty).isTrue()
        assertThat(PhoneLoginUiState(phone = "   ").phoneEmpty).isTrue()
        assertThat(PhoneLoginUiState(phone = "13800138000").phoneEmpty).isFalse()
    }

    @Test
    fun `手机号格式校验：11 位数字以 1 开头通过`() {
        assertThat(PhoneLoginUiState(phone = "13800138000").phoneValid).isTrue()
        assertThat(PhoneLoginUiState(phone = "15912345678").phoneValid).isTrue()
        assertThat(PhoneLoginUiState(phone = "1380013800").phoneValid).isFalse()  // 10 位
        assertThat(PhoneLoginUiState(phone = "138001380000").phoneValid).isFalse() // 12 位
        assertThat(PhoneLoginUiState(phone = "03800138000").phoneValid).isFalse()  // 不以 1 开头
        assertThat(PhoneLoginUiState(phone = "138-0013-8000").phoneValid).isFalse() // 含分隔符
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
    fun `表单可提交需手机号合法且验证码合法`() {
        assertThat(PhoneLoginUiState(phone = "13800138000", code = "123456").canSubmit).isTrue()
        assertThat(PhoneLoginUiState(phone = "13800138000", code = "").canSubmit).isFalse()
        assertThat(PhoneLoginUiState(phone = "", code = "123456").canSubmit).isFalse()
        assertThat(PhoneLoginUiState(phone = "1380013800", code = "123456").canSubmit).isFalse()
        assertThat(PhoneLoginUiState(phone = "13800138000", code = "12345").canSubmit).isFalse()
    }

    @Test
    fun `只有提交过才展示字段错误`() {
        assertThat(PhoneLoginUiState().showErrors).isFalse()
        assertThat(PhoneLoginUiState(submitAttempted = true).showErrors).isTrue()
    }

    // ---- 状态转移 ----

    @Test
    fun `输入手机号清除提交标记`() {
        val state = PhoneLoginUiState(submitAttempted = true)
        val next = phoneLoginReduce(state, PhoneLoginIntent.PhoneChanged("13800138000"))
        assertThat(next.phone).isEqualTo("13800138000")
        assertThat(next.submitAttempted).isFalse()
    }

    @Test
    fun `输入验证码清除提交标记`() {
        val state = PhoneLoginUiState(submitAttempted = true)
        val next = phoneLoginReduce(state, PhoneLoginIntent.CodeChanged("123456"))
        assertThat(next.code).isEqualTo("123456")
        assertThat(next.submitAttempted).isFalse()
    }

    @Test
    fun `点击获取验证码开启发送态与倒计时`() {
        val next = phoneLoginReduce(PhoneLoginUiState(), PhoneLoginIntent.GetCode)
        assertThat(next.sendingCode).isTrue()
        assertThat(next.countdown).isEqualTo(60)
    }

    @Test
    fun `点击提交标记尝试提交`() {
        val next = phoneLoginReduce(PhoneLoginUiState(), PhoneLoginIntent.Submit)
        assertThat(next.submitAttempted).isTrue()
    }

    @Test
    fun `点击其他方式登录不改变表单状态`() {
        val state = PhoneLoginUiState(phone = "13800138000", code = "123456")
        val next = phoneLoginReduce(state, PhoneLoginIntent.OtherMethods)
        assertThat(next).isEqualTo(state)
    }
}
