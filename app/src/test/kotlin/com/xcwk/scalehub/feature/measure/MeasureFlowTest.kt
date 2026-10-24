package com.xcwk.scalehub.feature.measure

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.reflect.KClass

/**
 * T-077 测量完整状态机单测。
 *
 * 覆盖验收里**可自动判定**的两部分：
 * - 八个 view 的逐字文案（原型 `12-measure-full.html`）
 * - 状态机的进入条件、合法转移、非法转移被忽略、弹层归属
 *
 * 视觉布局与真实蓝牙/算法时序不在此测（需真机）；保存与反馈是宿主职责，不在此测。
 */
class MeasureFlowTest {

    /** 沿合法路径走到目标态：进入条件一旦失效，这里先红。 */
    private fun at(step: MeasureFlowStep): MeasureFlowUiState = when (step) {
        MeasureFlowStep.Check -> MeasureFlowUiState()
        MeasureFlowStep.Prep -> measureReduce(
            measureReduce(at(MeasureFlowStep.Check), MeasureFlowIntent.ConfirmSurface),
            MeasureFlowIntent.ConfirmPrecheck,
        )
        MeasureFlowStep.Stable -> measureReduce(at(MeasureFlowStep.Prep), MeasureFlowIntent.ReadyToStand)
        MeasureFlowStep.Calc -> measureReduce(at(MeasureFlowStep.Stable), MeasureFlowIntent.ReadingStable(62.4))
        MeasureFlowStep.Left -> measureReduce(at(MeasureFlowStep.Calc), MeasureFlowIntent.DeviceLeft)
        MeasureFlowStep.Invalid -> measureReduce(at(MeasureFlowStep.Calc), MeasureFlowIntent.DataInvalid)
        MeasureFlowStep.SaveFail -> measureReduce(at(MeasureFlowStep.Calc), MeasureFlowIntent.SaveFailed)
        MeasureFlowStep.Success -> measureReduce(at(MeasureFlowStep.Calc), MeasureFlowIntent.CalcFinished)
    }

    // ---------------------------------------------------------------- 文案

    @Test
    fun `v-check 文案逐字取自原型`() {
        assertThat(MeasureFlowText.CheckTitle).isEqualTo("测量前检查")
        assertThat(MeasureFlowText.CheckHero).isEqualTo("确认当前成员与设备")
        assertThat(MeasureFlowText.CheckMember).isEqualTo("测量对象：林悦")
        assertThat(MeasureFlowText.CheckDevice).isEqualTo("设备已连接")
        assertThat(MeasureFlowText.CheckDeviceDetail).isEqualTo("轻衡 S1 · 电量 82%")
        assertThat(MeasureFlowText.Ready).isEqualTo("就绪")
        assertThat(MeasureFlowText.CheckBluetooth).isEqualTo("蓝牙已开启")
        assertThat(MeasureFlowText.CheckSurface).isEqualTo("秤面干燥平整")
        assertThat(MeasureFlowText.Pending).isEqualTo("待确认")
        assertThat(MeasureFlowText.CheckConfirm).isEqualTo("确认，下一步")
    }

    @Test
    fun `v-prep 文案逐字取自原型`() {
        assertThat(MeasureFlowText.PrepTitle).isEqualTo("测量准备")
        assertThat(MeasureFlowText.PrepHero).isEqualTo("赤脚站上体脂秤")
        assertThat(MeasureFlowText.PrepElectrodes).isEqualTo("双脚踩住四片电极")
        assertThat(MeasureFlowText.PrepLegs).isEqualTo("双腿微开，手臂自然下垂")
        assertThat(MeasureFlowText.PrepStill).isEqualTo("站稳后保持静止几秒")
        assertThat(MeasureFlowText.PrepReady).isEqualTo("我准备好了")
    }

