package com.xcwk.scalehub.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-074 账号生命周期七屏的纯逻辑单测。
 *
 * 覆盖验收里可自动判定的部分：
 * - 边界事件 → 页面规格分发（含「强更无跳过」硬断言）
 * - 验证码剩余次数 / 重发倒计时 / 提交可用性
 * - 多设备登录文案拼装
 * - 健康数据五问五答与原型文案逐字
 */
class AccountLifecycleTest {

    // ---- 事件 → 页面 ----

    @Test
    fun `七个边界事件各自映射到对应页面`() {
        assertThat(accountBoundarySpec(AccountBoundaryEvent.ForceUpdate).view)
            .isEqualTo(AccountBoundaryView.ForceUpdate)
        assertThat(accountBoundarySpec(AccountBoundaryEvent.MinVersion).view)
            .isEqualTo(AccountBoundaryView.MinVersion)
        assertThat(accountBoundarySpec(AccountBoundaryEvent.PrivacyLock).view)
            .isEqualTo(AccountBoundaryView.PrivacyLock)
        assertThat(accountBoundarySpec(AccountBoundaryEvent.CodeError).view)
            .isEqualTo(AccountBoundaryView.CodeError)
        assertThat(accountBoundarySpec(AccountBoundaryEvent.LoginExpired).view)
            .isEqualTo(AccountBoundaryView.LoginExpired)
        assertThat(accountBoundarySpec(AccountBoundaryEvent.MultiDevice).view)
            .isEqualTo(AccountBoundaryView.MultiDevice)
        assertThat(accountBoundarySpec(AccountBoundaryEvent.HealthInfo).view)
            .isEqualTo(AccountBoundaryView.HealthInfo)
    }

    @Test
    fun `强制更新不可跳过且没有次入口`() {
        val spec = accountBoundarySpec(AccountBoundaryEvent.ForceUpdate)
        assertThat(spec.dismissible).isFalse()
        assertThat(spec.secondaryAction).isNull()
        assertThat(spec.primaryAction).isEqualTo("立即更新")
        assertThat(accountBoundaryAllowsSkip(AccountBoundaryEvent.ForceUpdate)).isFalse()
        // 强更页不得出现「稍后」：任何可见文案都不含该词
        assertThat(spec.primaryAction.contains("稍后")).isFalse()
        assertThat(forceUpdateMessage().contains("稍后")).isFalse()
        assertThat(AccountLifecycleText.ForceUpdateTitle.contains("稍后")).isFalse()
    }

    @Test
    fun `登录过期与多设备允许关闭并提供两个入口`() {
        val expired = accountBoundarySpec(AccountBoundaryEvent.LoginExpired)
        assertThat(expired.dismissible).isTrue()
        assertThat(expired.secondaryAction).isEqualTo("稍后")
        assertThat(expired.primaryAction).isEqualTo("重新登录")
        val multi = accountBoundarySpec(AccountBoundaryEvent.MultiDevice)
        assertThat(multi.secondaryAction).isEqualTo("是我的")
        assertThat(multi.primaryAction).isEqualTo("不是我，改密码")
    }

    @Test
    fun `七个事件的主按钮与次按钮符合原型`() {
        assertThat(accountBoundarySpec(AccountBoundaryEvent.MinVersion).primaryAction).isEqualTo("查看升级方法")
        assertThat(accountBoundarySpec(AccountBoundaryEvent.MinVersion).secondaryAction).isEqualTo("返回")
        assertThat(accountBoundarySpec(AccountBoundaryEvent.PrivacyLock).primaryAction).isEqualTo("去同意隐私政策")
        assertThat(accountBoundarySpec(AccountBoundaryEvent.PrivacyLock).secondaryAction).isNull()
        assertThat(accountBoundarySpec(AccountBoundaryEvent.CodeError).primaryAction).isEqualTo("登录")
        assertThat(accountBoundarySpec(AccountBoundaryEvent.HealthInfo).primaryAction).isEqualTo("我知道了")
    }

    @Test
    fun `仅强更与三类弹层以弹层呈现其余为整页`() {
        assertThat(accountBoundarySpec(AccountBoundaryEvent.ForceUpdate).isDialog).isTrue()
        assertThat(accountBoundarySpec(AccountBoundaryEvent.LoginExpired).isDialog).isTrue()
        assertThat(accountBoundarySpec(AccountBoundaryEvent.MultiDevice).isDialog).isTrue()
        assertThat(accountBoundarySpec(AccountBoundaryEvent.MinVersion).isDialog).isFalse()
        assertThat(accountBoundarySpec(AccountBoundaryEvent.PrivacyLock).isDialog).isFalse()
        assertThat(accountBoundarySpec(AccountBoundaryEvent.CodeError).isDialog).isFalse()
        assertThat(accountBoundarySpec(AccountBoundaryEvent.HealthInfo).isDialog).isFalse()
    }

    // ---- 逐字文案 ----

    @Test
    fun `强制更新文案逐字对齐原型`() {
        assertThat(AccountLifecycleText.ForceUpdateTitle).isEqualTo("需要更新到新版本")
        assertThat(forceUpdateMessage()).isEqualTo("当前 v0.1.0 已停止服务，需更新至 v0.2.0（不可跳过）。")
        assertThat(AccountLifecycleText.ForceUpdateAction).isEqualTo("立即更新")
    }

    @Test
    fun `最低版本文案逐字对齐原型`() {
        assertThat(AccountLifecycleText.MinVersionTitle).isEqualTo("系统版本过低")
        assertThat(AccountLifecycleText.MinVersionBody)
            .isEqualTo("轻衡需要 Android 8.0 及以上，当前系统版本过低。")
        assertThat(AccountLifecycleText.MinVersionUpgrade).isEqualTo("查看升级方法")
        assertThat(AccountLifecycleText.MinVersionBack).isEqualTo("返回")
    }

