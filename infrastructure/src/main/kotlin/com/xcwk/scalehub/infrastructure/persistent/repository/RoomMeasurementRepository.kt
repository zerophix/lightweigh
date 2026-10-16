package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.infrastructure.persistent.dao.MeasurementDao
import com.xcwk.scalehub.infrastructure.persistent.po.MeasurementPo

/** repository 实现 — Room 版（生产）。取代旧库 SharedPreferences JSON（有 180 条截断）。 */
class RoomMeasurementRepository(private val dao: MeasurementDao) : MeasurementRepository {

    override suspend fun save(record: MeasureRecord) = dao.upsert(record.toPo())

    override suspend fun recent(limit: Int): List<MeasureRecord> = dao.recent(limit).map { it.toDomain() }

    private fun MeasureRecord.toPo() = MeasurementPo(
        measuredAt = time, weightKg = weight, impedanceOhm = impedance,
        fat = fat, mac = mac, algorithmId = algorithm,
        male = male, ageYears = ageYears, heightCm = heightCm,
    )

    private fun MeasurementPo.toDomain() = MeasureRecord(
        measuredAt, weightKg, impedanceOhm, fat, mac, algorithmId,
    )
}
