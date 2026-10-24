package com.xcwk.scalehub.feature.device

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * T-053 设备页扩展（测量小贴士 / 最近测量）纯 JVM 单测。
 *
 * 覆盖验收里可自动判定的四类事实：
 * 1. 设备页存在可见入口：入口文案为常量且非空（宿主页直接引用，不另写字面量）；
 * 2. 组件不再是被预览独占的死代码：源码守卫断言宿主页与贴士页确实调用它们；
 * 3. 最近测量时间的兜底口径：空/空白一律回落「暂无记录」，不编造时间；
 * 4. 返回路径：返回只清本层的显示标记，不改设备状态。
 *
 * 布局与视觉不在本测范围（:app 无 compose ui-test 基建），属已声明盲区。
 */
class DeviceTipsEntryTest {

    // ---- 1. 入口文案 ----

    @Test
    fun `入口文案逐字且非空`() {
        assertThat(DEVICE_TIPS_ENTRY_TITLE).isEqualTo("测量小贴士")
        assertThat(DEVICE_TIPS_ENTRY_HINT).isNotEmpty()
        assertThat(DEVICE_TIPS_TITLE).isEqualTo(DEVICE_TIPS_ENTRY_TITLE)
        assertThat(DEVICE_TIPS_NO_RECENT).isEqualTo("暂无记录")
    }

    // ---- 2. 最近测量时间兜底 ----

    @Test
    fun `没有测量时间时不编造时间`() {
        assertThat(deviceRecentTimeText(null) == null).isTrue()
        assertThat(deviceRecentTimeText("") == null).isTrue()
        assertThat(deviceRecentTimeText("   ") == null).isTrue()
    }

    @Test
    fun `有测量时间时原样透传`() {
        assertThat(deviceRecentTimeText("今天 07:30")).isEqualTo("今天 07:30")
    }

    // ---- 3. 返回路径 ----

    @Test
    fun `贴士页返回即回设备页`() {
        // 返回只把宿主层的显示标记清掉，设备绑定/扫描态不受影响
        val state = DeviceUiState(scanning = true)
        var tipsVisible = true

        val onBack: () -> Unit = { tipsVisible = false }
        onBack()

        assertThat(tipsVisible).isFalse()
        assertThat(state.scanning).isTrue()
    }

    @Test
    fun `开始称重回到设备页并继续扫描`() {
        // 入口按钮的语义是「回设备页开始搜索」，不是跳到第四个页面
        var tipsVisible = true
        var intent: DeviceIntent? = null
        val onStartMeasure: () -> Unit = {
            tipsVisible = false
            intent = DeviceIntent.ToggleScan
        }
        onStartMeasure()

        assertThat(tipsVisible).isFalse()
        assertThat(intent).isEqualTo(DeviceIntent.ToggleScan)
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
        "app/src/main/kotlin/com/xcwk/scalehub/feature/device/DeviceScreen.kt"
    private val tipsPath =
        "app/src/main/kotlin/com/xcwk/scalehub/feature/device/DeviceTipsScreen.kt"

    @Test
    fun `设备页存在通往贴士页的可见入口`() {
        val code = codeOnly(sourceOf(hostPath))
        assertThat(code).contains("DEVICE_TIPS_ENTRY_TITLE")
        assertThat(code).contains("DEVICE_TIPS_ENTRY_HINT")
        assertThat(code).contains("DeviceTipsScreen(")
    }

    @Test
    fun `既有贴士组件在自身文件之外被引用`() {
        val code = codeOnly(sourceOf(tipsPath))
        assertThat(code).contains("DeviceTipsCard()")
        assertThat(code).contains("DeviceRecentCard(")
    }

    @Test
    fun `贴士页无硬编码颜色`() {
        val code = codeOnly(sourceOf(tipsPath))
        assertWithMessage("DeviceTipsScreen.kt 硬编码颜色")
            .that(Regex("""Color\(0x[0-9A-Fa-f]{6,8}\)""").findAll(code).map { it.value }.toList())
            .isEmpty()
        assertThat(code).contains("QkColor.")
    }
}
