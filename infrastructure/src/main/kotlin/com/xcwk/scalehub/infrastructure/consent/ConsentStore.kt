package com.xcwk.scalehub.infrastructure.consent

import android.content.Context

/**
 * 协议版本同意痕迹（DEC-S07）。设备级，不挂 memberId，不上云。
 * entered=true 且尚无版本时写入 legacy 哨兵；重大版本变更由 [needsReconsent] 判断。
 * 再同意 UI 不在本变更里。
 */
class ConsentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("scalehub_consent", Context.MODE_PRIVATE)

    fun privacyPolicyVersion(): String = prefs.getString(KEY_PRIVACY, "") ?: ""

    fun userAgreementVersion(): String = prefs.getString(KEY_AGREEMENT, "") ?: ""

    fun consentedAt(): Long = prefs.getLong(KEY_AT, 0L)

    fun hasRecord(): Boolean = prefs.contains(KEY_PRIVACY)

    fun record(privacyPolicyVersion: String, userAgreementVersion: String, consentedAt: Long) {
        prefs.edit()
            .putString(KEY_PRIVACY, privacyPolicyVersion)
            .putString(KEY_AGREEMENT, userAgreementVersion)
            .putLong(KEY_AT, consentedAt)
            .apply()
    }

    /** 已进入且没有版本键 → 记 legacy，不强制当场弹窗。 */
    fun seedLegacyIfEntered(entered: Boolean) {
        if (!entered || hasRecord()) return
        record(LEGACY, LEGACY, 0L)
    }

    fun needsReconsent(currentPrivacy: String, currentAgreement: String): Boolean {
        if (!hasRecord()) return true
        if (privacyPolicyVersion() == LEGACY || userAgreementVersion() == LEGACY) return true
        return privacyPolicyVersion() != currentPrivacy || userAgreementVersion() != currentAgreement
    }

    companion object {
        const val LEGACY = "legacy"
        private const val KEY_PRIVACY = "privacyPolicyVersion"
        private const val KEY_AGREEMENT = "userAgreementVersion"
        private const val KEY_AT = "consentedAt"
    }
}
