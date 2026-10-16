package com.xcwk.scalehub.domain.repository

import com.xcwk.scalehub.domain.model.MeasureRecord

/**
 * 测量仓储端口。
 * 语义承诺：save 返回 = 本地已持久化；云端同步是独立端口的职责（离线优先）。
 * 实现方：RoomMeasurementRepository(生产) / InMemoryMeasurementRepository(测试)。
 */
interface MeasurementRepository {
    suspend fun save(record: MeasureRecord)
    /** 最新在前。 */
    suspend fun recent(limit: Int): List<MeasureRecord>

    /** 区间读 [fromInc, toExc)，按时间升序（趋势查询用，DEC-022 组 B）。 */
    suspend fun between(fromInc: Long, toExc: Long): List<MeasureRecord>

    /** 按测量时间戳删除单条（历史页删除，唯一键即时间戳）。 */
    suspend fun delete(time: Long)
}
