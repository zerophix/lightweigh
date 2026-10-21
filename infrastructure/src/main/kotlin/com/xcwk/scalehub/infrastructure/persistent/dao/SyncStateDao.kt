package com.xcwk.scalehub.infrastructure.persistent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xcwk.scalehub.infrastructure.persistent.po.SyncStatePo

/**
 * dao — 同步状态数据访问（M2b 水位线）。
 *
 * 关键约束：
 * - `get` / `upsert` 必须在同一个数据库事务中调用（Room @Transaction），
 *   保证 watermark 读取与更新的原子性。
 * - `upsert` 使用 REPLACE 策略——并发触发时后写覆盖前写，但 SyncService
 *   内部已用 Mutex 串行，不会并发调用。
 */
@Dao
interface SyncStateDao {

    /**
     * 读取指定表 + 成员的同步状态。不存在时返回 null（调用方需初始化为 0）。
     */
    @Query("SELECT * FROM sync_state WHERE tableName = :tableName AND memberId = :memberId")
    suspend fun get(tableName: String, memberId: String): SyncStatePo?

    /**
     * 写入/覆盖同步状态（REPLACE 语义）。
     * watermark 更新顺序：先推/拉成功，再调用本方法写入新值。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(po: SyncStatePo)

    /**
     * 注销时清除某成员的全部同步状态（水位线重置）。
     */
    @Query("DELETE FROM sync_state WHERE memberId = :memberId")
    suspend fun clearFor(memberId: String)

    /**
     * 注销时清除全部同步状态（云端清表后，所有成员水位线归零）。
     */
    @Query("DELETE FROM sync_state")
    suspend fun clearAll()
}
