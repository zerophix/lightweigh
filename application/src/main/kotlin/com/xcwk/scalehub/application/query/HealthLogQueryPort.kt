package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.model.HealthLog

/**
 * 健康记录查询端口（T-016 仓储接口精简）。
 *
 * 从 HealthLogRepository 剥离的只读查询操作移入查询端口，
 * 端口定义在 application、实现在 infrastructure（同 MeasurementQueryPort）。
 */
interface HealthLogQueryPort {
    /** 最新在前。 */
    suspend fun recent(limit: Int): List<HealthLog>

    /** 区间读 [fromInc, toExc)，按时间升序（首页今日汇总/同步用）。 */
    suspend fun between(fromInc: Long, toExc: Long): List<HealthLog>
}
