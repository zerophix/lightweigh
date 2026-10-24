package com.xcwk.scalehub.feature.member

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * member — [ActionPlansScreen] 的接入装配点（T-087 接入波）。
 *
 * 成员管理页只挂一个入口（[com.xcwk.scalehub.ui.shell.WiringEntries.ActionPlans]），
 * 六态的推进全部由 [actionPlansReduce] 承担，本文件不新增业务判断。
 *
 * ## 本轮仍是空回调的地方（诚实标注，不假装已接数据层）
 * - 计划/任务落库：保存只回列表，不写库
 * - 「了解计划如何生成」说明页：尚无对应屏，保持空回调
 * - 通知权限：真实探测归宿主；本轮点「前往系统设置」按「已授予」回传
 */
@Composable
fun ActionPlansRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf(ActionPlansUiState()) }

    ActionPlansScreen(
        state = state,
        onIntent = { state = actionPlansReduce(state, it) },
        modifier = modifier,
        onBack = onBack,
        onEditPlan = { state = actionPlansReduce(state, ActionPlanIntent.OpenEditPlan) },
        onEditTask = { taskId -> state = actionPlansReduce(state, ActionPlanIntent.OpenEditTask(taskId)) },
        onOpenRemind = { state = actionPlansReduce(state, ActionPlanIntent.OpenRemind) },
        // 空态「生成本周计划」：本轮直接落到计划列表（真实生成在 AI 侧）
        onAdoptPlan = { state = actionPlansReduce(state, ActionPlanIntent.GeneratePlan) },
        // 从系统通知设置返回后按「已授予」回传，避免开关恒为关
        onGoNotificationSettings = {
            state = actionPlansReduce(state, ActionPlanIntent.SetNotificationGranted(true))
        },
    )
}
