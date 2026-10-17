package com.xcwk.scalehub.infrastructure.profile

import android.content.Context
import com.xcwk.scalehub.domain.repository.GoalRepository

/**
 * repository 实现 — 目标存储（旧库 HealthLogStore 目标段 :136-146，键按成员隔离）。
 */
class GoalStore(
    context: Context,
    private val activeMemberId: () -> String,
) : GoalRepository {

    private val prefs = context.applicationContext.getSharedPreferences("scalehub_goals", Context.MODE_PRIVATE)

    private fun key(base: String) = "goal_${activeMemberId()}_$base"

    override var targetWeight: Float
        get() = prefs.getFloat(key("targetWeight"), 0f)
        set(value) { prefs.edit().putFloat(key("targetWeight"), value).apply() }

    override var calorieGoal: Int
        get() = prefs.getInt(key("calorieGoal"), 2049)
        set(value) { prefs.edit().putInt(key("calorieGoal"), value).apply() }

    override var waterGoal: Int
        get() = prefs.getInt(key("waterGoal"), 2000)
        set(value) { prefs.edit().putInt(key("waterGoal"), value).apply() }
}
