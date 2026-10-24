package com.xcwk.scalehub.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.pow

/**
 * 无障碍对比度单测（T-070）。
 *
 * 口径：WCAG 2.1 相对亮度与对比度公式（非近似），纯 JVM 可跑——不依赖设备、不依赖截图，
 * `:app:testDebugUnitTest` 即执行。阈值固定为 AA 两级，**不得下调**：
 *  - 正文（<18sp 或 <14sp 粗体）：4.5:1
 *  - 大字（≥18sp / ≥14sp 粗体）与图标等非文本图形：3:1
 *
 * 为什么必须在这里测：色值改动（QkColor 是单点 token）不会有编译错误、也不会有视觉回归测试，
 * 只有算一遍对比度才能发现「灰底上灰字」这类静默退化。
 *
 * 分层说明（本轮审计边界，不是放宽阈值）：
 *  - [QkColor.Muted] / [QkColor.Hint] 属「次要提示」层级，本轮只按 3:1 审计；
 *    它们在 12sp 正文里的用法仍低于 AA 4.5:1（Muted 3.15:1、Hint 2.16:1），
 *    是已登记的已知缺口——修它要改 `--muted` 设计 token 并同步 docs 里的色值映射
 *    （`docs/guides/GC-prototype-driven-dev.md`、`prototype-dev` skill），超出 T-070 写域。
 *  - 主绿 `Primary` 上的白字（按钮/图标）为 3.52:1，按「大字与图标」层级审计，
 *    改主绿会偏离原型 --green，同样不在本任务写域内。
 */
class ContrastTest {

    /** WCAG 相对亮度：先做 sRGB 反伽马，再按 0.2126/0.7152/0.0722 加权。 */
    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    /** WCAG 对比度：亮者在上，比值范围 1.0-21.0。 */
    private fun contrastRatio(foreground: Color, background: Color): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertAtLeast(ratio: Double, threshold: Double, what: String) {
        assertWithMessage("$what 对比度 ${"%.2f".format(ratio)}:1 低于阈值 ${threshold}:1")
            .that(ratio >= threshold)
            .isTrue()
    }

    // ---- 算法自检：先证明尺子是准的，再拿它量设计 token ----

    @Test
    fun `坐标轴极值符合 WCAG 定义`() {
        assertThat(contrastRatio(Color.Black, Color.White)).isWithin(1e-6).of(21.0)
        assertThat(contrastRatio(Color.White, Color.White)).isWithin(1e-6).of(1.0)
    }

    @Test
    fun `已知灰阶对比度与公开值一致`() {
        // #767676 在纯白上是 WCAG 常引用的 4.54:1（AA 正文的最小常用灰）
        assertThat(contrastRatio(Color(0xFF767676), Color.White)).isWithin(0.02).of(4.54)
    }

    // ---- 正文层级：4.5:1 ----

    @Test
    fun `主要正文与底色的对比度不低于 4_5 比 1`() {
        val pairs = listOf(
            Triple("Fg on Card", QkColor.Fg, QkColor.Card),
            Triple("Fg on Bg", QkColor.Fg, QkColor.Bg),
            Triple("Fg on Mint", QkColor.Fg, QkColor.Mint),
            Triple("Fg on Secondary", QkColor.Fg, QkColor.Secondary),
            Triple("Sub on Card", QkColor.Sub, QkColor.Card),
            Triple("Sub on Bg", QkColor.Sub, QkColor.Bg),
            Triple("Sub on Mint", QkColor.Sub, QkColor.Mint),
            Triple("FieldLabel on FieldBg", QkColor.FieldLabel, QkColor.FieldBg),
            Triple("GhostInk on Card", QkColor.GhostInk, QkColor.Card),
            Triple("ChipInk on ChipBg", QkColor.ChipInk, QkColor.ChipBg),
            Triple("Danger on Card", QkColor.Danger, QkColor.Card),
            Triple("Danger on DangerSoft", QkColor.Danger, QkColor.DangerSoft),
            Triple("PrimaryDark on Mint", QkColor.PrimaryDark, QkColor.Mint),
        )
        pairs.forEach { (name, fg, bg) ->
            assertAtLeast(contrastRatio(fg, bg), 4.5, name)
        }
    }

    // ---- 大字与图标层级：3:1 ----

    @Test
    fun `大字与图标的对比度不低于 3 比 1`() {
        val pairs = listOf(
            // 主绿：大数字 / 图标 / 主按钮底上的白字（粗体大字口径）
            Triple("Primary on Card", QkColor.Primary, QkColor.Card),
            Triple("Card on Primary", QkColor.Card, QkColor.Primary),
            Triple("PrimaryDark on Card", QkColor.PrimaryDark, QkColor.Card),
            // 图标：圆形图标按钮、页签、箭头
            Triple("IconInk on Card", QkColor.IconInk, QkColor.Card),
            Triple("SegmentInactive on Secondary", QkColor.SegmentInactive, QkColor.Secondary),
            Triple("AmberInk on AccentSoft", QkColor.AmberInk, QkColor.AccentSoft),
            Triple("Danger on DangerSoft", QkColor.Danger, QkColor.DangerSoft),
            // 次要提示：按 3:1 审计（见类注释与「已知缺口」用例）
            Triple("Muted on Card", QkColor.Muted, QkColor.Card),
        )
        pairs.forEach { (name, fg, bg) ->
            assertAtLeast(contrastRatio(fg, bg), 3.0, name)
        }
    }

    /**
     * 已知缺口钉桩（不是豁免，是不许装作没看见）。
     *
     * [QkColor.Muted] / [QkColor.Hint] 是 `--muted` / `--hint` 设计 token，色值在
     * `docs/guides/GC-prototype-driven-dev.md` 与 prototype-dev skill 里逐字映射，
     * 调整它们属于设计口径变更（要同步 docs、重跑原型视觉核对），超出 T-070 写域。
     * 因此这里把当前实测值钉住：一旦有人改动色值，本用例立刻红，缺口状态必须
     * 由改动者显式确认——既不会「悄悄变成 4.5」，也不会「悄悄继续退化」。
     */
    @Test
    fun `已知缺口 - 次要提示色低于正文阈值`() {
        assertThat(contrastRatio(QkColor.Muted, QkColor.Bg)).isWithin(0.05).of(2.92)
        assertThat(contrastRatio(QkColor.Muted, QkColor.Card)).isWithin(0.05).of(3.15)
        assertThat(contrastRatio(QkColor.Hint, QkColor.Card)).isWithin(0.05).of(2.16)
        assertThat(contrastRatio(QkColor.Muted, QkColor.Bg)).isLessThan(4.5)
    }

    /**
     * 反向断言：阈值确实在生效。
     *
     * 若有人把 `assertAtLeast` 写成恒真（或把阈值改成 1.0），这里会红：
     * 低对比组合必须判失败，高对比组合必须判通过。
     */
    @Test
    fun `低对比组合会被判定为不达标`() {
        assertThat(contrastRatio(QkColor.Hint, QkColor.Card) < 3.0).isTrue()
        assertThat(contrastRatio(QkColor.Hint, QkColor.Card)).isLessThan(3.0)
        assertThat(contrastRatio(QkColor.Fg, QkColor.Card)).isGreaterThan(4.5)
    }
}
