package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.model.MeasureRecord

/**
 * 测量查询端口（T-016 仓储接口精简）。
 *
 * 从 MeasurementRepository 剥离的只读查询操作移入查询端口：
 * domain 仓储仅存取（save/delete），查询语义归查询层，
 * 便于未来替换为专用查询模型 / 读模型 / 缓存层。
 *
 * 端口定义在 application（先例 SyncStatePort/AiPort），实现在 infrastructure
 * （RoomMeasurementQuery / InMemoryMeasurementRepository）——依赖倒置不变。
 */
interface MeasurementQueryPort {
    /** 最新在前。 */
    suspend fun recent(limit: Int): List<MeasureRecord>

    /** 区间读 [fromInc, toExc)，按时间升序（趋势/历史/报告查询用）。 */
    suspend fun between(fromInc: Long, toExc: Long): List<MeasureRecord>
}
