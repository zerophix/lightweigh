package com.qingke.health.application.query

import com.qingke.health.domain.model.MeasureRecord
import com.qingke.health.domain.repository.MeasurementRepository

/** query — 只读查询（最新测量列表）。 */
class MeasurementQuery(private val repository: MeasurementRepository) {

    suspend fun recent(limit: Int = 30): List<MeasureRecord> = repository.recent(limit)
}
