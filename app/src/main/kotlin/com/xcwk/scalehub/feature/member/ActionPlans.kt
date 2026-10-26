package com.xcwk.scalehub.feature.member

import com.xcwk.scalehub.ui.components.QkHeaderStyle
import com.xcwk.scalehub.ui.components.QkScreenHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.components.QkProgress
import com.xcwk.scalehub.ui.components.QkSegmented
import com.xcwk.scalehub.ui.components.QkWheelPicker
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * member — 行动计划六态（T-087 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/13-members-plans.html` 的六个 view：
 * - `v-plans` 计划列表：已采纳卡 + 三项任务 + 计划与提醒状态入口 + 任务弹层
 * - `v-edit-plan` 编辑计划：勾选想保留的任务（已选 3/5）+ 保存修改
 * - `v-edit-task` 编辑任务：每周频次 / 提醒时间 / 单次时长 + 移除任务 / 保存
 * - `v-plan-empty` 空计划：本周还没有计划 + 生成本周计划
 * - `v-remind` 提醒设置：开关 + 提醒时间 / 频率 / 通知权限 + 时间弹层
 * - `v-notif-denied` 通知被拒：权限横幅 + 前往系统设置 / 关闭测量提醒
 *
 * ## 呈现式边界（硬约束）
 * 本文件**只维护六态呈现**，不接触任何系统 API：
 * - 通知权限判定由调用方探测后经 [ActionPlanIntent.SetNotificationGranted] 注入；
 *   跳系统设置走 [ActionPlansScreen] 的 `onGoNotificationSettings` 回调。
 * - 提醒写入、计划落库、任务替换均经回调交给宿主（本文件不写库、不设闹钟）。
 * - 通知权限被拒时**不得**把提醒呈现为「已开启」：开关的勾选值一律取
 *   [ActionPlansUiState.reminderActive]（= 用户开关 且 权限已授予），
 *   权限缺失时即使 `remindEnabled` 为真也不显示为已开启。
 *
 * ## 纯函数（JVM 单测钉死）
 * 进度文案 [planProgressText]、勾选计数 [editPlanHintText]、任务勾选 [toggleKept]、
 * 频次/时长 [updateFrequency] / [updateDuration]、提醒可用判定 [isReminderEffective]。
 */

// ---- 六态状态机 ----

/** 行动计划流程的六个呈现态（与原型六个 view 一一对应）。 */
enum class ActionPlanStep { Plans, EditPlan, EditTask, Empty, Remind, NotifDenied }

/** 计划任务（一条小目标）。字段全部用于呈现，不含任何持久化语义。 */
data class PlanTask(
    val id: String,
    val title: String,
    /** 编辑计划页副标题（建议每周 3 次 / 建议任选 3 晚 / 建议每日提醒），无则空串 */
    val suggestion: String = "",
    /** 计划列表页副标题（点击编辑此项 / 跳过/取消示例）；「已完成」由 done 推导 */
    val planHint: String = "",
    /** 编辑任务页说明（建议任选 3 晚，手动记录。） */
    val detail: String = "",
    /** 是否已完成（本周） */
    val done: Boolean = false,
    /** 是否保留在本周计划里（编辑计划页的勾选结果） */
    val kept: Boolean = true,
    /** 每周频次（1 / 2 / 3 / 5） */
    val weeklyTimes: Int = 3,
    /** 提醒时间（HH:mm） */
    val reminderTime: String = "19:00",
    /** 单次时长（分钟：10 / 20 / 30） */
    val durationMinutes: Int = 20,
    /** 点击该行是否走「本次跳过 / 换一个任务 / 移除」弹层（原型第三行语义） */
    val opensSkipSheet: Boolean = false,
)

/** 呈现态。时间戳/生效日期由调用方注入，本文件不生成。 */
data class ActionPlansUiState(
    val step: ActionPlanStep = ActionPlanStep.Plans,
    val tasks: List<PlanTask> = DEFAULT_PLAN_TASKS,
    /** 编辑任务页当前编辑的任务 id */
    val editingTaskId: String = TASK_MEASURE,
    /** 任务处理弹层当前指向的任务 id（空串 = 弹层关闭） */
    val skipSheetTaskId: String = "",
    /** 时间选择弹层是否可见 */
    val timeSheetVisible: Boolean = false,
    /** 用户是否打开测量提醒（用户意图，不代表提醒真的会弹） */
    val remindEnabled: Boolean = true,
    /** 系统通知权限是否已授予（由调用方探测后注入） */
    val notificationGranted: Boolean = true,
    /** 提醒时间（HH:mm） */
    val remindTime: String = DEFAULT_REMIND_TIME,
    /** 计划生效日期文案 */
    val effectiveAt: String = DEFAULT_EFFECTIVE_AT,
) {
    /** 本周计划里的任务（未被移除的） */
    val planTasks: List<PlanTask> get() = tasks.filter { it.kept }

    /** 编辑任务页的任务；被移除后回落为第一项，避免空白页 */
    val editingTask: PlanTask
        get() = tasks.firstOrNull { it.id == editingTaskId } ?: tasks.first()

    /** 计划行进度文案：本周 1/3 已完成，继续保持。 */
    val progressText: String get() = planProgressText(tasks)

    /** 进度条百分比 */
    val progressPercent: Int get() = planProgressPercent(tasks)

    /** 编辑计划页提示：勾选想保留的任务（已选 3/5） */
    val editHintText: String get() = editPlanHintText(tasks)

    /** 提醒是否真的会弹：用户开关与系统权限同时满足 */
    val reminderActive: Boolean get() = isReminderEffective(remindEnabled, notificationGranted)
}

/** 流程内的呈现态转移。外部副作用（落库、换任务、跳设置）走回调，不进 reduce。 */
sealed interface ActionPlanIntent {
    data class OpenEditTask(val taskId: String) : ActionPlanIntent
    data object OpenEditPlan : ActionPlanIntent
    data object OpenEmptyPlan : ActionPlanIntent
    data object OpenRemind : ActionPlanIntent
    data object OpenNotifDenied : ActionPlanIntent
    data object BackToPlans : ActionPlanIntent
    data object BackToRemind : ActionPlanIntent

    /** 打开任务处理弹层（本次跳过 / 换一个任务 / 从本周计划移除） */
    data class OpenSkipSheet(val taskId: String) : ActionPlanIntent
    data object CloseSkipSheet : ActionPlanIntent
    data class SkipOnce(val taskId: String) : ActionPlanIntent
    data class SwapTask(val taskId: String) : ActionPlanIntent
    data class RemoveTask(val taskId: String) : ActionPlanIntent

    data class ToggleDone(val taskId: String) : ActionPlanIntent
    data class ToggleKept(val taskId: String) : ActionPlanIntent
    data class SelectFrequency(val taskId: String, val times: Int) : ActionPlanIntent
    data class SelectDuration(val taskId: String, val minutes: Int) : ActionPlanIntent

    data object ToggleRemindEnabled : ActionPlanIntent
    data object DisableReminder : ActionPlanIntent
    data class SetNotificationGranted(val granted: Boolean) : ActionPlanIntent
    data object OpenTimeSheet : ActionPlanIntent
    data object DismissTimeSheet : ActionPlanIntent
    data class ConfirmTime(val time: String) : ActionPlanIntent

    data object GeneratePlan : ActionPlanIntent
    data object SavePlan : ActionPlanIntent
    data object SaveTask : ActionPlanIntent
    data object SaveReminder : ActionPlanIntent
}

/**
 * 纯呈现态转移 —— JVM 单测钉死的部分。
 *
 * 口径：
 * - 「本次跳过」= 本周任务保留（[PlanTask.kept] 不变、[PlanTask.done] 不变），只收弹层；
 * - 「从本周计划移除」= 取消保留（[PlanTask.kept] 置 false）并回列表；
 * - 「换一个任务」的候选内容由调用方提供，纯状态只负责收弹层；
 * - 保存/生成只负责回列表，真正的落库在回调侧。
 */
fun actionPlansReduce(state: ActionPlansUiState, intent: ActionPlanIntent): ActionPlansUiState = when (intent) {
    ActionPlanIntent.OpenEditPlan -> state.copy(step = ActionPlanStep.EditPlan)
    is ActionPlanIntent.OpenEditTask -> state.copy(step = ActionPlanStep.EditTask, editingTaskId = intent.taskId)
    ActionPlanIntent.OpenEmptyPlan -> state.copy(step = ActionPlanStep.Empty)
    ActionPlanIntent.OpenRemind -> state.copy(step = ActionPlanStep.Remind)
    ActionPlanIntent.OpenNotifDenied -> state.copy(step = ActionPlanStep.NotifDenied)
    ActionPlanIntent.BackToPlans -> state.copy(step = ActionPlanStep.Plans, skipSheetTaskId = "", timeSheetVisible = false)
    ActionPlanIntent.BackToRemind -> state.copy(step = ActionPlanStep.Remind)

    is ActionPlanIntent.OpenSkipSheet -> state.copy(skipSheetTaskId = intent.taskId)
    ActionPlanIntent.CloseSkipSheet -> state.copy(skipSheetTaskId = "")
    is ActionPlanIntent.SkipOnce -> state.copy(skipSheetTaskId = "")
    is ActionPlanIntent.SwapTask -> state.copy(skipSheetTaskId = "")
    // 移除任务 = 取消保留并回列表（原型 v-edit-task / 弹层两处都 data-goto="v-plans"）
    is ActionPlanIntent.RemoveTask -> state.copy(
        step = ActionPlanStep.Plans,
        tasks = removeTask(state.tasks, intent.taskId),
        skipSheetTaskId = "",
    )

    is ActionPlanIntent.ToggleDone -> state.copy(tasks = toggleDone(state.tasks, intent.taskId))
    is ActionPlanIntent.ToggleKept -> state.copy(tasks = toggleKept(state.tasks, intent.taskId))
    is ActionPlanIntent.SelectFrequency -> state.copy(
        tasks = updateFrequency(state.tasks, intent.taskId, intent.times),
    )
    is ActionPlanIntent.SelectDuration -> state.copy(
        tasks = updateDuration(state.tasks, intent.taskId, intent.minutes),
    )

    ActionPlanIntent.ToggleRemindEnabled -> state.copy(remindEnabled = !state.remindEnabled)
    // 「关闭测量提醒」= 不开启通知也不再提醒：回提醒设置页并关掉用户开关
    ActionPlanIntent.DisableReminder -> state.copy(step = ActionPlanStep.Remind, remindEnabled = false)
    is ActionPlanIntent.SetNotificationGranted -> state.copy(notificationGranted = intent.granted)
    ActionPlanIntent.OpenTimeSheet -> state.copy(timeSheetVisible = true)
    ActionPlanIntent.DismissTimeSheet -> state.copy(timeSheetVisible = false)
    is ActionPlanIntent.ConfirmTime -> state.copy(remindTime = intent.time, timeSheetVisible = false)

    ActionPlanIntent.GeneratePlan -> state.copy(step = ActionPlanStep.Plans)
    ActionPlanIntent.SavePlan -> state.copy(step = ActionPlanStep.Plans)
    ActionPlanIntent.SaveTask -> state.copy(step = ActionPlanStep.Plans)
    ActionPlanIntent.SaveReminder -> state.copy(step = ActionPlanStep.Plans)
}

// ---- 纯函数（勾选 / 频次 / 提醒可用性） ----

/** 本周已完成数（只数保留在计划里的任务）。 */
fun completedCount(tasks: List<PlanTask>): Int = tasks.count { it.kept && it.done }

/** 本周任务总数（只数保留在计划里的任务）。 */
fun plannedCount(tasks: List<PlanTask>): Int = tasks.count { it.kept }

/** 进度文案：「本周 1/3 已完成，继续保持。」分母为 0 时只说「本周还没有任务」。 */
fun planProgressText(tasks: List<PlanTask>): String {
    val total = plannedCount(tasks)
    if (total == 0) return ActionPlanText.NoPlanTasks
    return "本周 ${completedCount(tasks)}/$total 已完成，继续保持。"
}

/** 进度百分比（0..100，向下取整）。 */
fun planProgressPercent(tasks: List<PlanTask>): Int {
    val total = plannedCount(tasks)
    if (total == 0) return 0
    return completedCount(tasks) * 100 / total
}

/** 编辑计划页提示：「勾选想保留的任务（已选 3/5）」。 */
fun editPlanHintText(tasks: List<PlanTask>): String =
    "勾选想保留的任务（已选 ${tasks.count { it.kept }}/${tasks.size}）"

/** 计划行副标题：已完成的行固定显示「已完成」，其余用原型给的示例副标题。 */
fun planRowHint(task: PlanTask): String = if (task.done) ActionPlanText.TaskDone else task.planHint

/** 勾选/取消一项任务是否保留在本周计划（返回新列表，原列表不变）。 */
fun toggleKept(tasks: List<PlanTask>, taskId: String): List<PlanTask> =
    tasks.map { if (it.id == taskId) it.copy(kept = !it.kept) else it }

/** 勾选/取消完成（返回新列表）。 */
fun toggleDone(tasks: List<PlanTask>, taskId: String): List<PlanTask> =
    tasks.map { if (it.id == taskId) it.copy(done = !it.done) else it }

/** 从本周计划移除 = 取消保留（保留数据行，便于「编辑计划」再勾回来）。 */
fun removeTask(tasks: List<PlanTask>, taskId: String): List<PlanTask> =
    tasks.map { if (it.id == taskId) it.copy(kept = false) else it }

/** 设置每周频次；非法档位（不在 [FREQUENCY_OPTIONS] 内）原样返回。 */
fun updateFrequency(tasks: List<PlanTask>, taskId: String, times: Int): List<PlanTask> {
    if (times !in FREQUENCY_OPTIONS) return tasks
    return tasks.map { if (it.id == taskId) it.copy(weeklyTimes = times) else it }
}

/** 设置单次时长；非法档位（不在 [DURATION_OPTIONS] 内）原样返回。 */
fun updateDuration(tasks: List<PlanTask>, taskId: String, minutes: Int): List<PlanTask> {
    if (minutes !in DURATION_OPTIONS) return tasks
    return tasks.map { if (it.id == taskId) it.copy(durationMinutes = minutes) else it }
}

/** 频次档位 → 分段控件文案（3 -> 3 次）。 */
fun frequencyLabel(times: Int): String = "$times 次"

/** 时长档位 → 分段控件文案（20 -> 20 分钟）。 */
fun durationLabel(minutes: Int): String = "$minutes 分钟"

/** 提醒时间行文案（07:30 -> 每天 07:30）。 */
fun dailyTimeLabel(time: String): String = "每天 $time"

/**
 * 提醒可用判定：只有「用户打开提醒」且「系统通知权限已授予」才算真的会弹。
 *
 * 通知权限被拒时即使开关为真也返回 false —— 不允许把不会弹的提醒呈现为已开启。
 */
fun isReminderEffective(enabled: Boolean, notificationGranted: Boolean): Boolean =
    enabled && notificationGranted

// ---- 静态文案与数据规格（逐字取自原型，JVM 单测校验） ----

object ActionPlanText {
    // v-plans
    const val PlansTitle = "本周行动计划"
    const val AdoptedTag = "已采纳"
    const val SectionStatus = "计划与提醒状态"
    const val StatusEmpty = "空计划"
    const val StatusEditPlan = "编辑计划"
    const val StatusRemind = "提醒设置"
    const val Disclaimer = "计划为建议，经确认后生效，不承诺效果。"
    const val TaskDone = "已完成"

    // v-plans 任务处理弹层
    const val SkipSheetTitle = "「记录睡眠」怎么处理？"
    const val SkipOnce = "本次跳过"
    const val SkipOnceHint = "本周任务保留"
    const val SwapTask = "换一个任务"
    const val RemoveFromPlan = "从本周计划移除"

    // v-edit-plan
    const val EditPlanTitle = "编辑计划"
    const val SavePlan = "保存修改"

    // v-edit-task
    const val EditTaskTitle = "编辑任务"
    const val FrequencyLabel = "每周频次"
    const val RemindTimeLabel = "提醒时间"
    const val FrequencyUnit = "每天"
    const val DurationLabel = "单次时长"
    const val RemoveTask = "移除任务"
    const val Save = "保存"

    // v-plan-empty
    const val EmptyTitle = "本周还没有计划"
    const val EmptyDesc = "AI 可根据你的测量趋势，生成一份轻松可行的每周小目标。"
    const val GeneratePlan = "生成本周计划"
    const val LearnGeneration = "了解计划如何生成"

    // v-remind
    const val RemindTitle = "测量提醒"
    const val RemindSwitch = "开启测量提醒"
    const val RemindSwitchHint = "每天固定时间提醒"
    const val RemindRateLabel = "提醒频率"
    const val RemindRateValue = "每天"
    const val NotifPermissionLabel = "通知权限"
    const val NotifPermissionHint = "查看被拒后的引导"
    const val View = "查看"
    const val RemindBanner = "固定时段测量，数据可比性更好。"
    const val TimeSheetCancel = "取消"
    const val TimeSheetConfirm = "确定"
    const val TimeHour = "时"
    const val TimeMinute = "分"

    // v-notif-denied
    const val DeniedBanner = "系统通知权限已关闭，提醒不会弹出。"
    const val GoSystemSettings = "前往系统设置"
    const val GoSystemSettingsHint = "设置 › 通知 › 轻衡 › 开启"
    const val CloseReminder = "关闭测量提醒"
    const val CloseReminderHint = "不开启通知，也不再提醒"
    const val DeniedNote = "即使关闭提醒，测量与记录功能完全不受影响。"
    const val GoSystemSettingsPrimary = "去系统设置开启"

    // 纯函数兜底文案（分母为 0，原型未给）
    const val NoPlanTasks = "本周还没有任务"
}

// ---- 规格常量 ----

const val TASK_WALK = "plan-walk"
const val TASK_MEASURE = "plan-measure"
const val TASK_SLEEP = "plan-sleep"
const val TASK_SUGAR = "plan-sugar"
const val TASK_STRETCH = "plan-stretch"

/** 提醒时间默认值（原型 v-remind：每天 07:30）。 */
const val DEFAULT_REMIND_TIME = "07:30"

/** 计划生效日期文案（原型 v-plans：10月21日生效）。 */
const val DEFAULT_EFFECTIVE_AT = "10月21日生效"

/** 每周频次四档（原型 v-edit-task 分段控件）。 */
val FREQUENCY_OPTIONS = listOf(1, 2, 3, 5)

/** 单次时长三档（原型 v-edit-task 分段控件）。 */
val DURATION_OPTIONS = listOf(10, 20, 30)

/**
 * 原型 v-edit-plan 的五项候选任务：前三项已勾选（3/5），后两项未勾选。
 * v-plans 的三行与之一一对应（保留了前三项）。
 */
val DEFAULT_PLAN_TASKS = listOf(
    PlanTask(
        id = TASK_WALK,
        title = "轻松散步 20 分钟",
        suggestion = "建议每周 3 次",
        weeklyTimes = 3,
        durationMinutes = 20,
        done = true,
    ),
    PlanTask(
        id = TASK_MEASURE,
        title = "固定时段测量体重",
        suggestion = "建议任选 3 晚",
        planHint = "点击编辑此项",
        detail = "建议任选 3 晚，手动记录。",
        weeklyTimes = 3,
        durationMinutes = 20,
    ),
    PlanTask(
        id = TASK_SLEEP,
        title = "记录睡眠感受",
        planHint = "跳过/取消示例",
        opensSkipSheet = true,
    ),
    PlanTask(
        id = TASK_SUGAR,
        title = "减少含糖饮料",
        suggestion = "建议每日提醒",
        kept = false,
    ),
    PlanTask(
        id = TASK_STRETCH,
        title = "拉伸放松 10 分钟",
        kept = false,
    ),
)

// ---- 六 view 共享的呈现原语 ----

/** 压栈页导航条：返回圆钮 + 页标题 + 可选右侧动作。 */
@Composable
private fun ActionPlanNavBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    QkScreenHeader(title, onBack, modifier = modifier, style = QkHeaderStyle.Title, action = action)
}

/** 页面骨架：统一边距 + 纵向滚动。 */
@Composable
private fun ActionPlanPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page)
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}

/** 任务勾选框（原型 .chk：圆角方块，选中主绿底白勾）。 */
@Composable
private fun ActionPlanCheck(checked: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(QkRadius.Field)
    Box(
        modifier = modifier
            .size(QkSpacing.Xl)
            .clip(shape)
            .background(if (checked) QkColor.Primary else Color.Transparent)
            .border(QkSpacing.Xs / 4, if (checked) QkColor.Primary else QkColor.Border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            QkIcon(Icons.Outlined.Check, tint = QkColor.Card, size = QkSpacing.Lg)
        }
    }
}

/** 开关（原型 .sw：主绿枕头 + 白色圆钮）。 */
@Composable
private fun ActionPlanSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    val trackWidth = QkSpacing.Xl + QkSpacing.Sm
    val trackHeight = QkSpacing.Lg + QkSpacing.Xs
    Row(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> QkColor.Secondary
                    checked -> QkColor.Primary
                    else -> QkColor.Secondary
                },
            )
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(QkSpacing.Xs / 2),
        horizontalArrangement = if (checked) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(trackHeight - QkSpacing.Xs)
                .clip(CircleShape)
                .background(QkColor.Card),
        )
    }
}

/** 提示横幅（原型 .banner）：[warn] 为真时用危险底，否则薄荷底。 */
@Composable
private fun ActionPlanBanner(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    warn: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(if (warn) QkColor.DangerSoft else QkColor.Mint)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkIcon(icon, tint = if (warn) QkColor.Danger else QkColor.Primary, size = QkSpacing.Xl)
        Text(
            text,
            modifier = Modifier.padding(start = QkSpacing.Sm).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (warn) QkColor.Danger else QkColor.Sub,
        )
    }
}

/** 说明小字（原型 .note）。 */
@Composable
private fun ActionPlanNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(top = QkSpacing.Md),
        style = MaterialTheme.typography.labelSmall,
        color = QkColor.Muted,
        textAlign = TextAlign.Center,
    )
}

/** 计划列表/编辑计划的一行：勾选框 + 标题 + 副标题 + 可选箭头。 */
@Composable
private fun ActionPlanTaskRow(
    title: String,
    hint: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    done: Boolean = false,
    onClick: () -> Unit,
    onCheck: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(Modifier.clickable(onClick = onClick))
            .padding(vertical = QkSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.clickable(onClick = onCheck)) {
            ActionPlanCheck(checked = checked)
        }
        Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (done) QkColor.Muted else QkColor.Fg,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }
    }
}

/** 分组容器（原型 .rows：白底圆角，行间细线由行自身留白形成）。 */
@Composable
private fun ActionPlanGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Card))
            .background(QkColor.Card)
            .border(QkSpacing.Xs / 4, QkColor.CardBorder, RoundedCornerShape(QkRadius.Card))
            .padding(horizontal = QkSpacing.CardPad),
        content = content,
    )
}

/** 底部弹层（任务处理 / 时间选择共用骨架）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionPlanSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = QkColor.Card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QkSpacing.Lg)
                .padding(bottom = QkSpacing.Xl),
            content = content,
        )
    }
}

// ---- v-plans：计划列表 ----

/** 已采纳卡：标签 + 生效日期 + 进度文案 + 进度条。 */
@Composable
private fun PlanSummaryCard(state: ActionPlansUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Card))
            .background(QkColor.Mint)
            .padding(QkSpacing.Lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkPill(ActionPlanText.AdoptedTag, tone = QkPillTone.Green)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                state.effectiveAt,
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Muted,
            )
        }
        Text(
            state.progressText,
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
        )
        QkProgress(
            percent = state.progressPercent,
            color = QkColor.Primary,
            modifier = Modifier.padding(top = QkSpacing.Md),
            height = QkSpacing.Sm,
        )
    }
}

@Composable
private fun PlansView(
    state: ActionPlansUiState,
    onIntent: (ActionPlanIntent) -> Unit,
    onBack: () -> Unit,
    onEditPlan: () -> Unit,
    onOpenRemind: () -> Unit,
    onEditTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionPlanPage(modifier) {
        ActionPlanNavBar(ActionPlanText.PlansTitle, onBack)
        PlanSummaryCard(state, modifier = Modifier.padding(top = QkSpacing.Md))
        Column(modifier = Modifier.padding(top = QkSpacing.Md)) {
            state.planTasks.forEach { task ->
                ActionPlanTaskRow(
                    title = task.title,
                    hint = planRowHint(task),
                    checked = task.done,
                    done = task.done,
                    onCheck = { onIntent(ActionPlanIntent.ToggleDone(task.id)) },
                    onClick = {
                        when {
                            task.done -> onIntent(ActionPlanIntent.ToggleDone(task.id))
                            task.opensSkipSheet -> onIntent(ActionPlanIntent.OpenSkipSheet(task.id))
                            else -> {
                                onEditTask(task.id)
                                onIntent(ActionPlanIntent.OpenEditTask(task.id))
                            }
                        }
                    },
                )
            }
        }
        Text(
            ActionPlanText.SectionStatus,
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
        ) {
            QkButton(
                text = ActionPlanText.StatusEmpty,
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(ActionPlanIntent.OpenEmptyPlan) },
            )
            QkButton(
                text = ActionPlanText.StatusEditPlan,
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = {
                    onEditPlan()
                    onIntent(ActionPlanIntent.OpenEditPlan)
                },
            )
            QkButton(
                text = ActionPlanText.StatusRemind,
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = {
                    onOpenRemind()
                    onIntent(ActionPlanIntent.OpenRemind)
                },
            )
        }
        ActionPlanNote(ActionPlanText.Disclaimer)
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }

    if (state.skipSheetTaskId.isNotEmpty()) {
        ActionPlanSheet(onDismiss = { onIntent(ActionPlanIntent.CloseSkipSheet) }) {
            Text(
                ActionPlanText.SkipSheetTitle,
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
            Spacer(modifier = Modifier.height(QkSpacing.Md))
            ActionPlanSheetItem(
                icon = Icons.Outlined.Add,
                title = ActionPlanText.SkipOnce,
                hint = ActionPlanText.SkipOnceHint,
                onClick = { onIntent(ActionPlanIntent.SkipOnce(state.skipSheetTaskId)) },
            )
            ActionPlanSheetItem(
                icon = Icons.Outlined.Refresh,
                title = ActionPlanText.SwapTask,
                onClick = { onIntent(ActionPlanIntent.SwapTask(state.skipSheetTaskId)) },
            )
            ActionPlanSheetItem(
                icon = Icons.Outlined.Close,
                title = ActionPlanText.RemoveFromPlan,
                danger = true,
                onClick = { onIntent(ActionPlanIntent.RemoveTask(state.skipSheetTaskId)) },
            )
        }
    }
}

/** 弹层里的一行可选动作（原型 .sheet-item）。 */
@Composable
private fun ActionPlanSheetItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) QkColor.Danger else QkColor.Primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .clickable(onClick = onClick)
            .padding(vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(QkSpacing.Xl + QkSpacing.Sm)
                .clip(RoundedCornerShape(QkRadius.Field))
                .background(if (danger) QkColor.DangerSoft else QkColor.Mint),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(icon, tint = tint, size = QkSpacing.Lg)
        }
        Column(modifier = Modifier.padding(start = QkSpacing.Md)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (danger) QkColor.Danger else QkColor.Fg,
            )
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
            }
        }
    }
}

// ---- v-edit-plan：编辑计划 ----

@Composable
private fun EditPlanView(
    state: ActionPlansUiState,
    onIntent: (ActionPlanIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionPlanPage(modifier) {
        ActionPlanNavBar(ActionPlanText.EditPlanTitle, onBack)
        Text(
            state.editHintText,
            modifier = Modifier.padding(top = QkSpacing.Md),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )
        Column(modifier = Modifier.padding(top = QkSpacing.Sm)) {
            state.tasks.forEach { task ->
                ActionPlanTaskRow(
                    title = task.title,
                    hint = task.suggestion,
                    checked = task.kept,
                    onClick = { onIntent(ActionPlanIntent.ToggleKept(task.id)) },
                    onCheck = { onIntent(ActionPlanIntent.ToggleKept(task.id)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        QkButton(
            text = ActionPlanText.SavePlan,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onIntent(ActionPlanIntent.SavePlan) },
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}

// ---- v-edit-task：编辑单项任务 ----

@Composable
private fun EditTaskView(
    state: ActionPlansUiState,
    onIntent: (ActionPlanIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = state.editingTask
    ActionPlanPage(modifier) {
        ActionPlanNavBar(ActionPlanText.EditTaskTitle, onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Md)
                .clip(RoundedCornerShape(QkRadius.Card))
                .background(QkColor.Mint)
                .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        ) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
            if (task.detail.isNotEmpty()) {
                Text(
                    task.detail,
                    modifier = Modifier.padding(top = QkSpacing.Xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Md)
                .clip(RoundedCornerShape(QkRadius.Card))
                .background(QkColor.Card)
                .border(QkSpacing.Xs / 4, QkColor.CardBorder, RoundedCornerShape(QkRadius.Card))
                .padding(QkSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Lg),
        ) {
            Column {
                Text(
                    ActionPlanText.FrequencyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.FieldLabel,
                )
                QkSegmented(
                    items = FREQUENCY_OPTIONS.map { frequencyLabel(it) },
                    selectedIndex = FREQUENCY_OPTIONS.indexOf(task.weeklyTimes).coerceAtLeast(0),
                    onSelect = {
                        onIntent(ActionPlanIntent.SelectFrequency(task.id, FREQUENCY_OPTIONS[it]))
                    },
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ActionPlanText.RemindTimeLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Fg,
                )
                Text(
                    task.reminderTime,
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Fg,
                )
                Text(
                    ActionPlanText.FrequencyUnit,
                    modifier = Modifier.padding(start = QkSpacing.Sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
            Column {
                Text(
                    ActionPlanText.DurationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.FieldLabel,
                )
                QkSegmented(
                    items = DURATION_OPTIONS.map { durationLabel(it) },
                    selectedIndex = DURATION_OPTIONS.indexOf(task.durationMinutes).coerceAtLeast(0),
                    onSelect = {
                        onIntent(ActionPlanIntent.SelectDuration(task.id, DURATION_OPTIONS[it]))
                    },
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                )
            }
        }
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            QkButton(
                text = ActionPlanText.RemoveTask,
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(ActionPlanIntent.RemoveTask(task.id)) },
            )
            QkButton(
                text = ActionPlanText.Save,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(ActionPlanIntent.SaveTask) },
            )
        }
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}

// ---- v-plan-empty：空计划 ----

@Composable
private fun EmptyPlanView(
    onIntent: (ActionPlanIntent) -> Unit,
    onBack: () -> Unit,
    onAdoptPlan: () -> Unit,
    onLearnGeneration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionPlanPage(modifier) {
        ActionPlanNavBar(ActionPlanText.PlansTitle, onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(QkSpacing.Xl + QkSpacing.Xl)
                    .clip(CircleShape)
                    .background(QkColor.Mint),
                contentAlignment = Alignment.Center,
            ) {
                QkIcon(
                    Icons.Outlined.EventAvailable,
                    tint = QkColor.Primary,
                    size = QkSpacing.Xl,
                )
            }
            Text(
                ActionPlanText.EmptyTitle,
                modifier = Modifier.padding(top = QkSpacing.Lg),
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
            Text(
                ActionPlanText.EmptyDesc,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = QkSpacing.Sm)
                    .padding(horizontal = QkSpacing.Xl),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
            QkButton(
                text = ActionPlanText.GeneratePlan,
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg),
                onClick = {
                    onAdoptPlan()
                    onIntent(ActionPlanIntent.GeneratePlan)
                },
            )
            QkButton(
                text = ActionPlanText.LearnGeneration,
                style = QkButtonStyle.Ghost,
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
                onClick = onLearnGeneration,
            )
        }
    }
}

// ---- v-remind：提醒设置 ----

@Composable
private fun RemindView(
    state: ActionPlansUiState,
    onIntent: (ActionPlanIntent) -> Unit,
    onBack: () -> Unit,
    onGoNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionPlanPage(modifier) {
        ActionPlanNavBar(ActionPlanText.RemindTitle, onBack)
        ActionPlanGroup(modifier = Modifier.padding(top = QkSpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        ActionPlanText.RemindSwitch,
                        style = MaterialTheme.typography.titleSmall,
                        color = QkColor.Fg,
                    )
                    Text(
                        ActionPlanText.RemindSwitchHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Muted,
                    )
                }
                // 开关勾选值取 reminderActive：权限缺失时不得呈现为已开启
                ActionPlanSwitch(
                    checked = state.reminderActive,
                    onToggle = { onIntent(ActionPlanIntent.ToggleRemindEnabled) },
                )
            }
        }
        ActionPlanGroup(modifier = Modifier.padding(top = QkSpacing.Sm)) {
            QkListRow(
                title = ActionPlanText.RemindTimeLabel,
                onClick = { onIntent(ActionPlanIntent.OpenTimeSheet) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            dailyTimeLabel(state.remindTime),
                            style = MaterialTheme.typography.titleSmall,
                            color = QkColor.Fg,
                        )
                        QkIcon(
                            QkIcons.ChevronRight,
                            tint = QkColor.Hint,
                            size = QkSpacing.Lg,
                        )
                    }
                },
            )
            QkListRow(
                title = ActionPlanText.RemindRateLabel,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ActionPlanText.RemindRateValue,
                            style = MaterialTheme.typography.titleSmall,
                            color = QkColor.Fg,
                        )
                        QkIcon(
                            QkIcons.ChevronRight,
                            tint = QkColor.Hint,
                            size = QkSpacing.Lg,
                        )
                    }
                },
            )
            QkListRow(
                title = ActionPlanText.NotifPermissionLabel,
                subtitle = ActionPlanText.NotifPermissionHint,
                showDivider = false,
                onClick = {
                    onGoNotificationSettings()
                    onIntent(ActionPlanIntent.OpenNotifDenied)
                },
                trailing = {
                    Text(
                        ActionPlanText.View,
                        style = MaterialTheme.typography.titleSmall,
                        color = QkColor.Primary,
                    )
                },
            )
        }
        ActionPlanBanner(
            icon = Icons.Outlined.Schedule,
            text = ActionPlanText.RemindBanner,
            modifier = Modifier.padding(top = QkSpacing.Md),
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        QkButton(
            text = ActionPlanText.Save,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onIntent(ActionPlanIntent.SaveReminder) },
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }

    if (state.timeSheetVisible) {
        ActionPlanTimeSheet(
            time = state.remindTime,
            onDismiss = { onIntent(ActionPlanIntent.DismissTimeSheet) },
            onConfirm = { onIntent(ActionPlanIntent.ConfirmTime(it)) },
        )
    }
}

/** 时间选择弹层（原型 sheet-time：取消 / 提醒时间 / 确定 + 时/分双滚轮）。 */
@Composable
private fun ActionPlanTimeSheet(
    time: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 7
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 30
    var selectedHour by remember { mutableStateOf(hour) }
    var selectedMinute by remember { mutableStateOf(minute / TIME_MINUTE_STEP) }
    val hourLabels = remember { (0..23).map { it.toString().padStart(2, '0') } }
    val minuteLabels = remember { (0..59 step TIME_MINUTE_STEP).map { it.toString().padStart(2, '0') } }

    ActionPlanSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                ActionPlanText.TimeSheetCancel,
                modifier = Modifier.clickable(onClick = onDismiss),
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Muted,
            )
            Text(
                ActionPlanText.RemindTimeLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
            Text(
                ActionPlanText.TimeSheetConfirm,
                modifier = Modifier.clickable {
                    val text = "${hourLabels[selectedHour]}:${minuteLabels[selectedMinute]}"
                    onConfirm(text)
                },
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Primary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Lg),
        ) {
            QkWheelPicker(
                labels = hourLabels,
                initialIndex = selectedHour,
                modifier = Modifier.weight(1f),
                onIndex = { selectedHour = it },
            )
            QkWheelPicker(
                labels = minuteLabels,
                initialIndex = selectedMinute,
                modifier = Modifier.weight(1f),
                onIndex = { selectedMinute = it },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Lg),
        ) {
            Text(
                ActionPlanText.TimeHour,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
            Text(
                ActionPlanText.TimeMinute,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 时间滚轮分钟步长（原型 15/30/45/55 附近以 15 分钟为刻度，这里取 5 分钟便于微调）。 */
private const val TIME_MINUTE_STEP = 5

// ---- v-notif-denied：通知权限被拒 ----

@Composable
private fun NotifDeniedView(
    state: ActionPlansUiState,
    onIntent: (ActionPlanIntent) -> Unit,
    onBack: () -> Unit,
    onGoNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionPlanPage(modifier) {
        ActionPlanNavBar(ActionPlanText.RemindTitle, onBack)
        ActionPlanGroup(modifier = Modifier.padding(top = QkSpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ActionPlanText.RemindSwitch,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Fg,
                )
                // 权限缺失时开关不可操作且不显示为已开启：提醒本来就不会弹
                ActionPlanSwitch(
                    checked = state.reminderActive,
                    enabled = false,
                    onToggle = {},
                )
            }
        }
        ActionPlanBanner(
            icon = Icons.Outlined.NotificationsOff,
            text = ActionPlanText.DeniedBanner,
            modifier = Modifier.padding(top = QkSpacing.Sm),
            warn = true,
        )
        ActionPlanGroup(modifier = Modifier.padding(top = QkSpacing.Md)) {
            QkListRow(
                title = ActionPlanText.GoSystemSettings,
                subtitle = ActionPlanText.GoSystemSettingsHint,
                onClick = onGoNotificationSettings,
                trailing = {
                    QkIcon(
                        QkIcons.ChevronRight,
                        tint = QkColor.Hint,
                        size = QkSpacing.Lg,
                    )
                },
            )
            QkListRow(
                title = ActionPlanText.CloseReminder,
                subtitle = ActionPlanText.CloseReminderHint,
                showDivider = false,
                onClick = { onIntent(ActionPlanIntent.DisableReminder) },
            )
        }
        ActionPlanNote(ActionPlanText.DeniedNote)
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        QkButton(
            text = ActionPlanText.GoSystemSettingsPrimary,
            modifier = Modifier.fillMaxWidth(),
            onClick = onGoNotificationSettings,
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}

// ---- 六态路由 ----

/**
 * 行动计划六态入口：按 [ActionPlansUiState.step] 呈现对应 view。
 *
 * 外部交接点（本组件不落库、不请求权限、不跳系统设置）：
 * - [onEditPlan] / [onEditTask] / [onOpenRemind] 计划与提醒的导航意图
 * - [onAdoptPlan] 采纳 AI 生成的计划（空态「生成本周计划」）
 * - [onGoNotificationSettings] 跳系统通知设置（通知权限被拒时的唯一出路）
 * - [onLearnPlanGeneration] 「了解计划如何生成」说明页
 *
 * 通知权限探测结果由宿主经 [ActionPlanIntent.SetNotificationGranted] 注入，
 * 组件不 import 任何 android.* 通知/闹钟 API。
 */
@Composable
fun ActionPlansScreen(
    state: ActionPlansUiState,
    onIntent: (ActionPlanIntent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onEditPlan: () -> Unit = {},
    onEditTask: (String) -> Unit = {},
    onOpenRemind: () -> Unit = {},
    onAdoptPlan: () -> Unit = {},
    onGoNotificationSettings: () -> Unit = {},
    onLearnPlanGeneration: () -> Unit = {},
) {
    when (state.step) {
        ActionPlanStep.Plans -> PlansView(
            state = state,
            onIntent = onIntent,
            onBack = onBack,
            onEditPlan = onEditPlan,
            onOpenRemind = onOpenRemind,
            onEditTask = onEditTask,
            modifier = modifier,
        )

        ActionPlanStep.EditPlan -> EditPlanView(state, onIntent, onBack, modifier)
        ActionPlanStep.EditTask -> EditTaskView(state, onIntent, onBack, modifier)
        ActionPlanStep.Empty -> EmptyPlanView(
            onIntent = onIntent,
            onBack = onBack,
            onAdoptPlan = onAdoptPlan,
            onLearnGeneration = onLearnPlanGeneration,
            modifier = modifier,
        )

        ActionPlanStep.Remind -> RemindView(
            state = state,
            onIntent = onIntent,
            onBack = onBack,
            onGoNotificationSettings = onGoNotificationSettings,
            modifier = modifier,
        )

        ActionPlanStep.NotifDenied -> NotifDeniedView(
            state = state,
            onIntent = onIntent,
            onBack = onBack,
            onGoNotificationSettings = onGoNotificationSettings,
            modifier = modifier,
        )
    }
}