    @Test
    fun `v-stable 文案与超时弹窗逐字取自原型`() {
        assertThat(MeasureFlowText.MeasuringTitle).isEqualTo("测量中")
        assertThat(MeasureFlowText.StablePending).isEqualTo("62.–")
        assertThat(MeasureFlowText.StableWaiting).isEqualTo("等待稳定…")
        assertThat(MeasureFlowText.StableKeepStill).isEqualTo("请保持站稳")
        assertThat(MeasureFlowText.StableAutoNext).isEqualTo("读数稳定后自动进入下一步")
        assertThat(MeasureFlowText.StableDone).isEqualTo("稳定了 → 开始分析")
        assertThat(MeasureFlowText.StableTimeoutHint).isEqualTo("读数一直不稳定？→ 超时")
        assertThat(MeasureFlowText.TimeoutTitle).isEqualTo("测量超时")
        assertThat(MeasureFlowText.TimeoutBody).isEqualTo("超过 60 秒体重仍未稳定，可能站姿不稳或秤放置不平。")
        assertThat(MeasureFlowText.GotIt).isEqualTo("知道了")
        assertThat(MeasureFlowText.RetryMeasure).isEqualTo("重新测量")
    }

    @Test
    fun `v-calc 文案与四个演示分支逐字取自原型`() {
        assertThat(MeasureFlowText.MeasuringTitle).isEqualTo("测量中")
        assertThat(MeasureFlowText.CalcLocked).isEqualTo("体重已锁定")
        assertThat(MeasureFlowText.CalcTitle).isEqualTo("正在计算身体成分")
        assertThat(MeasureFlowText.CalcBranchLabel).isEqualTo("触发分支（演示）")
        assertThat(MeasureFlowText.BranchBluetoothLost).isEqualTo("蓝牙中断")
        assertThat(MeasureFlowText.BranchLeftEarly).isEqualTo("提前离开")
        assertThat(MeasureFlowText.BranchInvalid).isEqualTo("无效数据")
        assertThat(MeasureFlowText.BranchSaveFail).isEqualTo("保存失败")
        assertThat(MeasureFlowText.CalcDone).isEqualTo("完成 → 保存成功")
    }

    @Test
    fun `v-left 文案逐字取自原型`() {
        assertThat(MeasureFlowText.LeftTitle).isEqualTo("测量未完成")
        assertThat(MeasureFlowText.LeftHeadline).isEqualTo("检测到你已离开秤面")
        assertThat(MeasureFlowText.LeftBody).isEqualTo("身体成分测量需全程站在秤上。\n本次未采集完整数据。")
        assertThat(MeasureFlowText.RetryMeasure).isEqualTo("重新测量")
        assertThat(MeasureFlowText.Back).isEqualTo("返回")
    }

    @Test
    fun `v-invalid 文案逐字取自原型`() {
        assertThat(MeasureFlowText.InvalidTitle).isEqualTo("数据异常")
        assertThat(MeasureFlowText.InvalidHeadline).isEqualTo("设备返回的数据无效")
        assertThat(MeasureFlowText.InvalidBody).isEqualTo("读数超出合理范围，可能电极接触不良。\n该数据未保存。")
        assertThat(MeasureFlowText.RetryMeasure).isEqualTo("重新测量")
        assertThat(MeasureFlowText.Feedback).isEqualTo("反馈此问题")
    }

    @Test
    fun `v-savefail 文案逐字取自原型`() {
        assertThat(MeasureFlowText.SaveFailTitle).isEqualTo("保存失败")
        assertThat(MeasureFlowText.SaveFailDoneHint).isEqualTo("本次测量已完成")
        assertThat(MeasureFlowText.Unsaved).isEqualTo("未保存")
        assertThat(MeasureFlowText.FatInline).isEqualTo("体脂 ")
        assertThat(MeasureFlowText.PercentUnit).isEqualTo("%")
        assertThat(MeasureFlowText.SaveFailBody).isEqualTo("保存时出错，数据暂存本机，可重试，无需重新测量。")
        assertThat(MeasureFlowText.RetrySave).isEqualTo("重试保存")
        assertThat(MeasureFlowText.Later).isEqualTo("稍后再说")
    }

