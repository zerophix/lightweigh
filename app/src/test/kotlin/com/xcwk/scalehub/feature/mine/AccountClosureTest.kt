package com.xcwk.scalehub.feature.mine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * T-089 账号注销四屏（呈现式组件）纯 JVM 单测。
 *
 * 覆盖三类可自动判定事实：
 * 1. 文案逐字取自原型07 四个 v-closure-* view（AC then 原文，改一个字即红）；
 * 2. 冷静期与验证码倒计时为纯函数——冷静期 > 0 必须展示撤销入口，
 *    归零后才允许进入结果页（AC 末条门禁）；
 * 3. 源码守卫：目标文件无硬编码颜色、无裸 dp、不触系统敏感 API。
 *
 * 视觉排版不在此测（:app 无 compose ui-test 基建，已声明盲区）。
 */
class AccountClosureTest {

    // ---- 1. 文案逐字（AC then 第 1..4 条）----

    @Test
    fun `v-closure-apply 文案逐字取自原型`() {
        assertThat(TITLE_CLOSURE_APPLY).isEqualTo("注销账号")
        assertThat(HEAD_CLOSURE_BEFORE).isEqualTo("注销前，请了解")
        assertThat(WARN_CLOSURE_IRREVERSIBLE).isEqualTo("注销是不可逆操作。")
        assertThat(CONSEQUENCE_DELETE_RECORDS).isEqualTo("全部测量记录将删除")
        assertThat(CONSEQUENCE_NO_RECOVERY).isEqualTo("本地与云端均不可恢复")
        assertThat(CONSEQUENCE_UNLINK).isEqualTo("AI 对话与家庭成员关联将解除")
        assertThat(ACTION_CLOSURE_NEXT).isEqualTo("我已了解，下一步")
        assertThat(ACTION_CLOSURE_RECONSIDER).isEqualTo("再想想")
    }

    @Test
    fun `v-closure-verify 文案逐字取自原型`() {
        assertThat(TITLE_CLOSURE_VERIFY).isEqualTo("身份验证")
        assertThat(HEAD_CLOSURE_VERIFY).isEqualTo("验证是你本人")
        assertThat(LABEL_CLOSURE_SEND_TO).isEqualTo("验证码将发送至")
        assertThat(CLOSURE_PHONE_MASKED).isEqualTo("138****0000")
        assertThat(LABEL_CLOSURE_CODE).isEqualTo("验证码")
        assertThat(RESEND_COUNTDOWN_TEMPLATE_CLOSURE).isEqualTo("%ds 后重发")
        assertThat(HINT_CLOSURE_COOLING).isEqualTo("验证后进入 7 天冷静期，期间登录可撤销。")
        assertThat(ACTION_CONFIRM_CLOSURE).isEqualTo("确认注销")
    }

    @Test
    fun `v-closure-processing 文案逐字取自原型`() {
        assertThat(TITLE_CLOSURE_SUBMITTED).isEqualTo("注销申请已提交")
        assertThat(TITLE_CLOSURE_PROCESSING).isEqualTo("注销申请处理中")
        assertThat(LABEL_CLOSURE_ENTER).isEqualTo("账号进入")
        assertThat(LABEL_CLOSURE_COOLING_DAYS).isEqualTo("7 天冷静期")
        assertThat(LABEL_CLOSURE_COOLING_TAIL).isEqualTo("，期间登录可撤销。")
        assertThat(LABEL_CLOSURE_REMAINING).isEqualTo("剩余冷静期")
        assertThat(HINT_CLOSURE_PROTECTED).isEqualTo("冷静期内数据仍受保护，不会被使用。")
        assertThat(ACTION_CLOSURE_REVOKE).isEqualTo("撤销注销，恢复账号")
        assertThat(ACTION_CLOSURE_SIMULATE_END).isEqualTo("模拟冷静期结束 → 结果")
    }

    @Test
    fun `v-closure-result 文案逐字取自原型`() {
        assertThat(TITLE_CLOSURE_RESULT).isEqualTo("账号已注销")
        assertThat(HEAD_CLOSURE_RESULT_DELETED).isEqualTo("你的账号与相关数据已按规则删除。")
        assertThat(HEAD_CLOSURE_RESULT_THANKS).isEqualTo("感谢你曾使用轻衡。")
        assertThat(ACTION_CLOSURE_BACK).isEqualTo("返回（演示结束）")
    }

    // ---- 2. 纯函数（AC 末条：冷静期门禁 + 倒计时）----

    @Test
    fun `冷静期剩余格式化为 原型形态`() {
        assertThat(coolingRemainingLabel(158)).isEqualTo("6天 14时")
        assertThat(coolingRemainingLabel(0)).isEqualTo("0天 0时")
    }

    @Test
    fun `冷静期大于零时可撤销且不得进入结果页`() {
        assertThat(canRevokeClosure(158)).isTrue()
        assertThat(canEnterResult(158)).isFalse()
    }

    @Test
    fun `冷静期归零后不可撤销但可进入结果页`() {
        assertThat(canRevokeClosure(0)).isFalse()
        assertThat(canEnterResult(0)).isTrue()
    }

    @Test
    fun `验证码重发倒计时文案`() {
        assertThat(closureResendLabel(58)).isEqualTo("58s 后重发")
        assertThat(closureResendLabel(0)).isEqualTo("重新发送")
        assertThat(closureResendLabel(-1)).isEqualTo("重新发送")
    }

    // ---- 3. 源码守卫（同包既有模式）----

    @Test
    fun `目标文件无硬编码颜色与裸 dp`() {
        val src = File("src/main/kotlin/com/xcwk/scalehub/feature/mine/AccountClosure.kt")
            .readText()
            .substringBefore("// ---- 演示入口")
        assertThat(Regex("""Color\(0x""").containsMatchIn(src)).isFalse()
        assertThat(Regex("""\b\d+\.dp\b""").containsMatchIn(src)).isFalse()
    }

    @Test
    fun `目标文件不触系统敏感 API`() {
        val src = File("src/main/kotlin/com/xcwk/scalehub/feature/mine/AccountClosure.kt").readText()
        listOf("ConnectivityManager", "android.net", "getSharedPreferences", "ContentResolver")
            .forEach { assertThat(src).doesNotContain(it) }
    }

    @Test
    fun `目标文件使用 Qk 组件与主题 token`() {
        val src = File("src/main/kotlin/com/xcwk/scalehub/feature/mine/AccountClosure.kt").readText()
        assertThat(src).contains("QkCard")
        assertThat(src).contains("QkButton")
        assertThat(src).contains("QkColor.")
    }
}
