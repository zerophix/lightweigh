package com.xcwk.scalehub.trigger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.command.RecordMeasurementCommand
import com.xcwk.scalehub.application.query.MeasurementQuery
import com.xcwk.scalehub.application.service.RecordMeasurementService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * trigger — UDF 三件套（v2 首屏：手动记体重 + 最近列表）。
 * TODO 后续阶段：BLE 称重流接入（ScaleGateway）、登录/云同步、趋势与报告。
 */
data class MeasureUiState(
    val weightInput: String = "",
    val saving: Boolean = false,
    val lastRecord: String? = null,
    val error: String? = null,
    val recent: List<String> = emptyList(),
)

sealed interface MeasureIntent {
    data class WeightChanged(val value: String) : MeasureIntent
    data object Save : MeasureIntent
}

class MeasureViewModel(
    private val recordMeasurement: RecordMeasurementService,
    private val measurementQuery: MeasurementQuery,
) : ViewModel() {

    private val _state = MutableStateFlow(MeasureUiState())
    val state: StateFlow<MeasureUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
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
        _state.update { it.copy(recent = items) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                MeasureViewModel(app.container.recordMeasurementService, app.container.measurementQuery)
            }
        }
    }
}