    @Test
    fun `v-success 文案逐字取自原型`() {
        assertThat(MeasureFlowText.SuccessBanner).isEqualTo("测量完成 · 已保存 · 10月21日 07:30")
        assertThat(MeasureFlowText.SuccessWeightUnit).isEqualTo("kg")
        assertThat(MeasureFlowText.SuccessDiff).isEqualTo("↓0.3 较上次")
        assertThat(MeasureFlowText.SuccessSummary).isEqualTo("整体状态平稳，继续保持。")
        assertThat(MeasureFlowText.BodyFatLabel).isEqualTo("体脂率")
        assertThat(MeasureFlowText.MuscleLabel).isEqualTo("肌肉量")
    }

    @Test
    fun `v-success 示例数据与原型一致`() {
        val result = MeasureFlowResult()
        assertThat(result.weightText).isEqualTo("62.4")
        assertThat(result.fatText).isEqualTo("28.6")
        assertThat(result.muscleText).isEqualTo("42.3")
        assertThat(result.diffText).isEqualTo("↓0.3 较上次")
    }

    // ---------------------------------------------------------------- 进入条件

    @Test
    fun `初始态是测量前检查`() {
        assertThat(MeasureFlowUiState().step).isEqualTo(MeasureFlowStep.Check)
    }

    @Test
    fun `八个态都能沿合法路径到达`() {
        MeasureFlowStep.entries.forEach { step ->
            assertThat(at(step).step).isEqualTo(step)
        }
    }

    @Test
    fun `秤面未确认时确认无效`() {
        val before = MeasureFlowUiState()
        assertThat(before.ready).isFalse()
        val after = measureReduce(before, MeasureFlowIntent.ConfirmPrecheck)
        assertThat(after).isEqualTo(before)
        assertThat(after.step).isEqualTo(MeasureFlowStep.Check)
    }

    @Test
    fun `三项各自未就绪都会挡住确认`() {
        val surfaceOnly = measureReduce(MeasureFlowUiState(), MeasureFlowIntent.ConfirmSurface)
        assertThat(surfaceOnly.ready).isTrue()

        val noDevice = measureReduce(
            surfaceOnly,
            MeasureFlowIntent.SetDeviceReady(false),
        )
        assertThat(measureReduce(noDevice, MeasureFlowIntent.ConfirmPrecheck).step)
            .isEqualTo(MeasureFlowStep.Check)

        val noBt = measureReduce(
            surfaceOnly,
            MeasureFlowIntent.SetBluetoothReady(false),
        )
        assertThat(noBt.ready).isFalse()
        assertThat(measureReduce(noBt, MeasureFlowIntent.ConfirmPrecheck).step)
            .isEqualTo(MeasureFlowStep.Check)
    }

    @Test
    fun `就绪后确认进入测量准备`() {
        val ready = measureReduce(MeasureFlowUiState(), MeasureFlowIntent.ConfirmSurface)
        assertThat(measureReduce(ready, MeasureFlowIntent.ConfirmPrecheck).step)
            .isEqualTo(MeasureFlowStep.Prep)
    }

    @Test
    fun `准备态进入稳定态时读数复位为跳动读数`() {
        val stable = at(MeasureFlowStep.Stable)
        assertThat(stable.liveWeightText).isEqualTo("62.–")
        assertThat(stable.lockedWeightKg).isNull()
        assertThat(stable.weightText).isEqualTo("62.–")
    }

    @Test
    fun `合理读数进入计算态并锁定体重`() {
        val calc = at(MeasureFlowStep.Calc)
        assertThat(calc.lockedWeightKg).isEqualTo(62.4)
        assertThat(calc.weightText).isEqualTo("62.4")
    }

    @Test
    fun `越界读数不进计算而落无效数据`() {
        val over = measureReduce(at(MeasureFlowStep.Stable), MeasureFlowIntent.ReadingStable(400.0))
        assertThat(over.step).isEqualTo(MeasureFlowStep.Invalid)
        assertThat(over.lockedWeightKg).isNull()

        val under = measureReduce(at(MeasureFlowStep.Stable), MeasureFlowIntent.ReadingStable(5.0))
        assertThat(under.step).isEqualTo(MeasureFlowStep.Invalid)
    }

