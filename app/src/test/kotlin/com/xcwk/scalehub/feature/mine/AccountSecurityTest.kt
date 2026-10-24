package com.xcwk.scalehub.feature.mine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * T-101 账号与安全页纯 JVM 单测。
 *
 * 覆盖三类可自动判定事实：
 * 1. 文案逐字取自原型07 v-account（AC then 原文，改一个字即红）；
 * 2. 三绑定行顺序与原型一致；
 * 3. 源码守卫：无硬编码颜色、无裸 dp、不触系统敏感 API、绑定态文案由
 *    调用方注入（不编造原型未定义的状态文案）。
 */
class AccountSecurityTest {

    @Test
    fun `v-account 文案逐字取自原型`() {
        assertThat(TITLE_ACCOUNT_SECURITY).isEqualTo("账号与安全")
        assertThat(ROW_PHONE).isEqualTo("手机号")
        assertThat(PHONE_MASKED_DEFAULT).isEqualTo("138****0000")
        assertThat(ACTION_REPLACE_PHONE).isEqualTo("更换")
        assertThat(ROW_EMAIL).isEqualTo("邮箱")
        assertThat(EMAIL_UNBOUND).isEqualTo("未绑定")
        assertThat(ACTION_BIND).isEqualTo("绑定")
        assertThat(ROW_PASSWORD).isEqualTo("登录密码")
        assertThat(PASSWORD_SET).isEqualTo("已设置")
        assertThat(ACTION_MODIFY).isEqualTo("修改")
        assertThat(ROW_ACCOUNT_CLOSURE).isEqualTo("注销账号")
        assertThat(ACCOUNT_CLOSURE_CAPTION).isEqualTo("注销后数据永久删除")
        assertThat(ACCOUNT_CLOSURE_HINT).isEqualTo("注销前建议先在「隐私与数据管理」导出数据。")
    }

    @Test
    fun `三绑定行顺序与原型一致`() {
        assertThat(ACCOUNT_ROWS)
            .containsExactly("手机号", "邮箱", "登录密码")
            .inOrder()
    }

    @Test
    fun `注销入口接 T-089 流程文案`() {
        // 注销行点击应可达 T-089 的注销须知标题（流程衔接由常量一致性保证）
        assertThat(ROW_ACCOUNT_CLOSURE).isEqualTo(TITLE_CLOSURE_APPLY)
    }

    // ---- 源码守卫 ----

    private val src: String = File(
        "src/main/kotlin/com/xcwk/scalehub/feature/mine/AccountSecurity.kt",
    ).readText()

    @Test
    fun `无硬编码颜色与裸 dp`() {
        assertThat(Regex("""Color\(0x""").containsMatchIn(src)).isFalse()
        assertThat(Regex("""\b\d+\.dp\b""").containsMatchIn(src)).isFalse()
    }

    @Test
    fun `不触系统敏感 API`() {
        listOf("ConnectivityManager", "android.net", "ContentResolver", "Room")
            .forEach { assertThat(src).doesNotContain(it) }
    }

    @Test
    fun `使用 Qk 组件与主题 token`() {
        assertThat(src).contains("QkCard")
        assertThat(src).contains("QkListRow")
        assertThat(src).contains("QkColor.")
    }
}
