package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.infrastructure.persistent.dao.MeasurementDao

/**
 * MeasurementQueryPort 实现 —— Room DAO 直读（T-016 仓储接口精简）。
 * 列表默认不含软删行。
 */
class RoomMeasurementQuery(
    private val dao: MeasurementDao,
    private val memberId: () -> String,
) : MeasurementQueryPort {

    override suspend fun recent(limit: Int): List<MeasureRecord> =
        dao.recent(limit, memberId()).map { it.toMeasureRecord() }

    override suspend fun between(fromInc: Long, toExc: Long): List<MeasureRecord> =
        dao.between(fromInc, toExc, memberId()).map { it.toMeasureRecord() }
}
