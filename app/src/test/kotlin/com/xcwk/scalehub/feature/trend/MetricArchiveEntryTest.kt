package com.xcwk.scalehub.feature.trend

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * T-055 档案 / 指标页接入（围度 / 内脏脂肪 / 目标进度）纯 JVM 单测。
 *
 * 覆盖验收里可自动判定的四类事实：
 * 1. 趋势页存在可见入口：入口文案为常量且非空，宿主页直接引用；
 * 2. 组件不再是死代码：源码守卫断言宿主页确实调用 [MetricDetailScreen]，
 *    以及围度 / 目标进度两个档案组件；
 * 3. 读数派生口径：体重取最新点且带区间变化，缺数据一律「--」不冒用别的指标；
 * 4. 返回路径：详情 → 列表 → 趋势页两级返回都不改数据态。
 *
 * 布局与视觉不在本测范围（:app 无 compose ui-test 基建），属已声明盲区。
 */
class MetricArchiveEntryTest {

    private fun weightBlock(): TrendBlockUi = TrendBlockUi(
        points = listOf(TrendPointUi(0, 62.4), TrendPointUi(1, 62.1)),
        caption = "2026/10/20 ~ 2026/10/21",
        highValue = "62.4", highDay = "10/20",
        lowValue = "62.1", lowDay = "10/21",
        changeValue = "-0.3公斤", emptyText = null,
    )

    // ---- 1. 入口文案 ----

    @Test
    fun `入口文案逐字且非空`() {
        assertThat(METRIC_ARCHIVE_ENTRY_TITLE).isEqualTo("身体指标与档案")
        assertThat(METRIC_ARCHIVE_ENTRY_HINT).isNotEmpty()
        assertThat(METRIC_ARCHIVE_TITLE).isEqualTo("全部身体指标")
        assertThat(METRIC_ARCHIVE_NO_VALUE).isEqualTo("--")
    }

    // ---- 2. 读数派生 ----

    @Test
    fun `八项指标按原型顺序全覆盖`() {
        val readings = metricArchiveReadings(TrendUiState())

        assertThat(readings.map { it.spec.key }).containsExactlyElementsIn(METRIC_SPECS.map { it.key }).inOrder()
        assertThat(readings.map { it.spec.title })
            .containsAtLeast("体重", "内脏脂肪等级", "BMI", "身体评分")
        // T-055 点名的一项必须在列表里（否则详情页无从进入）
        assertThat(readings.any { it.spec.key == MetricKey.Visceral }).isTrue()
    }

    @Test
    fun `体重取最新点并带区间变化`() {
        val reading = metricArchiveReadings(TrendUiState(weight = weightBlock()))
            .first { it.spec.key == MetricKey.Weight }

        assertThat(reading.value).isEqualTo("62.1")
        assertThat(reading.deltaText).isEqualTo("-0.3公斤")
        assertThat(reading.trendPoints).hasSize(2)
    }

    @Test
    fun `没有体重块时体重显示占位而不是编造数值`() {
        val reading = metricArchiveReadings(TrendUiState())
            .first { it.spec.key == MetricKey.Weight }

        assertThat(reading.value).isEqualTo(METRIC_ARCHIVE_NO_VALUE)
        assertThat(reading.trendPoints).isEmpty()
    }

    @Test
    fun `没有运行时读数的指标一律显示占位`() {
        val readings = metricArchiveReadings(TrendUiState(weight = weightBlock()))

        readings.filter { it.spec.key != MetricKey.Weight }.forEach { reading ->
            assertThat(reading.value).isEqualTo(METRIC_ARCHIVE_NO_VALUE)
        }
    }

    @Test
    fun `档案侧栏缺省为空不伪造目标`() {
        assertThat(TrendUiState().archive == null).isTrue()
        assertThat(MetricArchiveUi().targetWeight == null).isTrue()
        assertThat(MetricArchiveUi().waistCm == null).isTrue()
    }

    // ---- 3. 返回路径 ----

    @Test
    fun `详情返回列表再返回趋势页`() {
        val readings = metricArchiveReadings(TrendUiState(weight = weightBlock()))
        var archiveVisible = true

        // 列表里点开内脏脂肪 → 拿到对应详情
        val opened = metricArchiveOpenReading(MetricKey.Visceral.name, readings)
        assertThat(opened?.spec?.key).isEqualTo(MetricKey.Visceral)

        // 详情页返回：key 清空 → 解析为 null，回到列表（宿主仍在）
        assertThat(metricArchiveOpenReading(null, readings) == null).isTrue()
        assertThat(archiveVisible).isTrue()

        // 列表页返回：清宿主显示标记，回趋势页
        archiveVisible = false
        assertThat(archiveVisible).isFalse()
    }

    @Test
    fun `未知指标 key 不造详情页`() {
        val readings = metricArchiveReadings(TrendUiState())

        assertThat(metricArchiveOpenReading("NoSuchMetric", readings) == null).isTrue()
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

    private val trendPath =
        "app/src/main/kotlin/com/xcwk/scalehub/feature/trend/TrendScreen.kt"
    private val archivePath =
        "app/src/main/kotlin/com/xcwk/scalehub/feature/trend/MetricArchiveScreen.kt"

    @Test
    fun `趋势页存在通往指标档案页的可见入口`() {
        val code = codeOnly(sourceOf(trendPath))
        assertThat(code).contains("METRIC_ARCHIVE_ENTRY_TITLE")
        assertThat(code).contains("METRIC_ARCHIVE_ENTRY_HINT")
        assertThat(code).contains("MetricArchiveScreen(")
    }

    @Test
    fun `既有指标详情与档案组件在自身文件之外被引用`() {
        val code = codeOnly(sourceOf(archivePath))
        assertThat(code).contains("MetricDetailScreen(")
        assertThat(code).contains("ArchiveWaistCard(")
        assertThat(code).contains("ArchiveGoalProgress(")
    }

    @Test
    fun `指标档案页无硬编码颜色`() {
        val code = codeOnly(sourceOf(archivePath))
        assertWithMessage("MetricArchiveScreen.kt 硬编码颜色")
            .that(Regex("""Color\(0x[0-9A-Fa-f]{6,8}\)""").findAll(code).map { it.value }.toList())
            .isEmpty()
        assertThat(code).contains("QkColor.")
    }
}
