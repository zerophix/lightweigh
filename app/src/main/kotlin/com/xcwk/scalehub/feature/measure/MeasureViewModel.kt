package com.xcwk.scalehub.feature.measure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.command.RecordMeasurementCommand
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.query.HealthLogItem
import com.xcwk.scalehub.application.query.HealthLogQuery
import com.xcwk.scalehub.application.query.MeasurementQuery
import com.xcwk.scalehub.application.service.RecordMeasurementService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * feature.measure — UDF 四件套之 ViewModel（v2 首屏：手动记体重 + 最近列表）。
 * TODO 后续阶段：BLE 称重流接入（ScaleGateway）、登录/云同步、趋势与报告。
 */
data class MeasureUiState(
    val weightInput: String = "",
    val saving: Boolean = false,
    val lastRecord: String? = null,
    val error: String? = null,
    val recent: List<String> = emptyList(),
    /** 最近健康记录（加号面板写入的可见出口）。 */
    val recentLogs: List<HealthLogItem> = emptyList(),
)

sealed interface MeasureIntent {
    data class WeightChanged(val value: String) : MeasureIntent
    data object Save : MeasureIntent
}

class MeasureViewModel(
    private val recordMeasurement: RecordMeasurementService,
    private val measurementQuery: MeasurementQuery,
    private val healthLogQuery: HealthLogQuery,
    private val refreshBus: DataRefreshBus? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(MeasureUiState())
    val state: StateFlow<MeasureUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
        // 订阅数据源信号（DEC-021 支柱2：跨屏保存/删除后自动重读，替代 UiRefresh 计数）
        refreshBus?.changed?.let { signal ->
            viewModelScope.launch { signal.collect { refresh() } }
        }
    }

    fun onIntent(intent: MeasureIntent) {
        when (intent) {
            is MeasureIntent.WeightChanged -> _state.update { it.copy(weightInput = intent.value, error = null) }
            MeasureIntent.Save -> save()
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
        val items = measurementQuery.recent(20).map {
            val fatText = if (it.fat >= 0) " · 体脂 ${it.fat}%" else ""
            "${it.weight} kg$fatText"
        }
        val logs = healthLogQuery.recent(5)
        _state.update { it.copy(recent = items, recentLogs = logs) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                MeasureViewModel(
                    app.container.recordMeasurementService,
                    app.container.measurementQuery,
                    app.container.healthLogQuery,
                    app.container.dataRefreshBus,
                )
            }
        }
    }
}
