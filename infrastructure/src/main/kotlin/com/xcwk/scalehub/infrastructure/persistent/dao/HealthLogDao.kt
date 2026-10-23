package com.xcwk.scalehub.infrastructure.persistent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xcwk.scalehub.infrastructure.persistent.po.HealthLogPo

@Dao
interface HealthLogDao {
    /** 唯一键 (memberId, loggedAt, type) 冲突覆盖。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(po: HealthLogPo)

    @Query(
        "SELECT * FROM health_logs WHERE memberId = :memberId AND deletedAt IS NULL " +
            "ORDER BY loggedAt DESC LIMIT :limit",
    )
    suspend fun recent(limit: Int, memberId: String): List<HealthLogPo>

    @Query(
        "SELECT * FROM health_logs WHERE memberId = :memberId AND deletedAt IS NULL " +
            "AND loggedAt >= :from AND loggedAt < :to ORDER BY loggedAt ASC",
    )
    suspend fun between(from: Long, to: Long, memberId: String): List<HealthLogPo>

    @Query(
        "UPDATE health_logs SET deletedAt = :now, updatedAt = :now " +
            "WHERE memberId = :memberId AND loggedAt = :time AND type = :type AND deletedAt IS NULL",
    )
    suspend fun markDeleted(memberId: String, time: Long, type: String, now: Long)
}
