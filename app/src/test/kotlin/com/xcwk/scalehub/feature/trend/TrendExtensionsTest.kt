package com.xcwk.scalehub.feature.trend

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.xcwk.scalehub.application.query.TrendRange
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * T-048 趋势扩展（v-trend）纯 JVM 单测。
 *
 * 覆盖验收里可自动判定的四类事实：
 * 1. 文案逐字取自原型 05-history-trend.html · v-trend（AC-T048 then，改一个字即红）；
 * 2. 聚合粒度（周/月/年）与范围（近7天/近30天/全部）双向映射一致；
 * 3. 首屏副标题「成员 · MM/dd — MM/dd」由点序派生，无数据不编造区间；
 * 4. 源码守卫：目标文件无硬编码颜色、页面确实引用文案真源与两个弹层。
 *
 * 视觉呈现（弹层动画/排版落位）不在此测——:app 无 compose ui-test 基建，
 * 属已声明盲区；由原型对照与人工验收关闭。
 */
class TrendExtensionsTest {

    // ---- 1. 文案逐字（AC-T048 then）----

    @Test
    fun `首屏文案逐字取自原型 v-trend`() {
        assertThat(TrendText.HeroTitle).isEqualTo("看见每一步改变")
        assertThat(TrendText.MemberFallback).isEqualTo("林悦")
    }

    @Test
    fun `聚合粒度三档逐字`() {
        assertThat(TrendAggregation.entries.map { it.label })
            .containsExactly("周", "月", "年")
            .inOrder()
    }

    @Test
    fun `卡头与二级行文案逐字`() {
        assertThat(TrendText.MetricCaret).isEqualTo(" ▾")
        assertThat(TrendText.RangeRowTitle).isEqualTo("时间范围")
        assertThat(TrendText.AllRecords).isEqualTo("全部测量记录")
    }

    @Test
    fun `选择指标弹层文案逐字`() {
        assertThat(TrendText.MetricSheetTitle).isEqualTo("选择指标")
        assertThat(TrendText.Cancel).isEqualTo("取消")
        // 原型 #sheet-metric 的四个选项，顺序即原型顺序
        assertThat(TREND_METRICS.take(4).map { it.name })
            .containsExactly("体重", "体脂率", "BMI", "肌肉量")
            .inOrder()
    }

    @Test
    fun `时间范围弹层四个选项逐字`() {
        assertThat(TrendText.RangeSheetTitle).isEqualTo("时间范围")
        assertThat(TrendText.Range7).isEqualTo("近 7 天")
        assertThat(TrendText.Range30).isEqualTo("近 30 天")
        assertThat(TrendText.RangeYear).isEqualTo("近一年")
        assertThat(TrendText.RangeCustom).isEqualTo("自定义…")
    }

    @Test
    fun `扩展指标名称逐字`() {
        val names = TREND_METRICS.map { it.name }
        assertThat(names).containsAtLeast("骨骼肌", "水分", "基础代谢", "肌肉量", "蛋白质")
        assertThat(TREND_METRICS.first { it.id == "bmr" }.unit).isEqualTo("kcal")
        assertThat(TREND_METRICS.first { it.id == "muscle" }.unit).isEqualTo("kg")
    }

    // ---- 2. 聚合粒度与范围映射 ----

    @Test
    fun `聚合粒度按范围反查`() {
        assertThat(trendAggregationFor(TrendRange.TODAY)).isEqualTo(TrendAggregation.WEEK)
        assertThat(trendAggregationFor(TrendRange.LAST_7)).isEqualTo(TrendAggregation.WEEK)
        assertThat(trendAggregationFor(TrendRange.LAST_30)).isEqualTo(TrendAggregation.MONTH)
        assertThat(trendAggregationFor(TrendRange.ALL)).isEqualTo(TrendAggregation.YEAR)
    }

