package com.xcwk.scalehub.infrastructure.profile

import android.content.Context
import com.xcwk.scalehub.domain.model.LengthUnit
import com.xcwk.scalehub.domain.model.UserUnitPreferences
import com.xcwk.scalehub.domain.model.WeightUnit

/**
 * unit_prefs（ordinal）→ ProfileStore（DEC-S08）。只在档案单位键还不存在时拷一次。
 * 清本机会清掉 unit_prefs，避免清完又被迁回。
 */
object LegacyUnitMigration {
    private const val PREFS = "unit_prefs"
    private const val FLAG = "migrated_to_profile"

    fun migrate(context: Context, profile: ProfileStore) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(FLAG, false)) return
        if (profile.hasUnitKeys()) {
            prefs.edit().putBoolean(FLAG, true).apply()
            return
        }
        if (!prefs.contains("weight") && !prefs.contains("length")) {
            prefs.edit().putBoolean(FLAG, true).apply()
            return
        }
        val weight = WeightUnit.entries.getOrElse(prefs.getInt("weight", 0)) { WeightUnit.KG }
        val length = LengthUnit.entries.getOrElse(prefs.getInt("length", 0)) { LengthUnit.CM }
        profile.writeUnits(
            UserUnitPreferences(
                weightUnit = weight,
                lengthUnit = length,
                applyToDerived = profile.unitPreferences().applyToDerived,
            ),
        )
        prefs.edit().putBoolean(FLAG, true).apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
