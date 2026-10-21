package com.xcwk.scalehub.feature.habit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * habit — 路由挂载点（同 MineRoute/TrendRoute 口径）。
 * 创建 ViewModel，收集 StateFlow → 传递给 HabitScreen。
 */
@Composable
fun HabitRoute(
    onBack: () -> Unit = {},
    vm: HabitViewModel = viewModel(factory = HabitViewModel.Factory),
) {
    val habits by vm.habits.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()

    HabitScreen(
        onBack = onBack,
        habits = habits,
        state = state,
        onIntent = vm::onIntent,
    )
}
