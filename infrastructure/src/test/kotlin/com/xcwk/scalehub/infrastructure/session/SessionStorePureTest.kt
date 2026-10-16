package com.xcwk.scalehub.infrastructure.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** SessionStore 纯函数（不触 SP/网络；JWT subject 解码需 android.util.Base64，属仪器测试范围）。 */
class SessionStorePureTest {

    @Test
    fun `手机号规范化_旧库international口径`() {
        assertThat(SessionStore.international("13800138000")).isEqualTo("+86 13800138000")
        assertThat(SessionStore.international("138 0013 8000")).isEqualTo("+86 13800138000")
        assertThat(SessionStore.international("138-0013-8000")).isEqualTo("+86 13800138000")
        assertThat(SessionStore.international("12001380000")).isNull()   // 号段非法
        assertThat(SessionStore.international("1380013800")).isNull()    // 长度不足
        assertThat(SessionStore.international("")).isNull()
    }

    @Test
    fun `错误码文案映射_旧库authMessage口径`() {
        assertThat(SessionStore.authMessage("""{"error":"rate_limit_exceeded"}""", "兜底"))
            .isEqualTo("验证码发送太频繁，请 60 秒后再试")
        assertThat(SessionStore.authMessage("""{"error":"invalid_phone_number"}""", "兜底"))
            .isEqualTo("请输入正确的手机号")
        assertThat(SessionStore.authMessage("""{"error":"captcha_required"}""", "兜底"))
            .isEqualTo("发送过于频繁，请稍后再试")
        assertThat(SessionStore.authMessage("""{"error":"invalid_verification_code"}""", "兜底"))
            .isEqualTo("验证码不正确")
        assertThat(SessionStore.authMessage("""{"error":"other"}""", "兜底")).isEqualTo("兜底")
        assertThat(SessionStore.authMessage("非JSON", "兜底")).isEqualTo("兜底")
    }
}
