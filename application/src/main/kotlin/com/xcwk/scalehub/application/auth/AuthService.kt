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
}
