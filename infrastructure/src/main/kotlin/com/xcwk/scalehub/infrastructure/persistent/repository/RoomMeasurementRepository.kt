package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.infrastructure.persistent.dao.MeasurementDao

/**
 * repository 实现 — Room 版（生产）。
 * 删除写 deletedAt 墓碑（DEC-S13），列表查询过滤墓碑。
 */
class RoomMeasurementRepository(
    private val dao: MeasurementDao,
    private val memberId: () -> String,
) : MeasurementRepository {

    override suspend fun save(record: MeasureRecord, memberId: String) =
        dao.upsert(record.toMeasurementPo(memberId))

    override suspend fun delete(time: Long) {
        dao.delete(time, memberId(), System.currentTimeMillis())
    }
}
