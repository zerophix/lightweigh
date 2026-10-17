package com.xcwk.scalehub.infrastructure.profile

import android.content.Context
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MemberProfileWriter
import java.time.LocalDate

/**
 * 档案存储 — MemberProfileReader/Writer 双端口实现（SharedPreferences 轻存储）。
 * 多成员（移植补齐）：键按当前成员隔离 "profile_{id}_{base}"；
 * member "1" 读回退旧单键（多成员升级前的存量数据语义），写一律走成员键。
 */
class ProfileStore(
    context: Context,
    private val activeMemberId: () -> String,
) : MemberProfileReader, MemberProfileWriter {

    private val prefs = context.applicationContext.getSharedPreferences("scalehub_profile", Context.MODE_PRIVATE)

    override fun male(): Boolean = readBool(KEY_MALE, true)

    override fun ageYears(): Int =
        MemberProfile.ageFrom(birthYear(), birthMonth(), birthDay(), LocalDate.now())

    override fun heightCm(): Int = readInt(KEY_HEIGHT_CM, 0)

    override fun birthYear(): Int = readInt(KEY_BIRTH_YEAR, 0)

    override fun birthMonth(): Int = readInt(KEY_BIRTH_MONTH, 0)

    override fun birthDay(): Int = readInt(KEY_BIRTH_DAY, 0)

    override fun nickname(): String = prefs.getString(key(KEY_NICKNAME), prefs.getString(KEY_NICKNAME, "")) ?: ""

    override suspend fun save(profile: MemberProfile) {
        prefs.edit()
            .putBoolean(key(KEY_MALE), profile.male)
            .putInt(key(KEY_BIRTH_YEAR), profile.birthYear)
            .putInt(key(KEY_BIRTH_MONTH), profile.birthMonth)
            .putInt(key(KEY_BIRTH_DAY), profile.birthDay)
            .putInt(key(KEY_HEIGHT_CM), profile.heightCm)
            .putString(key(KEY_NICKNAME), profile.nickname)
            .apply()
    }

    /** 删除某成员的全部档案键（成员移除联动）。 */
    fun clearMember(id: String) {
        val editor = prefs.edit()
        listOf(KEY_MALE, KEY_BIRTH_YEAR, KEY_BIRTH_MONTH, KEY_BIRTH_DAY, KEY_HEIGHT_CM, KEY_NICKNAME)
            .forEach { editor.remove("profile_${id}_$it") }
        editor.apply()
    }

    private fun key(base: String) = "profile_${activeMemberId()}_$base"

    private fun readInt(base: String, fallback: Int): Int {
        val scoped = prefs.getInt(key(base), Int.MIN_VALUE)
        if (scoped != Int.MIN_VALUE) return scoped
        // member 1 回退旧单键（升级前存量数据）
        return if (activeMemberId() == "1") prefs.getInt(base, fallback) else fallback
    }

    private fun readBool(base: String, fallback: Boolean): Boolean {
        if (prefs.contains(key(base))) return prefs.getBoolean(key(base), fallback)
        return if (activeMemberId() == "1") prefs.getBoolean(base, fallback) else fallback
    }

    companion object {
        const val KEY_MALE = "male"
        const val KEY_BIRTH_YEAR = "birthYear"
        const val KEY_BIRTH_MONTH = "birthMonth"
        const val KEY_BIRTH_DAY = "birthDay"
        const val KEY_HEIGHT_CM = "heightCm"
        const val KEY_NICKNAME = "nickname"
    }
}
