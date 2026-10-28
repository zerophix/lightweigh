package com.xcwk.scalehub.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-117 PasswordLoginScreen 单测：
 * - 文案逐字对齐原型 v-password
 * - 表单校验逻辑
 * - 状态转移
 */
class PasswordLoginScreenTest {

    // ---- 原型文案逐字 ----

    @Test
    fun `账号密码登录页文案逐字对齐原型 v-password`() {
        assertThat(PasswordLoginText.NavTitle).isEqualTo("账号密码登录")
        assertThat(PasswordLoginText.Headline).isEqualTo("登录你的账号")
        assertThat(PasswordLoginText.Subline).isEqualTo("支持用户名、手机号或邮箱")
        assertThat(PasswordLoginText.AccountLabel).isEqualTo("账号")
        assertThat(PasswordLoginText.AccountPlaceholder).isEqualTo("用户名 / 手机号 / 邮箱")
        assertThat(PasswordLoginText.PasswordLabel).isEqualTo("密码")
        assertThat(PasswordLoginText.PasswordPlaceholder).isEqualTo("请输入密码")
        assertThat(PasswordLoginText.ForgotPassword).isEqualTo("忘记密码？")
        assertThat(PasswordLoginText.Submit).isEqualTo("登录")
        assertThat(PasswordLoginText.OtherMethods).isEqualTo("其他方式登录 ›")
    }

    // ---- 表单校验 ----

    @Test
    fun `账号为空时 accountEmpty 为真`() {
        assertThat(PasswordLoginUiState(account = "").accountEmpty).isTrue()
        assertThat(PasswordLoginUiState(account = "   ").accountEmpty).isTrue()
        assertThat(PasswordLoginUiState(account = "user").accountEmpty).isFalse()
    }

    @Test
    fun `密码为空时 passwordEmpty 为真`() {
        assertThat(PasswordLoginUiState(password = "").passwordEmpty).isTrue()
        assertThat(PasswordLoginUiState(password = "123456").passwordEmpty).isFalse()
    }

    @Test
    fun `密码长度校验：至少 6 位`() {
        assertThat(PasswordLoginUiState(password = "123456").passwordLongEnough).isTrue()
        assertThat(PasswordLoginUiState(password = "1234567").passwordLongEnough).isTrue()
        assertThat(PasswordLoginUiState(password = "12345").passwordLongEnough).isFalse()
        assertThat(PasswordLoginUiState(password = "").passwordLongEnough).isFalse()
    }

    @Test
    fun `表单可提交需账号非空且密码至少 6 位`() {
        assertThat(PasswordLoginUiState(account = "user", password = "123456").canSubmit).isTrue()
        assertThat(PasswordLoginUiState(account = "13800138000", password = "123456").canSubmit).isTrue()
        assertThat(PasswordLoginUiState(account = "a@b.com", password = "123456").canSubmit).isTrue()
        assertThat(PasswordLoginUiState(account = "", password = "123456").canSubmit).isFalse()
        assertThat(PasswordLoginUiState(account = "user", password = "12345").canSubmit).isFalse()
        assertThat(PasswordLoginUiState(account = "user", password = "").canSubmit).isFalse()
    }

    @Test
    fun `只有提交过才展示字段错误`() {
        assertThat(PasswordLoginUiState().showErrors).isFalse()
        assertThat(PasswordLoginUiState(submitAttempted = true).showErrors).isTrue()
    }

    // ---- 状态转移 ----

    @Test
    fun `输入账号清除提交标记`() {
        val state = PasswordLoginUiState(submitAttempted = true)
        val next = passwordLoginReduce(state, PasswordLoginIntent.AccountChanged("user"))
        assertThat(next.account).isEqualTo("user")
        assertThat(next.submitAttempted).isFalse()
    }

    @Test
    fun `输入密码清除提交标记`() {
        val state = PasswordLoginUiState(submitAttempted = true)
        val next = passwordLoginReduce(state, PasswordLoginIntent.PasswordChanged("123456"))
        assertThat(next.password).isEqualTo("123456")
        assertThat(next.submitAttempted).isFalse()
    }

    @Test
    fun `切换密码可见性翻转 showPassword`() {
        val next = passwordLoginReduce(PasswordLoginUiState(showPassword = false), PasswordLoginIntent.TogglePasswordVisibility)
        assertThat(next.showPassword).isTrue()
        val next2 = passwordLoginReduce(next, PasswordLoginIntent.TogglePasswordVisibility)
        assertThat(next2.showPassword).isFalse()
    }

    @Test
    fun `点击登录标记尝试提交`() {
        val next = passwordLoginReduce(PasswordLoginUiState(), PasswordLoginIntent.Submit)
        assertThat(next.submitAttempted).isTrue()
    }

    @Test
    fun `点击忘记密码不改变表单状态`() {
        val state = PasswordLoginUiState(account = "user", password = "123456")
        val next = passwordLoginReduce(state, PasswordLoginIntent.ForgotPassword)
        assertThat(next).isEqualTo(state)
    }

    @Test
    fun `点击其他方式登录不改变表单状态`() {
        val state = PasswordLoginUiState(account = "user", password = "123456")
        val next = passwordLoginReduce(state, PasswordLoginIntent.OtherMethods)
        assertThat(next).isEqualTo(state)
    }
}
