package com.xcwk.scalehub.feature.mine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 *
 * 本轮补「账号与信息」四条二级入口（账号与安全 / 隐私与数据管理 / 健康数据说明 / 关于）：
 * 它们是我的页内部页，不进壳层覆盖页栈，栈深一层由本层持有，返回键回到我的页。
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
    var subPage by rememberSaveable { mutableStateOf<MineEntry?>(null) }

    subPage?.let { entry ->
        MineMoreHost(entry = entry, onBack = { subPage = null })
        return
    }

    MineScreen(
        state = state,
        onIntent = vm::onIntent,
        onOpenSettings = onOpenSettings,
        newCameraTarget = app.container.newCameraTarget,
        onOpenMembers = onOpenMembers,
        onOpenHistory = onOpenHistory,
        onOpenDevice = onOpenDevice,
        onOpenHabit = onOpenHabit,
        onOpenMineEntry = { subPage = it },
    )
}
