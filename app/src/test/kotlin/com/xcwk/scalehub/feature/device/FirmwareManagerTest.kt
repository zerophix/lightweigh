package com.xcwk.scalehub.feature.device

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * T-079 设备固件管理纯逻辑单测。
 *
 * 覆盖验收中**唯一可自动判定**的部分：
 * - 三态状态机（检查 → 可更新/已最新 → 升级中 → 完成）：[firmwareReduce]
 * - 进度百分比边界（0 / 99 / 100 / 越界）与「未满 100% 不得进成功态」
 * - 静态文案与规格逐字对齐原型 `18-device-setup.html`（AC-D4 三条 then）
 *
 * 视觉布局不在此测（需真机/截图）；状态与文案写错了没人会发现，故钉死。
 */
class FirmwareManagerTest {

    private fun state(
        step: FirmwareStep = FirmwareStep.Check,
        hasUpdate: Boolean = true,
        progress: Int = 0,
        remaining: Int = DEFAULT_REMAINING_SECONDS,
    ) = FirmwareUiState(
        step = step,
        hasUpdate = hasUpdate,
        progressPercent = progress,
        remainingSeconds = remaining,
    )

    // ---- 三态转移 ----

    @Test
    fun `初始态为固件检查页`() {
        assertThat(state().step).isEqualTo(FirmwareStep.Check)
    }

    @Test
    fun `检查结果有更新停在可更新页`() {
        val next = firmwareReduce(
            state(step = FirmwareStep.Latest),
            FirmwareIntent.CheckResult(hasUpdate = true, currentVersion = "v1.1.0", latestVersion = "v1.2.0"),
        )
        assertThat(next.step).isEqualTo(FirmwareStep.Check)
        assertThat(next.hasUpdate).isTrue()
        assertThat(next.currentVersion).isEqualTo("v1.1.0")
        assertThat(next.latestVersion).isEqualTo("v1.2.0")
    }

    @Test
    fun `检查结果无更新直接进入设备就绪页`() {
        val next = firmwareReduce(state(), FirmwareIntent.CheckResult(hasUpdate = false))
        assertThat(next.step).isEqualTo(FirmwareStep.Latest)
        assertThat(next.hasUpdate).isFalse()
    }

    @Test
    fun `检查结果重置进度避免沿用上一轮残留`() {
        val next = firmwareReduce(
            state(step = FirmwareStep.Updating, progress = 70),
            FirmwareIntent.CheckResult(hasUpdate = true),
        )
        assertThat(next.progressPercent).isEqualTo(0)
    }

    @Test
    fun `立即更新进入升级中且进度从零开始`() {
        val next = firmwareReduce(state(progress = 88), FirmwareIntent.StartUpdate)
        assertThat(next.step).isEqualTo(FirmwareStep.Updating)
        assertThat(next.progressPercent).isEqualTo(0)
    }

    @Test
    fun `无更新时点立即更新不会进入升级中`() {
        // 防御错误外部调用：没有新版本就没有可升的固件
        val next = firmwareReduce(state(hasUpdate = false), FirmwareIntent.StartUpdate)
        assertThat(next.step).isEqualTo(FirmwareStep.Check)
    }

    @Test
    fun `暂不更新进入就绪页但保留可更新标记`() {
        // 原型「暂不更新」跳 v-firmware-latest；语义上更新机会不能丢
        val next = firmwareReduce(state(step = FirmwareStep.Check), FirmwareIntent.SkipUpdate)
        assertThat(next.step).isEqualTo(FirmwareStep.Latest)
        assertThat(next.hasUpdate).isTrue()
    }

    @Test
    fun `升级中点暂不更新不得静默退出`() {
        val next = firmwareReduce(state(step = FirmwareStep.Updating, progress = 40), FirmwareIntent.SkipUpdate)
        assertThat(next.step).isEqualTo(FirmwareStep.Updating)
    }

    // ---- 进度百分比边界 ----

    @Test
    fun `进度未满 100 停留升级中不展示成功态`() {
        val next = firmwareReduce(state(step = FirmwareStep.Updating), FirmwareIntent.Progress(99))
        assertThat(next.step).isEqualTo(FirmwareStep.Updating)
        assertThat(next.progressPercent).isEqualTo(99)
    }

