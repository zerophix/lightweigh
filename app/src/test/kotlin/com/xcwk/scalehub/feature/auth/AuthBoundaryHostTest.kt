package com.xcwk.scalehub.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * 登录门上的账号边界接入（原型 14 接入波）—— 触发条件与返回路径。
 *
 * 钉三件事：
 * 1. 三个边界页各自有可判定的触发条件（不是"塞个看不见的入口"）；
 * 2. 优先级固定：启动自检 > 隐私锁 > 验证码错误，已进入应用的用户不被错误页盖住；
 * 3. 关闭边界页后登录门回到表单（状态标记清干净），且关键文案逐字取自原型。
 */
class AuthBoundaryHostTest {

    private fun state(
        agreed: Boolean = true,
        privacyBlocked: Boolean = false,
        attempts: Int = 0,
        mode: LoginMode = LoginMode.CODE,
        entered: Boolean = false,
        phone: String = "13800000000",
        code: String = "1234",
        countdown: Int = 0,
    ) = AuthUiState(
        loading = false,
        entered = entered,
        agreed = agreed,
        privacyBlocked = privacyBlocked,
        phone = phone,
        code = code,
        countdown = countdown,
        codeAttempts = attempts,
        loginMode = mode,
    )

    // ---- 启动自检 ----

    @Test
    fun `系统低于最低支持版本时拦到最低版本页`() {
        assertThat(startupBoundaryOf(MIN_SUPPORTED_SDK - 1)).isEqualTo(AccountBoundaryView.MinVersion)
        assertThat(startupBoundaryOf(MIN_SUPPORTED_SDK)).isNull()
        assertThat(startupBoundaryOf(MIN_SUPPORTED_SDK + 5)).isNull()
    }

    @Test
    fun `最低支持版本与 app minSdk 对齐`() {
        val gradle = File("build.gradle.kts").readText()
        assertThat(gradle).contains("minSdk = $MIN_SUPPORTED_SDK")
    }

    // ---- 隐私锁 ----

    @Test
    fun `未同意隐私政策点登录切到隐私锁页`() {
        val gate = authGateBoundaryOf(state(agreed = false, privacyBlocked = true), sdkInt = 34)
        assertThat(gate).isEqualTo(AccountBoundaryView.PrivacyLock)
    }

    @Test
    fun `隐私锁页去同意后回到登录门`() {
        val blocked = state(agreed = false, privacyBlocked = true)
        val agreed = blocked.copy(agreed = true, privacyBlocked = false)
        assertThat(authGateBoundaryOf(agreed, sdkInt = 34)).isNull()
    }

    // ---- 验证码错误 ----

    @Test
    fun `验证码登录失败一次进错误页且剩余次数递减`() {
        val failed = state(attempts = 1)
        assertThat(authGateBoundaryOf(failed, sdkInt = 34)).isEqualTo(AccountBoundaryView.CodeError)
        assertThat(codeErrorStateOf(failed).remainingAttempts)
            .isEqualTo(CODE_MAX_ATTEMPTS - 1)
        assertThat(codeErrorStateOf(failed).phoneMasked).isEqualTo("138****0000")
        assertThat(codeErrorStateOf(failed).errorText).isEqualTo("验证码错误，还可尝试 4 次")
    }

    @Test
    fun `密码登录失败不切验证码错误页`() {
        val failed = state(attempts = 1, mode = LoginMode.PASSWORD)
        assertThat(authGateBoundaryOf(failed, sdkInt = 34)).isNull()
    }

    @Test
    fun `已进入应用的用户不被验证码错误页盖住`() {
        val entered = state(attempts = 2, entered = true)
        assertThat(authGateBoundaryOf(entered, sdkInt = 34)).isNull()
    }

    @Test
    fun `关闭错误页后回到登录门`() {
        val failed = state(attempts = 3)
        val dismissed = failed.copy(codeAttempts = 0)
        assertThat(authGateBoundaryOf(dismissed, sdkInt = 34)).isNull()
    }

    // ---- 优先级 ----

    @Test
    fun `启动自检优先于隐私锁与验证码错误`() {
        val all = state(agreed = false, privacyBlocked = true, attempts = 2)
        assertThat(authGateBoundaryOf(all, sdkInt = MIN_SUPPORTED_SDK - 1))
            .isEqualTo(AccountBoundaryView.MinVersion)
        assertThat(authGateBoundaryOf(all, sdkInt = 34))
            .isEqualTo(AccountBoundaryView.PrivacyLock)
    }

    // ---- 关键文案（逐字取自原型 14） ----

    @Test
    fun `三个边界页标题逐字取自原型`() {
        assertThat(AccountLifecycleText.MinVersionTitle).isNotEmpty()
        assertThat(AccountLifecycleText.PrivacyLockTitle).isEqualTo("功能暂不可用")
        assertThat(AccountLifecycleText.HealthInfoAcknowledge).isNotEmpty()
        assertThat(AccountLifecycleText.CodeLockBanner).isNotEmpty()
    }

    // ---- 登录门确实挂了这道门（源码守卫，防止回调被摘掉） ----

    @Test
    fun `登录门渲染边界宿主`() {
        val screen = File("src/main/kotlin/com/xcwk/scalehub/feature/auth/AuthScreen.kt").readText()
        assertThat(screen).contains("authGateBoundaryOf(state, Build.VERSION.SDK_INT)")
        assertThat(screen).contains("AuthGateBoundaryHost(")
    }

    @Test
    fun `废弃的 AuthRoute 已删除且无引用`() {
        val route = File("src/main/kotlin/com/xcwk/scalehub/feature/auth/AuthRoute.kt")
        assertThat(route.exists()).isFalse()
        val mainShell = File("src/main/kotlin/com/xcwk/scalehub/ui/shell/MainShell.kt").readText()
        // 宿主一直用的是 AuthScreen + AuthFlowRoute，AuthRoute 是零调用方的旧壳
        assertThat(mainShell).doesNotContain("AuthRoute(")
        assertThat(mainShell).contains("AuthFlowRoute(")
    }
}
