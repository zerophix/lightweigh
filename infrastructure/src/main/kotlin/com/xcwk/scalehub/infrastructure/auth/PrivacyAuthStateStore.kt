package com.xcwk.scalehub.infrastructure.auth

import android.content.Context
import com.xcwk.scalehub.application.auth.PrivacyAuthStatePort

/**
 * 隐私授权状态实现（SharedPreferences）。
 *
 * 存储键：privacy_agreed / privacy_version / privacy_time
 */
class PrivacyAuthStateStore(context: Context) : PrivacyAuthStatePort {

    private val prefs = context.applicationContext
        .getSharedPreferences("scalehub_privacy", Context.MODE_PRIVATE)

    override fun isAgreed(): Boolean = prefs.getBoolean(KEY_AGREED, false)

    override fun saveAgreed() {
        prefs.edit()
            .putBoolean(KEY_AGREED, true)
            .putString(KEY_VERSION, CURRENT_VERSION)
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
    }

    override fun revokeAgreed() {
        prefs.edit()
            .putBoolean(KEY_AGREED, false)
            .putString(KEY_VERSION, "")
            .putLong(KEY_TIME, 0L)
            .apply()
    }

    override fun agreedVersion(): String? = prefs.getString(KEY_VERSION, null)

    override fun agreedTime(): Long = prefs.getLong(KEY_TIME, 0L)

    companion object {
        private const val KEY_AGREED = "privacy_agreed"
        private const val KEY_VERSION = "privacy_version"
        private const val KEY_TIME = "privacy_time"
        const val CURRENT_VERSION = "v2.1"
    }
}
