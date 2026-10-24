package com.xcwk.scalehub.feature.privacy

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 隐私设置页「健康数据授权」入口 —— 入口登记 + 五态流程的进入/返回路径。
 *
 * `PrivacyAuthFlowTest` 已钉住逐字文案与状态机细节，这里只钉「设置页点得到」
 * 与「同意 / 撤回走到哪个分支」，避免入口没接上时测试仍全绿。
 */
class PrivacyAuthEntryTest {

    @Test
    fun `入口文案与目标屏指向隐私授权流程`() {
        assertThat(PrivacyAuthEntry.ID).isEqualTo("settings.privacyAuth")
        assertThat(PrivacyAuthEntry.TITLE).isEqualTo("健康数据授权")
        assertThat(PrivacyAuthEntry.HINT).contains("删除")
        assertThat(PrivacyAuthEntry.TARGET).isEqualTo("PrivacyAuthFlow")
    }

    @Test
    fun `进入即授权页`() {
        assertThat(PrivacyAuthUiState().step).isEqualTo(PrivacyAuthStep.Auth)
    }

    @Test
    fun `同意并继续进已授权页`() {
        val next = privacyAuthReduce(PrivacyAuthUiState(), PrivacyAuthIntent.AgreeAndContinue)

        assertThat(next.step).isEqualTo(PrivacyAuthStep.Granted)
    }

    @Test
    fun `仅本机同样进已授权页且强制关闭云同步`() {
        val selected = privacyAuthReduce(PrivacyAuthUiState(), PrivacyAuthIntent.ToggleCloudSync)
        val next = privacyAuthReduce(selected, PrivacyAuthIntent.LocalOnly)

        assertThat(next.step).isEqualTo(PrivacyAuthStep.Granted)
        assertThat(next.cloudSyncSelected).isFalse()
    }

    @Test
    fun `撤回先弹层再落到未授权页`() {
        val granted = privacyAuthReduce(PrivacyAuthUiState(), PrivacyAuthIntent.AgreeAndContinue)
        val asking = privacyAuthReduce(granted, PrivacyAuthIntent.RequestWithdraw)

        assertThat(asking.withdrawSheetVisible).isTrue()
        assertThat(asking.step).isEqualTo(PrivacyAuthStep.Granted)

        val withdrawn = privacyAuthReduce(asking, PrivacyAuthIntent.ConfirmWithdraw)
        assertThat(withdrawn.step).isEqualTo(PrivacyAuthStep.Unauth)
        assertThat(withdrawn.withdrawSheetVisible).isFalse()
    }

    @Test
    fun `规则页与咨询页都有返回授权页的出口`() {
        val rules = privacyAuthReduce(PrivacyAuthUiState(), PrivacyAuthIntent.OpenRules)
        assertThat(rules.step).isEqualTo(PrivacyAuthStep.Rules)

        val consult = privacyAuthReduce(PrivacyAuthUiState(), PrivacyAuthIntent.GoConsult)
        assertThat(consult.step).isEqualTo(PrivacyAuthStep.Consult)
        assertThat(privacyAuthReduce(consult, PrivacyAuthIntent.OpenPersonalization).step)
            .isEqualTo(PrivacyAuthStep.Auth)
    }
}
