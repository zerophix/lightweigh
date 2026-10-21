package com.xcwk.scalehub.feature.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.command.RecordHabitCommand
import com.xcwk.scalehub.application.query.HabitQuery
import com.xcwk.scalehub.application.service.RecordHabitService
import com.xcwk.scalehub.domain.model.HabitItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * habit — ViewModel：读 HabitQuery（Flow），写 RecordHabitService。
 *
 * 列表实时刷新：HabitQuery.allFlow() 返回 Flow，收集到 StateFlow。
 * 写操作后广播 DataChange.Habits，DAO Flow 自动收到 DB 变更推送，
 * 无需额外订阅 Habits 信号。
 *
 * DataRefreshBus 订阅（DEC-034 组 A）：收到 MemberScopeChanged 时刷新，
 * 确保成员切换后 StateFlow 重新计算（Room DAO Flow 需在收集时捕获 memberId）。
 *
 * 工厂：从 AppContainer 拿查询 + 写用例 + 总线（同 SettingsViewModel/MineViewModel 同口径）。
 */
class HabitViewModel(
    private val habitQuery: HabitQuery,
    private val recordHabitService: RecordHabitService,
    private val refreshBus: DataRefreshBus? = null,
) : ViewModel() {

    /** 习惯列表 —— DAO Flow 收集进 StateFlow，DB 变更自动推送。 */
    val habits: StateFlow<List<HabitItem>> = habitQuery.allFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(HabitUiState())
    val state: StateFlow<HabitUiState> = _state.asStateFlow()

    init {
        // 成员切换时触发刷新：DAO Flow 按成员隔离，切换成员需重新收集
        refreshBus?.changed?.let { flow ->
            viewModelScope.launch {
                flow.collect { change ->
                    if (change == DataChange.MemberScopeChanged) {
                        // habits StateFlow 由 DAO Flow 自动刷新；
                        // 收到成员切换信号后状态自然更新（DB 行已变）
                        _state.update { it.copy(lastRefreshTrigger = System.currentTimeMillis()) }
                    }
                }
            }
        }
    }

    fun onIntent(intent: HabitIntent) {
        when (intent) {
            HabitIntent.CloseEditor -> _state.update {
                it.copy(showEditor = false, editingId = null, error = null)
            }
            is HabitIntent.OpenEditor -> _state.update {
                it.copy(showEditor = true, editingId = intent.habitId, error = null)
            }
            is HabitIntent.Save -> save(intent)
            is HabitIntent.Delete -> delete(intent.id)
            is HabitIntent.ToggleDone -> toggleDone(intent.id)
        }
    }

    private fun save(intent: HabitIntent.Save) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                recordHabitService.save(
                    RecordHabitCommand(
                        id = intent.id,
                        name = intent.name,
                        period = intent.period,
                        targetCount = intent.targetCount,
                        reminderEnabled = intent.reminderEnabled,
                        reminderHour = intent.reminderHour,
                        reminderMinute = intent.reminderMinute,
                    ),
                )
                _state.update { it.copy(loading = false, showEditor = false, editingId = null, error = null) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "保存失败，请稍后重试")
                }
            }
        }
    }

    private fun delete(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                recordHabitService.delete(id)
                _state.update { it.copy(loading = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "删除失败，请稍后重试") }
            }
        }
    }

    private fun toggleDone(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                recordHabitService.tick(id)
                _state.update { it.copy(loading = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "打卡失败，请稍后重试") }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                HabitViewModel(
                    app.container.habitQuery,
                    app.container.recordHabitService,
                    app.container.dataRefreshBus,
                )
            }
        }
    }
}
