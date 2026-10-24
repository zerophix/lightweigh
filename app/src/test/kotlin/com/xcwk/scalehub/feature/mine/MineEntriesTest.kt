package com.xcwk.scalehub.feature.mine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * 我的页「账号与信息」四入口的接入（T-050 / T-089 / T-090 / T-101 接入波）。
 *
 * 钉四件事：
 * 1. 每行入口都有文案且指向一个真实存在的交付屏（不留"点了没反应"的行）；
 * 2. 我的页真的渲染了这张卡，我的页宿主真的挂了二级页宿主（回调被摘掉即红）；
 * 3. 注销四态只能逐级前进，返回路径回到我的页；
 * 4. 手机号/版本号这类注入值不在界面里写死。
 */
class MineEntriesTest {

    private fun source(rel: String) = File(rel).readText()

    private val featureDir = File("src/main/kotlin/com/xcwk/scalehub/feature")

    private fun deliveredScreens(): String =
        featureDir.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }

    // ---- 1. 入口 → 目标屏 ----

    @Test
    fun `四行入口文案齐全且不重复`() {
        assertThat(MINE_ENTRIES).hasSize(4)
        assertThat(MINE_ENTRIES.map { it.title }).containsNoDuplicates()
        MINE_ENTRIES.forEach { entry ->
            assertThat(entry.title).isNotEmpty()
            assertThat(entry.hint).isNotEmpty()
        }
        assertThat(MINE_ENTRIES.map { it.title }).containsExactly(
            "账号与安全",
            "隐私与数据管理",
            "健康数据说明",
            "关于轻衡",
        ).inOrder()
    }

    @Test
    fun `每行入口都指向已交付的屏函数`() {
        val screens = deliveredScreens()
        MINE_ENTRIES.forEach { entry ->
            assertThat(screens).contains("fun ${entry.screen}(")
        }
        assertThat(mineEntryScreenName("账号与安全")).isEqualTo("AccountSecurityScreen")
        assertThat(mineEntryScreenName("不存在的行")).isNull()
    }

    @Test
    fun `组标题不为空`() {
        assertThat(MINE_ENTRIES_SECTION).isEqualTo("账号与信息")
    }

    // ---- 2. 入口真的挂在宿主页上 ----

    @Test
    fun `我的页渲染入口卡`() {
        val screen = source("src/main/kotlin/com/xcwk/scalehub/feature/mine/MineScreen.kt")
        assertThat(screen).contains("MineEntriesCard(onOpenEntry = onOpenMineEntry)")
        assertThat(screen).contains("onOpenMineEntry: (MineEntry) -> Unit")
    }

    @Test
    fun `我的页路由挂载二级页宿主并可返回`() {
        val route = source("src/main/kotlin/com/xcwk/scalehub/feature/mine/MineRoute.kt")
        assertThat(route).contains("MineMoreHost(entry = entry, onBack = { subPage = null })")
        assertThat(route).contains("onOpenMineEntry = { subPage = it }")
    }

    @Test
    fun `二级页宿主有系统返回键且四屏都装配到`() {
        val host = source("src/main/kotlin/com/xcwk/scalehub/feature/mine/MineMoreHost.kt")
        assertThat(host).contains("BackHandler(onBack = onBack)")
        MineEntry.entries.forEach { entry ->
            assertThat(host).contains("MineEntry.${entry.name} ->")
        }
    }

    // ---- 3. 注销四态推进表 ----

    @Test
    fun `注销流程逐级前进`() {
        assertThat(closureStepReduce(ClosureStep.Apply, ClosureEvent.Next))
            .isEqualTo(ClosureStep.Verify)
        assertThat(closureStepReduce(ClosureStep.Verify, ClosureEvent.Confirm))
            .isEqualTo(ClosureStep.Processing)
        assertThat(closureStepReduce(ClosureStep.Processing, ClosureEvent.CoolDownEnded))
            .isEqualTo(ClosureStep.Result)
    }

    @Test
    fun `注销流程不允许越级或倒退`() {
        assertThat(closureStepReduce(ClosureStep.Apply, ClosureEvent.Confirm))
            .isEqualTo(ClosureStep.Apply)
        assertThat(closureStepReduce(ClosureStep.Apply, ClosureEvent.CoolDownEnded))
            .isEqualTo(ClosureStep.Apply)
        assertThat(closureStepReduce(ClosureStep.Result, ClosureEvent.Next))
            .isEqualTo(ClosureStep.Result)
    }

    @Test
    fun `冷静期与原型的 7 天一致`() {
        assertThat(CLOSURE_COOLING_HOURS).isEqualTo(168L)
        assertThat(coolingRemainingLabel(CLOSURE_COOLING_HOURS)).isEqualTo("7天 0时")
    }

    // ---- 4. 注入值不写死 ----

    @Test
    fun `手机号掩码取自会话真值`() {
        assertThat(accountPhoneMasked("13800000000")).isEqualTo("138****0000")
        assertThat(accountPhoneMasked("+86 13800000000")).isEqualTo("138****0000")
        assertThat(accountPhoneMasked("")).isEqualTo(ACCOUNT_PHONE_UNBOUND)
        assertThat(accountPhoneMasked("  ")).isEqualTo(ACCOUNT_PHONE_UNBOUND)
    }

    @Test
    fun `版本号按原型加 v 前缀且不重复加`() {
        assertThat(aboutVersionLabel("0.1.0")).isEqualTo("v0.1.0")
        assertThat(aboutVersionLabel("v0.2.0")).isEqualTo("v0.2.0")
        assertThat(aboutTagline(aboutVersionLabel("0.1.0"))).isEqualTo("v0.1.0 · 让健康轻一点")
    }
}
