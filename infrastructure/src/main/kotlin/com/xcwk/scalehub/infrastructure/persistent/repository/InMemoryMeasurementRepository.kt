package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import java.util.concurrent.CopyOnWriteArrayList

/** repository 实现 — 内存版（测试/演示）。 */
class InMemoryMeasurementRepository : MeasurementRepository {
    private val rows = CopyOnWriteArrayList<MeasureRecord>()

    /** 各次 save 收到的归属，供测试断言接口确实把 memberId 传到了实现（组 D）。 */
    val savedMemberIds = CopyOnWriteArrayList<String>()

    override suspend fun save(record: MeasureRecord, memberId: String) {
        savedMemberIds.add(memberId)
        rows.removeAll { it.time == record.time }
        rows.add(record)
    }

    override suspend fun recent(limit: Int): List<MeasureRecord> =
        rows.sortedByDescending { it.time }.take(limit)

    override suspend fun between(fromInc: Long, toExc: Long): List<MeasureRecord> =
        rows.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }

    override suspend fun delete(time: Long) {
        rows.removeAll { it.time == time }
    }
}
