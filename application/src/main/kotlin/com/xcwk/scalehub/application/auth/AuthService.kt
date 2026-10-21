package com.xcwk.scalehub.application.auth

import com.xcwk.scalehub.application.sync.SyncService

/**
 * application service — 登录/游客门编排。
 * 语义随旧库：登录成功即进入（markEntered）并自动 pullThenPush（finishSignIn:142，
 * 失败不阻断进入——云端不可用时本机照常可用）；游客只置进入态。
 */
class AuthService(
    private val auth: AuthPort,
    private val sync: SyncService? = null,
) {

    fun isEntered(): Boolean = auth.isEntered()

    fun enterAsGuest() {
        auth.markEntered()
    }

    /** 退出登录（本机测量与云端数据保留——需求口径）。 */
    fun signOut() {
        auth.signOut()
    }

    /** 注销验证码（仅正式用户）。 */
    suspend fun sendDeleteCode(): String? = auth.sendDeleteCode()

    suspend fun sendSmsCode(phone: String): SmsCodeResult = auth.sendSmsCode(phone)

    suspend fun signIn(
        phone: String,
        verificationId: String,
        registered: Boolean,
        code: String,
    ): SignInResult {
        val result = auth.signInWithCode(phone, verificationId, registered, code)
        if (result.ok) {
            auth.markEntered()
            runCatching { sync?.restore() }
        }
        return result
    }

    /** 密码登录（T-044/DEC-043-G）。 */
    suspend fun signInWithPassword(phone: String, password: String): SignInResult {
        val result = auth.signInWithPassword(phone, password)
        if (result.ok) {
            auth.markEntered()
            runCatching { sync?.restore() }
        }
        return result
    }

    /** 邮箱登录。 */
    suspend fun signInWithEmail(email: String, password: String): SignInResult {
        val result = auth.signInWithEmail(email, password)
        if (result.ok) {
            auth.markEntered()
            runCatching { sync?.restore() }
        }
        return result
    }

    /** 微信登录。 */
    suspend fun signInWithWeChat(): SignInResult {
        val result = auth.signInWithWeChat()
        if (result.ok) {
            auth.markEntered()
            runCatching { sync?.restore() }
        }
        return result
    }
}
