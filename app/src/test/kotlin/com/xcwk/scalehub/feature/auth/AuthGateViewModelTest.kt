package com.xcwk.scalehub.feature.auth

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.auth.AuthPort
import com.xcwk.scalehub.application.auth.AuthService
import com.xcwk.scalehub.application.auth.SignInResult
import com.xcwk.scalehub.application.auth.SmsCodeResult
import com.xcwk.scalehub.ui.legal.LegalDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/** 游客门行为（需求 3.1：先同意后进入；旧库 GuestGate/Session 口径）。 */
class AuthGateViewModelTest {

    companion object {
        private lateinit var scheduler: kotlinx.coroutines.test.TestCoroutineScheduler

        @BeforeClass
        @JvmStatic
        fun setupMain() {
            val dispatcher = UnconfinedTestDispatcher()
            scheduler = dispatcher.scheduler
            Dispatchers.setMain(dispatcher)
        }

        @AfterClass
        @JvmStatic
        fun tearDownMain() {
            Dispatchers.resetMain()
        }
    }

    private class FakeAuthPort(
        var smsResult: SmsCodeResult = SmsCodeResult(true, "验证码已发送", "v-1", false),
        var signInResult: SignInResult = SignInResult(true, "登录成功"),
    ) : AuthPort {
        var enteredFlag = false
        val signInRegistered = mutableListOf<Boolean>()
        override suspend fun sendSmsCode(rawDigits: String) = smsResult
        override suspend fun signInWithPassword(phone: String, password: String) = signInResult
        override suspend fun signInWithEmail(email: String, password: String) = signInResult
        override suspend fun signInWithWeChat() = signInResult
        override suspend fun signInWithCode(
            rawDigits: String, verificationId: String, registered: Boolean, code: String,
        ): SignInResult {
            signInRegistered += registered
            return signInResult
        }
        override suspend fun deleteCurrentUser(verifyCode: String): String? = throw UnsupportedOperationException()
        override fun isEntered() = enteredFlag
        override fun markEntered() { enteredFlag = true }
        override fun isFormalAccount() = enteredFlag
        override fun phone() = ""
        override fun signOut() { enteredFlag = false }
        override suspend fun sendDeleteCode() = null
    }

    private fun vm(port: FakeAuthPort) = AuthGateViewModel(AuthService(port))

    @Test
    fun `未勾选协议_三路全拦`() {
        val port = FakeAuthPort()
        val v = vm(port)
        v.onIntent(AuthIntent.EnterGuest)
        assertThat(v.state.value.message).isEqualTo("请先阅读并同意协议")
        assertThat(v.state.value.entered).isFalse()
        v.onIntent(AuthIntent.SendCode)
        assertThat(v.state.value.message).isEqualTo("请先阅读并同意协议")
        v.onIntent(AuthIntent.SignIn)
        assertThat(v.state.value.entered).isFalse()
        assertThat(port.enteredFlag).isFalse()
    }

    @Test
    fun `勾选后游客进入`() {
        val port = FakeAuthPort()
        val v = vm(port)
        v.onIntent(AuthIntent.AgreedChanged(true))
        v.onIntent(AuthIntent.EnterGuest)
        assertThat(v.state.value.entered).isTrue()
        assertThat(port.enteredFlag).isTrue()
    }

    @Test
    fun `已进入用户直接过门`() {
        val port = FakeAuthPort().apply { enteredFlag = true }
        assertThat(vm(port).state.value.entered).isTrue()
    }

    @Test
    fun `发送验证码_回填与倒计时`() {
        val port = FakeAuthPort(smsResult = SmsCodeResult(true, "验证码已发送", "v-9", true))
        val v = vm(port)
        v.onIntent(AuthIntent.AgreedChanged(true))
        v.onIntent(AuthIntent.SendCode)
        assertThat(v.state.value.verificationId).isEqualTo("v-9")
        assertThat(v.state.value.isUser).isTrue()
        assertThat(v.state.value.countdown).isEqualTo(60)
        scheduler.advanceTimeBy(3_001)   // 边界含入（advanceTimeBy 不含恰好边界）
        assertThat(v.state.value.countdown).isEqualTo(57)
        scheduler.advanceUntilIdle()
        assertThat(v.state.value.countdown).isEqualTo(0)
    }

    @Test
    fun `发送失败_文案透出无倒计时`() {
        val port = FakeAuthPort(smsResult = SmsCodeResult(false, "请输入正确的手机号", "", false))
        val v = vm(port)
        v.onIntent(AuthIntent.AgreedChanged(true))
        v.onIntent(AuthIntent.SendCode)
        assertThat(v.state.value.message).isEqualTo("请输入正确的手机号")
        assertThat(v.state.value.countdown).isEqualTo(0)
    }

    @Test
    fun `登录成功_按isUser分支并进入`() {
        val port = FakeAuthPort(signInResult = SignInResult(true, "登录成功"))
        val v = vm(port)
        v.onIntent(AuthIntent.AgreedChanged(true))
        v.onIntent(AuthIntent.SendCode)   // isUser=false 默认
        v.onIntent(AuthIntent.SignIn)
        assertThat(port.signInRegistered).containsExactly(false)
        assertThat(v.state.value.entered).isTrue()
        assertThat(port.enteredFlag).isTrue()
        assertThat(v.state.value.message).isNull()
    }

    @Test
    fun `登录失败_文案透出不进入`() {
        val port = FakeAuthPort(signInResult = SignInResult(false, "验证码不正确"))
        val v = vm(port)
        v.onIntent(AuthIntent.AgreedChanged(true))
        v.onIntent(AuthIntent.SignIn)
        assertThat(v.state.value.message).isEqualTo("验证码不正确")
        assertThat(v.state.value.entered).isFalse()
    }

    @Test
    fun `法务阅读态切换`() {
        val v = vm(FakeAuthPort())
        v.onIntent(AuthIntent.OpenLegal(LegalDoc.AGREEMENT))
        assertThat(v.state.value.legalDoc).isEqualTo(LegalDoc.AGREEMENT)
        v.onIntent(AuthIntent.OpenLegal(LegalDoc.PRIVACY))
        assertThat(v.state.value.legalDoc).isEqualTo(LegalDoc.PRIVACY)
        v.onIntent(AuthIntent.CloseLegal)
        assertThat(v.state.value.legalDoc).isEqualTo(LegalDoc.NONE)
    }
}