    @Test
    fun `体重合理区间边界`() {
        assertThat(isPlausibleWeight(20.0)).isTrue()
        assertThat(isPlausibleWeight(300.0)).isTrue()
        assertThat(isPlausibleWeight(19.9)).isFalse()
        assertThat(isPlausibleWeight(300.1)).isFalse()
        assertThat(formatLockedWeight(62.44)).isEqualTo("62.4")
    }

    // ---------------------------------------------------------------- 弹层

    @Test
    fun `超时弹层只在稳定态可见并可重测`() {
        val shown = measureReduce(at(MeasureFlowStep.Stable), MeasureFlowIntent.Timeout)
        assertThat(shown.step).isEqualTo(MeasureFlowStep.Stable)
        assertThat(shown.timeoutDialogVisible).isTrue()

        val dismissed = measureReduce(shown, MeasureFlowIntent.DismissTimeout)
        assertThat(dismissed.timeoutDialogVisible).isFalse()

        val restarted = measureReduce(shown, MeasureFlowIntent.RestartAfterTimeout)
        assertThat(restarted.timeoutDialogVisible).isFalse()
        assertThat(restarted.step).isEqualTo(MeasureFlowStep.Stable)
        assertThat(restarted.liveWeightText).isEqualTo("62.–")
    }

    @Test
    fun `计算态返回键弹取消确认`() {
        val asked = measureReduce(at(MeasureFlowStep.Calc), MeasureFlowIntent.Back)
        assertThat(asked.cancelDialogVisible).isTrue()
        assertThat(asked.step).isEqualTo(MeasureFlowStep.Calc)

        val kept = measureReduce(asked, MeasureFlowIntent.DismissCancel)
        assertThat(kept.cancelDialogVisible).isFalse()
        assertThat(kept.step).isEqualTo(MeasureFlowStep.Calc)
    }

    @Test
    fun `确认取消回到检查并复位秤面确认`() {
        val asked = measureReduce(at(MeasureFlowStep.Calc), MeasureFlowIntent.RequestCancel)
        val back = measureReduce(asked, MeasureFlowIntent.ConfirmCancel)
        assertThat(back.step).isEqualTo(MeasureFlowStep.Check)
        assertThat(back.precheck.surfaceConfirmed).isFalse()
        assertThat(back.precheck.deviceReady).isTrue()
        assertThat(back.lockedWeightKg).isNull()
        assertThat(back.cancelDialogVisible).isFalse()
    }

    @Test
    fun `蓝牙弹层可重连也可取消`() {
        val shown = measureReduce(at(MeasureFlowStep.Calc), MeasureFlowIntent.BluetoothLost)
        assertThat(shown.bluetoothDialogVisible).isTrue()

        val reconnected = measureReduce(shown, MeasureFlowIntent.ReconnectBluetooth)
        assertThat(reconnected.bluetoothDialogVisible).isFalse()
        assertThat(reconnected.step).isEqualTo(MeasureFlowStep.Calc)

        val cancelled = measureReduce(shown, MeasureFlowIntent.CancelAfterBluetoothLost)
        assertThat(cancelled.step).isEqualTo(MeasureFlowStep.Check)
        assertThat(cancelled.bluetoothDialogVisible).isFalse()
    }

    @Test
    fun `离开秤面时旧态弹层一并收掉`() {
        val shown = measureReduce(at(MeasureFlowStep.Calc), MeasureFlowIntent.RequestCancel)
        val left = measureReduce(shown, MeasureFlowIntent.DeviceLeft)
        assertThat(left.step).isEqualTo(MeasureFlowStep.Left)
        assertThat(left.cancelDialogVisible).isFalse()
        assertThat(left.bluetoothDialogVisible).isFalse()
    }

    // ---------------------------------------------------------------- 分支与出口

    @Test
    fun `计算完成进保存成功并把锁定体重带进结果`() {
        val success = at(MeasureFlowStep.Success)
        assertThat(success.result.weightText).isEqualTo("62.4")
    }

    @Test
    fun `保存失败可重试成功`() {
        val saveFail = at(MeasureFlowStep.SaveFail)
        assertThat(saveFail.result.weightText).isEqualTo("62.4")
        val success = measureReduce(saveFail, MeasureFlowIntent.RetrySave)
        assertThat(success.step).isEqualTo(MeasureFlowStep.Success)
    }

