package com.xcwk.scalehub.infrastructure.persistent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xcwk.scalehub.infrastructure.persistent.po.MeasurementPo

@Dao
interface MeasurementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(po: MeasurementPo)

    @Query(
        "SELECT * FROM measurements WHERE memberId = :memberId AND deletedAt IS NULL " +
            "ORDER BY measuredAt DESC LIMIT :limit",
    )
    suspend fun recent(limit: Int, memberId: String): List<MeasurementPo>

    @Query(
        "SELECT * FROM measurements WHERE memberId = :memberId AND deletedAt IS NULL " +
            "AND measuredAt >= :from AND measuredAt < :to ORDER BY measuredAt ASC",
    )
    suspend fun between(from: Long, to: Long, memberId: String): List<MeasurementPo>

    /** 软删：写墓碑并标待推，不物理删除（DEC-S13）。 */
    @Query(
        "UPDATE measurements SET deletedAt = :now, updatedAt = :now, syncStatus = 1 " +
            "WHERE measuredAt = :time AND memberId = :memberId AND deletedAt IS NULL",
    )
    suspend fun delete(time: Long, memberId: String, now: Long)
}
