package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.application.sync.MeasurementSyncPort
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.infrastructure.persistent.dao.MeasurementSyncDao

class RoomMeasurementSync(
    private val dao: MeasurementSyncDao,
) : MeasurementSyncPort {
    override suspend fun due(memberId: String, watermark: Long): List<MeasureRecord> =
        dao.due(memberId, watermark).map { it.toMeasureRecord() }

    override suspend fun markClean(recordIds: List<String>) {
        if (recordIds.isEmpty()) return
        dao.markClean(recordIds)
    }

    override suspend fun all(memberId: String): List<MeasureRecord> =
        dao.allForMember(memberId).map { it.toMeasureRecord() }
}
