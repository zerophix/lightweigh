package com.xcwk.scalehub.infrastructure.persistent.dao

import androidx.room.Dao
import androidx.room.Query
import com.xcwk.scalehub.infrastructure.persistent.po.MeasurementPo

/** 同步专用读口：包含墓碑，按 updatedAt / syncStatus 选取（DEC-S01 / S16）。 */
@Dao
interface MeasurementSyncDao {
    @Query(
        "SELECT * FROM measurements WHERE memberId = :memberId " +
            "AND (updatedAt > :watermark OR syncStatus != 0) ORDER BY updatedAt ASC",
    )
    suspend fun due(memberId: String, watermark: Long): List<MeasurementPo>

    @Query("UPDATE measurements SET syncStatus = 0 WHERE recordId IN (:ids)")
    suspend fun markClean(ids: List<String>)

    @Query("SELECT * FROM measurements WHERE memberId = :memberId")
    suspend fun allForMember(memberId: String): List<MeasurementPo>
}
