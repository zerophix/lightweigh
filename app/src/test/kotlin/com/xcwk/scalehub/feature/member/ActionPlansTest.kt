package com.xcwk.scalehub.feature.member

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-087 行动计划六态纯逻辑单测。
 *
 * 覆盖验收里**唯一可自动判定**的两部分：
 * - 六态呈现转移（[actionPlansReduce]）：列表 / 编辑计划 / 编辑任务 / 空态 / 提醒 / 通知被拒
 * - 纯函数：进度文案、勾选计数、勾选与频次/时长更新、提醒可用判定
 * - 静态文案逐字对齐原型 `13-members-plans.html`（六个 view）
 *
 * 视觉布局不在此测（需真机/截图）；状态转移与「权限被拒不得显示已开启」写错了没人会发现，故钉死。
 */
class ActionPlansTest {

    private fun state(
        step: ActionPlanStep = ActionPlanStep.Plans,
        tasks: List<PlanTask> = DEFAULT_PLAN_TASKS,
        skipSheet: String = "",
        timeSheet: Boolean = false,
        remindEnabled: Boolean = true,
        granted: Boolean = true,
        remindTime: String = DEFAULT_REMIND_TIME,
    ) = ActionPlansUiState(
        step = step,
        tasks = tasks,
        skipSheetTaskId = skipSheet,
        timeSheetVisible = timeSheet,
        remindEnabled = remindEnabled,
        notificationGranted = granted,
        remindTime = remindTime,
    )

    private fun task(state: ActionPlansUiState, id: String): PlanTask = state.tasks.single { it.id == id }

    // ---- 初始态与列表口径 ----

    @Test
    fun `初始态为计划列表且只呈现已保留的三项`() {
        val s = state()
        assertThat(s.step).isEqualTo(ActionPlanStep.Plans)
        assertThat(s.tasks).hasSize(5)
        assertThat(s.planTasks.map { it.title })
            .containsExactly("轻松散步 20 分钟", "固定时段测量体重", "记录睡眠感受")
            .inOrder()
    }

    @Test
    fun `进度文案为本周 1 除以 3 已完成`() {
        assertThat(planProgressText(DEFAULT_PLAN_TASKS)).isEqualTo("本周 1/3 已完成，继续保持。")
        assertThat(completedCount(DEFAULT_PLAN_TASKS)).isEqualTo(1)
        assertThat(plannedCount(DEFAULT_PLAN_TASKS)).isEqualTo(3)
        assertThat(planProgressPercent(DEFAULT_PLAN_TASKS)).isEqualTo(33)
    }

    @Test
    fun `进度分母为零时用兜底文案不除零`() {
        val empty = DEFAULT_PLAN_TASKS.map { it.copy(kept = false) }
        assertThat(planProgressText(empty)).isEqualTo(ActionPlanText.NoPlanTasks)
        assertThat(planProgressPercent(empty)).isEqualTo(0)
    }

    @Test
    fun `已完成行副标题固定为已完成未完成行用原型示例`() {
        val s = state()
        assertThat(planRowHint(task(s, TASK_WALK))).isEqualTo("已完成")
        assertThat(planRowHint(task(s, TASK_MEASURE))).isEqualTo("点击编辑此项")
        assertThat(planRowHint(task(s, TASK_SLEEP))).isEqualTo("跳过/取消示例")
        // 取消完成后回落到原型副标题（散步任务没有示例副标题，故为空串）
        val undone = state(tasks = toggleDone(DEFAULT_PLAN_TASKS, TASK_WALK))
        assertThat(planRowHint(task(undone, TASK_WALK))).isEmpty()
    }

    // ---- 列表 -> 编辑计划 -> 保存 ----

    @Test
    fun `列表进入编辑计划再保存修改回到列表`() {
        val edited = actionPlansReduce(state(), ActionPlanIntent.OpenEditPlan)
        assertThat(edited.step).isEqualTo(ActionPlanStep.EditPlan)
        assertThat(edited.editHintText).isEqualTo("勾选想保留的任务（已选 3/5）")

        val saved = actionPlansReduce(edited, ActionPlanIntent.SavePlan)
        assertThat(saved.step).isEqualTo(ActionPlanStep.Plans)
    }

