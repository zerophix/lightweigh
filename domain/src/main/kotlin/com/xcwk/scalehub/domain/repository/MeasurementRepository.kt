package com.xcwk.scalehub.domain.repository

import com.xcwk.scalehub.domain.model.MeasureRecord

/**
 * 测量仓储端口。
 * 语义承诺：save 返回 = 本地已持久化；云端同步是独立端口的职责（离线优先）。
 * 实现方：RoomMeasurementRepository(生产) / InMemoryMeasurementRepository(测试)。
 *
 * 成员隔离是这条端口的核心约束，故显式出现在 save 签名上（DEC-034 组 D / F-005）——
 * 此前签名只有 record，归属由实现方偷偷注入「当前成员」，读接口看不出多成员隔离，
 * 而 MeasureRecord 里的 memberId 字段从没拿到过有效值（等于允许无成员的记录）。
 * 现在「这条记录属于谁」是调用方必须回答的问题。
 */
interface MeasurementRepository {
    suspend fun save(record: MeasureRecord, memberId: String)

    /** 按测量时间戳删除单条（历史页删除，唯一键即时间戳）。 */
    suspend fun delete(time: Long)
}
