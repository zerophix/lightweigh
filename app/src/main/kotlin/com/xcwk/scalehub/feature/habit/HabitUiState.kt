package com.xcwk.scalehub.feature.habit

/**
 * habit — ViewModel UI 状态（DEC-043-D）。
 * 习惯列表实时流由 ViewModel.habits StateFlow 承载，
 * 本状态仅跟踪编辑器/加载/错误等 UI 元状态。
 */
data class HabitUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val showEditor: Boolean = false,
    val editingId: String? = null,
    val lastRefreshTrigger: Long = 0,
)

/** habit 页面意图。 */
sealed interface HabitIntent {
    /** 打开/关闭编辑器（新增或编辑）。 */
    data class OpenEditor(val habitId: String? = null) : HabitIntent
    data object CloseEditor : HabitIntent

    /** 保存习惯（新增或编辑）。 */
    data class Save(
        val id: String? = null,
        val name: String,
        val period: String,
        val targetCount: Int,
        val reminderEnabled: Boolean,
        val reminderHour: Int,
        val reminderMinute: Int,
    ) : HabitIntent

    /** 删除习惯。 */
    data class Delete(val id: String) : HabitIntent

    /** 打卡切换。 */
    data class ToggleDone(val id: String) : HabitIntent
}
