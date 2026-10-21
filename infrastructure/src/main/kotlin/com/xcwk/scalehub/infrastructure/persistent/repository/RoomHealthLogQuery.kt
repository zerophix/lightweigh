package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.application.query.HealthLogQueryPort
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.infrastructure.persistent.dao.HealthLogDao
import com.xcwk.scalehub.infrastructure.persistent.po.HealthLogPo

/**
 * HealthLogQueryPort 实现 —— Room DAO 直读（T-016，同 RoomMeasurementQuery）。
 */
class RoomHealthLogQuery(
    private val dao: HealthLogDao,
    private val memberId: () -> String,
) : HealthLogQueryPort {

    override suspend fun recent(limit: Int): List<HealthLog> =
        dao.recent(limit, memberId()).map { it.toDomain() }

    override suspend fun between(fromInc: Long, toExc: Long): List<HealthLog> =
        dao.between(fromInc, toExc, memberId()).map { it.toDomain() }

    private fun HealthLogPo.toDomain() = HealthLog(
        loggedAt, type, value, value2, label, note,
    )
}
