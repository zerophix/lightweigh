package com.xcwk.scalehub.application.sync

import com.xcwk.scalehub.application.debug.DebugLog

/**
 * application service — 冲突解决编排。
 *
 * 职责：
 *  - 在拉取云端数据时遇到本地已存在的记录时，应用冲突解决策略
 *  - 比较本地与云端的时间戳，决定本地优先/云端优先/合并/手动
 *  - 记录冲突日志
 *
 * 用于 SyncService 在 mergeMeasurements / mergeHealthLogs 时调用。
 */
class ConflictResolutionService(
    private val port: ConflictResolutionPort,
) {

    /**
     * 解决单条记录冲突。
     * @param tableName 表名（用于日志）
     * @param localTs 本地最后更新时间戳
     * @param cloudTs 云端最后更新时间戳
     * @param localFields 本地字段快照
     * @param cloudFields 云端字段快照
     * @return 合并结果 + 策略
     */
    suspend fun resolve(
        tableName: String,
        localTs: Long,
        cloudTs: Long,
        localFields: Map<String, Any?>,
        cloudFields: Map<String, Any?>,
    ): ConflictResolutionResult {
        DebugLog.add("conflict", "Resolving $tableName: localTs=$localTs cloudTs=$cloudTs")
        val result = port.resolve(localTs, cloudTs, localFields, cloudFields)
        DebugLog.add("conflict", "Result: strategy=${result.strategy}, override=${result.needsOverride}")
        return result
    }

    /**
     * 判断是否需要用户手动介入。
     */
    suspend fun needsManualResolution(localTs: Long, cloudTs: Long): Boolean {
        return port.needsManualResolution(localTs, cloudTs)
    }

    /**
     * 获取自某时间戳以来的所有冲突记录 ID（用于 UI 显示）。
     */
    suspend fun conflictKeys(since: Long): List<String> {
        return port.conflictKeys(since)
    }
}
