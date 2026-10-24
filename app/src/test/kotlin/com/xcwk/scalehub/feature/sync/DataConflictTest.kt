package com.xcwk.scalehub.feature.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * T-094 数据冲突处理六态（呈现式组件）纯 JVM 单测。
 *
 * 覆盖验收中可自动判定的四类事实：
 * 1. 六个 view 的文案逐字取自原型22（改一个字即红）；
 * 2. 离线记录 / 设备名 / 冲突字段全部由状态注入——换数据即换文案；
 * 3. 状态机推进：离线切换、多设备合并、同步冲突解决，且解决结果回写（备注 / 读数）；
 * 4. 源码守卫：目标文件无硬编码颜色、无裸 dp、无 remember/系统服务引用（AC forbidden）。
 *
 * 视觉呈现（排版/配色落位）不在此测——:app 无 compose ui-test 基建，
 * 属已声明盲区；视觉由原型对照与人工验收关闭。
 */
class DataConflictTest {

    private val defaults = DataConflictUiState()

    // ---- 1. v-offline-switch 文案逐字 ----

    @Test
    fun `离线切换页文案逐字取自原型 v-offline-switch`() {
        assertThat(DataConflictText.TitleOfflineSwitch).isEqualTo("离线测量 · 切换成员")
        assertThat(DataConflictText.offlineDetected(defaults.pendingCount, defaults.targetName))
            .isEqualTo("检测到 2 条未同步的离线测量记录，\n当前要切换到「妈妈」。")
        assertThat(DataConflictText.pendingHeader(defaults.ownerName))
            .isEqualTo("未同步记录（属于 林悦）")
        assertThat(defaults.offlineRecords.map { it.summary })
            .containsExactly("10/21 07:30 · 62.4 kg", "10/20 07:28 · 62.7 kg")
            .inOrder()
        assertThat(defaults.offlineRecords.map { it.state }).containsExactly("待同步", "待同步")
        assertThat(DataConflictText.offlineOwnershipNote(defaults.ownerName, defaults.targetName))
            .isEqualTo(
                "离线记录属于「林悦」，\n" +
                    "切换到「妈妈」后这些记录仍归林悦，不会混入妈妈的数据。",
            )
        assertThat(DataConflictText.SwitchAfterSync).isEqualTo("先同步再切换")
        assertThat(DataConflictText.SwitchDirect).isEqualTo("直接切换")
    }

    // ---- 2. v-offline-done 文案逐字 ----

    @Test
    fun `离线切换完成页文案逐字取自原型 v-offline-done`() {
        assertThat(DataConflictText.TitleOfflineDone).isEqualTo("已切换")
        assertThat(DataConflictText.viewingAs(defaults.targetName)).isEqualTo("当前查看：妈妈")
        assertThat(defaults.targetInitial).isEqualTo("妈")
        assertThat(DataConflictText.keptOwnershipSummary(defaults.ownerName, defaults.pendingCount))
            .isEqualTo("林悦的 2 条离线记录已保留原归属")
        assertThat(DataConflictText.OwnershipHeader).isEqualTo("归属确认")
        assertThat(
            DataConflictText.ownershipKept(
                defaults.ownerName,
                defaults.pendingCount,
                defaults.targetName,
            ),
        ).isEqualTo("林悦的 2 条离线记录 → 仍归林悦，不混入妈妈")
        assertThat(DataConflictText.targetHistoryIntact(defaults.targetName))
            .isEqualTo("妈妈的历史记录不受影响")
        assertThat(DataConflictText.syncWhenOnline(defaults.ownerName))
            .isEqualTo("联网后林悦的记录将自动同步到云端")
        assertThat(DataConflictText.SwitchCheckNote)
            .isEqualTo("每次切换都会检查未同步记录，确保归属正确。")
        assertThat(DataConflictText.BackToScenarios).isEqualTo("返回场景列表")
    }

