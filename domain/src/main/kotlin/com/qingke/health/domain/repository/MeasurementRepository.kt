package com.qingke.health.domain.repository

import com.qingke.health.domain.model.MeasureRecord

/**
 * 测量仓储端口。
 * 语义承诺：save 返回 = 本地已持久化；云端同步是独立端口的职责（离线优先）。
 * 实现方：RoomMeasurementRepository(生产) / InMemoryMeasurementRepository(测试)。
 */
interface MeasurementRepository {
    suspend fun save(record: MeasureRecord)
    /** 最新在前。 */
    suspend fun recent(limit: Int): List<MeasureRecord>
}
