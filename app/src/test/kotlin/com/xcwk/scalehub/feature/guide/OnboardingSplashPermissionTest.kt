package com.xcwk.scalehub.feature.guide

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-075（guide: 启动引导：闪屏 / 条款 / 权限 / 授权 / 拒绝）纯逻辑单测。
 *
 * 覆盖验收里可自动判定的两块：
 * - 原型文案逐字固定（`02-onboarding-permission.html` 的 v-splash / v-terms /
 *   v-permissions / v-granted / v-denied），防止后续改写文案
 * - 可判定逻辑：条款勾选门控、权限结果分支、更新弹窗开合
 *
 * 视觉排版与系统弹窗（真机/平台绘制）不在 JVM 单测范围。
 */
class OnboardingSplashPermissionTest {

    // ---- v-splash ----

    @Test
    fun `闪屏文案逐字取自原型 v-splash`() {
        assertThat(SPLASH_TITLE).isEqualTo("轻衡")
        assertThat(SPLASH_TAGLINE).isEqualTo("让健康，轻一点。")
    }

    // ---- v-terms ----

    @Test
    fun `条款页文案逐字取自原型 v-terms`() {
        assertThat(TERMS_HEADLINE).isEqualTo("你的健康数据，由你做主")
        assertThat(TERMS_SUBLINE).isEqualTo("开始前，请阅读并同意以下条款。")
        assertThat(TERMS_AGREEMENT_LABEL).isEqualTo("我已阅读并同意《用户协议》")
        assertThat(TERMS_PRIVACY_LABEL).isEqualTo("我已阅读并同意《隐私政策》")
        assertThat(TERMS_BANNER).isEqualTo("未同意前，我们不会收集任何个人数据。")
        assertThat(TERMS_ACCEPT).isEqualTo("同意并继续")
        assertThat(TERMS_DECLINE).isEqualTo("不同意，退出应用")
    }

    @Test
    fun `未勾选时同意并继续不可用`() {
        assertThat(TermsUiState().acceptEnabled).isFalse()
        assertThat(TermsUiState(agreementAgreed = true).acceptEnabled).isFalse()
        assertThat(TermsUiState(privacyAgreed = true).acceptEnabled).isFalse()
    }

    @Test
    fun `两项都勾选时同意并继续可用`() {
        assertThat(TermsUiState(agreementAgreed = true, privacyAgreed = true).acceptEnabled).isTrue()
    }

    @Test
    fun `勾选意图互不影响且可回退`() {
        val both = TermsUiState().reduce(TermsIntent.ToggleAgreement).reduce(TermsIntent.TogglePrivacy)
        assertThat(both.acceptEnabled).isTrue()
        // 再点一次「用户协议」取消勾选，隐私勾选保持
        val reverted = both.reduce(TermsIntent.ToggleAgreement)
        assertThat(reverted.agreementAgreed).isFalse()
        assertThat(reverted.privacyAgreed).isTrue()
        assertThat(reverted.acceptEnabled).isFalse()
    }

    @Test
    fun `条款未全勾选时不允许进入权限流程`() {
        val onTermsButUnchecked = SplashUiState(step = SplashStep.Terms)
        assertThat(canProceedToPermissions(SplashStep.Terms, onTermsButUnchecked.terms)).isFalse()

        val accepted = onTermsButUnchecked.reduce(SplashIntent.ToggleAgreement(TermsIntent.ToggleAgreement))
            .reduce(SplashIntent.ToggleAgreement(TermsIntent.TogglePrivacy))
        assertThat(accepted.terms.acceptEnabled).isTrue()
        assertThat(canProceedToPermissions(SplashStep.Terms, accepted.terms)).isTrue()
    }

    @Test
    fun `闪屏阶段即使条款已勾选也不在权限流程里`() {
        assertThat(canProceedToPermissions(SplashStep.Splash, TermsUiState(true, true))).isFalse()
    }

    @Test
    fun `进入意图只把闪屏切到条款页`() {
        val state = SplashUiState().reduce(SplashIntent.Enter)
        assertThat(state.step).isEqualTo(SplashStep.Terms)
        assertThat(state.terms.acceptEnabled).isFalse()
    }

    // ---- v-permissions ----

    @Test
    fun `权限说明文案逐字取自原型 v-permissions`() {
        assertThat(PERMISSION_HEADLINE).isEqualTo("需要几项权限，把体脂秤连起来")
        assertThat(PERMISSION_SUBLINE).isEqualTo("只用于连接设备与提醒测量。")
        assertThat(PERMISSION_CONTINUE).isEqualTo("继续（触发系统授权）")
        assertThat(PERMISSION_ITEMS.map { it.title })
            .containsExactly("蓝牙", "附近设备", "通知")
            .inOrder()
        assertThat(PERMISSION_ITEMS.map { it.desc })
            .containsExactly(
                "用于搜索并连接体脂秤",
                "Android 12+ 发现体脂秤所需",
                "可选，用于测量提醒",
            ).inOrder()
    }

    @Test
    fun `只有通知是可选项其余两项必选`() {
        assertThat(PERMISSION_ITEMS.filter { it.required }.map { it.kind })
            .containsExactly(PermissionKind.Bluetooth, PermissionKind.NearbyDevices)
        assertThat(PERMISSION_ITEMS.single { !it.required }.kind)
            .isEqualTo(PermissionKind.Notifications)
    }

    // ---- v-granted / v-denied ----