    // ---- 3. v-multi-device 文案逐字 ----

    @Test
    fun `多设备页文案逐字取自原型 v-multi-device`() {
        assertThat(DataConflictText.TitleMultiDevice).isEqualTo("多设备新记录")
        assertThat(DataConflictText.MultiDeviceDetected)
            .isEqualTo("检测到另一台设备也产生了新测量记录，\n需要合并到你的账号。")
        assertThat(DataConflictText.ConflictRecordsHeader)
            .isEqualTo("冲突记录（同一时间点，两端不同）")
        assertThat(defaults.localRecord.summary).isEqualTo("10/21 07:30 · 62.4 kg")
        assertThat(defaults.localRecord.statusLine).isEqualTo("本机 · 已同步")
        assertThat(defaults.remoteRecord.summary).isEqualTo("10/21 07:32 · 62.5 kg")
        assertThat(defaults.remoteRecord.statusLine).isEqualTo("iPhone 15 Pro · 未同步")
        assertThat(DataConflictText.TagLocal).isEqualTo("本地")
        assertThat(DataConflictText.TagRemote).isEqualTo("远端")
        assertThat(DataConflictText.MultiDeviceNote)
            .isEqualTo("两条记录时间相近但读数不同，\n请选择保留哪一条。不会自动合并。")
        assertThat(DataConflictText.KeepLocalRecord).isEqualTo("保留本机记录")
        assertThat(DataConflictText.KeepRemoteRecord).isEqualTo("采用远端记录")
        assertThat(DataConflictText.KeepBothRecords).isEqualTo("两条都保留")
    }

    // ---- 4. v-multi-done 文案逐字 ----

    @Test
    fun `多设备合并完成页文案逐字取自原型 v-multi-done`() {
        assertThat(DataConflictText.TitleMultiDone).isEqualTo("合并完成")
        assertThat(DataConflictText.MergedTitle).isEqualTo("记录已合并")
        assertThat(DataConflictText.MergedSubtitle).isEqualTo("根据你的选择更新了本地与云端")
        assertThat(DataConflictText.MergeResultHeader).isEqualTo("合并结果")
        assertThat(DataConflictText.MergeResultLocalSynced).isEqualTo("本机记录已同步到云端")
        assertThat(DataConflictText.MergeResultRemoteHandled).isEqualTo("远端记录已按你的选择处理")
        assertThat(DataConflictText.MergeResultTrendUpdated).isEqualTo("趋势图已更新，无重复数据")
        assertThat(DataConflictText.MergeUndoNote)
            .isEqualTo("合并操作可撤销，7 天内可在「数据管理」恢复。")
    }

    // ---- 5. v-sync-conflict 文案逐字 ----

    @Test
    fun `同步冲突页文案逐字取自原型 v-sync-conflict`() {
        assertThat(DataConflictText.TitleSyncConflict).isEqualTo("同步冲突")
        assertThat(DataConflictText.SyncConflictDetected)
            .isEqualTo("同一条记录在本地和云端都被修改过，\n请选择保留哪个版本。")
        assertThat(DataConflictText.conflictDetailHeader(defaults.conflictMeasuredAt))
            .isEqualTo("冲突详情 · 10/21 07:30 测量记录")
        assertThat(defaults.localVersion.label).isEqualTo("本地版本")
        assertThat(defaults.localVersion.noteLine).isEqualTo("备注：晨起空腹 · 修改于 10:30")
        assertThat(DataConflictText.TagLocal).isEqualTo("本地")
        assertThat(defaults.cloudVersion.label).isEqualTo("云端版本")
        assertThat(defaults.cloudVersion.noteLine).isEqualTo("备注：早上测量 · 修改于 10:28")
        assertThat(DataConflictText.TagCloud).isEqualTo("云端")
        assertThat(DataConflictText.SyncConflictNote)
            .isEqualTo("测量数值两端一致，仅备注不同。\n选择保留的版本将覆盖另一端。")
        assertThat(DataConflictText.KeepLocalVersion).isEqualTo("保留本地版本")
        assertThat(DataConflictText.KeepCloudVersion).isEqualTo("采用云端版本")
    }

