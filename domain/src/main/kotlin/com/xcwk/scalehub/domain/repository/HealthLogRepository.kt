package com.xcwk.scalehub.domain.repository

import com.xcwk.scalehub.domain.model.HealthLog

/**
 * 健康记录仓储端口。
 * 语义承诺：save 返回 = 本地已持久化；云端同步是独立端口职责（离线优先，同测量）。
 * 唯一性约定 (time, type) —— 对应云表 UNIQUE(owner, logged_at, type)。
 * 实现方：InMemoryHealthLogRepository(当前) / RoomHealthLogRepository(组 C 接入)。
 */
interface HealthLogRepository {
    suspend fun save(log: HealthLog)
    /** 最新在前。 */
    suspend fun recent(limit: Int): List<HealthLog>
}