    @Test
    fun `编辑计划勾选任务后计数随之变化`() {
        val edited = actionPlansReduce(state(), ActionPlanIntent.OpenEditPlan)
        val keptSugar = actionPlansReduce(edited, ActionPlanIntent.ToggleKept(TASK_SUGAR))
        assertThat(task(keptSugar, TASK_SUGAR).kept).isTrue()
        assertThat(keptSugar.editHintText).isEqualTo("勾选想保留的任务（已选 4/5）")
        // 取消散步后回到 3/5，但换的是另一项：原列表不被就地修改
        val droppedWalk = actionPlansReduce(keptSugar, ActionPlanIntent.ToggleKept(TASK_WALK))
        assertThat(droppedWalk.editHintText).isEqualTo("勾选想保留的任务（已选 3/5）")
        assertThat(task(keptSugar, TASK_WALK).kept).isTrue()
    }

    @Test
    fun `取消保留后任务从计划列表消失且分母同步`() {
        val next = actionPlansReduce(state(), ActionPlanIntent.ToggleKept(TASK_MEASURE))
        assertThat(next.planTasks.map { it.id }).containsExactly(TASK_WALK, TASK_SLEEP).inOrder()
        assertThat(next.progressText).isEqualTo("本周 1/2 已完成，继续保持。")
    }

    // ---- 列表 -> 编辑任务 -> 保存 ----

    @Test
    fun `点击测量任务进入编辑任务页并带上任务 id`() {
        val next = actionPlansReduce(state(), ActionPlanIntent.OpenEditTask(TASK_MEASURE))
        assertThat(next.step).isEqualTo(ActionPlanStep.EditTask)
        assertThat(next.editingTask.title).isEqualTo("固定时段测量体重")
        assertThat(next.editingTask.detail).isEqualTo("建议任选 3 晚，手动记录。")
        assertThat(next.editingTask.weeklyTimes).isEqualTo(3)
        assertThat(next.editingTask.durationMinutes).isEqualTo(20)
        assertThat(next.editingTask.reminderTime).isEqualTo("19:00")
    }

    @Test
    fun `编辑任务改频次与时长后保存回到列表`() {
        var s = actionPlansReduce(state(), ActionPlanIntent.OpenEditTask(TASK_MEASURE))
        s = actionPlansReduce(s, ActionPlanIntent.SelectFrequency(TASK_MEASURE, 5))
        s = actionPlansReduce(s, ActionPlanIntent.SelectDuration(TASK_MEASURE, 30))
        assertThat(task(s, TASK_MEASURE).weeklyTimes).isEqualTo(5)
        assertThat(task(s, TASK_MEASURE).durationMinutes).isEqualTo(30)

        val saved = actionPlansReduce(s, ActionPlanIntent.SaveTask)
        assertThat(saved.step).isEqualTo(ActionPlanStep.Plans)
        assertThat(task(saved, TASK_MEASURE).weeklyTimes).isEqualTo(5)
    }

    @Test
    fun `不在档位内的频次与时长被忽略`() {
        val tasks = updateFrequency(DEFAULT_PLAN_TASKS, TASK_MEASURE, 4)
        assertThat(tasks).isEqualTo(DEFAULT_PLAN_TASKS)
        val durations = updateDuration(DEFAULT_PLAN_TASKS, TASK_MEASURE, 15)
        assertThat(durations).isEqualTo(DEFAULT_PLAN_TASKS)
    }

    @Test
    fun `移除任务只是取消保留并回到列表`() {
        var s = actionPlansReduce(state(), ActionPlanIntent.OpenEditTask(TASK_MEASURE))
        s = actionPlansReduce(s, ActionPlanIntent.RemoveTask(TASK_MEASURE))
        assertThat(s.step).isEqualTo(ActionPlanStep.Plans)
        assertThat(task(s, TASK_MEASURE).kept).isFalse()
        assertThat(s.tasks).hasSize(5)
        assertThat(s.planTasks.map { it.id }).containsExactly(TASK_WALK, TASK_SLEEP).inOrder()
    }

    // ---- 任务处理弹层 ----

