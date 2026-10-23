package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.infrastructure.persistent.dao.HealthLogDao

/**
 * repository 实现 — Room 版（生产）。
 * 写口 only：查询经 HealthLogQueryPort。软删写 deletedAt（DEC-S13）。
 */
class RoomHealthLogRepository(
    private val dao: HealthLogDao,
    private val memberId: () -> String,
) : HealthLogRepository {

    override suspend fun save(log: HealthLog, memberId: String) =
        dao.upsert(log.toHealthLogPo(memberId))

    override suspend fun markDeleted(time: Long, type: String, memberId: String) {
        dao.markDeleted(memberId, time, type, System.currentTimeMillis())
    }
}
