package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.application.sync.SyncStatePort
import com.xcwk.scalehub.infrastructure.persistent.dao.SyncStateDao
import com.xcwk.scalehub.infrastructure.persistent.po.SyncStatePo

/**
 * 实现 — 同步状态仓储（M2b 水位线）。
 *
 * 线程安全：SyncStateDao 的 suspend 方法由 Room 保证线程安全；
 * SyncService 内部已用 Mutex 串行化 push/restore，不会并发调用本类。
 */
class RoomSyncStateRepository(
    private val dao: SyncStateDao,
    private val currentTimeMillis: () -> Long,
) : SyncStatePort {

    override suspend fun get(tableName: String, memberId: String): Pair<Long, Long> {
        val po = dao.get(tableName, memberId) ?: return 0L to 0L
        return po.pushedWatermark to po.pulledWatermark
    }

    override suspend fun updatePushed(tableName: String, memberId: String, watermark: Long) {
        val existing = dao.get(tableName, memberId)
        dao.upsert(
            SyncStatePo(
                tableName = tableName,
                memberId = memberId,
                pushedWatermark = watermark,
                pulledWatermark = existing?.pulledWatermark ?: 0L,
                updatedAt = currentTimeMillis(),
            ),
        )
    }

    override suspend fun updatePulled(tableName: String, memberId: String, watermark: Long) {
        val existing = dao.get(tableName, memberId)
        dao.upsert(
            SyncStatePo(
                tableName = tableName,
                memberId = memberId,
                pushedWatermark = existing?.pushedWatermark ?: 0L,
                pulledWatermark = watermark,
                updatedAt = currentTimeMillis(),
            ),
        )
    }

    override suspend fun clearFor(memberId: String) {
        dao.clearFor(memberId)
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }
}