    // ---- 6. v-sync-done 文案逐字 ----

    @Test
    fun `同步冲突解决页文案逐字取自原型 v-sync-done`() {
        assertThat(DataConflictText.TitleSyncDone).isEqualTo("冲突已解决")
        assertThat(DataConflictText.ResolvedTitle).isEqualTo("冲突已解决")
        assertThat(DataConflictText.ResolvedSubtitle).isEqualTo("保留的版本已覆盖另一端")
        assertThat(DataConflictText.ResolveResultHeader).isEqualTo("解决结果")
        assertThat(DataConflictText.ResolveResultNote).isEqualTo("备注已统一为你选择的版本")
        assertThat(DataConflictText.resolveResultWeight(defaults.measureWeight))
            .isEqualTo("测量数值保持不变（62.4 kg）")
        assertThat(DataConflictText.ResolveResultSaved).isEqualTo("冲突解决记录已保存，可追溯")
        assertThat(DataConflictText.ResolvePreferenceNote)
            .isEqualTo("下次遇到同类冲突会记住你的偏好，但每次仍会确认。")
        assertThat(DataConflictText.PrototypeFooter)
            .isEqualTo("轻衡 · 数据冲突处理交互原型 · 7 屏（补 page-list-TODO §13 缺口）")
    }

    // ---- 7. 数据全部外部注入 ----

    @Test
    fun `离线记录与成员名换注入即换文案`() {
        val state = DataConflictUiState(
            ownerName = "陈默",
            targetName = "爸爸",
            offlineRecords = listOf(
                OfflinePendingRecord(measuredAt = "11/02 06:50", weight = "70.1 kg"),
            ),
        )
        assertThat(state.pendingCount).isEqualTo(1)
        assertThat(state.targetInitial).isEqualTo("爸")
        assertThat(DataConflictText.offlineDetected(state.pendingCount, state.targetName))
            .isEqualTo("检测到 1 条未同步的离线测量记录，\n当前要切换到「爸爸」。")
        assertThat(DataConflictText.pendingHeader(state.ownerName)).isEqualTo("未同步记录（属于 陈默）")
        assertThat(state.offlineRecords.first().summary).isEqualTo("11/02 06:50 · 70.1 kg")
        assertThat(DataConflictText.syncWhenOnline(state.ownerName))
            .isEqualTo("联网后陈默的记录将自动同步到云端")
    }

    @Test
    fun `多设备两端设备名与同步状态换注入即换文案`() {
        val state = DataConflictUiState(
            localRecord = DeviceConflictRecord(
                measuredAt = "11/02 06:50",
                weight = "70.1 kg",
                deviceLabel = "Pixel 9",
                synced = false,
            ),
            remoteRecord = DeviceConflictRecord(
                measuredAt = "11/02 06:52",
                weight = "70.3 kg",
                deviceLabel = "iPad mini",
                synced = true,
            ),
        )
        assertThat(state.localRecord.statusLine).isEqualTo("Pixel 9 · 未同步")
        assertThat(state.remoteRecord.statusLine).isEqualTo("iPad mini · 已同步")
    }

    @Test
    fun `同步冲突两端备注换注入即换文案`() {
        val state = DataConflictUiState(
            localVersion = SyncVersion(label = "本地版本", note = "运动后", modifiedAt = "11:05"),
            cloudVersion = SyncVersion(label = "云端版本", note = "晚饭前", modifiedAt = "11:08"),
        )
        assertThat(state.localVersion.noteLine).isEqualTo("备注：运动后 · 修改于 11:05")
        assertThat(state.cloudVersion.noteLine).isEqualTo("备注：晚饭前 · 修改于 11:08")
    }