    @Test
    fun `保存失败的稍后再说不改结果只回检查`() {
        val later = measureReduce(at(MeasureFlowStep.SaveFail), MeasureFlowIntent.Later)
        assertThat(later.step).isEqualTo(MeasureFlowStep.Check)
    }

    @Test
    fun `提前离开与无效数据的两个出口都回检查`() {
        assertThat(measureReduce(at(MeasureFlowStep.Left), MeasureFlowIntent.RetryMeasure).step)
            .isEqualTo(MeasureFlowStep.Check)
        assertThat(measureReduce(at(MeasureFlowStep.Left), MeasureFlowIntent.Back).step)
            .isEqualTo(MeasureFlowStep.Check)
        assertThat(measureReduce(at(MeasureFlowStep.Invalid), MeasureFlowIntent.RetryMeasure).step)
            .isEqualTo(MeasureFlowStep.Check)
    }

    @Test
    fun `反馈问题不改变呈现态`() {
        val invalid = at(MeasureFlowStep.Invalid)
        assertThat(measureReduce(invalid, MeasureFlowIntent.Feedback)).isEqualTo(invalid)
    }

    @Test
    fun `完成退出不改呈现态由宿主收尾`() {
        val success = at(MeasureFlowStep.Success)
        assertThat(measureReduce(success, MeasureFlowIntent.Finish)).isEqualTo(success)
    }

    @Test
    fun `重复测量弹层三个出口都回检查`() {
        val shown = measureReduce(at(MeasureFlowStep.Success), MeasureFlowIntent.RequestDuplicateMeasure)
        assertThat(shown.duplicateDialogVisible).isTrue()

        val replace = measureReduce(shown, MeasureFlowIntent.ReplaceLastRecord)
        val keep = measureReduce(shown, MeasureFlowIntent.KeepAsNewRecord)
        val giveUp = measureReduce(shown, MeasureFlowIntent.GiveUpDuplicate)
        listOf(replace, keep, giveUp).forEach {
            assertThat(it.step).isEqualTo(MeasureFlowStep.Check)
            assertThat(it.duplicateDialogVisible).isFalse()
        }
    }

    // ---------------------------------------------------------------- 非法转移

    /** 每个态允许的意图类型；其余类型一律视为非法（迟到事件）并被忽略。 */
    private val allowed: Map<MeasureFlowStep, Set<KClass<out MeasureFlowIntent>>> = mapOf(
        MeasureFlowStep.Check to setOf(
            MeasureFlowIntent.SetDeviceReady::class,
            MeasureFlowIntent.SetBluetoothReady::class,
            MeasureFlowIntent.ConfirmSurface::class,
            MeasureFlowIntent.ConfirmPrecheck::class,
        ),
        MeasureFlowStep.Prep to setOf(
            MeasureFlowIntent.ReadyToStand::class,
            MeasureFlowIntent.Back::class,
        ),
        MeasureFlowStep.Stable to setOf(
            MeasureFlowIntent.ReadingStable::class,
            MeasureFlowIntent.Timeout::class,
            MeasureFlowIntent.DismissTimeout::class,
            MeasureFlowIntent.RestartAfterTimeout::class,
            MeasureFlowIntent.Back::class,
        ),
        MeasureFlowStep.Calc to setOf(
            MeasureFlowIntent.CalcFinished::class,
            MeasureFlowIntent.DeviceLeft::class,
            MeasureFlowIntent.DataInvalid::class,
            MeasureFlowIntent.SaveFailed::class,
            MeasureFlowIntent.RequestCancel::class,
            MeasureFlowIntent.DismissCancel::class,
            MeasureFlowIntent.ConfirmCancel::class,
            MeasureFlowIntent.Back::class,
            MeasureFlowIntent.BluetoothLost::class,
            MeasureFlowIntent.ReconnectBluetooth::class,
            MeasureFlowIntent.CancelAfterBluetoothLost::class,
        ),
        MeasureFlowStep.Left to setOf(
            MeasureFlowIntent.RetryMeasure::class,
            MeasureFlowIntent.Back::class,
        ),
        MeasureFlowStep.Invalid to setOf(
            MeasureFlowIntent.RetryMeasure::class,
            MeasureFlowIntent.Back::class,
            MeasureFlowIntent.Feedback::class,
        ),
        MeasureFlowStep.SaveFail to setOf(
            MeasureFlowIntent.RetrySave::class,
            MeasureFlowIntent.Later::class,
            MeasureFlowIntent.Back::class,
        ),
        MeasureFlowStep.Success to setOf(
            MeasureFlowIntent.Finish::class,
            MeasureFlowIntent.RequestDuplicateMeasure::class,
            MeasureFlowIntent.ReplaceLastRecord::class,
            MeasureFlowIntent.KeepAsNewRecord::class,
            MeasureFlowIntent.GiveUpDuplicate::class,
        ),
    )

