package com.xcwk.scalehub.domain.repository

import com.xcwk.scalehub.domain.model.HealthLog

/**
 * 健康记录仓储端口。
 * 语义承诺：save 返回 = 本地已持久化；云端同步是独立端口职责（离线优先，同测量）。
 * 本地唯一性 (memberId, time, type)（DEC-058）；云表仍是 UNIQUE(owner, logged_at, type)，
 * 云侧 member 列与 deleted_at 见 DEC 云跟进。
 * 实现方：InMemoryHealthLogRepository(当前) / RoomHealthLogRepository(组 C 接入)。
 *
 * 成员隔离显式出现在 save 签名上（DEC-034 组 D / F-005，同 MeasurementRepository）：
 * 云表唯一键含 owner，归属不是可省略的细节。
 */
interface HealthLogRepository {
    suspend fun save(log: HealthLog, memberId: String)

    /**
     * 软删：写 deletedAt 墓碑，不物理删除（DEC-S13）。
     * 默认空实现，避免测试假件被迫实现；Room 实现写墓碑。
     */
    suspend fun markDeleted(time: Long, type: String, memberId: String) {}
}