    // ---- 8. 状态机推进与回写 ----

    @Test
    fun `先同步再切换推进到完成页并记住切换方式`() {
        val next = reduceDataConflict(defaults, DataConflictIntent.SwitchAfterSync)
        assertThat(next.step).isEqualTo(DataConflictStep.OfflineDone)
        assertThat(next.offlineSwitchMode).isEqualTo(OfflineSwitchMode.SyncThenSwitch)
        assertThat(next.offlineSyncFailed).isFalse()
    }

    @Test
    fun `直接切换推进到完成页且记录仍归原成员`() {
        val next = reduceDataConflict(defaults, DataConflictIntent.SwitchDirect)
        assertThat(next.step).isEqualTo(DataConflictStep.OfflineDone)
        assertThat(next.offlineSwitchMode).isEqualTo(OfflineSwitchMode.DirectSwitch)
        assertThat(next.pendingCount).isEqualTo(2)
        assertThat(next.ownerName).isEqualTo("林悦")
    }

    @Test
    fun `先同步再切换成功会清掉同步失败标记`() {
        val failed = defaults.copy(offlineSyncFailed = true)
        val next = reduceDataConflict(failed, DataConflictIntent.SwitchAfterSync)
        assertThat(next.step).isEqualTo(DataConflictStep.OfflineDone)
        assertThat(next.offlineSyncFailed).isFalse()
    }

    @Test
    fun `重试只在同步失败态生效且不改步骤`() {
        val failed = defaults.copy(offlineSyncFailed = true)
        val retried = reduceDataConflict(failed, DataConflictIntent.Retry)
        assertThat(retried.offlineSyncFailed).isFalse()
        assertThat(retried.step).isEqualTo(DataConflictStep.OfflineSwitch)

        val healthy = reduceDataConflict(defaults, DataConflictIntent.Retry)
        assertThat(healthy).isEqualTo(defaults)
    }

    @Test
    fun `多设备保留本机记录只回写本机读数`() {
        val multi = defaults.copy(step = DataConflictStep.MultiDevice)
        val next = reduceDataConflict(multi, DataConflictIntent.KeepLocal)
        assertThat(next.step).isEqualTo(DataConflictStep.MultiDone)
        assertThat(next.resolution?.choice).isEqualTo(DataConflictChoice.KeepLocal)
        assertThat(next.resolution?.mergedWeights).containsExactly("62.4 kg")
        assertThat(next.resolution?.resolvedNote).isNull()
    }

    @Test
    fun `多设备采用远端记录只回写远端读数`() {
        val multi = defaults.copy(step = DataConflictStep.MultiDevice)
        val next = reduceDataConflict(multi, DataConflictIntent.KeepRemote)
        assertThat(next.step).isEqualTo(DataConflictStep.MultiDone)
        assertThat(next.resolution?.choice).isEqualTo(DataConflictChoice.KeepRemote)
        assertThat(next.resolution?.mergedWeights).containsExactly("62.5 kg")
    }

    @Test
    fun `多设备两条都保留回写两条读数`() {
        val multi = defaults.copy(step = DataConflictStep.MultiDevice)
        val next = reduceDataConflict(multi, DataConflictIntent.MergeBoth)
        assertThat(next.step).isEqualTo(DataConflictStep.MultiDone)
        assertThat(next.resolution?.choice).isEqualTo(DataConflictChoice.Both)
        assertThat(next.resolution?.mergedWeights).containsExactly("62.4 kg", "62.5 kg").inOrder()
    }

