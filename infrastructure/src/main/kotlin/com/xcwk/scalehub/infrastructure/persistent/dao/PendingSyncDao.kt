package com.xcwk.scalehub.infrastructure.persistent.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xcwk.scalehub.infrastructure.persistent.po.PendingSyncPo

@Dao
interface PendingSyncDao {
    @Upsert
    fun upsert(row: PendingSyncPo)

    @Query("DELETE FROM pending_sync WHERE entityType = :entityType AND entityId = :entityId")
    fun clear(entityType: String, entityId: String)

    @Query("DELETE FROM pending_sync")
    fun deleteAll()
}