    @Test
    fun `本次跳过只收弹层任务仍保留本周`() {
        val opened = actionPlansReduce(state(), ActionPlanIntent.OpenSkipSheet(TASK_SLEEP))
        assertThat(opened.skipSheetTaskId).isEqualTo(TASK_SLEEP)
        val skipped = actionPlansReduce(opened, ActionPlanIntent.SkipOnce(TASK_SLEEP))
        assertThat(skipped.skipSheetTaskId).isEmpty()
        assertThat(task(skipped, TASK_SLEEP).kept).isTrue()
        assertThat(task(skipped, TASK_SLEEP).done).isFalse()
    }

    @Test
    fun `从本周计划移除清空弹层并从列表消失`() {
        val opened = actionPlansReduce(state(), ActionPlanIntent.OpenSkipSheet(TASK_SLEEP))
        val removed = actionPlansReduce(opened, ActionPlanIntent.RemoveTask(TASK_SLEEP))
        assertThat(removed.skipSheetTaskId).isEmpty()
        assertThat(removed.planTasks.map { it.id }).containsExactly(TASK_WALK, TASK_MEASURE).inOrder()
    }

    @Test
    fun `换一个任务收弹层不改动本周任务`() {
        val opened = actionPlansReduce(state(), ActionPlanIntent.OpenSkipSheet(TASK_SLEEP))
        val swapped = actionPlansReduce(opened, ActionPlanIntent.SwapTask(TASK_SLEEP))
        assertThat(swapped.skipSheetTaskId).isEmpty()
        assertThat(swapped.tasks).isEqualTo(DEFAULT_PLAN_TASKS)
    }

    // ---- 空计划 ----

    @Test
    fun `空计划页生成本周计划回到列表`() {
        val empty = actionPlansReduce(state(), ActionPlanIntent.OpenEmptyPlan)
        assertThat(empty.step).isEqualTo(ActionPlanStep.Empty)
        val generated = actionPlansReduce(empty, ActionPlanIntent.GeneratePlan)
        assertThat(generated.step).isEqualTo(ActionPlanStep.Plans)
    }

    // ---- 提醒与通知权限 ----

    @Test
    fun `提醒开关往返翻转`() {
        val off = actionPlansReduce(state(), ActionPlanIntent.ToggleRemindEnabled)
        assertThat(off.remindEnabled).isFalse()
        assertThat(off.reminderActive).isFalse()
        val on = actionPlansReduce(off, ActionPlanIntent.ToggleRemindEnabled)
        assertThat(on.remindEnabled).isTrue()
        assertThat(on.reminderActive).isTrue()
    }

    @Test
    fun `通知权限被拒时即使开关为开也不算提醒生效`() {
        // 硬约束：权限缺失不得呈现为已开启
        assertThat(isReminderEffective(enabled = true, notificationGranted = false)).isFalse()
        assertThat(isReminderEffective(enabled = false, notificationGranted = true)).isFalse()
        assertThat(isReminderEffective(enabled = true, notificationGranted = true)).isTrue()

        val denied = state(granted = false)
        assertThat(denied.remindEnabled).isTrue()
        assertThat(denied.reminderActive).isFalse()
    }

    @Test
    fun `权限探测结果由外部注入`() {
        val revoked = actionPlansReduce(state(), ActionPlanIntent.SetNotificationGranted(false))
        assertThat(revoked.notificationGranted).isFalse()
        assertThat(revoked.reminderActive).isFalse()
        val restored = actionPlansReduce(revoked, ActionPlanIntent.SetNotificationGranted(true))
        assertThat(restored.reminderActive).isTrue()
    }

    @Test
    fun `通知被拒分支关闭测量提醒回到提醒设置页`() {
        val denied = actionPlansReduce(state(granted = false), ActionPlanIntent.OpenNotifDenied)
        assertThat(denied.step).isEqualTo(ActionPlanStep.NotifDenied)
        val closed = actionPlansReduce(denied, ActionPlanIntent.DisableReminder)
        assertThat(closed.step).isEqualTo(ActionPlanStep.Remind)
        assertThat(closed.remindEnabled).isFalse()
        assertThat(closed.reminderActive).isFalse()
    }

    @Test
    fun `提醒设置保存回到列表`() {
        val remind = actionPlansReduce(state(), ActionPlanIntent.OpenRemind)
        assertThat(remind.step).isEqualTo(ActionPlanStep.Remind)
        assertThat(actionPlansReduce(remind, ActionPlanIntent.SaveReminder).step)
            .isEqualTo(ActionPlanStep.Plans)
    }

