package com.xcwk.scalehub.feature.settings

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.auth.AuthPort
import com.xcwk.scalehub.application.auth.AuthService
import com.xcwk.scalehub.application.auth.SignInResult
import com.xcwk.scalehub.application.auth.SmsCodeResult
import com.xcwk.scalehub.application.sync.CloudDataPort
import com.xcwk.scalehub.application.sync.CloudRow
import com.xcwk.scalehub.application.sync.CloudSessionPort
import com.xcwk.scalehub.application.sync.SyncService
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MemberProfileWriter
import com.xcwk.scalehub.ui.legal.LegalDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/** 设置页账号三流程行为（需求：恢复/退出/注销口径，M2c DEC-022）。 */
class SettingsViewModelTest {

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

    private class FakeAuth(
        var formal: Boolean = true,
        var phoneValue: String = "13800138000",
    ) : AuthPort {
        var enteredFlag = true
        var signedOut = false
        var deleteCodeError: String? = null
        var deleteCodeSends = 0
        var deleteCurrentUserError: String? = null

        override suspend fun sendSmsCode(rawDigits: String) =
            SmsCodeResult(true, "验证码已发送", "v-1", false)
        override suspend fun signInWithCode(
            rawDigits: String,
            verificationId: String,
            registered: Boolean,
            code: String,
        ) = SignInResult(true, "登录成功")
        override suspend fun deleteCurrentUser(verifyCode: String) = deleteCurrentUserError
        override fun isEntered() = enteredFlag
        override fun markEntered() { enteredFlag = true }
        override fun isFormalAccount() = formal
        override fun phone() = phoneValue
        override fun signOut() { formal = false; signedOut = true }
        override suspend fun sendDeleteCode(): String? {
            deleteCodeSends++
            return deleteCodeError
        }
    }

    private class FakeSession(private val auth: FakeAuth) : CloudSessionPort {
        var sessionSignedOut = false
        override fun isFormalAccount() = auth.formal
        override fun accessToken() = if (auth.formal) "tok" else null
        override fun subject(token: String) = "u-1"
        override fun signOut() { sessionSignedOut = true; auth.formal = false }
    }

    private class FakeCloud : CloudDataPort {
        override suspend fun rows(table: String, query: String): List<CloudRow>? = emptyList()
        override suspend fun upsert(table: String, conflict: String, rows: List<CloudRow>) = true
        override suspend fun delete(table: String, filter: String) = true
    }

    private class FakeMeasurements : MeasurementRepository {
        override suspend fun save(record: MeasureRecord) = Unit
        override suspend fun recent(limit: Int): List<MeasureRecord> = emptyList()
        override suspend fun between(fromInc: Long, toExc: Long): List<MeasureRecord> = emptyList()
        override suspend fun delete(time: Long) = Unit
    }

    private class FakeLogs : HealthLogRepository {
        override suspend fun save(log: HealthLog) = Unit
        override suspend fun recent(limit: Int): List<HealthLog> = emptyList()
    }

    private class FakeProfileReader : MemberProfileReader {
        override fun male() = true
        override fun ageYears() = 30
        override fun heightCm() = 175
        override fun birthYear() = 1996
        override fun birthMonth() = 5
        override fun birthDay() = 20
        override fun nickname() = "测试"
    }

    private class FakeProfileWriter : MemberProfileWriter {
        var saves = 0
        override suspend fun save(profile: MemberProfile) { saves++ }
    }

    private class Harness(
        val auth: FakeAuth = FakeAuth(),
        val session: FakeSession = FakeSession(auth),
        val bus: DataRefreshBus = DataRefreshBus(),
    ) {
        val sync = SyncService(
            session, FakeCloud(), auth,
            FakeMeasurements(), FakeLogs(), FakeProfileReader(), FakeProfileWriter(),
        )
        val vm = SettingsViewModel(AuthService(auth, sync), sync, auth, bus)
    }

    @Test
    fun `init reads formal and phone from auth port`() = runTest {
        val h = Harness()
        val s = h.vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.formal).isTrue()
        assertThat(s.phone).isEqualTo("13800138000")
    }

    @Test
    fun `sign out clears formal account and keeps entered`() = runTest {
        val h = Harness()
        h.vm.onIntent(SettingsIntent.SignOutAsk)
        assertThat(h.vm.state.value.showSignOutConfirm).isTrue()
        h.vm.onIntent(SettingsIntent.SignOutConfirm)
        val s = h.vm.state.value
        assertThat(s.formal).isFalse()
        assertThat(s.showSignOutConfirm).isFalse()
        assertThat(s.message).isEqualTo("已退出登录，本机记录保留")
        // 进入态不受退出登录影响（需求：退出只清登录状态）
        assertThat(h.auth.enteredFlag).isTrue()
    }

    @Test
    fun `restore succeeds and broadcasts refresh signal`() = runTest {
        val h = Harness()
        var busSignals = 0
        val job = launch { h.bus.changed.collect { busSignals++ } }
        yield()   // 先让订阅者就位（无订阅者时发射即丢弃是总线口径）
        h.vm.onIntent(SettingsIntent.Restore)
        yield()   // 计数恢复在调度器上执行，推进一次再断言
        val s = h.vm.state.value
        assertThat(s.busy).isFalse()
        assertThat(s.message).isEqualTo("已从云端恢复当前测量用户的记录")
        assertThat(busSignals).isEqualTo(1)
        job.cancel()
    }

    @Test
    fun `restore as guest is rejected with login hint`() = runTest {
        val h = Harness(auth = FakeAuth(formal = false, phoneValue = ""))
        h.vm.onIntent(SettingsIntent.Restore)
        assertThat(h.vm.state.value.message).isEqualTo("请先使用手机号登录")
    }

    @Test
    fun `delete account sends code then confirms and signs out`() = runTest {
        val h = Harness()
        h.vm.onIntent(SettingsIntent.DeleteAsk)
        h.vm.onIntent(SettingsIntent.DeleteSendCode)
        var s = h.vm.state.value
        assertThat(s.deleteCodeSent).isTrue()
        assertThat(s.message).isEqualTo("注销验证码已发送")
        assertThat(h.auth.deleteCodeSends).isEqualTo(1)

        h.vm.onIntent(SettingsIntent.DeleteCodeChanged("1234"))
        h.vm.onIntent(SettingsIntent.DeleteConfirm)
        s = h.vm.state.value
        assertThat(s.formal).isFalse()
        assertThat(s.showDeleteConfirm).isFalse()
        assertThat(s.message).isEqualTo("账号已注销")
        assertThat(h.session.sessionSignedOut).isTrue()
    }

    @Test
    fun `delete account keeps dialog open on short code`() = runTest {
        val h = Harness()
        h.vm.onIntent(SettingsIntent.DeleteAsk)
        h.vm.onIntent(SettingsIntent.DeleteCodeChanged("12"))
        h.vm.onIntent(SettingsIntent.DeleteConfirm)
        val s = h.vm.state.value
        assertThat(s.message).isEqualTo("请输入短信验证码")
        assertThat(s.showDeleteConfirm).isTrue()
        assertThat(s.formal).isTrue()
    }

    @Test
    fun `legal intent toggles document pane`() = runTest {
        val h = Harness()
        h.vm.onIntent(SettingsIntent.OpenLegal(LegalDoc.PRIVACY))
        assertThat(h.vm.state.value.legalDoc).isEqualTo(LegalDoc.PRIVACY)
        h.vm.onIntent(SettingsIntent.CloseLegal)
        assertThat(h.vm.state.value.legalDoc).isEqualTo(LegalDoc.NONE)
    }
}
