package com.xcwk.scalehub.feature.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.command.UpdateProfileCommand
import com.xcwk.scalehub.application.query.CurrentProfile
import com.xcwk.scalehub.application.query.ProfileQuery
import com.xcwk.scalehub.application.service.UpdateProfileService
import com.xcwk.scalehub.domain.types.DomainException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * mine — UDF 四件套之 ViewModel：读 ProfileQuery、存 UpdateProfileService，
 * 业务规则（量程/生日/昵称）全在 domain 构造守卫，这里只做文本解析与错误转述。
 * TODO M4 视觉轮：出生/身高滚轮选择器（旧库 Wheel 等价物），现以数字输入替代。
 */
class MineViewModel(
    private val profileQuery: ProfileQuery,
    private val updateProfile: UpdateProfileService,
) : ViewModel() {

    private val _state = MutableStateFlow(MineUiState())
    val state: StateFlow<MineUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onIntent(intent: MineIntent) {
        when (intent) {
            is MineIntent.MaleChanged -> _state.update { it.copy(male = intent.male, error = null) }
            is MineIntent.YearChanged -> _state.update { it.copy(birthYear = intent.value, error = null) }
            is MineIntent.MonthChanged -> _state.update { it.copy(birthMonth = intent.value, error = null) }
            is MineIntent.DayChanged -> _state.update { it.copy(birthDay = intent.value, error = null) }
            is MineIntent.HeightChanged -> _state.update { it.copy(heightCm = intent.value, error = null) }
            is MineIntent.NicknameChanged -> _state.update { it.copy(nickname = intent.value, error = null) }
            MineIntent.Save -> save()
        }
    }

    private suspend fun load() {
        val p = profileQuery.current()
        _state.update {
            it.copy(
                loading = false,
                male = p.male,
                birthYear = p.birthYear.takeIf { y -> y > 0 }?.toString() ?: "",
                birthMonth = p.birthMonth.takeIf { m -> m in 1..12 }?.toString() ?: "",
                birthDay = p.birthDay.takeIf { d -> d in 1..31 }?.toString() ?: "",
                heightCm = p.heightCm.takeIf { h -> h > 0 }?.toString() ?: "",
                nickname = p.nickname,
                summary = summaryOf(p),
                unsetHint = !p.heightSet,
            )
        }
    }

    private fun summaryOf(p: CurrentProfile): String =
        "${if (p.male) "男" else "女"} | " +
            (if (p.ageYears > 0) "${p.ageYears}岁" else "--岁") + " | " +
            (if (p.heightSet) "${p.heightCm}cm" else "--cm")

    private fun save() {
        val s = _state.value
        val year = s.birthYear.toIntOrNull()
        val month = s.birthMonth.toIntOrNull()
        val day = s.birthDay.toIntOrNull()
        val height = s.heightCm.toIntOrNull()
        if (year == null || month == null || day == null || height == null) {
            _state.update { it.copy(error = "出生日期与身高请输入数字") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, saved = false) }
            try {
                updateProfile.execute(
                    UpdateProfileCommand(s.male, year, month, day, height, s.nickname),
                )
                load()
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: DomainException) {
                _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                MineViewModel(app.container.profileQuery, app.container.updateProfileService)
            }
        }
    }
}
