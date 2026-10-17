package com.xcwk.scalehub.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.command.RecordHealthLogCommand
import com.xcwk.scalehub.application.command.RecordMeasurementCommand
import com.xcwk.scalehub.application.service.RecordHealthLogService
import com.xcwk.scalehub.application.service.RecordMeasurementService
import com.xcwk.scalehub.domain.types.DomainException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * record — UDF 四件套之 ViewModel：8 类记录表单。
 * 字段语义逐条随旧库 RecordScreen（Pages.kt:466-530）：
 * 标签预设（饮食四餐/运动六项/血糖四态）、副值字段（体重体脂/运动千卡/体围臀围/血压舒张压）、
 * 喝水快捷量、习惯类以名称为记录主体、非习惯类主值必须为正否则「请输入有效数字」。
 * 双保存路径：weight 走测量用例（体脂直录=旧库 saveManualWeight），其余走健康记录用例。
 */
class RecordViewModel(
    private val type: String,
    private val recordHealthLog: RecordHealthLogService,
    private val recordMeasurement: RecordMeasurementService,
) : ViewModel() {

    private val spec: TypeSpec = SPECS[type] ?: error("未知记录类型: $type")

    private val _state = MutableStateFlow(RecordUiState(type = type))
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(
                title = spec.title,
                primaryLabel = spec.primaryLabel,
                extraLabel = spec.extraLabel,
                presets = spec.presets,
                quickValues = spec.quickValues,
                isHabit = spec.isHabit,
            )
        }
    }

    fun onIntent(intent: RecordIntent) {
        when (intent) {
            is RecordIntent.ValueChanged -> _state.update { it.copy(primaryValue = intent.value, error = null) }
            is RecordIntent.ExtraChanged -> _state.update { it.copy(extraValue = intent.value, error = null) }
            is RecordIntent.PresetPicked -> _state.update { it.copy(selectedPreset = intent.preset, error = null) }
            is RecordIntent.QuickPicked -> _state.update { it.copy(primaryValue = intent.value.toString(), error = null) }
            RecordIntent.Save -> save()
        }
    }

    private fun save() {
        val s = _state.value
        if (s.isHabit) {
            if (s.primaryValue.isBlank()) {
                _state.update { it.copy(error = "请输入有效数字") }   // 旧库同款提示位，习惯实为名称必填
                return
            }
        } else {
            val number = s.primaryValue.toDoubleOrNull()
            if (number == null || number <= 0) {
                _state.update { it.copy(error = "请输入有效数字") }   // Pages.kt:532-535
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                if (s.type == "weight") {
                    recordMeasurement.execute(
                        RecordMeasurementCommand(
                            weightKg = s.primaryValue.toDouble(),
                            impedanceOhm = 0.0,
                            mac = "",
                            algorithmId = "builtin-1",
                            manualFat = s.extraValue.toDoubleOrNull() ?: -1.0,
                        ),
                    )
                } else {
                    val value = if (s.isHabit) 1.0 else s.primaryValue.toDouble()
                    recordHealthLog.execute(
                        RecordHealthLogCommand(
                            type = s.type,
                            value = value,
                            value2 = s.extraValue.toDoubleOrNull() ?: 0.0,
                            label = if (s.isHabit) s.primaryValue.trim() else (s.selectedPreset ?: ""),
                        ),
                    )
                }
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: DomainException) {
                _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
            }
        }
    }

    private data class TypeSpec(
        val title: String,
        val primaryLabel: String,
        val extraLabel: String? = null,
        val presets: List<String> = emptyList(),
        val quickValues: List<Int> = emptyList(),
        val isHabit: Boolean = false,
    )

    companion object {
        /** 逐条随旧库 RecordScreen 字段口径（Pages.kt:466-530）。 */
        private val SPECS = mapOf(
            "weight" to TypeSpec("添加体重", "体重 kg", extraLabel = "体脂率 %，可空"),
            "diet" to TypeSpec("记饮食", "摄入热量 千卡", presets = listOf("早餐", "午餐", "晚餐", "加餐")),
            "exercise" to TypeSpec(
                "记运动", "运动时长 分钟", extraLabel = "消耗热量 千卡",
                presets = listOf("健走", "跑步", "骑行", "跳绳", "力量训练", "瑜伽"),
            ),
            "water" to TypeSpec("去喝水", "喝水量 ml", quickValues = listOf(200, 250, 300, 500)),
            "waist" to TypeSpec("记体围", "腰围 cm", extraLabel = "臀围 cm，可空"),
            "glucose" to TypeSpec("记血糖", "血糖 mmol/L", presets = listOf("空腹", "餐前", "餐后2小时", "睡前")),
            "blood_pressure" to TypeSpec("记血压", "收缩压", extraLabel = "舒张压"),
            "habit" to TypeSpec("记习惯", "习惯名称", isHabit = true),
        )

        fun Factory(type: String, preset: String = "") = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                RecordViewModel(type, app.container.recordHealthLogService, app.container.recordMeasurementService)
            }
        }
    }
}
