package com.xcwk.scalehub.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-073 密码找回与账号恢复纯逻辑单测。
 *
 * 覆盖验收中**唯一可自动判定**的部分：
 * - 原型文案逐字（`01-login.html` v-reset 与 `21-account-recovery.html` 五屏）
 * - 找回状态机（[accountRecoveryReduce]）：方式选择 → 验证 → 设密 → 成功
 * - 密码规则（[validateNewPassword]）：8 位 / 字母数字 / 两次一致 / 不与旧密码相同
 * - 倒计时（[canResend] / [resendLabel]）：结束前重发不可用
 *
 * 视觉布局不在此测（需真机/截图）；状态推进与校验写错了没人会发现，故钉死。
 */
class PasswordRecoveryFlowTest {

    // ---- v-reset 文案（原型 01-login.html 逐字） ----

    @Test
    fun `重置密码页文案与原型一致`() {
        assertThat(TITLE_RESET_PASSWORD).isEqualTo("重置密码")
        assertThat(HEADLINE_SET_PASSWORD).isEqualTo("设置新密码")
        assertThat(resetAccountSubtitle("138****0000")).isEqualTo("为账号 138****0000 重置密码")
        assertThat(LABEL_VERIFICATION_CODE).isEqualTo("验证码")
        assertThat(resendLabel(58)).isEqualTo("58s 后重发")
        assertThat(LABEL_NEW_PASSWORD).isEqualTo("新密码")
        assertThat(LABEL_CONFIRM_PASSWORD).isEqualTo("确认新密码")
        assertThat(ACTION_CONFIRM_RESET).isEqualTo("确认重置")
    }

    @Test
    fun `设置新密码页三条规则与提示文案与原型一致`() {
        assertThat(PASSWORD_RULES).containsExactly("8 位以上", "含字母和数字", "两次一致").inOrder()
        assertThat(HINT_NEW_PASSWORD_DIFFERS).isEqualTo("新密码不能与旧密码相同")
        assertThat(HINT_OTHER_SESSIONS_INVALID).isEqualTo("修改密码后，其他设备的登录状态将失效。")
    }

    // ---- 找回五屏文案（原型 21-account-recovery.html 逐字） ----

    @Test
    fun `找回方式选择屏文案与原型一致`() {
        assertThat(TITLE_RECOVERY).isEqualTo("找回密码")
        assertThat(HEADLINE_CHOOSE_RECOVERY).isEqualTo("选择找回方式")
        assertThat(SUBLINE_CHOOSE_RECOVERY).isEqualTo("通过绑定的手机号或邮箱验证身份")
        assertThat(ENTRY_RECOVER_BY_PHONE).isEqualTo("通过手机号找回")
        assertThat(DEFAULT_PHONE_MASKED).isEqualTo("138****0000")
        assertThat(ENTRY_RECOVER_BY_EMAIL).isEqualTo("通过邮箱找回")
        assertThat(DEFAULT_EMAIL_MASKED).isEqualTo("l***@example.com")
        assertThat(BANNER_CONTACT_SUPPORT).isEqualTo("两种方式都不可用？请联系客服协助找回。")
        assertThat(ACTION_CONTACT_SUPPORT).isEqualTo("联系客服")
    }

    @Test
    fun `手机验证屏文案与原型一致`() {
        assertThat(TITLE_PHONE_VERIFY).isEqualTo("手机验证")
        assertThat(HEADLINE_PHONE_VERIFY).isEqualTo("验证手机号")
        assertThat(phoneVerifySubtitle("138****0000")).isEqualTo("验证码将发送至 +86 138****0000")
        assertThat(LABEL_VERIFICATION_CODE).isEqualTo("验证码")
        assertThat(resendLabel(42)).isEqualTo("42s 后重发")
        assertThat(BANNER_CODE_SECURITY).isEqualTo("验证成功后才能设置新密码，请勿泄露验证码。")
        assertThat(ACTION_VERIFY_AND_CONTINUE).isEqualTo("验证并继续")
    }

    @Test
    fun `邮箱验证屏文案与原型一致`() {
        assertThat(TITLE_EMAIL_VERIFY).isEqualTo("邮箱验证")
        assertThat(HEADLINE_EMAIL_VERIFY).isEqualTo("验证邮箱")
        assertThat(emailSentSubtitle("l***@example.com")).isEqualTo("验证链接已发送至 l***@example.com")
        assertThat(EMAIL_LINK_HINT).isEqualTo("请打开邮箱点击验证链接，\n验证成功后自动返回设置新密码。")
        assertThat(LABEL_EMAIL_CODE).isEqualTo("或输入邮件中的验证码")
        assertThat(BANNER_EMAIL_NOT_RECEIVED).isEqualTo("没收到邮件？请检查垃圾箱，或重新发送。")
        assertThat(ACTION_RESEND_EMAIL).isEqualTo("重新发送验证邮件")
    }

