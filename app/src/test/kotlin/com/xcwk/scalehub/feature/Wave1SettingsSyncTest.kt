package com.xcwk.scalehub.feature

import com.google.common.truth.Truth.assertThat
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.xcwk.scalehub.feature.settings.AppLanguage
import com.xcwk.scalehub.feature.settings.AppearanceCopy
import com.xcwk.scalehub.feature.settings.AppearanceIntent
import com.xcwk.scalehub.feature.settings.AppearanceSettingsViewModel
import com.xcwk.scalehub.feature.settings.LanguageCopy
import com.xcwk.scalehub.feature.settings.LanguageIntent
import com.xcwk.scalehub.feature.settings.LanguageSettingsViewModel
import com.xcwk.scalehub.feature.settings.ThemeMode
import com.xcwk.scalehub.feature.settings.appearanceSelectMessage
import com.xcwk.scalehub.feature.sync.NetworkStatusAction
import com.xcwk.scalehub.feature.sync.NetworkStatusCopy
import com.xcwk.scalehub.feature.sync.ServiceStatusAction
import com.xcwk.scalehub.feature.sync.ServiceStatusCopy
import com.xcwk.scalehub.feature.sync.dispatchNetworkStatus
import com.xcwk.scalehub.feature.sync.dispatchServiceStatus
import org.junit.Test

/**
 * Wave 1（T-091 语言与外观 / T-102 网络与服务状态）可自动判定的部分。
 *
 * - 语言：四语列表、默认简体中文、保存才把所选语言写入 applied
 * - 外观：浅色 / 深色 / 跟随系统，当前项切换，保存上报
 * - 网络与服务：原型原文，且点击只走回调，不依赖 android.net
 */
class Wave1SettingsSyncTest {

    @Test
    fun `语言列表与原型一致且默认简体中文`() {
        assertThat(AppLanguage.entries.map { it.label })
            .containsExactly("简体中文", "繁體中文", "English", "日本語")
            .inOrder()
        val vm = LanguageSettingsViewModel()
        assertThat(vm.state.value.selected).isEqualTo(AppLanguage.SimplifiedChinese)
        assertThat(vm.state.value.applied).isEqualTo(AppLanguage.SimplifiedChinese)
        assertThat(vm.state.value.message).isNull()
    }

    @Test
    fun `未保存前不改已生效语言保存后上报所选`() {
        val vm = LanguageSettingsViewModel()
        vm.onIntent(LanguageIntent.Select(AppLanguage.English))
        assertThat(vm.state.value.selected).isEqualTo(AppLanguage.English)
        assertThat(vm.state.value.applied).isEqualTo(AppLanguage.SimplifiedChinese)
        assertThat(vm.state.value.message).isNull()
        vm.onIntent(LanguageIntent.Save)
        assertThat(vm.state.value.applied).isEqualTo(AppLanguage.English)
        assertThat(vm.state.value.message).isEqualTo(LanguageCopy.SAVED_TOAST)
    }

    @Test
    fun `语言页含重启与翻译不完整两条提示`() {
        val blob = listOf(
            LanguageCopy.NAV_TITLE,
            LanguageCopy.SECTION,
            LanguageCopy.RESTART_LINE,
            LanguageCopy.INCOMPLETE_LINE,
            LanguageCopy.SAVE,
        ).joinToString("\n")
        assertThat(blob).contains("切换语言后需重启应用生效")
        assertThat(blob).contains("部分翻译可能不完整")
        assertThat(blob).contains("持续完善中")
        assertThat(blob).contains("界面语言")
        assertThat(blob).contains("保存")
        assertThat(blob).contains("语言")
    }

