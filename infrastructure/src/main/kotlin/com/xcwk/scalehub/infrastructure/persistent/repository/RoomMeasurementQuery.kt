package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.infrastructure.persistent.dao.MeasurementDao
import com.xcwk.scalehub.infrastructure.persistent.po.MeasurementPo

/**
 * MeasurementQueryPort 实现 —— Room DAO 直读（T-016 仓储接口精简）。
 *
 * 查询端口与仓储写口分离：仓储只管 save/delete（domain 端口），
 * 查询按当前成员过滤后走 DAO（同 RoomMeasurementRepository 读路径语义）。
 */
class RoomMeasurementQuery(
    private val dao: MeasurementDao,
    private val memberId: () -> String,
) : MeasurementQueryPort {

    override suspend fun recent(limit: Int): List<MeasureRecord> =
        dao.recent(limit, memberId()).map { it.toDomain() }

    override suspend fun between(fromInc: Long, toExc: Long): List<MeasureRecord> =
        dao.between(fromInc, toExc, memberId()).map { it.toDomain() }

    private fun MeasurementPo.toDomain() = MeasureRecord(
        measuredAt, weightKg, impedanceOhm, fat, mac, algorithmId,
        male, ageYears, heightCm,
    )
}