    @Test
    fun `重置成功屏文案与原型一致`() {
        assertThat(HEADLINE_RESET_SUCCESS).isEqualTo("密码已重置")
        assertThat(SUCCESS_LINE_RELOGIN).isEqualTo("请用新密码重新登录。")
        assertThat(SUCCESS_LINE_OTHER_SESSIONS).isEqualTo("其他设备的登录状态已失效。")
        assertThat(ACTION_BACK_TO_LOGIN).isEqualTo("返回登录")
    }

    // ---- 掩码与验证码 ----

    @Test
    fun `手机号与邮箱掩码与原型示例一致`() {
        assertThat(maskedPhone("13800000000")).isEqualTo("138****0000")
        assertThat(maskedEmail("linyue@example.com")).isEqualTo("l***@example.com")
        // 非预期输入不抛异常、原样返回（避免页面因脏数据崩）
        assertThat(maskedPhone("123")).isEqualTo("123")
        assertThat(maskedEmail("not-an-email")).isEqualTo("not-an-email")
    }

    @Test
    fun `验证码必须六位且全数字`() {
        assertThat(isVerificationCodeComplete("123456")).isTrue()
        assertThat(isVerificationCodeComplete("12345")).isFalse()
        assertThat(isVerificationCodeComplete("1234567")).isFalse()
        assertThat(isVerificationCodeComplete("12345a")).isFalse()
        assertThat(isVerificationCodeComplete("")).isFalse()
    }

    @Test
    fun `倒计时结束前不可重发`() {
        assertThat(canResend(58)).isFalse()
        assertThat(canResend(1)).isFalse()
        assertThat(canResend(0)).isTrue()
        assertThat(resendLabel(58)).isEqualTo("58s 后重发")
        assertThat(resendLabel(0)).isEqualTo("重新发送")
    }

    // ---- 密码规则 ----

    @Test
    fun `密码必须八位以上`() {
        val tooShort = validateNewPassword("a1b2c3d", "a1b2c3d")
        assertThat(tooShort.lengthOk).isFalse()
        assertThat(tooShort.isValid).isFalse()
        assertThat(validateNewPassword("a1b2c3d4", "a1b2c3d4").lengthOk).isTrue()
    }

    @Test
    fun `密码必须同时含字母和数字`() {
        val onlyDigits = validateNewPassword("12345678", "12345678")
        assertThat(onlyDigits.letterDigitOk).isFalse()
        assertThat(onlyDigits.isValid).isFalse()
        val onlyLetters = validateNewPassword("abcdefgh", "abcdefgh")
        assertThat(onlyLetters.letterDigitOk).isFalse()
        assertThat(validateNewPassword("abcd1234", "abcd1234").letterDigitOk).isTrue()
    }

    @Test
    fun `两次输入必须一致`() {
        val mismatch = validateNewPassword("abcd1234", "abcd12345")
        assertThat(mismatch.lengthOk).isTrue()
        assertThat(mismatch.matchOk).isFalse()
        assertThat(mismatch.isValid).isFalse()
        assertThat(validateNewPassword("abcd1234", "abcd1234").matchOk).isTrue()
    }

    @Test
    fun `新密码不能与旧密码相同`() {
        val sameAsOld = validateNewPassword("abcd1234", "abcd1234", oldPassword = "abcd1234")
        assertThat(sameAsOld.differsFromOld).isFalse()
        assertThat(sameAsOld.isValid).isFalse()
        val changed = validateNewPassword("abcd1234", "abcd1234", oldPassword = "abcd0000")
        assertThat(changed.differsFromOld).isTrue()
        assertThat(changed.isValid).isTrue()
    }

    @Test
    fun `旧密码未知时只校验前三项`() {
        val noOld = validateNewPassword("abcd1234", "abcd1234", oldPassword = null)
        assertThat(noOld.differsFromOld).isTrue()
        assertThat(noOld.isValid).isTrue()
    }

    @Test
    fun `规则行勾选状态跟随校验结果`() {
        val partial = validateNewPassword("abcd1234", "abcd12345")
        val rules = passwordRules(partial)
        assertThat(rules.map { it.text }).containsExactly("8 位以上", "含字母和数字", "两次一致").inOrder()
        assertThat(rules.map { it.satisfied }).containsExactly(true, true, false).inOrder()
    }

