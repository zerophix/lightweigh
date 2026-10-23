package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.application.sync.PendingSyncPort
import com.xcwk.scalehub.infrastructure.persistent.dao.PendingSyncDao
import com.xcwk.scalehub.infrastructure.persistent.po.PendingSyncPo

class RoomPendingSyncRepository(
    private val dao: PendingSyncDao,
) : PendingSyncPort {
    override fun enqueue(entityType: String, entityId: String, op: String) {
        val now = System.currentTimeMillis()
        dao.upsert(
            PendingSyncPo(
                entityType = entityType,
                entityId = entityId,
                op = op,
                payloadJson = "",
                attempts = 0,
                nextRetryAt = now,
                updatedAt = now,
            ),
        )
    }

    override fun clear(entityType: String, entityId: String) {
        dao.clear(entityType, entityId)
    }
}
