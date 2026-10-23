package com.xcwk.scalehub.application.sync

import com.xcwk.scalehub.domain.model.HealthLog

/** 健康日志同步读口。含墓碑，按 updatedAt 增量（DEC-S13）。 */
interface HealthLogSyncPort {
    suspend fun due(memberId: String, watermark: Long): List<HealthLog>

    suspend fun all(memberId: String): List<HealthLog>
}