    @Test
    fun `重置密码页可提交需验证码填满且密码合规`() {
        val ready = PasswordResetUiState(
            code = "123456",
            newPassword = "abcd1234",
            confirmPassword = "abcd1234",
        )
        assertThat(ready.canSubmit).isTrue()
        assertThat(ready.copy(code = "12345").canSubmit).isFalse()
        assertThat(ready.copy(confirmPassword = "abcd12345").canSubmit).isFalse()
    }

    // ---- 找回状态机 ----

    private fun state(
        step: RecoveryStep = RecoveryStep.Choose,
        secondsLeft: Int = 42,
    ) = AccountRecoveryUiState(step = step, codeSecondsLeft = secondsLeft)

    @Test
    fun `初始态为找回方式选择`() {
        assertThat(state().step).isEqualTo(RecoveryStep.Choose)
    }

    @Test
    fun `选择手机号进入手机验证并清空输入`() {
        val typed = state().copy(newPassword = "abcd1234", confirmPassword = "abcd1234", code = "12")
        val next = accountRecoveryReduce(typed, AccountRecoveryIntent.ChoosePhone)
        assertThat(next.step).isEqualTo(RecoveryStep.PhoneVerify)
        assertThat(next.channel).isEqualTo(RecoveryChannel.Phone)
        assertThat(next.code).isEmpty()
        assertThat(next.newPassword).isEmpty()
        assertThat(next.confirmPassword).isEmpty()
    }

    @Test
    fun `选择邮箱进入邮箱验证`() {
        val next = accountRecoveryReduce(state(), AccountRecoveryIntent.ChooseEmail)
        assertThat(next.step).isEqualTo(RecoveryStep.EmailVerify)
        assertThat(next.channel).isEqualTo(RecoveryChannel.Email)
    }

    @Test
    fun `验证码未填满时验证不推进且标记错误`() {
        val phone = accountRecoveryReduce(state(), AccountRecoveryIntent.ChoosePhone)
        val short = accountRecoveryReduce(phone, AccountRecoveryIntent.CodeChanged("12345"))
        val afterVerify = accountRecoveryReduce(short, AccountRecoveryIntent.Verify)
        assertThat(afterVerify.step).isEqualTo(RecoveryStep.PhoneVerify)
        assertThat(afterVerify.codeError).isTrue()
        // 继续输入会清掉错误态
        val retyped = accountRecoveryReduce(afterVerify, AccountRecoveryIntent.CodeChanged("123456"))
        assertThat(retyped.codeError).isFalse()
    }

    @Test
    fun `验证码只保留数字`() {
        val phone = accountRecoveryReduce(state(), AccountRecoveryIntent.ChoosePhone)
        val typed = accountRecoveryReduce(phone, AccountRecoveryIntent.CodeChanged("12 34-56"))
        assertThat(typed.code).isEqualTo("123456")
    }

    @Test
    fun `倒计时未结束时重发是空操作`() {
        val phone = accountRecoveryReduce(state(secondsLeft = 42), AccountRecoveryIntent.ChoosePhone)
        val withCode = accountRecoveryReduce(phone, AccountRecoveryIntent.CodeChanged("123456"))
        val resend = accountRecoveryReduce(withCode, AccountRecoveryIntent.ResendCode)
        assertThat(resend).isEqualTo(withCode)
        assertThat(resend.step).isEqualTo(RecoveryStep.PhoneVerify)
        assertThat(resend.code).isEqualTo("123456")
    }

    @Test
    fun `倒计时结束后重发清空验证码`() {
        val phone = accountRecoveryReduce(state(secondsLeft = 0), AccountRecoveryIntent.ChoosePhone)
        val withCode = accountRecoveryReduce(phone, AccountRecoveryIntent.CodeChanged("123456"))
        val resend = accountRecoveryReduce(withCode, AccountRecoveryIntent.ResendCode)
        assertThat(resend.code).isEmpty()
        assertThat(resend.step).isEqualTo(RecoveryStep.PhoneVerify)
    }

    @Test
    fun `手机验证成功进入设置新密码`() {
        val phone = accountRecoveryReduce(state(), AccountRecoveryIntent.ChoosePhone)
        val withCode = accountRecoveryReduce(phone, AccountRecoveryIntent.CodeChanged("123456"))
        val next = accountRecoveryReduce(withCode, AccountRecoveryIntent.Verify)
        assertThat(next.step).isEqualTo(RecoveryStep.SetPassword)
        assertThat(next.codeError).isFalse()
    }

