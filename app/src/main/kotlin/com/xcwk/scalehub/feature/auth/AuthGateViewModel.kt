package com.xcwk.scalehub.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.ui.legal.LegalDoc
import com.xcwk.scalehub.application.auth.AuthService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * auth — 游客门 ViewModel。规则随旧库 QingkeApp GuestGate 与 Session：
 * 未同意协议不得取验证码/登录/游客进入；发送成功 60 秒冷却；
 * 登录成功或游客进入即置 entered（AuthService 内部含登录后 pullThenPush）。
 */
class AuthGateViewModel(
    private val authService: AuthService,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(loading = false, entered = authService.isEntered()) }
    }

    fun onIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.AgreedChanged -> _state.update { it.copy(agreed = intent.agreed, message = null) }
            is AuthIntent.PhoneChanged -> _state.update { it.copy(phone = intent.value, message = null) }
            is AuthIntent.CodeChanged -> _state.update { it.copy(code = intent.value, message = null) }
            is AuthIntent.PasswordChanged -> _state.update { it.copy(password = intent.value, message = null) }
            is AuthIntent.EmailChanged -> _state.update { it.copy(email = intent.value, message = null) }
            is AuthIntent.LoginModeChanged -> _state.update { it.copy(loginMode = intent.mode, message = null) }
            is AuthIntent.OpenLegal -> _state.update { it.copy(legalDoc = intent.doc) }
            AuthIntent.CloseLegal -> _state.update { it.copy(legalDoc = LegalDoc.NONE) }
            AuthIntent.SendCode -> sendCode()
            AuthIntent.SignIn -> signIn()
            AuthIntent.EnterGuest -> enterGuest()
        }
    }

    private fun requireAgreed(): Boolean {
        if (!_state.value.agreed) {
            _state.update { it.copy(message = "请先阅读并同意协议") }
            return false
        }
        return true
    }

    private fun sendCode() {
        if (!requireAgreed()) return
        if (_state.value.countdown > 0) return
        viewModelScope.launch {
            _state.update { it.copy(sendingCode = true, message = null) }
            val result = authService.sendSmsCode(_state.value.phone)
            _state.update {
                it.copy(
                    sendingCode = false,
                    message = result.message,
                    verificationId = result.verificationId,
                    isUser = result.isUser,
                )
            }
            if (result.ok) startCountdown()
        }
    }

    private fun startCountdown() {
        viewModelScope.launch {
            for (seconds in 60 downTo 1) {
                _state.update { it.copy(countdown = seconds) }
                delay(1_000)
            }
            _state.update { it.copy(countdown = 0) }
        }
    }

    private fun signIn() {
        if (!requireAgreed()) return
        val s = _state.value
        when (s.loginMode) {
            LoginMode.PASSWORD -> { signInWithPassword(); return }
            LoginMode.EMAIL -> { signInWithEmail(); return }
            LoginMode.WECHAT -> { signInWithWeChat(); return }
            LoginMode.CODE -> {} // fall through to SMS code
        }
        viewModelScope.launch {
            _state.update { it.copy(signingIn = true, message = null) }
            val result = authService.signIn(s.phone, s.verificationId, s.isUser, s.code)
            _state.update {
                it.copy(
                    signingIn = false,
                    message = if (result.ok) null else result.message,
                    entered = result.ok || it.entered,
                )
            }
        }
    }

    private fun signInWithPassword() {
        viewModelScope.launch {
            _state.update { it.copy(signingIn = true, message = null) }
            val s = _state.value
            val result = authService.signInWithPassword(s.phone, s.password)
            _state.update {
                it.copy(
                    signingIn = false,
                    message = if (result.ok) null else result.message,
                    entered = result.ok || it.entered,
                )
            }
        }
    }

    private fun signInWithEmail() {
        viewModelScope.launch {
            _state.update { it.copy(signingIn = true, message = null) }
            val s = _state.value
            val result = authService.signInWithEmail(s.email, s.password)
            _state.update {
                it.copy(
                    signingIn = false,
                    message = if (result.ok) null else result.message,
                    entered = result.ok || it.entered,
                )
            }
        }
    }

    private fun signInWithWeChat() {
        viewModelScope.launch {
            _state.update { it.copy(signingIn = true, message = null) }
            val result = authService.signInWithWeChat()
            _state.update {
                it.copy(
                    signingIn = false,
                    message = if (result.ok) null else result.message,
                    entered = result.ok || it.entered,
                )
            }
        }
    }

    private fun enterGuest() {
        if (!requireAgreed()) return
        authService.enterAsGuest()
        _state.update { it.copy(entered = true, message = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                AuthGateViewModel(app.container.authService)
            }
        }
    }
}