    @Test
    fun `授权完成与更新弹窗文案逐字取自原型 v-granted`() {
        assertThat(GRANTED_TITLE).isEqualTo("权限已就绪")
        assertThat(GRANTED_BODY).isEqualTo("现在可以连接体脂秤了。")
        assertThat(UPDATE_DIALOG_TITLE).isEqualTo("发现新版本 v0.2.0")
        assertThat(UPDATE_DIALOG_BODY).isEqualTo("新增身体评分、优化蓝牙稳定性。")
        assertThat(UPDATE_LATER).isEqualTo("稍后")
        assertThat(UPDATE_NOW).isEqualTo("立即更新")
    }

    @Test
    fun `拒绝页文案逐字取自原型 v-denied`() {
        assertThat(DENIED_HEADLINE).isEqualTo("需要蓝牙权限才能连接体脂秤")
        assertThat(DENIED_SUBLINE).isEqualTo("你拒绝了蓝牙权限，暂时无法连接设备。")
        assertThat(DENIED_METHOD_TITLE).isEqualTo("开启方法")
        assertThat(DENIED_STEP_OPEN).isEqualTo("点击下方「去设置开启」")
        assertThat(DENIED_STEP_PATH).isEqualTo("设置 › 应用 › 轻衡 › 权限 › 蓝牙")
        assertThat(DENIED_OPEN_SETTINGS).isEqualTo("去设置开启")
        assertThat(DENIED_SKIP).isEqualTo("暂不连接")
    }

    // ---- 授权结果分支 ----

    @Test
    fun `蓝牙允许后进授权完成页`() {
        val state = permissionFlowReduce(
            PermissionFlowUiState(),
            PermissionFlowIntent.Resulted(PermissionKind.Bluetooth, PermissionResult.Granted),
        )
        assertThat(state.step).isEqualTo(PermissionFlowStep.Granted)
        assertThat(state.granted).containsExactly(PermissionKind.Bluetooth)
        assertThat(state.denied).isEmpty()
    }

    @Test
    fun `蓝牙拒绝后进拒绝引导页`() {
        val state = permissionFlowReduce(
            PermissionFlowUiState(),
            PermissionFlowIntent.Resulted(PermissionKind.Bluetooth, PermissionResult.Denied),
        )
        assertThat(state.step).isEqualTo(PermissionFlowStep.Denied)
        assertThat(state.denied).containsExactly(PermissionKind.Bluetooth)
    }

    @Test
    fun `附近设备拒绝不阻断蓝牙连接`() {
        val state = permissionFlowReduce(
            permissionFlowReduce(
                PermissionFlowUiState(),
                PermissionFlowIntent.Resulted(PermissionKind.Bluetooth, PermissionResult.Granted),
            ),
            PermissionFlowIntent.Resulted(PermissionKind.NearbyDevices, PermissionResult.Denied),
        )
        assertThat(state.step).isEqualTo(PermissionFlowStep.Granted)
        assertThat(state.denied).containsExactly(PermissionKind.NearbyDevices)
    }

    @Test
    fun `只回传可选通知结果时仍停在说明页`() {
        val state = permissionFlowReduce(
            PermissionFlowUiState(),
            PermissionFlowIntent.Resulted(PermissionKind.Notifications, PermissionResult.Granted),
        )
        assertThat(state.step).isEqualTo(PermissionFlowStep.Explain)
        assertThat(state.granted).containsExactly(PermissionKind.Notifications)
    }

    @Test
    fun `拒绝后再申请会清掉上一次的拒绝标记`() {
        val denied = permissionFlowReduce(
            PermissionFlowUiState(),
            PermissionFlowIntent.Resulted(PermissionKind.Bluetooth, PermissionResult.Denied),
        )
        val retried = permissionFlowReduce(
            denied,
            PermissionFlowIntent.PermissionRequested(PermissionKind.Bluetooth),
        )
        assertThat(retried.denied).isEmpty()
    }

    @Test
    fun `拒绝后从设置返回并允许会进完成页`() {
        val denied = permissionFlowReduce(
            PermissionFlowUiState(),
            PermissionFlowIntent.Resulted(PermissionKind.Bluetooth, PermissionResult.Denied),
        )
        val granted = permissionFlowReduce(
            denied,
            PermissionFlowIntent.Resulted(PermissionKind.Bluetooth, PermissionResult.Granted),
        )
        assertThat(granted.step).isEqualTo(PermissionFlowStep.Granted)
        assertThat(granted.denied).isEmpty()
    }

    @Test
    fun `跳设置与暂不连接不改呈现态`() {
        val denied = permissionFlowReduce(
            PermissionFlowUiState(),
            PermissionFlowIntent.Resulted(PermissionKind.Bluetooth, PermissionResult.Denied),
        )
        assertThat(permissionFlowReduce(denied, PermissionFlowIntent.OpenSettings)).isEqualTo(denied)
        assertThat(permissionFlowReduce(denied, PermissionFlowIntent.SkipConnection)).isEqualTo(denied)
    }

    @Test
    fun `更新弹窗开合与权限三态互不影响`() {
        val granted = permissionFlowReduce(
            PermissionFlowUiState(),
            PermissionFlowIntent.Resulted(PermissionKind.Bluetooth, PermissionResult.Granted),
        )
        val shown = permissionFlowReduce(granted, PermissionFlowIntent.ShowUpdateDialog)
        assertThat(shown.updateDialogVisible).isTrue()
        assertThat(shown.step).isEqualTo(PermissionFlowStep.Granted)

        val hidden = permissionFlowReduce(shown, PermissionFlowIntent.DismissUpdateDialog)
        assertThat(hidden.updateDialogVisible).isFalse()
        assertThat(hidden.step).isEqualTo(PermissionFlowStep.Granted)
    }

    @Test
    fun `默认态停在权限说明页`() {
        val state = PermissionFlowUiState()
        assertThat(state.step).isEqualTo(PermissionFlowStep.Explain)
        assertThat(state.updateDialogVisible).isFalse()
    }
}