    @Test
    fun `进度达 100 才进入完成态`() {
        val at99 = firmwareReduce(state(step = FirmwareStep.Updating), FirmwareIntent.Progress(99))
        assertThat(at99.step).isEqualTo(FirmwareStep.Updating)
        val at100 = firmwareReduce(at99, FirmwareIntent.Progress(100))
        assertThat(at100.step).isEqualTo(FirmwareStep.Latest)
        assertThat(at100.progressPercent).isEqualTo(100)
    }

    @Test
    fun `进度为 0 仍在升级中`() {
        val next = firmwareReduce(state(step = FirmwareStep.Updating, progress = 70), FirmwareIntent.Progress(0))
        assertThat(next.step).isEqualTo(FirmwareStep.Updating)
        assertThat(next.progressPercent).isEqualTo(0)
    }

    @Test
    fun `进度越界收敛到 0 到 100`() {
        val negative = firmwareReduce(state(step = FirmwareStep.Updating), FirmwareIntent.Progress(-20))
        assertThat(negative.progressPercent).isEqualTo(0)
        assertThat(negative.step).isEqualTo(FirmwareStep.Updating)

        val overflow = firmwareReduce(state(step = FirmwareStep.Updating), FirmwareIntent.Progress(150))
        assertThat(overflow.progressPercent).isEqualTo(100)
        assertThat(overflow.step).isEqualTo(FirmwareStep.Latest)
    }

    @Test
    fun `检查页收到进度事件被忽略`() {
        // 还没点「立即更新」就不该有进度；乱序事件不能把流程推进到升级中
        val next = firmwareReduce(state(), FirmwareIntent.Progress(30))
        assertThat(next.step).isEqualTo(FirmwareStep.Check)
        assertThat(next.progressPercent).isEqualTo(0)
    }

    @Test
    fun `完成态单调不回退`() {
        // 迟到的低进度事件不能把已完成的固件拉回「升级中」
        val done = state(step = FirmwareStep.Latest, progress = 100)
        val late = firmwareReduce(done, FirmwareIntent.Progress(40))
        assertThat(late.step).isEqualTo(FirmwareStep.Latest)
        assertThat(late.progressPercent).isEqualTo(100)
    }

    @Test
    fun `完成确认在未满 100 时不转移`() {
        val next = firmwareReduce(state(step = FirmwareStep.Updating, progress = 70), FirmwareIntent.CompleteUpdate)
        assertThat(next.step).isEqualTo(FirmwareStep.Updating)
    }

    @Test
    fun `完成确认在满 100 时转移`() {
        val next = firmwareReduce(state(step = FirmwareStep.Updating, progress = 100), FirmwareIntent.CompleteUpdate)
        assertThat(next.step).isEqualTo(FirmwareStep.Latest)
    }

    @Test
    fun `升级完成按钮只在满 100 时可点`() {
        assertThat(canCompleteUpdate(state(step = FirmwareStep.Updating, progress = 0))).isFalse()
        assertThat(canCompleteUpdate(state(step = FirmwareStep.Updating, progress = 99))).isFalse()
        assertThat(canCompleteUpdate(state(step = FirmwareStep.Updating, progress = 100))).isTrue()
        // 非升级中（检查页 / 完成页）一律不可点
        assertThat(canCompleteUpdate(state(step = FirmwareStep.Check, progress = 100))).isFalse()
        assertThat(canCompleteUpdate(state(step = FirmwareStep.Latest, progress = 100))).isFalse()
    }

    @Test
    fun `进度文案百分比边界`() {
        assertThat(firmwareProgressLabel(0)).isEqualTo("0%")
        assertThat(firmwareProgressLabel(70)).isEqualTo("70%")
        assertThat(firmwareProgressLabel(100)).isEqualTo("100%")
        assertThat(firmwareProgressLabel(120)).isEqualTo("100%")
        assertThat(firmwareProgressLabel(-5)).isEqualTo("0%")
    }

    @Test
    fun `百分比由外部传入且状态构造拒绝越界`() {
        // 状态里的进度必须已在 0..100；越界属于调用方 bug，直接抛错而不是静默画歪环
        assertThrows(IllegalArgumentException::class.java) {
            FirmwareUiState(step = FirmwareStep.Updating, progressPercent = 101)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FirmwareUiState(step = FirmwareStep.Updating, progressPercent = -1)
        }
    }