    @Test
    fun `时间弹层确认后写回提醒时间并收弹层`() {
        val opened = actionPlansReduce(state(), ActionPlanIntent.OpenTimeSheet)
        assertThat(opened.timeSheetVisible).isTrue()
        val confirmed = actionPlansReduce(opened, ActionPlanIntent.ConfirmTime("08:15"))
        assertThat(confirmed.timeSheetVisible).isFalse()
        assertThat(confirmed.remindTime).isEqualTo("08:15")
        assertThat(dailyTimeLabel(confirmed.remindTime)).isEqualTo("每天 08:15")
    }

    @Test
    fun `取消时间弹层不改提醒时间`() {
        val opened = actionPlansReduce(state(), ActionPlanIntent.OpenTimeSheet)
        val dismissed = actionPlansReduce(opened, ActionPlanIntent.DismissTimeSheet)
        assertThat(dismissed.timeSheetVisible).isFalse()
        assertThat(dismissed.remindTime).isEqualTo(DEFAULT_REMIND_TIME)
    }

    // ---- 文案逐字（原型 13） ----

    @Test
    fun `计划列表页文案与原型一致`() {
        assertThat(ActionPlanText.PlansTitle).isEqualTo("本周行动计划")
        assertThat(ActionPlanText.AdoptedTag).isEqualTo("已采纳")
        assertThat(DEFAULT_EFFECTIVE_AT).isEqualTo("10月21日生效")
        assertThat(ActionPlanText.SectionStatus).isEqualTo("计划与提醒状态")
        assertThat(ActionPlanText.StatusEmpty).isEqualTo("空计划")
        assertThat(ActionPlanText.StatusEditPlan).isEqualTo("编辑计划")
        assertThat(ActionPlanText.StatusRemind).isEqualTo("提醒设置")
        assertThat(ActionPlanText.Disclaimer).isEqualTo("计划为建议，经确认后生效，不承诺效果。")
        assertThat(ActionPlanText.TaskDone).isEqualTo("已完成")
    }

    @Test
    fun `任务处理弹层文案与原型一致`() {
        assertThat(ActionPlanText.SkipSheetTitle).isEqualTo("「记录睡眠」怎么处理？")
        assertThat(ActionPlanText.SkipOnce).isEqualTo("本次跳过")
        assertThat(ActionPlanText.SkipOnceHint).isEqualTo("本周任务保留")
        assertThat(ActionPlanText.SwapTask).isEqualTo("换一个任务")
        assertThat(ActionPlanText.RemoveFromPlan).isEqualTo("从本周计划移除")
    }

    @Test
    fun `编辑计划页五项任务与副标题与原型一致`() {
        assertThat(ActionPlanText.EditPlanTitle).isEqualTo("编辑计划")
        assertThat(ActionPlanText.SavePlan).isEqualTo("保存修改")
        assertThat(DEFAULT_PLAN_TASKS.map { it.title }).containsExactly(
            "轻松散步 20 分钟",
            "固定时段测量体重",
            "记录睡眠感受",
            "减少含糖饮料",
            "拉伸放松 10 分钟",
        ).inOrder()
        assertThat(task(state(), TASK_WALK).suggestion).isEqualTo("建议每周 3 次")
        assertThat(task(state(), TASK_MEASURE).suggestion).isEqualTo("建议任选 3 晚")
        assertThat(task(state(), TASK_SLEEP).suggestion).isEmpty()
        assertThat(task(state(), TASK_SUGAR).suggestion).isEqualTo("建议每日提醒")
        assertThat(task(state(), TASK_STRETCH).suggestion).isEmpty()
    }

