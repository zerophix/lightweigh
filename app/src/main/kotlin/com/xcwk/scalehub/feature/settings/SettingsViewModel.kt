package com.xcwk.scalehub.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.auth.AuthPort
import com.xcwk.scalehub.application.auth.AuthService
import com.xcwk.scalehub.application.sync.SyncService
import com.xcwk.scalehub.ui.legal.LegalDoc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * settings — ViewModel：账号三流程（恢复/退出/注销）编排，
 * 语义随旧库设置页与 CloudSync（恢复=拉取合并+回推并刷数据信号；退出只清登录态；
 * 注销=发码→确认→SyncService 三步，本机保留）。
 */
class SettingsViewModel(
    private val authService: AuthService,
    private val syncService: SyncService,
    private val auth: AuthPort,
    private val refreshBus: DataRefreshBus,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(loading = false, formal = auth.isFormalAccount(), phone = auth.phone())
        }
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.Restore -> restore()
            SettingsIntent.SignOutAsk -> _state.update { it.copy(showSignOutConfirm = true) }
            SettingsIntent.SignOutCancel -> _state.update { it.copy(showSignOutConfirm = false) }
            SettingsIntent.SignOutConfirm -> {
                authService.signOut()
                _state.update { it.copy(showSignOutConfirm = false, formal = false, message = "已退出登录，本机记录保留") }
            }
            SettingsIntent.DeleteAsk -> _state.update { it.copy(showDeleteConfirm = true, deleteCode = "", deleteCodeSent = false, message = null) }
            SettingsIntent.DeleteCancel -> _state.update { it.copy(showDeleteConfirm = false) }
            is SettingsIntent.DeleteCodeChanged -> _state.update { it.copy(deleteCode = intent.value) }
            SettingsIntent.DeleteSendCode -> sendDeleteCode()
            SettingsIntent.DeleteConfirm -> deleteAccount()
            is SettingsIntent.OpenLegal -> _state.update { it.copy(legalDoc = intent.doc) }
            SettingsIntent.CloseLegal -> _state.update { it.copy(legalDoc = LegalDoc.NONE) }
        }
    }

    private fun restore() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val outcome = syncService.restore()
            if (outcome.ok) refreshBus.notifyChanged()   // 恢复落库后广播刷新（首页/趋势/历史重读）
            _state.update { it.copy(busy = false, message = outcome.message) }
        }
    }

    private fun sendDeleteCode() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val error = authService.sendDeleteCode()
            _state.update {
                it.copy(
                    busy = false,
                    deleteCodeSent = error == null,
                    message = error ?: "注销验证码已发送",
                )
            }
        }
    }

    private fun deleteAccount() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val outcome = syncService.deleteAccount(_state.value.deleteCode)
            if (outcome.ok) {
                refreshBus.notifyChanged()
                _state.update {
                    it.copy(busy = false, showDeleteConfirm = false, formal = false, message = outcome.message)
                }
            } else {
                _state.update { it.copy(busy = false, message = outcome.message) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                SettingsViewModel(
                    app.container.authService,
                    app.container.syncService,
                    app.container.sessionStore,
                    app.container.dataRefreshBus,
                )
            }
        }
    }
}
