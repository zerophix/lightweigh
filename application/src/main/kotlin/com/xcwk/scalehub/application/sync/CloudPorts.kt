package com.xcwk.scalehub.application.sync

/**
 * M2 同步端口族（实现属 infrastructure，随 M2b 接入 CloudBase）。
 * 同步语义的可测性建立在这三个端口上——SyncService 只依赖它们，
 * 假实现即可完整测试推/拉/合并/注销全流程（不需网络与部署）。
 */

/** 云端一行（字段已按云端 snake_case 契约命名，见 spec v2 与 NOTE-20261016-001）。 */
data class CloudRow(val fields: Map<String, Any?>)

interface CloudDataPort {
    /**
     * 拉取行：query 为 PostgREST 过滤语法原样透传（旧库 Gateway.rows 口径）。
     * 返回 null = 拉取失败（任一表失败即整体中止——旧库 restoreNow:185-196）。
     */
    suspend fun rows(table: String, query: String): List<CloudRow>?

    /**
     * 批量 upsert：conflict 为冲突键列（如 "owner_id,measured_at"）；
     * 分块（40）与 on_conflict=merge-duplicates 由实现层负责（旧库 Gateway.upsert）。
     */
    suspend fun upsert(table: String, conflict: String, rows: List<CloudRow>): Boolean

    /** 按过滤条件删除（注销清表）；false = 删除请求失败。 */
    suspend fun delete(table: String, filter: String): Boolean
}

/** 会话状态（token/主体/正式用户判定/登出）——旧库 Session.kt 的最小读写面。 */
interface CloudSessionPort {
    fun isFormalAccount(): Boolean
    fun accessToken(): String?
    /** 从 token 解出用户主体 id（旧库 Session.subject：JWT sub）。 */
    fun subject(token: String): String?
    fun signOut()
}
