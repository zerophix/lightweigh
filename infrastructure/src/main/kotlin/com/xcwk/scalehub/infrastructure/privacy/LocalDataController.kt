package com.xcwk.scalehub.infrastructure.privacy

import android.content.Context
import com.xcwk.scalehub.application.sync.LocalDataControlPort
import com.xcwk.scalehub.infrastructure.member.MemberStore
import com.xcwk.scalehub.infrastructure.persistent.AppDatabase
import com.xcwk.scalehub.infrastructure.profile.AvatarStore
import com.xcwk.scalehub.infrastructure.profile.GoalStore
import com.xcwk.scalehub.infrastructure.profile.LegacyUnitMigration
import com.xcwk.scalehub.infrastructure.profile.ProfileStore

/**
 * 本机清理实现（DEC-S05 / DEC-S11）。
 *
 * 硬删 Room 业务表（测量、日志、设备、习惯、目标、待同步、水位），
 * 成员重置为「我」，清档案 / 目标 SP / 头像 / 缓存 / unit_prefs。
 * 保留 session 与 scalehub_consent。本类没有云端口（清本机 PH-R4）。
 * 注销与清本机的差别只在调用方是否接着 signOut。
 */
class LocalDataController(
    private val context: Context,
    private val database: AppDatabase,
    private val profile: ProfileStore,
    private val goals: GoalStore,
    private val members: MemberStore,
    private val avatars: AvatarStore,
) : LocalDataControlPort {

    override suspend fun wipeAfterAccountDeletion() = wipeBusinessData()

    override suspend fun clearLocalKeepingSession() = wipeBusinessData()

    private fun wipeBusinessData() {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            listOf(
                "measurements",
                "health_logs",
                "devices",
                "habits",
                "goals",
                "pending_sync",
                "sync_state",
            ).forEach { table -> db.execSQL("DELETE FROM `$table`") }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        members.resetToDefault()
        profile.clearAll()
        goals.clearAll()
        avatars.clearAll()
        LegacyUnitMigration.clear(context)
        context.cacheDir.listFiles()?.forEach { file -> file.deleteRecursively() }
    }
}