    @Test
    fun `隐私锁文案逐字对齐原型`() {
        assertThat(AccountLifecycleText.PrivacyLockNavTitle).isEqualTo("设备管理")
        assertThat(AccountLifecycleText.PrivacyLockBanner)
            .isEqualTo("需先同意《隐私政策》才能使用设备与数据功能。")
        assertThat(AccountLifecycleText.PrivacyLockTitle).isEqualTo("功能暂不可用")
        assertThat(AccountLifecycleText.PrivacyLockBody)
            .isEqualTo("完成隐私政策确认后才能连接设备、保存数据。")
        assertThat(AccountLifecycleText.PrivacyLockAction).isEqualTo("去同意隐私政策")
    }

    @Test
    fun `验证码错误页文案逐字对齐原型`() {
        assertThat(AccountLifecycleText.CodeNavTitle).isEqualTo("手机号登录")
        assertThat(AccountLifecycleText.CodeHeadline).isEqualTo("输入验证码")
        assertThat(AccountLifecycleText.CodeSentPrefix).isEqualTo("验证码已发送至 ")
        assertThat(AccountLifecycleText.CodeHelp).isEqualTo("收不到验证码？")
        assertThat(AccountLifecycleText.CodeLockBanner).isEqualTo("5 次错误后需等待 10 分钟再试。")
    }

    @Test
    fun `登录过期与多设备文案逐字对齐原型`() {
        assertThat(AccountLifecycleText.ExpiredTitle).isEqualTo("登录已过期")
        assertThat(AccountLifecycleText.ExpiredBody)
            .isEqualTo("为保护数据安全，登录状态已失效。重新登录后继续同步，本地记录不丢失。")
        assertThat(AccountLifecycleText.ExpiredLater).isEqualTo("稍后")
        assertThat(AccountLifecycleText.ExpiredRelogin).isEqualTo("重新登录")
        assertThat(AccountLifecycleText.MultiDeviceTitle).isEqualTo("账号已在其他设备登录")
        assertThat(AccountLifecycleText.MultiDeviceMine).isEqualTo("是我的")
        assertThat(AccountLifecycleText.MultiDeviceChangePassword).isEqualTo("不是我，改密码")
    }

    @Test
    fun `多设备提示拼装时间与设备名`() {
        assertThat(multiDeviceMessage()).isEqualTo(
            "你的账号于 10月22日 09:12 在「iPhone 15 Pro」登录。非本人操作请立即改密码。",
        )
        assertThat(multiDeviceMessage(MultiDeviceLogin("10月23日 08:00", "Pixel 9")))
            .isEqualTo("你的账号于 10月23日 08:00 在「Pixel 9」登录。非本人操作请立即改密码。")
    }

    // ---- 验证码逻辑 ----

    @Test
    fun `验证码剩余次数文案与锁定`() {
        // 第 1 次输错后还可尝试 4 次（原型 v-codeerr）
        assertThat(codeErrorRemainingText(remainingCodeAttempts(1))).isEqualTo("验证码错误，还可尝试 4 次")
        assertThat(remainingCodeAttempts(5)).isEqualTo(0)
        assertThat(remainingCodeAttempts(9)).isEqualTo(0)
        assertThat(codeLocked(4)).isFalse()
        assertThat(codeLocked(5)).isTrue()
    }

    @Test
    fun `重发倒计时文案与可重发判定`() {
        assertThat(resendCountdownText(52)).isEqualTo("重新发送（52s）")
        assertThat(canResendCode(52)).isFalse()
        assertThat(canResendCode(0)).isTrue()
        assertThat(CodeErrorUiState().resendText).isEqualTo("重新发送（52s）")
        assertThat(CodeErrorUiState(resendSeconds = 0).resendText).isEqualTo("重新发送")
    }

    @Test
    fun `验证码错误页默认状态与提交可用性`() {
        val state = CodeErrorUiState()
        assertThat(state.phoneMasked).isEqualTo("+86 138****0000")
        assertThat(state.errorText).isEqualTo("验证码错误，还可尝试 4 次")
        assertThat(state.canSubmit).isTrue()
        assertThat(state.copy(code = "12345").canSubmit).isFalse()
        assertThat(state.copy(code = "12 34 56").codeDigits).isEqualTo("123456")
        assertThat(state.copy(code = "12 34 56").canSubmit).isTrue()
    }

    // ---- 健康数据说明 ----

    @Test
    fun `健康数据说明五问五答逐字对齐原型`() {
        assertThat(AccountLifecycleText.HealthInfoNavTitle).isEqualTo("健康数据使用说明")
        assertThat(AccountLifecycleText.HealthInfoBanner)
            .isEqualTo("你的健康数据加密存储，不会用于广告或对外共享。")
        assertThat(AccountLifecycleText.HealthInfoNote)
            .isEqualTo("健康数据属敏感个人信息，详见《隐私政策》。")
        assertThat(AccountLifecycleText.HealthInfoAcknowledge).isEqualTo("我知道了")

        assertThat(HealthInfoText.items).hasSize(5)
        assertThat(HealthInfoText.items.map { it.question }).containsExactly(
            "收集哪些数据",
            "如何使用",
            "存储与加密",
            "谁能看到",
            "保留多久",
        ).inOrder()
        assertThat(HealthInfoText.items.map { it.answer }).containsExactly(
            "测量结果、健康档案、目标",
            "趋势分析、身体报告、AI 解读",
            "本机加密 + 云端端到端加密（可选）",
            "仅你本人；家庭成员数据独立",
            "账号存续期；删除/注销后按规则清除",
        ).inOrder()
    }
}
