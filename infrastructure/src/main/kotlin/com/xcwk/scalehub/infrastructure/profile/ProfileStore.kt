package com.xcwk.scalehub.infrastructure.profile

import android.content.Context
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.model.UserUnitPreferences
import com.xcwk.scalehub.domain.model.WeightUnit
import com.xcwk.scalehub.domain.model.LengthUnit
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

    override fun unitPreferences(): UserUnitPreferences {
        val weightUnitStr = prefs.getString(key(KEY_WEIGHT_UNIT), "KG") ?: "KG"
        val lengthUnitStr = prefs.getString(key(KEY_LENGTH_UNIT), "CM") ?: "CM"
        val applyToDerived = prefs.getBoolean(key(KEY_APPLY_TO_DERIVED), true)
        return UserUnitPreferences(
            weightUnit = WeightUnit.valueOf(weightUnitStr),
            lengthUnit = LengthUnit.valueOf(lengthUnitStr),
            applyToDerived = applyToDerived
        )
    }

    override suspend fun saveUnitPreferences(prefs: UserUnitPreferences) {
        this.prefs.edit()
            .putString(key(KEY_WEIGHT_UNIT), prefs.weightUnit.name)
            .putString(key(KEY_LENGTH_UNIT), prefs.lengthUnit.name)
            .putBoolean(key(KEY_APPLY_TO_DERIVED), prefs.applyToDerived)
            .apply()
    }

    /**
     * 删除某成员的全部档案键（成员移除联动）。
     * 遍历 [ALL_KEYS] 单一事实源，不再就地枚举：新增档案字段只需在 companion 登记一次，
     * 漏登记由 ProfileStoreKeyTest 反射断言拦截（原实现就地列 6 个键，加键即留孤儿数据）。
     */
    fun clearMember(id: String) {
        val editor = prefs.edit()
        ALL_KEYS.forEach { editor.remove("profile_${id}_$it") }
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
        const val KEY_WEIGHT_UNIT = "weightUnit"
        const val KEY_LENGTH_UNIT = "lengthUnit"
        const val KEY_APPLY_TO_DERIVED = "applyToDerived"

        /**
         * 档案键全集（单一事实源）：clearMember 遍历此表；读写各经对应常量。
         * 新增档案字段只需在此登记一次 —— ProfileStoreKeyTest 反射断言
         * 「KEY_ 常量集 == ALL_KEYS」，漏登记即测试失败（原实现由 clearMember 就地列键，
         * 加键而忘登记只会静默留孤儿数据，无任何信号）。
         */
        internal val ALL_KEYS = listOf(
            KEY_MALE, KEY_BIRTH_YEAR, KEY_BIRTH_MONTH, KEY_BIRTH_DAY, KEY_HEIGHT_CM, KEY_NICKNAME,
            KEY_WEIGHT_UNIT, KEY_LENGTH_UNIT, KEY_APPLY_TO_DERIVED
        )
    }
}