    @Test
    fun `编辑任务页档位与标签与原型一致`() {
        assertThat(ActionPlanText.EditTaskTitle).isEqualTo("编辑任务")
        assertThat(ActionPlanText.FrequencyLabel).isEqualTo("每周频次")
        assertThat(ActionPlanText.RemindTimeLabel).isEqualTo("提醒时间")
        assertThat(ActionPlanText.FrequencyUnit).isEqualTo("每天")
        assertThat(ActionPlanText.DurationLabel).isEqualTo("单次时长")
        assertThat(ActionPlanText.RemoveTask).isEqualTo("移除任务")
        assertThat(ActionPlanText.Save).isEqualTo("保存")
        assertThat(FREQUENCY_OPTIONS.map { frequencyLabel(it) })
            .containsExactly("1 次", "2 次", "3 次", "5 次")
            .inOrder()
        assertThat(DURATION_OPTIONS.map { durationLabel(it) })
            .containsExactly("10 分钟", "20 分钟", "30 分钟")
            .inOrder()
    }

    @Test
    fun `空计划页文案与原型一致`() {
        assertThat(ActionPlanText.EmptyTitle).isEqualTo("本周还没有计划")
        assertThat(ActionPlanText.EmptyDesc).isEqualTo("AI 可根据你的测量趋势，生成一份轻松可行的每周小目标。")
        assertThat(ActionPlanText.GeneratePlan).isEqualTo("生成本周计划")
        assertThat(ActionPlanText.LearnGeneration).isEqualTo("了解计划如何生成")
    }

    @Test
    fun `提醒设置页文案与原型一致`() {
        assertThat(ActionPlanText.RemindTitle).isEqualTo("测量提醒")
        assertThat(ActionPlanText.RemindSwitch).isEqualTo("开启测量提醒")
        assertThat(ActionPlanText.RemindSwitchHint).isEqualTo("每天固定时间提醒")
        assertThat(ActionPlanText.RemindRateLabel).isEqualTo("提醒频率")
        assertThat(ActionPlanText.RemindRateValue).isEqualTo("每天")
        assertThat(ActionPlanText.NotifPermissionLabel).isEqualTo("通知权限")
        assertThat(ActionPlanText.NotifPermissionHint).isEqualTo("查看被拒后的引导")
        assertThat(ActionPlanText.View).isEqualTo("查看")
        assertThat(ActionPlanText.RemindBanner).isEqualTo("固定时段测量，数据可比性更好。")
        assertThat(DEFAULT_REMIND_TIME).isEqualTo("07:30")
        assertThat(dailyTimeLabel(DEFAULT_REMIND_TIME)).isEqualTo("每天 07:30")
        assertThat(ActionPlanText.TimeSheetCancel).isEqualTo("取消")
        assertThat(ActionPlanText.TimeSheetConfirm).isEqualTo("确定")
        assertThat(ActionPlanText.TimeHour).isEqualTo("时")
        assertThat(ActionPlanText.TimeMinute).isEqualTo("分")
    }

    @Test
    fun `通知被拒页文案与原型一致`() {
        assertThat(ActionPlanText.DeniedBanner).isEqualTo("系统通知权限已关闭，提醒不会弹出。")
        assertThat(ActionPlanText.GoSystemSettings).isEqualTo("前往系统设置")
        assertThat(ActionPlanText.GoSystemSettingsHint).isEqualTo("设置 › 通知 › 轻衡 › 开启")
        assertThat(ActionPlanText.CloseReminder).isEqualTo("关闭测量提醒")
        assertThat(ActionPlanText.CloseReminderHint).isEqualTo("不开启通知，也不再提醒")
        assertThat(ActionPlanText.DeniedNote).isEqualTo("即使关闭提醒，测量与记录功能完全不受影响。")
        assertThat(ActionPlanText.GoSystemSettingsPrimary).isEqualTo("去系统设置开启")
    }

    @Test
    fun `返回计划会收起所有弹层`() {
        val opened = actionPlansReduce(
            state(skipSheet = TASK_SLEEP, timeSheet = true),
            ActionPlanIntent.BackToPlans,
        )
        assertThat(opened.skipSheetTaskId).isEmpty()
        assertThat(opened.timeSheetVisible).isFalse()
        assertThat(opened.step).isEqualTo(ActionPlanStep.Plans)
    }

    @Test
    fun `通知被拒页返回回到提醒设置`() {
        val denied = actionPlansReduce(state(granted = false), ActionPlanIntent.OpenNotifDenied)
        assertThat(actionPlansReduce(denied, ActionPlanIntent.BackToRemind).step)
            .isEqualTo(ActionPlanStep.Remind)
    }
}
