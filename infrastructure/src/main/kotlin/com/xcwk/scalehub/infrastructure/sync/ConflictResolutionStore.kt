package com.xcwk.scalehub.infrastructure.sync

import com.xcwk.scalehub.application.sync.ConflictResolutionPort
import com.xcwk.scalehub.application.sync.ConflictResolutionResult
import com.xcwk.scalehub.application.sync.ConflictResolutionStrategy

/**
 * 冲突解决实现（时间戳比较 + 合并策略）。
 *
 * 策略：
 *  - cloud 更新（cloudTs > localTs） → CLOUD_WINS
 *  - local 更新（localTs > cloudTs） → LOCAL_WINS
 *  - 同时间 → MANUAL（需要用户决定）
 *  - 云端缺失 updated_at → 走云端胜（保守）
 */
class ConflictResolutionStore : ConflictResolutionPort {

    override fun resolve(
        localTs: Long,
        cloudTs: Long,
        localFields: Map<String, Any?>,
        cloudFields: Map<String, Any?>,
    ): ConflictResolutionResult {
        // 如果本地时间戳更晚 → 本地优先
        if (localTs > cloudTs) {
            return ConflictResolutionResult(localFields, ConflictResolutionStrategy.LOCAL_WINS)
        }
        // 如果云端更晚 → 云端胜
        if (cloudTs > localTs) {
            return ConflictResolutionResult(cloudFields, ConflictResolutionStrategy.CLOUD_WINS, needsOverride = true)
        }
        // 同时间 → 手动
        return ConflictResolutionResult(
            mergeFields(localFields, cloudFields),
            ConflictResolutionStrategy.MANUAL,
            needsOverride = true,
        )
    }

    override fun needsManualResolution(localTs: Long, cloudTs: Long): Boolean = localTs == cloudTs

    override fun conflictKeys(since: Long): List<String> = emptyList()  // 由 Room 实现更精确

    /** 合并两个字段映射（云端优先覆盖本地）。 */
    private fun mergeFields(local: Map<String, Any?>, cloud: Map<String, Any?>): Map<String, Any?> {
        val result = local.toMutableMap()
        for ((key, value) in cloud) {
            if (value != null) result[key] = value
        }
        return result
    }
}
