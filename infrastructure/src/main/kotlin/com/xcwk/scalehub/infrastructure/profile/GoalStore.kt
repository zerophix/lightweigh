package com.xcwk.scalehub.infrastructure.profile

import android.content.Context
import com.xcwk.scalehub.application.sync.PendingSyncPort
import com.xcwk.scalehub.domain.repository.GoalRepository
import com.xcwk.scalehub.infrastructure.persistent.dao.GoalDao
import com.xcwk.scalehub.infrastructure.persistent.po.GoalPo

/**
 * 目标存储。权威是 Room `goals`（DEC-S02）。
 * 首次打开把 scalehub_goals 的存量键导入 Room，之后 SP 只读、不再双写。
 * targetWeight 变更写入 pending_sync；云列未落地前 SyncService 不把它放进 profiles payload。
 */
class GoalStore(
    context: Context,
    private val activeMemberId: () -> String,
    private val dao: GoalDao,
    private val pending: PendingSyncPort? = null,
) : GoalRepository {

    private val prefs = context.applicationContext.getSharedPreferences("scalehub_goals", Context.MODE_PRIVATE)

    init {
        importFromSp()
    }

    override var targetWeight: Float
        get() = row().targetWeightKg.toFloat()
        set(value) {
            write(row().copy(targetWeightKg = value.toDouble()))
        }

    override var calorieGoal: Int
        get() = row().calorieGoal
        set(value) {
            write(row().copy(calorieGoal = value))
        }

    override var waterGoal: Int
        get() = row().waterGoal
        set(value) {
            write(row().copy(waterGoal = value))
        }

    fun clearAll() {
        dao.deleteAll()
        prefs.edit().clear().apply()
    }

    private fun write(next: GoalPo) {
        val stamped = next.copy(updatedAt = System.currentTimeMillis(), deletedAt = null)
        dao.upsert(stamped)
        pending?.enqueue("goals", stamped.memberId, "upsert")
    }

    private fun row(): GoalPo {
        val id = activeMemberId()
        dao.get(id)?.let { return it }
        val created = GoalPo(
            memberId = id,
            targetWeightKg = 0.0,
            calorieGoal = 2049,
            waterGoal = 2000,
            updatedAt = System.currentTimeMillis(),
            deletedAt = null,
        )
        dao.upsert(created)
        return created
    }

    private fun importFromSp() {
        val ids = prefs.all.keys.mapNotNull { key ->
            if (!key.startsWith("goal_")) return@mapNotNull null
            val rest = key.removePrefix("goal_")
            val split = rest.lastIndexOf('_')
            if (split <= 0) null else rest.substring(0, split)
        }.toSet()
        val now = System.currentTimeMillis()
        for (id in ids) {
            if (dao.get(id) != null) continue
            dao.upsert(
                GoalPo(
                    memberId = id,
                    targetWeightKg = prefs.getFloat("goal_${id}_targetWeight", 0f).toDouble(),
                    calorieGoal = prefs.getInt("goal_${id}_calorieGoal", 2049),
                    waterGoal = prefs.getInt("goal_${id}_waterGoal", 2000),
                    updatedAt = now,
                    deletedAt = null,
                ),
            )
        }
    }
}
