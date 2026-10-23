package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.application.sync.HealthLogSyncPort
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.infrastructure.persistent.dao.HealthLogSyncDao

class RoomHealthLogSync(
    private val dao: HealthLogSyncDao,
) : HealthLogSyncPort {
    override suspend fun due(memberId: String, watermark: Long): List<HealthLog> =
        dao.due(memberId, watermark).map { it.toHealthLog() }

    override suspend fun all(memberId: String): List<HealthLog> =
        dao.allForMember(memberId).map { it.toHealthLog() }
}
