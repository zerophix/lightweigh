package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import java.util.concurrent.CopyOnWriteArrayList

/** repository 实现 — 内存版（测试/演示）。唯一性 (time, type) 对齐云表约束。 */
class InMemoryHealthLogRepository : HealthLogRepository {
    private val rows = CopyOnWriteArrayList<HealthLog>()

    /** 各次 save 收到的归属，供测试断言接口确实把 memberId 传到了实现（组 D）。 */
    val savedMemberIds = CopyOnWriteArrayList<String>()

    override suspend fun save(log: HealthLog, memberId: String) {
        savedMemberIds.add(memberId)
        rows.removeAll { it.time == log.time && it.type == log.type }
        rows.add(log)
    }

    override suspend fun recent(limit: Int): List<HealthLog> =
        rows.sortedByDescending { it.time }.take(limit)

    override suspend fun between(fromInc: Long, toExc: Long): List<HealthLog> =
        rows.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
}
