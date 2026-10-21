package com.xcwk.scalehub.feature.measure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.command.RecordMeasurementCommand
import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.query.HealthLogItem
import com.xcwk.scalehub.application.query.HealthLogQuery
import com.xcwk.scalehub.application.query.MeasurementQuery
import com.xcwk.scalehub.application.service.RecordMeasurementService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * feature.measure — UDF 四件套之 ViewModel（v2 首屏：手动记体重 + 最近列表）。
 * TODO 后续阶段：BLE 称重流接入（ScaleGateway）、登录/云同步、趋势与报告。
 */
/** 首页「最近测量」的一行：结构化而非预拼字符串，列表行要分别排版时间与数值。 */
data class RecentMeasureItem(
    val time: Long,
    val weightText: String,
    val fatText: String?,
    val timeText: String,
)

data class MeasureUiState(
    /** 首页目标进度（H2 移植补齐）。 */
    val home: com.xcwk.scalehub.application.query.HomeGoalState =
        com.xcwk.scalehub.application.query.HomeGoalState(),
    val weightInput: String = "",
    val saving: Boolean = false,
    val lastRecord: String? = null,
    val error: String? = null,
    val recent: List<RecentMeasureItem> = emptyList(),
    /** 最近健康记录（加号面板写入的可见出口）。 */
    val recentLogs: List<HealthLogItem> = emptyList(),
)

sealed interface MeasureIntent {
    data class WeightChanged(val value: String) : MeasureIntent
    data object Save : MeasureIntent
    data class SetTargetWeight(val kg: Float) : MeasureIntent
}

class MeasureViewModel(
    private val recordMeasurement: RecordMeasurementService,
    private val measurementQuery: MeasurementQuery,
    private val healthLogQuery: HealthLogQuery,
    private val refreshBus: DataRefreshBus? = null,
    private val homeQuery: com.xcwk.scalehub.application.query.HomeGoalQuery? = null,
    private val setTargetWeight: (Float) -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(MeasureUiState())
    val state: StateFlow<MeasureUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
        // 订阅数据源信号（DEC-021 支柱2：跨屏保存/删除后自动重读，替代 UiRefresh 计数）。
        // 首页目标进度读 HomeGoalQuery = 测量 + 健康记录 + 目标 + 档案，故四类都关心
        refreshBus?.changed
            ?.filter { it in HOME_RELEVANT }
            ?.let { signal ->
                viewModelScope.launch { signal.collect { refresh() } }
            }
    }

    private suspend fun refreshHome() {
        val query = homeQuery ?: return
        _state.update { it.copy(home = query.state()) }
    }

    fun onIntent(intent: MeasureIntent) {
        when (intent) {
            is MeasureIntent.WeightChanged -> _state.update { it.copy(weightInput = intent.value, error = null) }
            MeasureIntent.Save -> save()
            is MeasureIntent.SetTargetWeight -> {
                setTargetWeight(intent.kg)
                viewModelScope.launch { refreshHome() }
            }
        }
    }

    private fun save() {
        val kg = _state.value.weightInput.toDoubleOrNull()
        if (kg == null) {
            _state.update { it.copy(error = "请输入有效体重") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val record = recordMeasurement.execute(
                    // 手动录入：无阻抗（体脂 -1）、不挂秤；BLE 路径后续由 ScaleGateway 提供
                    RecordMeasurementCommand(kg, 0.0, "", "builtin-1"),
                )
                _state.update { it.copy(saving = false, weightInput = "", lastRecord = "已保存 ${record.weight} kg") }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
            }
        }
    }

    private suspend fun refresh() {
        refreshHome()
        val stamp = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
        val items = measurementQuery.recent(20).map { row ->
            RecentMeasureItem(
                time = row.time,
                weightText = String.format(java.util.Locale.US, "%.1f kg", row.weight),
                fatText = if (row.fat >= 0) String.format(java.util.Locale.US, "%.1f%%", row.fat) else null,
                timeText = stamp.format(java.util.Date(row.time)),
            )
        }
        val logs = healthLogQuery.recent(5)
        _state.update { it.copy(recent = items, recentLogs = logs) }
    }

    companion object {
        /** 本页关心的数据变更：首页进度读测量 + 健康记录 + 目标 + 档案（HomeGoalQuery）。 */
        private val HOME_RELEVANT = setOf(
            DataChange.Measurements, DataChange.HealthLogs,
            DataChange.Profile, DataChange.MemberScopeChanged,
        )

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                MeasureViewModel(
                    app.container.recordMeasurementService,
                    app.container.measurementQuery,
                    app.container.healthLogQuery,
                    app.container.dataRefreshBus,
                    homeQuery = app.container.homeGoalQuery,
                    setTargetWeight = app.container.setTargetWeight,
                )
            }
        }
    }
}
