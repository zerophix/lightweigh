package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.application.query.HealthLogQueryPort
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.infrastructure.persistent.dao.HealthLogDao

/**
 * HealthLogQueryPort 实现。列表不含软删行。
 */
class RoomHealthLogQuery(
    private val dao: HealthLogDao,
    private val memberId: () -> String,
) : HealthLogQueryPort {

    override suspend fun recent(limit: Int): List<HealthLog> =
        dao.recent(limit, memberId()).map { it.toHealthLog() }

    override suspend fun between(fromInc: Long, toExc: Long): List<HealthLog> =
        dao.between(fromInc, toExc, memberId()).map { it.toHealthLog() }
}
