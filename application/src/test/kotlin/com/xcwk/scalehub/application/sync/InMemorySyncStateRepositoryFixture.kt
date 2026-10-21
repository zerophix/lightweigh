package com.xcwk.scalehub.application.sync

/**
 * 测试用内存同步状态实现（M2b 水位线）。
 * 与 RoomSyncStateRepository 行为一致，但不依赖 Room。
 */
class InMemorySyncStateRepositoryFixture : SyncStatePort {

    /** key = "tableName|memberId"，value = (pushed, pulled) */
    private val state = mutableMapOf<String, Pair<Long, Long>>()

    private fun key(tableName: String, memberId: String) = "$tableName|$memberId"

    override suspend fun get(tableName: String, memberId: String): Pair<Long, Long> =
        state[key(tableName, memberId)] ?: (0L to 0L)

    override suspend fun updatePushed(tableName: String, memberId: String, watermark: Long) {
        val existing = state[key(tableName, memberId)] ?: (0L to 0L)
        state[key(tableName, memberId)] = watermark to existing.second
    }

    override suspend fun updatePulled(tableName: String, memberId: String, watermark: Long) {
        val existing = state[key(tableName, memberId)] ?: (0L to 0L)
        state[key(tableName, memberId)] = existing.first to watermark
    }

    override suspend fun clearFor(memberId: String) {
        val prefix = "|$memberId"
        state.keys.filter { it.endsWith(prefix) }.forEach { state.remove(it) }
    }

    override suspend fun clearAll() {
        state.clear()
    }
}
