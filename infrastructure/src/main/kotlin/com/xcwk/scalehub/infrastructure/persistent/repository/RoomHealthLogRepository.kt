package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.infrastructure.persistent.dao.HealthLogDao
import com.xcwk.scalehub.infrastructure.persistent.po.HealthLogPo

/**
 * repository 实现 — Room 版（生产），取代旧库 HealthLogStore 的 SP JSON。
 * 读路径经 HealthLog 构造守卫，脏行 fail-fast（同测量仓储语义）。
 */
class RoomHealthLogRepository(
    private val dao: HealthLogDao,
    private val memberId: () -> String,
) : HealthLogRepository {

    /** 落库归属由调用方给出（DEC-034 组 D / F-005，同测量仓储）。 */
    override suspend fun save(log: HealthLog, memberId: String) =
        dao.upsert(log.toPo().copy(memberId = memberId))

    /** 读取仍按当前成员过滤。 */
    override suspend fun recent(limit: Int): List<HealthLog> =
        dao.recent(limit, memberId()).map { it.toDomain() }

    override suspend fun between(fromInc: Long, toExc: Long): List<HealthLog> =
        dao.between(fromInc, toExc, memberId()).map { it.toDomain() }

    private fun HealthLog.toPo() = HealthLogPo(
        loggedAt = time, type = type, value = value,
        value2 = value2, label = label, note = note, memberId = "",
    )

    private fun HealthLogPo.toDomain() = HealthLog(
        loggedAt, type, value, value2, label, note,
    )
}
