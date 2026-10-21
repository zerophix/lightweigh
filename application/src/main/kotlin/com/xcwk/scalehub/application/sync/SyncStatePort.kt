package com.xcwk.scalehub.application.sync

/**
 * 同步状态端口（M2b 水位线/对账/幂等）。
 *
 * 语义承诺：
 * - `get` 返回的 watermark 是**已成功推/拉的最大时间戳**（0 = 尚未同步）
 * - `updatePushed` / `updatePulled` 必须在推/拉**成功后**调用（顺序保证：
 *   先推/拉，成功后更新 watermark。若在更新前崩溃，重启后会重推/重拉，
 *   但因 PostgREST `on_conflict=merge-duplicates` 幂等语义，结果等价）
 * - `clearFor` / `clearAll` 在注销时调用，水位线归零
 *
 * 实现方：RoomSyncStateRepository(生产，用 SyncStateDao) / InMemorySyncStateRepository(测试)。
 */
interface SyncStatePort {

    /**
     * 读取指定表 + 成员的同步状态。
     * @return watermark 对（pushed, pulled），未初始化时返回 (0L, 0L)
     */
    suspend fun get(tableName: String, memberId: String): Pair<Long, Long>

    /**
     * 更新推送水位线（push 成功后调用）。
     * @param watermark 本次成功推送的最大时间戳
     */
    suspend fun updatePushed(tableName: String, memberId: String, watermark: Long)

    /**
     * 更新拉取水位线（pull 成功后调用）。
     * @param watermark 本次成功拉取的最大时间戳
     */
    suspend fun updatePulled(tableName: String, memberId: String, watermark: Long)

    /**
     * 清除指定成员的全部同步状态（注销时调用）。
     */
    suspend fun clearFor(memberId: String)

    /**
     * 清除全部同步状态（云端清表后，所有成员水位线归零）。
     */
    suspend fun clearAll()
}
