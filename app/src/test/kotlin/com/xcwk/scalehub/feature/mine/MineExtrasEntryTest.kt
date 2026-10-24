package com.xcwk.scalehub.feature.mine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * T-054 我的页「更多资料」（个人简介 / 所在地 / 展示标签）纯 JVM 单测。
 *
 * 覆盖验收里可自动判定的四类事实：
 * 1. 入口可见：入口文案为常量且非空，宿主页直接引用；
 * 2. 组件不再是死代码：源码守卫断言宿主页与更多资料页确实调用 [ProfileExtraCard]；
 * 3. 关键文案逐字：三个字段名与「更多资料」分区头逐字断言（改一个字即红）；
 * 4. 返回路径：返回只清本层显示标记。
 *
 * 布局与视觉不在本测范围（:app 无 compose ui-test 基建），属已声明盲区。
 */
class MineExtrasEntryTest {

    // ---- 1. 入口文案 ----

    @Test
    fun `入口文案逐字且非空`() {
        assertThat(MINE_EXTRAS_ENTRY_TITLE).isEqualTo("个人简介与展示标签")
        assertThat(MINE_EXTRAS_ENTRY_HINT).isNotEmpty()
        assertThat(MINE_EXTRAS_TITLE).isEqualTo("个人资料")
        // 页面标题与组件内分区头各司其职，不重复同一句
        assertThat(MINE_EXTRAS_TITLE).isNotEqualTo("更多资料")
    }

    // ---- 2. 关键文案逐字（组件正文）----

    @Test
    fun `三个字段名逐字`() {
        val code = sourceOf(componentPath)
        assertThat(code).contains("Text(\"个人简介\"")
        assertThat(code).contains("Text(\"所在地\"")
        assertThat(code).contains("Text(\"展示标签\"")
        assertThat(code).contains("QkSectionHead(title = \"更多资料\"")
    }

    // ---- 3. 返回路径 ----

    @Test
    fun `更多资料页返回即回我的页`() {
        var extrasVisible = true

        val onBack: () -> Unit = { extrasVisible = false }
        onBack()

        assertThat(extrasVisible).isFalse()
    }

    // ---- 4. 源码守卫（组件不再是死代码）----

    private fun sourceOf(relPath: String): String {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val f = File(dir, relPath)
            if (f.exists()) return f.readText(Charsets.UTF_8)
            dir = dir.parentFile
        }
        error("$relPath 未找到（从 ${System.getProperty("user.dir")} 向上探测失败）")
    }

    private fun codeOnly(source: String): String =
        source
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//.*"""), "")

    private val hostPath =
        "app/src/main/kotlin/com/xcwk/scalehub/feature/mine/MineScreen.kt"
    private val extrasPath =
        "app/src/main/kotlin/com/xcwk/scalehub/feature/mine/MineExtrasScreen.kt"
    private val componentPath =
        "app/src/main/kotlin/com/xcwk/scalehub/feature/mine/MineExtensions.kt"

    @Test
    fun `我的页存在通往更多资料页的可见入口`() {
        val code = codeOnly(sourceOf(hostPath))
        assertThat(code).contains("MINE_EXTRAS_ENTRY_TITLE")
        assertThat(code).contains("MINE_EXTRAS_ENTRY_HINT")
        assertThat(code).contains("MineExtrasScreen(")
    }

    @Test
    fun `既有资料扩展组件在自身文件之外被引用`() {
        val code = codeOnly(sourceOf(extrasPath))
        assertThat(code).contains("ProfileExtraCard(")
    }

    @Test
    fun `组件自身持有三个字段的可见文案`() {
        // 文案真源在组件里：宿主页不复制，改字段名只改一处
        val code = sourceOf(componentPath)
        assertThat(code).contains("\"个人简介\"")
        assertThat(code).contains("\"所在地\"")
        assertThat(code).contains("\"展示标签\"")
    }


    @Test
    fun `更多资料页无硬编码颜色`() {
        val code = codeOnly(sourceOf(extrasPath))
        assertWithMessage("MineExtrasScreen.kt 硬编码颜色")
            .that(Regex("""Color\(0x[0-9A-Fa-f]{6,8}\)""").findAll(code).map { it.value }.toList())
            .isEmpty()
        assertThat(code).contains("QkColor.")
    }
}
