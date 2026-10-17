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

    @Query("SELECT * FROM measurements WHERE memberId = :memberId ORDER BY measuredAt DESC LIMIT :limit")
    suspend fun recent(limit: Int, memberId: String): List<MeasurementPo>

    @Query("SELECT * FROM measurements WHERE memberId = :memberId AND measuredAt >= :from AND measuredAt < :to ORDER BY measuredAt ASC")
    suspend fun between(from: Long, to: Long, memberId: String): List<MeasurementPo>

    @Query("DELETE FROM measurements WHERE measuredAt = :time")
    suspend fun delete(time: Long)
}
