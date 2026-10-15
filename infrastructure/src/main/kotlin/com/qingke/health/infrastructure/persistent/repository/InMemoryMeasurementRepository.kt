package com.qingke.health.infrastructure.persistent.repository

import com.qingke.health.domain.model.MeasureRecord
import com.qingke.health.domain.repository.MeasurementRepository
import java.util.concurrent.CopyOnWriteArrayList

/** repository 实现 — 内存版（测试/演示）。 */
class InMemoryMeasurementRepository : MeasurementRepository {
    private val rows = CopyOnWriteArrayList<MeasureRecord>()

    override suspend fun save(record: MeasureRecord) {
        rows.removeAll { it.time == record.time }
        rows.add(record)
    }

    override suspend fun recent(limit: Int): List<MeasureRecord> =
        rows.sortedByDescending { it.time }.take(limit)
}