    @Test
    fun `选择聚合粒度落到对应范围`() {
        assertThat(trendRangeFor(TrendAggregation.WEEK)).isEqualTo(TrendRange.LAST_7)
        assertThat(trendRangeFor(TrendAggregation.MONTH)).isEqualTo(TrendRange.LAST_30)
        assertThat(trendRangeFor(TrendAggregation.YEAR)).isEqualTo(TrendRange.ALL)
        // 往返一致：从范围出发再选同一档，范围不变
        TrendAggregation.entries.forEach { agg ->
            val range = trendRangeFor(agg)
            assertThat(trendAggregationFor(range)).isEqualTo(agg)
        }
    }

    @Test
    fun `行尾紧凑标签与弹层带空格标签各自逐字`() {
        assertThat(trendRangeLabel(TrendRange.TODAY)).isEqualTo("今天")
        assertThat(trendRangeLabel(TrendRange.LAST_7)).isEqualTo("近7天")
        assertThat(trendRangeLabel(TrendRange.LAST_30)).isEqualTo("近30天")
        assertThat(trendRangeLabel(TrendRange.ALL)).isEqualTo("全部")

        assertThat(trendRangeSheetLabel(TrendRange.LAST_7)).isEqualTo("近 7 天")
        assertThat(trendRangeSheetLabel(TrendRange.LAST_30)).isEqualTo("近 30 天")
        assertThat(trendRangeSheetLabel(TrendRange.ALL)).isEqualTo("近一年")
    }

    // ---- 3. 副标题与空态 ----

    @Test
    fun `副标题由成员名与首末点日期派生`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDate.of(2026, 9, 21).atTime(7, 30).atZone(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 10, 21).atTime(7, 30).atZone(zone).toInstant().toEpochMilli()
        val subtitle = trendHeroSubtitle(
            memberName = "林悦",
            points = listOf(TrendPointUi(start, 62.0), TrendPointUi(end, 62.4)),
            zone = zone,
        )
        assertThat(subtitle).isEqualTo("林悦 · 09/21 — 10/21")
    }

    @Test
    fun `无数据时副标题只给成员名`() {
        assertThat(trendHeroSubtitle("林悦", emptyList())).isEqualTo("林悦")
    }

    @Test
    fun `扩展指标无序列时给空态文案`() {
        val skeletal = trendMetricOf("skeletal")
        assertThat(trendMetricEmptyText(skeletal)).isEqualTo("这段时间还没有骨骼肌数据")
        assertThat(skeletal.hasSeries).isFalse()
        assertThat(trendMetricOf("weight").hasSeries).isTrue()
        assertThat(trendMetricOf("fat").hasSeries).isTrue()
        // 未知 id 回落体重，避免选择器状态损坏时崩屏
        assertThat(trendMetricOf("unknown").id).isEqualTo("weight")
    }

    // ---- 4. 源码守卫（AC forbidden：硬编码颜色 / 页面绕过文案真源）----

    private fun sourceOf(fileName: String): String {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val f = File(dir, "app/src/main/kotlin/com/xcwk/scalehub/feature/trend/$fileName")
            if (f.exists()) return f.readText(Charsets.UTF_8)
            dir = dir.parentFile
        }
        error("$fileName 未找到（从 ${System.getProperty("user.dir")} 向上探测失败）")
    }

    private fun codeOnly(source: String): String =
        source
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//.*"""), "")

    @Test
    fun `目标文件无硬编码颜色`() {
        listOf("TrendExtensions.kt", "TrendScreen.kt").forEach { name ->
            val code = codeOnly(sourceOf(name))
            assertWithMessage("$name 硬编码颜色")
                .that(Regex("""Color\(0x[0-9A-Fa-f]{6,8}\)""").findAll(code).map { it.value }.toList())
                .isEmpty()
            assertThat(code).contains("QkColor.")
        }
    }

    @Test
    fun `页面引用文案真源与两个弹层（范围控件只留一套）`() {
        val screen = sourceOf("TrendScreen.kt")
        listOf(
            "TrendText.HeroTitle",
            "TrendText.RangeRowTitle",
            "TrendText.AllRecords",
            "TrendMetricSheet(",
            "TrendRangeSheet(",


            "trendHeroSubtitle(",
        ).forEach { symbol ->
            assertWithMessage("TrendScreen.kt 缺少 $symbol").that(screen).contains(symbol)
        }
    }
}