    @Test
    fun `剩余秒数负数收敛为零`() {
        assertThat(firmwareUpdatingSubtitle(-3)).isEqualTo("请勿断开蓝牙或关闭秤\n预计剩余 0 秒")
    }

    // ---- 静态文案与规格（原型逐字） ----

    @Test
    fun `三个 view 的导航标题与原型一致`() {
        assertThat(TITLE_FIRMWARE_CHECK).isEqualTo("固件更新")
        assertThat(TITLE_FIRMWARE_UPDATING).isEqualTo("固件更新中")
        assertThat(TITLE_FIRMWARE_LATEST).isEqualTo("设备信息")
    }

    @Test
    fun `检查页设备行文案与状态胶囊`() {
        // AC-D4 then-1：设备行「轻衡 S1 / 当前固件 v1.1.0」+ 状态「可更新」
        assertThat(DEFAULT_DEVICE_NAME).isEqualTo("轻衡 S1")
        assertThat(firmwareCurrentLabel(DEFAULT_CURRENT_VERSION)).isEqualTo("当前固件 v1.1.0")
        assertThat(UPDATE_AVAILABLE_TAG).isEqualTo("可更新")
        assertThat(firmwareLatestLabel(DEFAULT_LATEST_VERSION)).isEqualTo("新版本 v1.2.0")
    }

    @Test
    fun `更新内容四条与原型逐字同序`() {
        assertThat(FIRMWARE_UPDATE_NOTES).containsExactly(
            "优化蓝牙连接稳定性",
            "修复特定机型测量偶发失败",
            "提升体脂率估算精度",
            "新增低电量提醒",
        ).inOrder()
    }

    @Test
    fun `检查页提示与两个按钮文案`() {
        assertThat(CHECK_NOTE).isEqualTo("更新过程中请勿断开蓝牙或关闭秤，\n预计耗时 2–3 分钟。")
        assertThat(ACTION_UPDATE_NOW).isEqualTo("立即更新")
        assertThat(ACTION_SKIP_UPDATE).isEqualTo("暂不更新")
    }

    @Test
    fun `升级中页文案与原型一致`() {
        // AC-D4 then-2：进度百分比 +「正在更新」+「正在更新固件…」+ 剩余时间 + 中断警示
        assertThat(UPDATING_PROGRESS_HINT).isEqualTo("正在更新")
        assertThat(UPDATING_HEADLINE).isEqualTo("正在更新固件…")
        assertThat(firmwareUpdatingSubtitle(45)).isEqualTo("请勿断开蓝牙或关闭秤\n预计剩余 45 秒")
        assertThat(UPDATE_INTERRUPT_WARNING).isEqualTo("更新中断可能导致设备无法使用，请保持耐心。")
        assertThat(ACTION_SIMULATE_COMPLETE).isEqualTo("模拟更新完成")
    }

    @Test
    fun `中断警示必须保留且不与成功态文案混同`() {
        // 「进度<100 不得展示成功态」的另一面：升级页的中断警示不能为空、不能被成功文案顶掉
        assertThat(UPDATE_INTERRUPT_WARNING).isNotEmpty()
        assertThat(UPDATE_INTERRUPT_WARNING).isNotEqualTo(LATEST_READY_TITLE)
        assertThat(UPDATE_INTERRUPT_WARNING).isNotEqualTo(LATEST_READY_SUBTITLE)
    }

    @Test
    fun `就绪页文案与原型一致`() {
        // AC-D4 then-3：设备已就绪 + 固件已是最新版本 + 可开始测量
        assertThat(LATEST_READY_TITLE).isEqualTo("设备已就绪")
        assertThat(LATEST_READY_SUBTITLE).isEqualTo("固件已是最新版本\n可以开始第一次测量了。")
        assertThat(ACTION_START_MEASURE).isEqualTo("开始测量")
        assertThat(ACTION_BACK).isEqualTo("返回")
    }

    @Test
    fun `默认版本号与原型一致`() {
        assertThat(DEFAULT_CURRENT_VERSION).isEqualTo("v1.1.0")
        assertThat(DEFAULT_LATEST_VERSION).isEqualTo("v1.2.0")
        assertThat(DEFAULT_REMAINING_SECONDS).isEqualTo(45)
    }
}
