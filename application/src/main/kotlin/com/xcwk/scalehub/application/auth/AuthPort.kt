package com.xcwk.scalehub.application.auth

/**
 * 认证端口（M2b）—— 旧库 Session.kt 语义切面：
 * 短信验证码流（send/verify+signin/signup）、注销验码删身份、应用进入态（游客/登录后）。
 * 凭据与 SP 持久化由实现持有；本层只传递语义结果与文案（旧库 authMessage 口径随实现）。
 */
data class SmsCodeResult(
    val ok: Boolean,
    val message: String,
    /** 空串=失败；非空为后续 verify 用。 */
    val verificationId: String,
    /** is_user：true=已注册走 signin，false=新号走 signup（旧库 finishSignIn:123-133）。 */
    val isUser: Boolean,
)

data class SignInResult(val ok: Boolean, val message: String)

interface AuthPort {
    /** 发送登录短信（实现内部做手机号规范化 +86 与错误映射，旧库 international/authMessage）。 */
    suspend fun sendSmsCode(rawDigits: String): SmsCodeResult

    /** 验码并登录/注册（registered 来自发送结果）。成功即持久化凭据并置正式用户。 */
    suspend fun signInWithCode(
        rawDigits: String,
        verificationId: String,
        registered: Boolean,
        code: String,
    ): SignInResult

    /** 注销：验码删云端身份；null=成功（SyncService 编排清表与登出）。 */
    suspend fun deleteCurrentUser(verifyCode: String): String?

    /** 进入态：首次启动的登录/游客门是否已通过（旧库 guestEntered 语义）。 */
    fun isEntered(): Boolean
    fun markEntered()

    /** 会话视图（设置页展示）。 */
    fun isFormalAccount(): Boolean
    fun phone(): String

    /** 退出登录：只清本机登录态（需求：不删本机测量、不删云端）。 */
    fun signOut()

    /** 向当前登录手机号发注销验证码；null=已发送（旧库 Session.sendDeleteCode :233-246）。 */
    suspend fun sendDeleteCode(): String?
}
