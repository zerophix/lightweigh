package com.xcwk.scalehub.feature.auth

import com.xcwk.scalehub.ui.legal.LegalDoc

/** auth — 游客门四件套之 UiState（需求 3.1：先同意后进入，游客可直入）。 */
data class AuthUiState(
    val loading: Boolean = true,
    val entered: Boolean = false,
    val agreed: Boolean = false,
    /** 未同意协议就点了取码/登录/游客：登录门据此切到隐私锁页（原型 v-privacy-lock）。 */
    val privacyBlocked: Boolean = false,
    val phone: String = "",
    val code: String = "",
    /** 验证码登录的本机失败次数：> 0 时登录门切到验证码错误页（原型 v-codeerr）。 */
    val codeAttempts: Int = 0,
    val password: String = "",
    val email: String = "",
    /** 登录方式：code=验证码 / password=密码（T-044/DEC-043-G）。 */
    val loginMode: LoginMode = LoginMode.CODE,
    val verificationId: String = "",
    val isUser: Boolean = false,
    /** 发送后倒计时秒数（0=可再发）。 */
    val countdown: Int = 0,
    val sendingCode: Boolean = false,
    val signingIn: Boolean = false,
    val message: String? = null,
    val legalDoc: LegalDoc = LegalDoc.NONE,
)

enum class LoginMode { CODE, PASSWORD, EMAIL, WECHAT }

sealed interface AuthIntent {
    data class AgreedChanged(val agreed: Boolean) : AuthIntent
    data class PhoneChanged(val value: String) : AuthIntent
    data class CodeChanged(val value: String) : AuthIntent
    data class PasswordChanged(val value: String) : AuthIntent
    data class EmailChanged(val value: String) : AuthIntent
    data class LoginModeChanged(val mode: LoginMode) : AuthIntent
    data object SendCode : AuthIntent
    data object SignIn : AuthIntent
    data object EnterGuest : AuthIntent
    /** 关闭边界页（隐私锁 / 验证码错误）并清掉对应标记。 */
    data object DismissBoundary : AuthIntent
    data class OpenLegal(val doc: LegalDoc) : AuthIntent
    data object CloseLegal : AuthIntent
}