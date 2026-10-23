package com.xcwk.scalehub.infrastructure.persistent.dao

import androidx.room.Dao
import androidx.room.Query
import com.xcwk.scalehub.infrastructure.persistent.po.HealthLogPo

/** 同步专用：含墓碑，按 updatedAt 增量（DEC-S13）。 */
@Dao
interface HealthLogSyncDao {
    @Query(
        "SELECT * FROM health_logs WHERE memberId = :memberId AND updatedAt > :watermark " +
            "ORDER BY updatedAt ASC",
    )
    suspend fun due(memberId: String, watermark: Long): List<HealthLogPo>

    @Query("SELECT * FROM health_logs WHERE memberId = :memberId")
    suspend fun allForMember(memberId: String): List<HealthLogPo>
}
