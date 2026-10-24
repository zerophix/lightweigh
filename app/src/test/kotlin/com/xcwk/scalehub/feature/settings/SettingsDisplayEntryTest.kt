package com.xcwk.scalehub.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 设置页「显示」分组接入（外观 / 语言）—— 入口登记、目标屏映射与进入/返回路径。
 *
 * 钉的是「设置页点得到、点了进对屏、返回回设置页」：
 * T-091 两屏此前零调用方，只靠手点才能发现丢入口。
 */
class SettingsDisplayEntryTest {

    @Test
    fun `显示分组恰好是外观与语言两行`() {
        val ids = settingsEntryIdsFor(SettingsEntryGroup.Display)

        assertThat(ids).containsExactly(SettingsEntries.Appearance.id, SettingsEntries.Language.id).inOrder()
    }

    @Test
    fun `入口指向两个设置屏`() {
        assertThat(settingsEntryTargetOf("settings.appearance")).isEqualTo(SettingsEntryTarget.Appearance)
        assertThat(settingsEntryTargetOf("settings.language")).isEqualTo(SettingsEntryTarget.Language)
        assertThat(SettingsEntryTarget.Appearance.screen).isEqualTo("AppearanceSettingsScreen")
        assertThat(SettingsEntryTarget.Language.screen).isEqualTo("LanguageSettingsScreen")
    }

    @Test
    fun `入口文案即设置页那一行`() {
        assertThat(SettingsEntries.Appearance.title).isEqualTo("外观")
        assertThat(SettingsEntries.Appearance.hint).contains("主题模式")
        assertThat(SettingsEntries.Language.title).isEqualTo("语言")
        assertThat(SettingsEntries.Language.hint).contains("界面语言")
    }

    @Test
    fun `点开外观后返回回到设置主页`() {
        val opened = SettingsEntriesNav().open("settings.appearance")

        assertThat(opened.openedEntry?.target).isEqualTo(SettingsEntryTarget.Appearance)
        assertThat(opened.back().openedId).isNull()
    }

    @Test
    fun `点开语言后返回回到设置主页`() {
        val opened = SettingsEntriesNav().open("settings.language")

        assertThat(opened.openedEntry?.target).isEqualTo(SettingsEntryTarget.Language)
        assertThat(opened.back().openedId).isNull()
    }

    @Test
    fun `显示入口不属于隐私与帮助分组`() {
        val privacy = settingsEntryIdsFor(SettingsEntryGroup.PrivacyAndData)
        val help = settingsEntryIdsFor(SettingsEntryGroup.Help)

        assertThat(privacy).doesNotContain(SettingsEntries.Appearance.id)
        assertThat(help).doesNotContain(SettingsEntries.Language.id)
    }

    @Test
    fun `维护态不影响显示分组入口`() {
        assertThat(settingsEntryIdsFor(SettingsEntryGroup.Display, maintenanceActive = true))
            .containsExactly(SettingsEntries.Appearance.id, SettingsEntries.Language.id)
        assertThat(settingsEntryIdsFor(SettingsEntryGroup.Display, maintenanceActive = false))
            .containsExactly(SettingsEntries.Appearance.id, SettingsEntries.Language.id)
    }

    @Test
    fun `外观页默认选中浅色且点选未保存`() {
        val state = AppearanceSettingsUiState().reduce(AppearanceSettingsIntent.SelectTheme(ThemeMode.Dark))

        assertThat(state.selected).isEqualTo(ThemeMode.Dark)
        assertThat(state.saved).isEqualTo(ThemeMode.Light)
        assertThat(state.changed).isTrue()
        assertThat(state.reduce(AppearanceSettingsIntent.Save).saved).isEqualTo(ThemeMode.Dark)
    }

    @Test
    fun `语言页选项齐备且保存后才落已保存值`() {
        assertThat(LanguageOption.entries.map { it.label })
            .containsExactly("简体中文", "繁體中文", "English", "日本語")
            .inOrder()
        val state = LanguageSettingsUiState().reduce(
            LanguageSettingsIntent.SelectLanguage(LanguageOption.English),
        )

        assertThat(state.changed).isTrue()
        assertThat(state.reduce(LanguageSettingsIntent.Save).saved).isEqualTo(LanguageOption.English)
    }
}
