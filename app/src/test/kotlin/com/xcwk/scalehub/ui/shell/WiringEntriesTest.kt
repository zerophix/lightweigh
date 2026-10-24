package com.xcwk.scalehub.ui.shell

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 接入波（第二批）入口表 —— 「入口 → 屏」映射的 JVM 单测。
 *
 * 钉的是三件容易被手改坏的事：
 * 1. 入口 id 唯一（进程重建/埋点按 id 回查，重 id 会导致恢复错屏）；
 * 2. 每个入口恰好一个目标屏，且没有两条入口挤向同一屏（说明漏挂了一屏）；
 * 3. 宿主页上的入口数符合本批接入清单（设备三行，其余各一行）。
 */
class WiringEntriesTest {

    @Test
    fun `六个入口一一对应六块屏`() {
        val targets = wiringEntryTargets()

        assertThat(targets).hasSize(6)
        assertThat(targets.values).containsExactly(
            WiringTarget.AiBoundaries,
            WiringTarget.ActionPlans,
            WiringTarget.ResultShare,
            WiringTarget.Firmware,
            WiringTarget.DeviceRename,
            WiringTarget.MemberDeviceLink,
        )
        // 入口与目标都是双射：有两条入口指向同一屏，就说明有一块屏没自己的入口
        assertThat(targets.values.toSet()).hasSize(WiringEntries.All.size)
    }

    @Test
    fun `入口按宿主页分组且设备页三行`() {
        assertThat(wiringEntryIdsFor(WiringHost.Ai)).containsExactly("ai.boundaries")
        assertThat(wiringEntryIdsFor(WiringHost.Members)).containsExactly("member.actionPlans")
        assertThat(wiringEntryIdsFor(WiringHost.Measure)).containsExactly("measure.resultShare")
        assertThat(wiringEntryIdsFor(WiringHost.Device)).containsExactly(
            "device.firmware",
            "device.rename",
            "device.memberLink",
        )
    }

    @Test
    fun `每个入口都有可点的标题与说明`() {
        WiringEntries.All.forEach { entry ->
            assertThat(entry.id).isNotEmpty()
            assertThat(entry.title).isNotEmpty()
            assertThat(entry.hint).isNotEmpty()
            assertThat(entry.target.screen).isNotEmpty()
        }
    }
}
