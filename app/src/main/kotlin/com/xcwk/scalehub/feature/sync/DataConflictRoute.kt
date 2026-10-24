package com.xcwk.scalehub.feature.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * sync — 数据冲突六态的路由装配（T-094 接入波第三批）。
 *
 * ## 谁进来、返回去哪
 * 数据同步页（[SyncCenterRoute]）→「数据冲突处理」→ 本路由；返回即回数据同步页。
 * 冲突六态（离线切换 / 离线完成 / 多设备 / 多设备完成 / 同步冲突 / 同步完成）
 * 全部由 [reduceDataConflict] 纯函数推进，本文件不持有业务状态，只把
 * 「按钮 → 意图」的映射写在一处。
 *
 * ## 仍是演示值的地方（诚实标注）
 * 记录、设备名、成员名沿用 [DataConflictUiState] 默认演示数据；真实数据由宿主注入。
 * 「先同步再切换」的上传动作归同步用例，本轮只完成状态推进。
 */

/** 数据冲突页上的动作（与六态按钮一一对应）。 */
enum class DataConflictRouteAction {
    SwitchAfterSync,
    SwitchDirect,
    KeepLocal,
    KeepRemote,
    MergeBoth,
    Retry,
}

/** 动作 → 状态机意图。映射固定，单测穷举，避免按钮串味。 */
fun dataConflictIntentOf(action: DataConflictRouteAction): DataConflictIntent = when (action) {
    DataConflictRouteAction.SwitchAfterSync -> DataConflictIntent.SwitchAfterSync
    DataConflictRouteAction.SwitchDirect -> DataConflictIntent.SwitchDirect
    DataConflictRouteAction.KeepLocal -> DataConflictIntent.KeepLocal
    DataConflictRouteAction.KeepRemote -> DataConflictIntent.KeepRemote
    DataConflictRouteAction.MergeBoth -> DataConflictIntent.MergeBoth
    DataConflictRouteAction.Retry -> DataConflictIntent.Retry
}

/** 数据冲突六态路由：本地持有状态，推进走 [reduceDataConflict]。 */
@Composable
fun DataConflictRoute(
    initial: DataConflictUiState = DataConflictUiState(),
    onBack: () -> Unit = {},
) {
    var state by remember(initial) { mutableStateOf(initial) }

    val dispatch: (DataConflictRouteAction, DataConflictUiState) -> Unit = { action, from ->
        state = reduceDataConflict(from, dataConflictIntentOf(action))
    }

    DataConflictScreen(
        state = state,
        onBack = onBack,
        onSwitchOffline = { dispatch(DataConflictRouteAction.SwitchAfterSync, it) },
        onSwitchOnline = { dispatch(DataConflictRouteAction.SwitchDirect, it) },
        onKeepLocal = { dispatch(DataConflictRouteAction.KeepLocal, it) },
        onKeepRemote = { dispatch(DataConflictRouteAction.KeepRemote, it) },
        onMerge = { dispatch(DataConflictRouteAction.MergeBoth, it) },
        onRetry = { dispatch(DataConflictRouteAction.Retry, it) },
    )
}
