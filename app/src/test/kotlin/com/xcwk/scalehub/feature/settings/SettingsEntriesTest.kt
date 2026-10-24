package com.xcwk.scalehub.feature.settings

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.feature.sync.HelpCenterStep
import org.junit.Test

/**
 * 接入波（第三批・设置区）入口表 —— 「入口 id → 目标屏」映射的 JVM 单测。
 *
 * 钉的是四件容易被手改坏的事：
 * 1. 入口 id 唯一（进程重建 / 埋点按 id 回查，重 id 会恢复错屏）；
 * 2. 每个入口都能解析到目标屏，未知 id 一律不跳转；
 * 3. 四块目标屏都有入口（漏一块 = 「交付了但点不到」）；
 * 4. 维护类入口只在维护态出现，帮助中心族三个入口各带自己的入口态。
 */
class SettingsEntriesTest {

    @Test
    fun `入口 id 唯一且都有标题与说明`() {
        val ids = SettingsEntries.All.map { it.id }

        assertThat(ids).hasSize(ids.toSet().size)
        SettingsEntries.All.forEach { entry ->
            assertThat(entry.id).isNotEmpty()
            assertThat(entry.title).isNotEmpty()
            assertThat(entry.hint).isNotEmpty()
            assertThat(entry.target.screen).isNotEmpty()
        }
    }

    @Test
    fun `每个入口 id 都能解析到目标屏`() {
        val targets = settingsEntryTargets(maintenanceActive = true)

        assertThat(targets).hasSize(SettingsEntries.All.size)
        SettingsEntries.All.forEach { entry ->
            assertThat(settingsEntryTargetOf(entry.id, maintenanceActive = true)).isEqualTo(entry.target)
        }
    }

    @Test
    fun `四块目标屏都有入口`() {
        val mounted = SettingsEntries.All.map { it.target }.toSet()

        assertThat(mounted).containsExactly(
            SettingsEntryTarget.SdkList,
            SettingsEntryTarget.DataRecovery,
            SettingsEntryTarget.Maintenance,
            SettingsEntryTarget.HelpCenter,
        )
    }

    @Test
    fun `帮助中心族三个入口各带自己的入口态`() {
        val helpSteps = SettingsEntries.All
            .filter { it.target == SettingsEntryTarget.HelpCenter }
            .map { it.helpStep }

        assertThat(helpSteps).containsExactly(
            HelpCenterStep.Help,
            HelpCenterStep.Feedback,
            HelpCenterStep.Diagnostics,
        )
    }

    @Test
    fun `未知 id 不跳转`() {
        assertThat(settingsEntryTargetOf("settings.unknown")).isNull()
        assertThat(SettingsEntries.find("settings.unknown")).isNull()
    }

    @Test
    fun `维护入口只在维护态可见`() {
        assertThat(settingsEntryTargetOf(SettingsEntries.Maintenance.id, maintenanceActive = false)).isNull()
        assertThat(settingsEntryTargetOf(SettingsEntries.Maintenance.id, maintenanceActive = true))
            .isEqualTo(SettingsEntryTarget.Maintenance)
        assertThat(settingsEntryIdsFor(SettingsEntryGroup.Other, maintenanceActive = false)).isEmpty()
        assertThat(settingsEntryIdsFor(SettingsEntryGroup.Other, maintenanceActive = true))
            .containsExactly(SettingsEntries.Maintenance.id)
    }

    @Test
    fun `分组归属符合设置页分组`() {
        assertThat(settingsEntryIdsFor(SettingsEntryGroup.PrivacyAndData)).containsExactly(
            SettingsEntries.SdkList.id,
            SettingsEntries.DataRecovery.id,
        )
        assertThat(settingsEntryIdsFor(SettingsEntryGroup.Help)).containsExactly(
            SettingsEntries.HelpCenter.id,
            SettingsEntries.Feedback.id,
            SettingsEntries.Diagnostics.id,
        )
    }

    @Test
    fun `单屏栈只接受可见入口且可回到主页`() {
        val home = SettingsEntriesNav()
        assertThat(home.openedEntry).isNull()

        val opened = home.open(SettingsEntries.HelpCenter.id)
        assertThat(opened.openedEntry?.target).isEqualTo(SettingsEntryTarget.HelpCenter)

        // 非维护态下打开维护入口：忽略，保持原状
        assertThat(opened.open(SettingsEntries.Maintenance.id).openedId)
            .isEqualTo(SettingsEntries.HelpCenter.id)

        assertThat(opened.back().openedEntry).isNull()
        assertThat(home.back()).isSameInstanceAs(home)
    }
}