    @Test
    fun `同步冲突保留本地版本回写本地备注`() {
        val conflict = defaults.copy(step = DataConflictStep.SyncConflict)
        val next = reduceDataConflict(conflict, DataConflictIntent.KeepLocal)
        assertThat(next.step).isEqualTo(DataConflictStep.SyncDone)
        assertThat(next.resolution?.choice).isEqualTo(DataConflictChoice.KeepLocal)
        assertThat(next.resolvedNote).isEqualTo("晨起空腹")
        assertThat(next.measureWeight).isEqualTo("62.4 kg")
        assertThat(next.resolution?.mergedWeights).isEmpty()
    }

    @Test
    fun `同步冲突采用云端版本回写云端备注`() {
        val conflict = defaults.copy(step = DataConflictStep.SyncConflict)
        val next = reduceDataConflict(conflict, DataConflictIntent.KeepRemote)
        assertThat(next.step).isEqualTo(DataConflictStep.SyncDone)
        assertThat(next.resolution?.choice).isEqualTo(DataConflictChoice.KeepRemote)
        assertThat(next.resolvedNote).isEqualTo("早上测量")
    }

    @Test
    fun `非法意图不跨屏跳跃`() {
        // 离线切换页点「保留本地」不生效
        assertThat(reduceDataConflict(defaults, DataConflictIntent.KeepLocal)).isEqualTo(defaults)
        // 多设备页点「先同步再切换」不生效
        val multi = defaults.copy(step = DataConflictStep.MultiDevice)
        assertThat(reduceDataConflict(multi, DataConflictIntent.SwitchAfterSync)).isEqualTo(multi)
        // 完成页不再接受任何推进
        val done = defaults.copy(step = DataConflictStep.SyncDone)
        assertThat(reduceDataConflict(done, DataConflictIntent.SwitchDirect)).isEqualTo(done)
    }

    @Test
    fun `返回场景列表不改状态由宿主出栈`() {
        val next = reduceDataConflict(defaults, DataConflictIntent.Back)
        assertThat(next).isEqualTo(defaults)
    }

    @Test
    fun `归约不改入参`() {
        val before = defaults
        reduceDataConflict(before, DataConflictIntent.SwitchDirect)
        assertThat(before.step).isEqualTo(DataConflictStep.OfflineSwitch)
        assertThat(before.offlineSwitchMode).isNull()
    }

    // ---- 9. 源码守卫（AC forbidden）----

    @Test
    fun `目标文件不含硬编码色值与裸 dp`() {
        val code = codeOnly(sourceOf("DataConflict.kt"))
        assertThat(Pattern.compile("Color\\(0x[0-9A-Fa-f]{6,8}\\)").matcher(code).find()).isFalse()
        assertThat(Pattern.compile("(?<!Qk)\\b\\d+\\.dp\\b").matcher(code).find()).isFalse()
    }

    @Test
    fun `目标文件不持有状态也不接触系统数据源`() {
        val code = codeOnly(sourceOf("DataConflict.kt"))
        assertThat(code).doesNotContain("remember {")
        assertThat(code).doesNotContain("remember(")
        assertThat(code).doesNotContain("import android.net")
        assertThat(code).doesNotContain("androidx.room")
        assertThat(code).doesNotContain("ViewModel")
    }

    @Test
    fun `六态枚举与原型 6 个 view 一一对应`() {
        assertThat(DataConflictStep.entries.map { it.name })
            .containsExactly(
                "OfflineSwitch",
                "OfflineDone",
                "MultiDevice",
                "MultiDone",
                "SyncConflict",
                "SyncDone",
            )
            .inOrder()
    }

    private fun sourceOf(fileName: String): String {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val f = File(dir, "app/src/main/kotlin/com/xcwk/scalehub/feature/sync/$fileName")
            if (f.exists()) return f.readText(Charsets.UTF_8)
            dir = dir.parentFile
        }
        error("$fileName 未找到（从 ${System.getProperty("user.dir")} 向上探测失败）")
    }

    /** 去掉注释后再断言：文档里允许出现约束原文，代码本身不许出现违规符号。 */
    private fun codeOnly(source: String): String =
        source
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?m)//.*$"""), "")
}
