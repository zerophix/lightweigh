package com.xcwk.scalehub.application.sync

import com.xcwk.scalehub.domain.model.MeasureRecord

/**
 * 测量同步读口（DEC-058）。与 UI 查询分离：包含墓碑，按 updatedAt / syncStatus 选取。
 */
interface MeasurementSyncPort {
    suspend fun due(memberId: String, watermark: Long): List<MeasureRecord>

    suspend fun markClean(recordIds: List<String>)

    /** 含墓碑，供拉取合并按 recordId 对账。 */
    suspend fun all(memberId: String): List<MeasureRecord>
}
