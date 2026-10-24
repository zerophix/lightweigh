package com.xcwk.scalehub.feature.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 数据同步页 →「数据冲突处理」入口（T-094 接入波第三批）—— 入口映射与进入/返回路径。
 *
 * 钉两件事：
 * 1. 六个按钮到状态机意图的映射固定（串味会让「保留本机」写成「保留远端」）；
 * 2. 三种冲突场景都能从入口态走到完成态，返回只出栈、不改步。
 */
class DataConflictRouteTest {

    @Test
    fun `入口清单只有一个数据冲突入口`() {
        assertThat(SyncEntries.All).hasSize(1)
        assertThat(SyncEntries.All.map { it.id }).containsExactly("sync.dataConflict")
        SyncEntries.All.forEach { entry ->
            assertThat(entry.title).isNotEmpty()
            assertThat(entry.hint).isNotEmpty()
        }
        assertThat(SYNC_CENTER_TITLE).isEqualTo("数据同步")
    }

    @Test
    fun `六个按钮映射到六个意图`() {
        assertThat(dataConflictIntentOf(DataConflictRouteAction.SwitchAfterSync))
            .isEqualTo(DataConflictIntent.SwitchAfterSync)
        assertThat(dataConflictIntentOf(DataConflictRouteAction.SwitchDirect))
            .isEqualTo(DataConflictIntent.SwitchDirect)
        assertThat(dataConflictIntentOf(DataConflictRouteAction.KeepLocal))
            .isEqualTo(DataConflictIntent.KeepLocal)
        assertThat(dataConflictIntentOf(DataConflictRouteAction.KeepRemote))
            .isEqualTo(DataConflictIntent.KeepRemote)
        assertThat(dataConflictIntentOf(DataConflictRouteAction.MergeBoth))
            .isEqualTo(DataConflictIntent.MergeBoth)
        assertThat(dataConflictIntentOf(DataConflictRouteAction.Retry))
            .isEqualTo(DataConflictIntent.Retry)
    }

    @Test
    fun `离线切换场景进入完成态`() {
        val start = DataConflictUiState(step = DataConflictStep.OfflineSwitch)

        val synced = reduceDataConflict(
            start,
            dataConflictIntentOf(DataConflictRouteAction.SwitchAfterSync),
        )
        val direct = reduceDataConflict(
            start,
            dataConflictIntentOf(DataConflictRouteAction.SwitchDirect),
        )

        assertThat(synced.step).isEqualTo(DataConflictStep.OfflineDone)
        assertThat(synced.offlineSwitchMode).isEqualTo(OfflineSwitchMode.SyncThenSwitch)
        assertThat(direct.step).isEqualTo(DataConflictStep.OfflineDone)
        assertThat(direct.offlineSwitchMode).isEqualTo(OfflineSwitchMode.DirectSwitch)
    }

    @Test
    fun `多设备场景三种选择都进入完成态`() {
        val start = DataConflictUiState(step = DataConflictStep.MultiDevice)

        val keepLocal = reduceDataConflict(start, dataConflictIntentOf(DataConflictRouteAction.KeepLocal))
        val keepRemote = reduceDataConflict(start, dataConflictIntentOf(DataConflictRouteAction.KeepRemote))
        val merge = reduceDataConflict(start, dataConflictIntentOf(DataConflictRouteAction.MergeBoth))

        assertThat(keepLocal.step).isEqualTo(DataConflictStep.MultiDone)
        assertThat(keepLocal.resolution?.mergedWeights).containsExactly(start.localRecord.weight)
        assertThat(keepRemote.resolution?.mergedWeights).containsExactly(start.remoteRecord.weight)
        assertThat(merge.resolution?.mergedWeights)
            .containsExactly(start.localRecord.weight, start.remoteRecord.weight)
    }

    @Test
    fun `同步冲突场景保留云端进入完成态`() {
        val start = DataConflictUiState(step = DataConflictStep.SyncConflict)

        val done = reduceDataConflict(start, dataConflictIntentOf(DataConflictRouteAction.KeepRemote))

        assertThat(done.step).isEqualTo(DataConflictStep.SyncDone)
        assertThat(done.resolvedNote).isEqualTo(start.cloudVersion.note)
    }

    @Test
    fun `返回不改步进 出栈由宿主负责`() {
        DataConflictStep.entries.forEach { step ->
            val state = DataConflictUiState(step = step)
            assertThat(reduceDataConflict(state, DataConflictIntent.Back).step).isEqualTo(step)
        }
    }
}
