package com.xcwk.scalehub.infrastructure.profile

import android.content.Context
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MemberProfileWriter
import java.time.LocalDate

/**
 * 档案存储 — MemberProfileReader/Writer 双端口实现（SharedPreferences 轻存储）。
 * M4 我的切片关闭本文件原 TODO：
 *  - writer：save 完整合法档案（守卫在 MemberProfile 构造期）
 *  - 生日月日精度：ageYears = 周岁（月日齐备），缺失退化为年差（MemberProfile.ageFrom）
 *  - 昵称归位档案存储（旧库放 HealthLogStore 属历史错位，v2 归档案——DEC-022 升级声明）
 * ageYears 语义：出生年缺失返回 0（沿旧库守卫），算法对无效输入自会给出无效结果。
 */
class ProfileStore(context: Context) : MemberProfileReader, MemberProfileWriter {

    private val prefs = context.applicationContext.getSharedPreferences("scalehub_profile", Context.MODE_PRIVATE)

    override fun male(): Boolean = prefs.getBoolean(KEY_MALE, true)

    override fun ageYears(): Int =
        MemberProfile.ageFrom(birthYear(), birthMonth(), birthDay(), LocalDate.now())

    override fun heightCm(): Int = prefs.getInt(KEY_HEIGHT_CM, 0)

    override fun birthYear(): Int = prefs.getInt(KEY_BIRTH_YEAR, 0)

    override fun birthMonth(): Int = prefs.getInt(KEY_BIRTH_MONTH, 0)

    override fun birthDay(): Int = prefs.getInt(KEY_BIRTH_DAY, 0)

    override fun nickname(): String = prefs.getString(KEY_NICKNAME, "") ?: ""

    override suspend fun save(profile: MemberProfile) {
        prefs.edit()
            .putBoolean(KEY_MALE, profile.male)
            .putInt(KEY_BIRTH_YEAR, profile.birthYear)
            .putInt(KEY_BIRTH_MONTH, profile.birthMonth)
            .putInt(KEY_BIRTH_DAY, profile.birthDay)
            .putInt(KEY_HEIGHT_CM, profile.heightCm)
            .putString(KEY_NICKNAME, profile.nickname)
            .apply()
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