    @Test
    fun `邮箱验证成功进入设置新密码`() {
        val email = accountRecoveryReduce(state(), AccountRecoveryIntent.ChooseEmail)
        val withCode = accountRecoveryReduce(email, AccountRecoveryIntent.CodeChanged("789012"))
        val next = accountRecoveryReduce(withCode, AccountRecoveryIntent.Verify)
        assertThat(next.step).isEqualTo(RecoveryStep.SetPassword)
    }

    @Test
    fun `密码不合规时不推进到成功页`() {
        val setPassword = state(step = RecoveryStep.SetPassword)
        val typed = accountRecoveryReduce(setPassword, AccountRecoveryIntent.NewPasswordChanged("abcd123"))
        val submit = accountRecoveryReduce(typed, AccountRecoveryIntent.SubmitPassword)
        assertThat(submit.step).isEqualTo(RecoveryStep.SetPassword)
    }

    @Test
    fun `两次输入不一致时不推进到成功页`() {
        val ready = state(step = RecoveryStep.SetPassword)
            .let { accountRecoveryReduce(it, AccountRecoveryIntent.NewPasswordChanged("abcd1234")) }
            .let { accountRecoveryReduce(it, AccountRecoveryIntent.ConfirmPasswordChanged("abcd12345")) }
        assertThat(accountRecoveryReduce(ready, AccountRecoveryIntent.SubmitPassword).step)
            .isEqualTo(RecoveryStep.SetPassword)
    }

    @Test
    fun `新密码与旧密码相同时不推进到成功页`() {
        val setPassword = state(step = RecoveryStep.SetPassword)
            .copy(oldPassword = "abcd1234")
            .let { accountRecoveryReduce(it, AccountRecoveryIntent.NewPasswordChanged("abcd1234")) }
            .let { accountRecoveryReduce(it, AccountRecoveryIntent.ConfirmPasswordChanged("abcd1234")) }
        assertThat(accountRecoveryReduce(setPassword, AccountRecoveryIntent.SubmitPassword).step)
            .isEqualTo(RecoveryStep.SetPassword)
    }

    @Test
    fun `密码合规提交后到成功页并清空密码`() {
        val setPassword = state(step = RecoveryStep.SetPassword)
            .let { accountRecoveryReduce(it, AccountRecoveryIntent.NewPasswordChanged("abcd1234")) }
            .let { accountRecoveryReduce(it, AccountRecoveryIntent.ConfirmPasswordChanged("abcd1234")) }
        val success = accountRecoveryReduce(setPassword, AccountRecoveryIntent.SubmitPassword)
        assertThat(success.step).isEqualTo(RecoveryStep.Success)
        // 明文不留在呈现态
        assertThat(success.newPassword).isEmpty()
        assertThat(success.confirmPassword).isEmpty()
    }

    @Test
    fun `验证页返回回到方式选择并清空`() {
        val phone = accountRecoveryReduce(state(), AccountRecoveryIntent.ChoosePhone)
            .let { accountRecoveryReduce(it, AccountRecoveryIntent.CodeChanged("123456")) }
        val back = accountRecoveryReduce(phone, AccountRecoveryIntent.BackToChoose)
        assertThat(back.step).isEqualTo(RecoveryStep.Choose)
        assertThat(back.channel).isNull()
        assertThat(back.code).isEmpty()
    }

    @Test
    fun `成功页返回登录回到方式选择并清空`() {
        val success = state(step = RecoveryStep.Success).copy(code = "123456")
        val back = accountRecoveryReduce(success, AccountRecoveryIntent.BackToLogin)
        assertThat(back.step).isEqualTo(RecoveryStep.Choose)
        assertThat(back.code).isEmpty()
    }

    @Test
    fun `完整链路 方式选择 验证 设密 成功`() {
        var step = state(secondsLeft = 0)
        step = accountRecoveryReduce(step, AccountRecoveryIntent.ChooseEmail)
        assertThat(step.step).isEqualTo(RecoveryStep.EmailVerify)
        step = accountRecoveryReduce(step, AccountRecoveryIntent.CodeChanged("789012"))
        step = accountRecoveryReduce(step, AccountRecoveryIntent.Verify)
        assertThat(step.step).isEqualTo(RecoveryStep.SetPassword)
        step = accountRecoveryReduce(step, AccountRecoveryIntent.NewPasswordChanged("abcd1234"))
        step = accountRecoveryReduce(step, AccountRecoveryIntent.ConfirmPasswordChanged("abcd1234"))
        assertThat(step.canSubmitPassword).isTrue()
        step = accountRecoveryReduce(step, AccountRecoveryIntent.SubmitPassword)
        assertThat(step.step).isEqualTo(RecoveryStep.Success)
    }
}
