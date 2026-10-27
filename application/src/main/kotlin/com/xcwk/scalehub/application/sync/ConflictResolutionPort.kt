package com.xcwk.scalehub.application.sync

import com.xcwk.scalehub.application.sync.ConflictResolutionStrategy

/**
 * 冲突解决端口（M3b）—— 抽象冲突合并语义。
 *
 * 用于 SyncService 在拉取云端行时遇到本地已有、但云端更新的情况（例如双设备并发写）。
 * 实现者负责判断冲突并返回合并后的行，或标记需要用户手动介入。
 */
interface ConflictResolutionPort {
    /**
     * 比较本地记录与云端记录，返回合并结果。
     *
     * @param localTs 本地最后更新时间戳
     * @param cloudTs 云端最后更新时间戳
     * @param localFields 本地字段快照
     * @param cloudFields 云端字段快照
     * @return 合并后的字段 + 策略标记
     */
    fun resolve(
        localTs: Long,
        cloudTs: Long,
        localFields: Map<String, Any?>,
        cloudFields: Map<String, Any?>,
    ): ConflictResolutionResult

    /**
     * 判断是否需要用户手动介入（例如两个设备同时修改，且时间戳相同）。
     */
    fun needsManualResolution(localTs: Long, cloudTs: Long): Boolean

    /** 批量冲突标记（用于 UI 显示）。 */
    fun conflictKeys(since: Long): List<String>
}

/** 合并结果，携带策略标记。 */
data class ConflictResolutionResult(
    val mergedFields: Map<String, Any?>,
    val strategy: ConflictResolutionStrategy,
    val needsOverride: Boolean = false,
)

/** 冲突解决策略。 */
enum class ConflictResolutionStrategy {
    LOCAL_WINS,   // 本地优先
    CLOUD_WINS,   // 云端优先
    MERGE,        // 自动合并
    MANUAL,       // 需要用户决定
}
