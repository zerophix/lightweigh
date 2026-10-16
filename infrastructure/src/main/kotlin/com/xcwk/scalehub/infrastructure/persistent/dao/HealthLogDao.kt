package com.xcwk.scalehub.infrastructure.persistent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xcwk.scalehub.infrastructure.persistent.po.HealthLogPo

@Dao
interface HealthLogDao {
    /** 唯一键 (loggedAt, type) 冲突覆盖，对齐云表 upsert 语义。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(po: HealthLogPo)

    @Query("SELECT * FROM health_logs ORDER BY loggedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<HealthLogPo>
}
