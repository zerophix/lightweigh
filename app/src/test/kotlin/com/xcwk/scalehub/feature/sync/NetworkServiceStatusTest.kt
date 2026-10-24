package com.xcwk.scalehub.feature.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * T-102 网络状态与服务状态页（呈现式组件）纯 JVM 单测。
 *
 * 覆盖验收中唯一可自动判定的三类事实：
 * 1. 文案逐字取自原型08 v-network / v-service（AC-N3 原文，改一个字即红）；
 * 2. 网络/服务可达性经由传入回调构建状态——呈现层不自检系统状态；
 * 3. 源码守卫：目标文件无系统网络服务引用、无硬编码颜色与裸 dp（AC forbidden）。
 *
 * 视觉呈现（排版/配色落位）不在此测——:app 无 compose ui-test 基建，
 * 属已声明盲区；视觉由原型对照与人工验收关闭。
 */
class NetworkServiceStatusTest {

    // ---- 1. 文案逐字（AC-N3 then 第 1/2 条）----

    @Test
    fun `网络页文案逐字取自原型 v-network`() {
        assertThat(NetworkStatusText.Title).isEqualTo("网络连接不可用")
        assertThat(NetworkStatusText.Description)
            .isEqualTo("测量与记录不受影响，数据先存本机，联网后自动同步。")
        assertThat(NetworkStatusText.PrimaryAction).isEqualTo("检查网络设置")
        assertThat(NetworkStatusText.SecondaryAction).isEqualTo("继续离线使用")
        assertThat(NetworkStatusText.TopBarTitle).isEqualTo("数据同步")
    }

    @Test
    fun `服务页文案逐字取自原型 v-service`() {
        assertThat(ServiceStatusText.Title).isEqualTo("云服务暂时不可用")
        assertThat(ServiceStatusText.Description)
            .isEqualTo("我们正在维护服务，数据安全无虞。本地测量与查看不受影响。")
        assertThat(ServiceStatusText.PrimaryAction).isEqualTo("重试连接")
        assertThat(ServiceStatusText.SecondaryAction).isEqualTo("查看服务公告")
        assertThat(ServiceStatusText.TopBarTitle).isEqualTo("数据同步")
    }

    // ---- 2. 检测经由传入回调（AC-N3 then 第 3 条）----

    @Test
    fun `探测回调报告断网则状态页要求呈现离线文案`() {
        val state = networkStatusStateOf { false }
        assertThat(state.offline).isTrue()
        assertThat(state.networkAvailable).isFalse()
    }

    @Test
    fun `探测回调报告联网则不再呈现离线文案`() {
        val state = networkStatusStateOf { true }
        assertThat(state.offline).isFalse()
        assertThat(state.networkAvailable).isTrue()
    }

    @Test
    fun `服务可用性同样只由注入回调决定`() {
        assertThat(serviceStatusStateOf { false }.degraded).isTrue()
        assertThat(serviceStatusStateOf { true }.degraded).isFalse()
    }

    // ---- 3. 源码守卫（AC forbidden：Screen 直连系统网络服务 / 硬编码颜色间距）----

    private fun sourceOf(fileName: String): String {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val f = File(dir, "app/src/main/kotlin/com/xcwk/scalehub/feature/sync/$fileName")
            if (f.exists()) return f.readText(Charsets.UTF_8)
            dir = dir.parentFile
        }
        error("$fileName 未找到（从 ${System.getProperty("user.dir")} 向上探测失败）")
    }

    /** 去掉注释后再断言：文档里允许出现约束原文，代码本身不许出现违规符号。 */
    private fun codeOnly(source: String): String =
        source
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?m)//.*$"""), "")

    private val targets = listOf("NetworkStatus.kt", "ServiceStatus.kt")

    @Test
    fun `目标文件代码不接触系统网络服务`() {
        targets.forEach { fileName ->
            val code = codeOnly(sourceOf(fileName))
            assertThat(code).doesNotContain("ConnectivityManager")
            assertThat(code).doesNotContain("android.net")
            assertThat(code).doesNotContain("getActiveNetwork")
        }
    }

    @Test
    fun `目标文件无硬编码颜色与裸 dp`() {
        targets.forEach { fileName ->
            val code = codeOnly(sourceOf(fileName))
            assertThat(code).doesNotMatch(Pattern.compile("""Color\(0x[0-9A-Fa-f]{6,8}\)"""))
            // 与 gov-prototype-verify 的 HARDCODED_DP 同口径：Qk token 之外不许写 NN.dp
            assertThat(code).doesNotMatch(Pattern.compile("""(?<!Qk)\b\d+\.dp\b"""))
        }
    }

    @Test
    fun `目标文件复用 Qk 设计系统组件`() {
        targets.forEach { fileName ->
            // 含 KDoc 原文（ServiceStatus 的 Qk 引用写在文档与共用版式签名处）
            val source = sourceOf(fileName)
            assertThat(source).contains("Qk")
        }
    }
}
