package com.xcwk.scalehub.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-072 登录方式扩展的纯逻辑单测。
 *
 * 覆盖验收里可自动判定的部分：
 * - 原型 v-email / v-wechat / v-guest / v-guest-done 文案逐字
 * - 邮箱表单校验与提交判定
 * - 微信授权勾选状态机
 * - 访客两态转移
 *
 * 布局与视觉不在此测（需真机/截图）。
 */
class AuthLoginMethodsTest {

    // ---- 原型文案逐字 ----

    @Test
    fun `邮箱登录页文案逐字对齐原型`() {
        assertThat(EmailLoginText.NavTitle).isEqualTo("邮箱登录")
        assertThat(EmailLoginText.Headline).isEqualTo("用邮箱登录")
        assertThat(EmailLoginText.Subline).isEqualTo("未注册的邮箱将自动创建账号")
        assertThat(EmailLoginText.EmailLabel).isEqualTo("邮箱地址")
        assertThat(EmailLoginText.PasswordLabel).isEqualTo("密码")
        assertThat(EmailLoginText.Submit).isEqualTo("登录")
        assertThat(EmailLoginText.OtherMethods).isEqualTo("其他方式登录 ›")
    }

    @Test
    fun `微信登录页文案逐字对齐原型`() {
        assertThat(WechatLoginText.NavTitle).isEqualTo("微信登录")
        assertThat(WechatLoginText.Headline).isEqualTo("使用微信账号登录")
        assertThat(WechatLoginText.Subline).isEqualTo("轻衡将获得你的微信昵称与头像")
        assertThat(WechatLoginText.NicknameTitle).isEqualTo("微信昵称")
        assertThat(WechatLoginText.NicknameDesc).isEqualTo("用于在轻衡中显示你的名字")
        assertThat(WechatLoginText.AvatarTitle).isEqualTo("微信头像")
        assertThat(WechatLoginText.AvatarDesc).isEqualTo("作为你在轻衡中的默认头像")
        assertThat(WechatLoginText.Authorize).isEqualTo("微信授权登录")
        assertThat(WechatLoginText.OtherMethods).isEqualTo("使用其他方式")
    }

    @Test
    fun `访客确认页文案逐字对齐原型`() {
        assertThat(GuestModeText.Headline).isEqualTo("先逛逛，不用登录")
        assertThat(GuestModeText.Subline).isEqualTo("访客模式下数据仅保存在本机，更换设备将丢失")
        assertThat(GuestModeText.LimitLocalOnly).isEqualTo("数据仅存本机，卸载或换设备将丢失")
        assertThat(GuestModeText.LimitNoSync).isEqualTo("无法云同步，无法多设备查看")
        assertThat(GuestModeText.Enter).isEqualTo("进入体验")
        assertThat(GuestModeText.BackToLogin).isEqualTo("返回登录")
    }

    @Test
    fun `访客落地页文案逐字对齐原型`() {
        assertThat(GuestLandingText.Headline).isEqualTo("你正在以访客身份浏览")
        assertThat(GuestLandingText.Subline).isEqualTo("登录后可同步数据、连接设备、使用 AI 健康助手。")
        assertThat(GuestLandingText.LoginOrRegister).isEqualTo("登录 / 注册")
    }

    // ---- 邮箱表单校验 ----

    @Test
    fun `邮箱与密码都为空时两个字段各报错`() {
        val result = validateEmailLogin("", "")
        assertThat(result.emailError).isEqualTo(EmailLoginError.EmailEmpty)
        assertThat(result.passwordError).isEqualTo(EmailLoginError.PasswordEmpty)
        assertThat(result.valid).isFalse()
    }

    @Test
    fun `邮箱格式非法被拦下`() {
        assertThat(validateEmailLogin("linyue.example.com", "123456").emailError)
            .isEqualTo(EmailLoginError.EmailInvalid)
        assertThat(validateEmailLogin("linyue@", "123456").emailError)
            .isEqualTo(EmailLoginError.EmailInvalid)
        assertThat(validateEmailLogin("linyue@example", "123456").emailError)
            .isEqualTo(EmailLoginError.EmailInvalid)
    }

    @Test
    fun `密码不足六位被拦下`() {
        assertThat(validateEmailLogin("linyue@example.com", "12345").passwordError)
            .isEqualTo(EmailLoginError.PasswordTooShort)
        // 6 位是下界，刚好通过
        assertThat(validateEmailLogin("linyue@example.com", "123456").passwordError).isNull()
    }

