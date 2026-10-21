package com.xcwk.scalehub.feature.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.command.UpdateProfileCommand
import com.xcwk.scalehub.application.query.CurrentProfile
import com.xcwk.scalehub.application.query.HomeGoalQuery
import com.xcwk.scalehub.application.query.ProfileQuery
import com.xcwk.scalehub.application.service.UpdateProfileService
import com.xcwk.scalehub.domain.types.DomainException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * mine — UDF 四件套之 ViewModel：读 ProfileQuery、存 UpdateProfileService，
 * 业务规则（量程/生日/昵称）全在 domain 构造守卫，这里只做文本解析与错误转述。
 * M4 视觉轮已落：出生/身高滚轮（QkWheelPicker）与头像（QkAvatar + 相册选图）。
 * 健康数据概览卡复用 HomeGoalQuery（首页体重卡同一事实源，DEC-026 组 I）。
 */
class MineViewModel(
    private val profileQuery: ProfileQuery,
    private val updateProfile: UpdateProfileService,
    private val saveAvatar: (String) -> String? = { null },
    private val avatarPath: () -> String = { "" },
    private val homeGoalQuery: HomeGoalQuery? = null,
    private val refreshBus: DataRefreshBus? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(MineUiState())
    val state: StateFlow<MineUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
        // 跨屏保存/删除等数据变更后自动重读（与 Measure/Trend VM 同口径，DEC-021 支柱2）。
        // 本页读 ProfileQuery + HomeGoalQuery：档案 + 测量 + 健康记录
        refreshBus?.changed
            ?.filter { it in MINE_RELEVANT }
            ?.let { signal ->
                viewModelScope.launch { signal.collect { load() } }
            }
    }

    fun onIntent(intent: MineIntent) {
        when (intent) {
            is MineIntent.MaleChanged -> _state.update { it.copy(male = intent.male, error = null) }
            is MineIntent.YearChanged -> _state.update { it.copy(birthYear = intent.value, error = null) }
            is MineIntent.MonthChanged -> _state.update { it.copy(birthMonth = intent.value, error = null) }
            is MineIntent.DayChanged -> _state.update { it.copy(birthDay = intent.value, error = null) }
            is MineIntent.HeightChanged -> _state.update { it.copy(heightCm = intent.value, error = null) }
            is MineIntent.NicknameChanged -> _state.update { it.copy(nickname = intent.value, error = null) }
            is MineIntent.BirthPicked -> _state.update {
                it.copy(
                    birthYear = intent.year.toString(),
                    birthMonth = intent.month.toString(),
                    birthDay = intent.day.toString(),
                    error = null,
                )
            }
            is MineIntent.HeightPicked -> _state.update { it.copy(heightCm = intent.cm.toString(), error = null) }
            is MineIntent.AvatarChosen -> {
                val saved = saveAvatar(intent.uriString)
                if (saved != null) {
                    _state.update { it.copy(avatarPath = saved) }
                } else {
                    _state.update { it.copy(error = "头像保存失败，请换一张图片") }
                }
            }
            MineIntent.Save -> save()
        }
    }

    private suspend fun load() {
        val p = profileQuery.current()
        val home = homeGoalQuery?.state()
        _state.update {
            it.copy(
                loading = false,
                male = p.male,
                birthYear = p.birthYear.takeIf { y -> y > 0 }?.toString() ?: "",
                birthMonth = p.birthMonth.takeIf { m -> m in 1..12 }?.toString() ?: "",
                birthDay = p.birthDay.takeIf { d -> d in 1..31 }?.toString() ?: "",
                heightCm = p.heightCm.takeIf { h -> h > 0 }?.toString() ?: "",
                nickname = p.nickname,
                avatarPath = avatarPath(),
                summary = summaryOf(p),
                unsetHint = !p.heightSet,
                overviewWeight = home?.latestWeight?.let { w -> one(w) } ?: "--",
                overviewFat = if (home != null && home.latestFatShown) one(home.latestFat) else "--",
                overviewBmi = home?.bmi?.let { b -> one(b) } ?: "--",
                hasOverview = home?.latestWeight != null,
            )
        }
    }

    private fun one(value: Double): String = "%.1f".format(Locale.US, value)

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
        /** 本页关心的数据变更：档案 + 首页进度（测量/健康记录/目标/档案）。 */
        private val MINE_RELEVANT = setOf(
            DataChange.Measurements, DataChange.HealthLogs,
            DataChange.Profile, DataChange.MemberScopeChanged,
        )

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                MineViewModel(
                    app.container.profileQuery,
                    app.container.updateProfileService,
                    saveAvatar = app.container.saveAvatar,
                    avatarPath = app.container.avatarPath,
                    homeGoalQuery = app.container.homeGoalQuery,
                    refreshBus = app.container.dataRefreshBus,
                )
            }
        }
    }
}
