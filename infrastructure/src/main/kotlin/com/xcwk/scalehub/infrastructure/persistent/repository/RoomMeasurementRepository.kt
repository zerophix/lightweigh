package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.infrastructure.persistent.dao.MeasurementDao
import com.xcwk.scalehub.infrastructure.persistent.po.MeasurementPo

/** repository 实现 — Room 版（生产）。取代旧库 SharedPreferences JSON（有 180 条截断）。 */
class RoomMeasurementRepository(
    private val dao: MeasurementDao,
    private val memberId: () -> String,
) : MeasurementRepository {

    /**
     * 落库归属由调用方给出（DEC-034 组 D / F-005）——此前实现偷偷注入「当前成员」，
     * 接口签名上看不出多成员隔离这条核心约束。
     */
    override suspend fun save(record: MeasureRecord, memberId: String) =
        dao.upsert(record.toPo().copy(memberId = memberId))

    /** 读取仍按当前成员过滤：查询是「我看到了什么」，不是「谁写了什么」。 */
    override suspend fun recent(limit: Int): List<MeasureRecord> =
        dao.recent(limit, memberId()).map { it.toDomain() }

    override suspend fun between(fromInc: Long, toExc: Long): List<MeasureRecord> =
        dao.between(fromInc, toExc, memberId()).map { it.toDomain() }

    override suspend fun delete(time: Long) = dao.delete(time)

    private fun MeasureRecord.toPo() = MeasurementPo(
        measuredAt = time, weightKg = weight, impedanceOhm = impedance,
        fat = fat, mac = mac, algorithmId = algorithm,
        male = male, ageYears = ageYears, heightCm = heightCm, memberId = "",
    )

    private fun MeasurementPo.toDomain() = MeasureRecord(
        measuredAt, weightKg, impedanceOhm, fat, mac, algorithmId,
        male, ageYears, heightCm,
    )
}