    @Test
    fun `外观三选默认浅色且当前项可切换`() {
        assertThat(ThemeMode.entries.map { it.label })
            .containsExactly("浅色", "深色", "跟随系统")
            .inOrder()
        val vm = AppearanceSettingsViewModel()
        assertThat(vm.state.value.selected).isEqualTo(ThemeMode.Light)
        assertThat(appearanceSelectMessage(ThemeMode.Light)).isNull()
        vm.onIntent(AppearanceIntent.Select(ThemeMode.Dark))
        assertThat(vm.state.value.selected).isEqualTo(ThemeMode.Dark)
        assertThat(vm.state.value.applied).isEqualTo(ThemeMode.Light)
        assertThat(vm.state.value.message).isEqualTo(AppearanceCopy.TOAST_DARK)
        vm.onIntent(AppearanceIntent.Select(ThemeMode.System))
        assertThat(vm.state.value.message).isEqualTo(AppearanceCopy.TOAST_SYSTEM)
        vm.onIntent(AppearanceIntent.Save)
        assertThat(vm.state.value.applied).isEqualTo(ThemeMode.System)
        assertThat(vm.state.value.message).isEqualTo(AppearanceCopy.SAVED_TOAST)
    }

    @Test
    fun `外观页含品牌色说明与后续版本提示`() {
        assertThat(AppearanceCopy.SECTION_MODE).isEqualTo("主题模式")
        assertThat(AppearanceCopy.SECTION_BRAND).isEqualTo("品牌色预览")
        assertThat(AppearanceCopy.BRAND_HINT).isEqualTo("轻衡绿 #258C77 · 墨绿 #203D37")
        assertThat(AppearanceCopy.LATER_HINT).contains("深色模式和跟随系统将在后续版本提供")
        assertThat(AppearanceCopy.NAV_TITLE).isEqualTo("外观")
        assertThat(AppearanceCopy.SAVE).isEqualTo("保存")
    }

    @Test
    fun `网络页文案与原型一致且检查只走回调`() {
        val blob = listOf(
            NetworkStatusCopy.NAV_TITLE,
            NetworkStatusCopy.TITLE,
            NetworkStatusCopy.BODY,
            NetworkStatusCopy.CHECK_SETTINGS,
            NetworkStatusCopy.CONTINUE_OFFLINE,
        ).joinToString("\n")
        listOf(
            "数据同步",
            "网络连接不可用",
            "测量与记录不受影响",
            "数据先存本机",
            "联网后自动同步",
            "检查网络设置",
            "继续离线使用",
        ).forEach { phrase -> assertThat(blob).contains(phrase) }

        var checks = 0
        var offline = 0
        dispatchNetworkStatus(NetworkStatusAction.CheckSettings, { checks++ }, { offline++ })
        assertThat(checks).isEqualTo(1)
        assertThat(offline).isEqualTo(0)
        dispatchNetworkStatus(NetworkStatusAction.ContinueOffline, { checks++ }, { offline++ })
        assertThat(offline).isEqualTo(1)
        assertThat(checks).isEqualTo(1)
    }

    @Test
    fun `服务页文案与原型一致且重试只走回调`() {
        val blob = listOf(
            ServiceStatusCopy.NAV_TITLE,
            ServiceStatusCopy.TITLE,
            ServiceStatusCopy.BODY,
            ServiceStatusCopy.RETRY,
            ServiceStatusCopy.ANNOUNCEMENT,
        ).joinToString("\n")
        listOf(
            "数据同步",
            "云服务暂时不可用",
            "我们正在维护服务",
            "数据安全无虞",
            "本地测量与查看不受影响",
            "重试连接",
            "查看服务公告",
        ).forEach { phrase -> assertThat(blob).contains(phrase) }

        var retries = 0
        var announcements = 0
        dispatchServiceStatus(ServiceStatusAction.Retry, { retries++ }, { announcements++ })
        assertThat(retries).isEqualTo(1)
        assertThat(announcements).isEqualTo(0)
        dispatchServiceStatus(
            ServiceStatusAction.ViewAnnouncement,
            { retries++ },
            { announcements++ },
        )
        assertThat(announcements).isEqualTo(1)
        assertThat(retries).isEqualTo(1)
    }

    @Test
    fun `网络与服务页不依赖系统网络包`() {
        val imported = ClassFileImporter().importPackages("com.xcwk.scalehub.feature.sync")
        noClasses()
            .that().resideInAnyPackage("com.xcwk.scalehub.feature.sync..")
            .should().dependOnClassesThat().resideInAnyPackage("android.net..")
            .check(imported)
    }
}
