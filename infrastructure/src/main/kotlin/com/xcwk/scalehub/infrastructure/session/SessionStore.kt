package com.xcwk.scalehub.infrastructure.session

import android.content.Context
import android.util.Base64
import com.xcwk.scalehub.application.auth.AuthPort
import com.xcwk.scalehub.application.auth.SignInResult
import com.xcwk.scalehub.application.auth.SmsCodeResult
import com.xcwk.scalehub.application.sync.CloudSessionPort
import com.xcwk.scalehub.infrastructure.config.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 会话存储与认证实现（旧库 Session.kt 端点/报文逐位移植）。
 * SP 键沿旧库语义（access/refresh/expires/phone/formalAccount + entered 进入态）。
 * token 刷新：过期前 60s 视为失效并走 refresh_token（旧库 :175-202）。
 */
class SessionStore(context: Context) : CloudSessionPort, AuthPort {

    private val prefs = context.applicationContext
        .getSharedPreferences("scalehub_session", Context.MODE_PRIVATE)

    override fun isFormalAccount(): Boolean = prefs.getBoolean(KEY_FORMAL, false)

    override fun accessToken(): String? {
        val access = prefs.getString(KEY_ACCESS, null)
        val expires = prefs.getLong(KEY_EXPIRES, 0L)
        if (!access.isNullOrEmpty() && System.currentTimeMillis() < expires - 60_000L) {
            return access
        }
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return refreshSession(refresh)
    }

    override fun subject(token: String): String? = subjectOf(token)