    @Test
    fun `合法邮箱与密码通过且邮箱去掉首尾空格`() {
        val state = EmailLoginUiState(email = "  linyue@example.com ", password = "123456")
        val submission = emailLoginSubmissionOf(state)
        assertThat(submission).isInstanceOf(EmailLoginSubmission.Valid::class.java)
        assertThat((submission as EmailLoginSubmission.Valid).email).isEqualTo("linyue@example.com")
        assertThat(submission.password).isEqualTo("123456")
    }

    @Test
    fun `非法输入得到 Invalid 并带字段错误`() {
        val submission = emailLoginSubmissionOf(EmailLoginUiState(email = "bad", password = "1"))
        assertThat(submission).isInstanceOf(EmailLoginSubmission.Invalid::class.java)
        val invalid = submission as EmailLoginSubmission.Invalid
        assertThat(invalid.validation.emailError).isEqualTo(EmailLoginError.EmailInvalid)
        assertThat(invalid.validation.passwordError).isEqualTo(EmailLoginError.PasswordTooShort)
    }

    @Test
    fun `只有提交过才展示字段错误`() {
        assertThat(EmailLoginUiState().showErrors).isFalse()
        assertThat(emailLoginReduce(EmailLoginUiState(), EmailLoginIntent.Submit).showErrors).isTrue()
    }

    @Test
    fun `输入改动会重置提交标记`() {
        val submitted = EmailLoginUiState(submitAttempted = true)
        val changed = emailLoginReduce(submitted, EmailLoginIntent.EmailChanged("a@b.com"))
        assertThat(changed.submitAttempted).isFalse()
        assertThat(changed.email).isEqualTo("a@b.com")
        val pwdChanged = emailLoginReduce(submitted, EmailLoginIntent.PasswordChanged("123456"))
        assertThat(pwdChanged.submitAttempted).isFalse()
        assertThat(pwdChanged.password).isEqualTo("123456")
    }

    @Test
    fun `字段错误文案不为空且互不相同`() {
        val messages = EmailLoginError.entries.map { it.message }
        assertThat(messages).hasSize(4)
        assertThat(messages.toSet()).hasSize(4)
        messages.forEach { assertThat(it).isNotEmpty() }
    }

    // ---- 微信授权状态机 ----

    @Test
    fun `微信昵称与头像默认都勾选`() {
        val state = WechatLoginUiState()
        assertThat(state.nicknameGranted).isTrue()
        assertThat(state.avatarGranted).isTrue()
    }

    @Test
    fun `微信授权勾选独立翻转`() {
        val nicknameOff = wechatLoginReduce(WechatLoginUiState(), WechatLoginIntent.ToggleNickname)
        assertThat(nicknameOff.nicknameGranted).isFalse()
        assertThat(nicknameOff.avatarGranted).isTrue()
        val avatarOff = wechatLoginReduce(WechatLoginUiState(), WechatLoginIntent.ToggleAvatar)
        assertThat(avatarOff.avatarGranted).isFalse()
        assertThat(avatarOff.nicknameGranted).isTrue()
    }

    // ---- 访客两态 ----

    @Test
    fun `访客流程初始为确认页`() {
        assertThat(GuestUiState().step).isEqualTo(GuestStep.Notice)
    }

    @Test
    fun `进入体验切到访客落地页`() {
        val next = guestModeReduce(GuestUiState(), GuestIntent.EnterExperience)
        assertThat(next.step).isEqualTo(GuestStep.Browsing)
    }

    @Test
    fun `返回登录与重新走登录流程都回到确认页`() {
        val browsing = GuestUiState(step = GuestStep.Browsing)
        assertThat(guestModeReduce(browsing, GuestIntent.BackToLogin).step).isEqualTo(GuestStep.Notice)
        assertThat(guestModeReduce(browsing, GuestIntent.RestartLogin).step).isEqualTo(GuestStep.Notice)
    }

    @Test
    fun `落地页点登录注册不改变呈现态`() {
        val browsing = GuestUiState(step = GuestStep.Browsing)
        assertThat(guestModeReduce(browsing, GuestIntent.LoginOrRegister).step)
            .isEqualTo(GuestStep.Browsing)
    }
}
