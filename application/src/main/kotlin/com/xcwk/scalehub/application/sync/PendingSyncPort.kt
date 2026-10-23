package com.xcwk.scalehub.application.sync

/**
 * 非时间戳实体的待同步标记（DEC-S16）。
 * 测量不走这里，走 measurements.syncStatus。
 */
interface PendingSyncPort {
    fun enqueue(entityType: String, entityId: String, op: String)

    fun clear(entityType: String, entityId: String)
}
