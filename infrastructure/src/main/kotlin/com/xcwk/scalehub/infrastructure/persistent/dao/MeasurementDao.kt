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

    @Query("SELECT * FROM measurements ORDER BY measuredAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MeasurementPo>
}
