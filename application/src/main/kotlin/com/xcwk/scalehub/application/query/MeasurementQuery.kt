package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.model.MeasureRecord

/** query — 只读查询（最新测量列表）。 */
class MeasurementQuery(private val port: MeasurementQueryPort) {

    suspend fun recent(limit: Int = 30): List<MeasureRecord> = port.recent(limit)
}
