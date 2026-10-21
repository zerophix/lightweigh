package com.xcwk.scalehub.feature.mine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.App

/** 路由契约（同 TrendRoute 口径，导航壳启用后映射类型安全 Route）。 */
const val MINE_ROUTE_NAME = "mine"

/**
 * 装配挂载点：我的资料页唯一入口。
 *
 * P1 起二级页（设置 / 成员管理）上提到 NavState 覆盖页栈：本层不再持有
 * showSettings / showMembers 两个局部导航布尔，返回键由壳层统一处理。
 */
@Composable
fun MineRoute(
    onOpenSettings: () -> Unit = {},
    onOpenMembers: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenDevice: () -> Unit = {},
    onOpenHabit: () -> Unit = {},
    vm: MineViewModel = viewModel(factory = MineViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as App
    MineScreen(
        state = state,
        onIntent = vm::onIntent,
        onOpenSettings = onOpenSettings,
        newCameraTarget = app.container.newCameraTarget,
        onOpenMembers = onOpenMembers,
        onOpenHistory = onOpenHistory,
        onOpenDevice = onOpenDevice,
        onOpenHabit = onOpenHabit,
    )
}