    override fun signOut() {
        prefs.edit()
            .remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES).remove(KEY_PHONE)
            .putBoolean(KEY_FORMAL, false)
            .apply()
    }

    override fun phone(): String = prefs.getString(KEY_PHONE, "") ?: ""

    override fun isEntered(): Boolean = prefs.getBoolean(KEY_ENTERED, false)

    override fun markEntered() {
        prefs.edit().putBoolean(KEY_ENTERED, true).apply()
    }

    override suspend fun sendSmsCode(rawDigits: String): SmsCodeResult = withContext(Dispatchers.IO) {
        val phone = international(rawDigits)
            ?: return@withContext SmsCodeResult(false, "请输入正确的手机号", "", false)
        val result = request(
            "POST", "${BackendConfig.BASE}/auth/v1/verification", null,
            JSONObject().put("phone_number", phone).put("target", "ANY").toString(),
        ) ?: return@withContext SmsCodeResult(false, "当前网络不可用，请联网后再获取验证码", "", false)
        if (result.first !in 200..299) {
            return@withContext SmsCodeResult(false, authMessage(result.second, "验证码发送失败，请稍后重试"), "", false)
        }
        val json = JSONObject(result.second)
        val verificationId = json.optString("verification_id")
        if (verificationId.isEmpty()) {
            return@withContext SmsCodeResult(false, "验证码发送失败，请稍后重试", "", false)
        }
        SmsCodeResult(true, "验证码已发送", verificationId, json.optBoolean("is_user", false))
    }

    override suspend fun signInWithCode(
        rawDigits: String,
        verificationId: String,
        registered: Boolean,
        code: String,
    ): SignInResult = withContext(Dispatchers.IO) {
        val phone = international(rawDigits)
            ?: return@withContext SignInResult(false, "请输入正确的手机号")
        if (verificationId.isEmpty()) return@withContext SignInResult(false, "请先获取验证码")
        if (!code.matches(Regex("\\d{6}"))) return@withContext SignInResult(false, "请输入 6 位验证码")
        val verified = request(
            "POST", "${BackendConfig.BASE}/auth/v1/verification/verify", null,
            JSONObject().put("verification_id", verificationId).put("verification_code", code).toString(),
        ) ?: return@withContext SignInResult(false, "当前网络不可用，请联网后再登录")
        if (verified.first !in 200..299) {
            return@withContext SignInResult(false, authMessage(verified.second, "验证码不正确"))
        }
        val vToken = JSONObject(verified.second).optString("verification_token")
        if (vToken.isEmpty()) return@withContext SignInResult(false, "验证码不正确")
        // 已注册走 signin，新号走 signup（旧库 finishSignIn:123-133）
        val session = if (registered) {
            request("POST", "${BackendConfig.BASE}/auth/v1/signin", null,
                JSONObject().put("verification_token", vToken).toString())
        } else {
            request("POST", "${BackendConfig.BASE}/auth/v1/signup", null,
                JSONObject().put("phone_number", phone).put("verification_token", vToken).toString())
        } ?: return@withContext SignInResult(false, "当前网络不可用，请联网后再登录")
        if (session.first !in 200..299) {
            return@withContext SignInResult(false, authMessage(session.second, "登录失败，请稍后重试"))
        }
        if (storeSession(session.second) == null) {
            return@withContext SignInResult(false, "登录失败，请稍后重试")
        }
        prefs.edit().putBoolean(KEY_FORMAL, true).putString(KEY_PHONE, rawDigits).apply()
        SignInResult(true, "登录成功")
    }

    override suspend fun sendDeleteCode(): String? = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext "登录已失效，请重新登录"
        val result = request(
            "POST", "${BackendConfig.BASE}/auth/v1/user/reauthenticate", token,
            JSONObject().put("verify_opt", "phone_code").toString(),
        ) ?: return@withContext "当前网络不可用，请联网后再试"
        if (result.first !in 200..299) {
            return@withContext authMessage(result.second, "验证码发送失败，请稍后再试")
        }
        null
    }

    override suspend fun deleteCurrentUser(verifyCode: String): String? = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext "登录已失效，请重新登录"
        val result = request("DELETE", "${BackendConfig.BASE}/auth/v1/user/me?verify_code=$verifyCode", token, null)
            ?: return@withContext "当前网络不可用，请联网后再试"
        if (result.first !in 200..299) {
            return@withContext authMessage(result.second, "验证码不正确或注销失败，账号还在，请重试")
        }
        null
    }

    // ---- 内部 ----

    private fun refreshSession(refresh: String): String? {
        val result = request(
            "POST", "${BackendConfig.BASE}/auth/v1/token?grant_type=refresh_token", null,
            JSONObject().put("refresh_token", refresh).toString(),
        ) ?: return null
        if (result.first !in 200..299) return null
        return storeSession(result.second)
    }

    private fun storeSession(body: String): String? {
        val json = JSONObject(body)
        val access = json.optString("access_token")
        if (access.isEmpty()) return null
        val refresh = json.optString("refresh_token")
        val expiresIn = json.optLong("expires_in", 7200L)
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putLong(KEY_EXPIRES, System.currentTimeMillis() + expiresIn * 1000L)
            .apply()
        return access
    }

    /** 原始 HTTP（调用方均在 IO 上下文）。@return (code, body)；null=网络异常。 */
    private fun request(method: String, url: String, token: String?, body: String?): Pair<Int, String>? =
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = BackendConfig.CONNECT_TIMEOUT_MS.toInt()
            conn.readTimeout = BackendConfig.READ_TIMEOUT_MS.toInt()
            conn.setRequestProperty("Content-Type", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            code to text
        }.getOrNull()

    companion object {
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_EXPIRES = "expires"
        private const val KEY_PHONE = "phone"
        private const val KEY_FORMAL = "formalAccount"
        private const val KEY_ENTERED = "entered"

        /** 旧库 international（:157-163）：1[3-9]\d{9} → "+86 xxx"。 */
        internal fun international(digits: String): String? {
            val compact = digits.filter { it.isDigit() }
            if (!compact.matches(Regex("1[3-9]\\d{9}"))) return null
            return "+86 $compact"
        }

        /** 旧库 subject（:220-232）：JWT payload base64url 解 sub。 */
        internal fun subjectOf(token: String): String? {
            val parts = token.split('.')
            if (parts.size < 2) return null
            var payload = parts[1].replace('-', '+').replace('_', '/')
            while (payload.length % 4 != 0) payload += "="
            val json = runCatching {
                String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull() ?: return null
            val sub = JSONObject(json).optString("sub")
            return sub.ifEmpty { null }
        }

        /** 旧库 authMessage（:165-174）。 */
        internal fun authMessage(body: String, fallback: String): String {
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return fallback
            return when (json.optString("error")) {
                "rate_limit_exceeded" -> "验证码发送太频繁，请 60 秒后再试"
                "invalid_phone_number" -> "请输入正确的手机号"
                "captcha_required" -> "发送过于频繁，请稍后再试"
                "invalid_verification_code" -> "验证码不正确"
                else -> fallback
            }
        }
    }
}
