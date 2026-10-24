package com.xcwk.scalehub.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-091（settings: 语言与外观设置）纯逻辑单测。
 *
 * 覆盖验收中**唯一可自动判定**的部分（视觉/选中态需真机或截图，不在此测）：
 * - AC-S5：语言页列出三种 AC 语言、外观页主题模式三选，文案与顺序与原型一致；
 *   保存动作落在 saved、点选只改 selected（Screen 零业务逻辑的证据）。
 * - 两条提示与品牌色说明逐字取自原型 v-language / v-appearance，防止后续改写文案。
 */
class LanguageAppearanceSettingsTest {

    // ---- 语言：选项与文案（原型 v-language）----

    @Test
    fun `语言选项文案与顺序与原型 v-language 一致`() {
        assertThat(LanguageOption.entries.map { it.label })
            .containsExactly("简体中文", "繁體中文", "English", "日本語")
            .inOrder()
    }

    @Test
    fun `AC-S5 点名的三种语言全部存在`() {
        assertThat(LanguageOption.entries.map { it.label })
            .containsAtLeast("简体中文", "繁體中文", "English")
    }

    @Test
    fun `语言页底两条提示逐字取自原型`() {
        assertThat(LANGUAGE_NOTICE_LINES).containsExactly(
            "切换语言后需重启应用生效。",
            "部分翻译可能不完整，持续完善中。",
        ).inOrder()
    }

    @Test
    fun `默认选中简体中文且无待保存修改`() {
        // 原型首行 radio on 落在「简体中文」
        val state = LanguageSettingsUiState()
        assertThat(state.selected).isEqualTo(LanguageOption.SimplifiedChinese)
        assertThat(state.changed).isFalse()
    }

    // ---- 语言：选项状态逻辑（选择意图 vs 保存意图）----

    @Test
    fun `点选只改 selected 不落盘`() {
        val state = LanguageSettingsUiState()
            .reduce(LanguageSettingsIntent.SelectLanguage(LanguageOption.English))
        assertThat(state.selected).isEqualTo(LanguageOption.English)
        assertThat(state.saved).isEqualTo(LanguageOption.SimplifiedChinese)
        assertThat(state.changed).isTrue()
    }

    @Test
    fun `保存后待保存修改归零`() {
        val state = LanguageSettingsUiState()
            .reduce(LanguageSettingsIntent.SelectLanguage(LanguageOption.Japanese))
            .reduce(LanguageSettingsIntent.Save)
        assertThat(state.saved).isEqualTo(LanguageOption.Japanese)
        assertThat(state.changed).isFalse()
    }

    @Test
    fun `未改动时保存是幂等的`() {
        val state = LanguageSettingsUiState()
        assertThat(state.reduce(LanguageSettingsIntent.Save)).isEqualTo(state)
    }

    @Test
    fun `保存后再点选重新出现待保存修改`() {
        val state = LanguageSettingsUiState()
            .reduce(LanguageSettingsIntent.SelectLanguage(LanguageOption.TraditionalChinese))
            .reduce(LanguageSettingsIntent.Save)
            .reduce(LanguageSettingsIntent.SelectLanguage(LanguageOption.SimplifiedChinese))
        assertThat(state.saved).isEqualTo(LanguageOption.TraditionalChinese)
        assertThat(state.selected).isEqualTo(LanguageOption.SimplifiedChinese)
        assertThat(state.changed).isTrue()
    }

    // ---- 外观：选项与文案（原型 v-appearance）----

    @Test
    fun `主题模式三选文案与顺序与原型 v-appearance 一致`() {
        assertThat(ThemeMode.entries.map { it.label })
            .containsExactly("浅色", "深色", "跟随系统")
            .inOrder()
    }

    @Test
    fun `外观页提示与品牌色说明逐字取自原型`() {
        assertThat(APPEARANCE_NOTICE_LINE)
            .isEqualTo("深色模式和跟随系统将在后续版本提供。")
        assertThat(BRAND_PREVIEW_HINT)
            .isEqualTo("轻衡绿 #258C77 · 墨绿 #203D37")
    }

    @Test
    fun `外观默认选中浅色`() {
        // 原型选中态（2px 主绿描边）落在第一块「浅色」卡
        val state = AppearanceSettingsUiState()
        assertThat(state.selected).isEqualTo(ThemeMode.Light)
        assertThat(state.changed).isFalse()
    }

    // ---- 外观：选项状态逻辑 ----

    @Test
    fun `点选深色只改 selected`() {
        val state = AppearanceSettingsUiState()
            .reduce(AppearanceSettingsIntent.SelectTheme(ThemeMode.Dark))
        assertThat(state.selected).isEqualTo(ThemeMode.Dark)
        assertThat(state.saved).isEqualTo(ThemeMode.Light)
        assertThat(state.changed).isTrue()
    }

    @Test
    fun `保存后跟随系统选择落盘`() {
        val state = AppearanceSettingsUiState()
            .reduce(AppearanceSettingsIntent.SelectTheme(ThemeMode.System))
            .reduce(AppearanceSettingsIntent.Save)
        assertThat(state.saved).isEqualTo(ThemeMode.System)
        assertThat(state.changed).isFalse()
    }

    @Test
    fun `未改动时外观保存是幂等的`() {
        val state = AppearanceSettingsUiState()
        assertThat(state.reduce(AppearanceSettingsIntent.Save)).isEqualTo(state)
    }
}
