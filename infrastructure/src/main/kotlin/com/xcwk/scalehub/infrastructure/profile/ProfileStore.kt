package com.xcwk.scalehub.infrastructure.profile

import android.content.Context
import com.xcwk.scalehub.domain.repository.MemberProfileReader

/**
 * 档案存储 — 实现 MemberProfileReader 读取端口（SharedPreferences 轻存储）。
 * ageYears 语义：出生年缺失返回 0（沿旧库守卫），算法对无效输入自会给出无效结果。
 * TODO: 档案编辑页落地时补 writer 与生日月日精度。
 */
class ProfileStore(context: Context) : MemberProfileReader {

    private val prefs = context.applicationContext.getSharedPreferences("scalehub_profile", Context.MODE_PRIVATE)

    override fun male(): Boolean = prefs.getBoolean(KEY_MALE, true)

    override fun ageYears(): Int {
        val birthYear = prefs.getInt(KEY_BIRTH_YEAR, 0)
        if (birthYear <= 0) return 0
        return java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - birthYear
    }

    override fun heightCm(): Int = prefs.getInt(KEY_HEIGHT_CM, 0)

    companion object {
        const val KEY_MALE = "male"
        const val KEY_BIRTH_YEAR = "birthYear"
        const val KEY_HEIGHT_CM = "heightCm"
    }
}
