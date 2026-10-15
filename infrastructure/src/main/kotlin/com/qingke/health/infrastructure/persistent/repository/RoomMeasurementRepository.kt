package com.qingke.health.infrastructure.persistent.repository

import com.qingke.health.domain.model.MeasureRecord
import com.qingke.health.domain.repository.MeasurementRepository
import com.qingke.health.infrastructure.persistent.dao.MeasurementDao
import com.qingke.health.infrastructure.persistent.po.MeasurementPo

/** repository 实现 — Room 版（生产）。取代旧库 SharedPreferences JSON（有 180 条截断）。 */
class RoomMeasurementRepository(private val dao: MeasurementDao) : MeasurementRepository {

    override suspend fun save(record: MeasureRecord) = dao.upsert(record.toPo())

    override suspend fun recent(limit: Int): List<MeasureRecord> = dao.recent(limit).map { it.toDomain() }

    private fun MeasureRecord.toPo() = MeasurementPo(
        measuredAt = time, weightKg = weight, impedanceOhm = impedance,
        fat = fat, mac = mac, algorithmId = algorithm,
    )

    private fun MeasurementPo.toDomain() = MeasureRecord(
        measuredAt, weightKg, impedanceOhm, fat, mac, algorithmId,
    )
}