    private val everyIntent: List<MeasureFlowIntent> = listOf(
        MeasureFlowIntent.SetDeviceReady(true),
        MeasureFlowIntent.SetDeviceReady(false),
        MeasureFlowIntent.SetBluetoothReady(true),
        MeasureFlowIntent.SetBluetoothReady(false),
        MeasureFlowIntent.ConfirmSurface,
        MeasureFlowIntent.ConfirmPrecheck,
        MeasureFlowIntent.Back,
        MeasureFlowIntent.ReadyToStand,
        MeasureFlowIntent.ReadingStable(62.4),
        MeasureFlowIntent.Timeout,
        MeasureFlowIntent.DismissTimeout,
        MeasureFlowIntent.RestartAfterTimeout,
        MeasureFlowIntent.CalcFinished,
        MeasureFlowIntent.DeviceLeft,
        MeasureFlowIntent.DataInvalid,
        MeasureFlowIntent.SaveFailed,
        MeasureFlowIntent.RequestCancel,
        MeasureFlowIntent.DismissCancel,
        MeasureFlowIntent.ConfirmCancel,
        MeasureFlowIntent.BluetoothLost,
        MeasureFlowIntent.ReconnectBluetooth,
        MeasureFlowIntent.CancelAfterBluetoothLost,
        MeasureFlowIntent.RetryMeasure,
        MeasureFlowIntent.Feedback,
        MeasureFlowIntent.RetrySave,
        MeasureFlowIntent.Later,
        MeasureFlowIntent.Finish,
        MeasureFlowIntent.RequestDuplicateMeasure,
        MeasureFlowIntent.ReplaceLastRecord,
        MeasureFlowIntent.KeepAsNewRecord,
        MeasureFlowIntent.GiveUpDuplicate,
    )

    @Test
    fun `非法转移一律被忽略且状态不变`() {
        MeasureFlowStep.entries.forEach { step ->
            val state = at(step)
            val legal = allowed.getValue(step)
            everyIntent
                .filter { it::class !in legal }
                .forEach { intent ->
                    val after = measureReduce(state, intent)
                    assertThat(after).isEqualTo(state)
                    assertThat(after.step).isEqualTo(step)
                }
        }
    }

    @Test
    fun `离开秤面后迟到的稳定读数不会把人拽回计算`() {
        val left = at(MeasureFlowStep.Left)
        val after = measureReduce(left, MeasureFlowIntent.ReadingStable(62.4))
        assertThat(after.step).isEqualTo(MeasureFlowStep.Left)
        assertThat(after.lockedWeightKg).isNull()
    }

    @Test
    fun `保存成功后迟到的保存失败事件不倒退`() {
        val success = at(MeasureFlowStep.Success)
        assertThat(measureReduce(success, MeasureFlowIntent.SaveFailed).step)
            .isEqualTo(MeasureFlowStep.Success)
    }

    @Test
    fun `检查态返回不跳到别的态`() {
        val check = MeasureFlowUiState()
        assertThat(measureReduce(check, MeasureFlowIntent.Back)).isEqualTo(check)
    }

    @Test
    fun `转移不修改入参状态`() {
        val before = at(MeasureFlowStep.Calc)
        val snapshot = before.copy()
        measureReduce(before, MeasureFlowIntent.CalcFinished)
        assertThat(before).isEqualTo(snapshot)
    }
}
