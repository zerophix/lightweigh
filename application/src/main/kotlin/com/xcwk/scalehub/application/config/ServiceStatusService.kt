package com.xcwk.scalehub.application.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * application service — 服务状态编排。
 *
 * 职责：
 *  - 查询当前服务状态（维护模式/正常）
 *  - 强制刷新
 *  - 订阅状态变更
 *  - 判断是否应该阻止用户操作
 *
 * 依赖 ServiceStatusPort（远程配置）。
 */
class ServiceStatusService(
    private val port: ServiceStatusPort?,
) {

    private val _status = MutableStateFlow<ServiceStatus>(
        ServiceStatus(
            maintenance = false,
            message = "服务正常",
            startTime = 0L,
            endTime = 0L,
            forced = false,
            version = "unknown",
        )
    )

    /** 当前服务状态（响应式）。 */
    val status: StateFlow<ServiceStatus> = _status

    /** 是否维护中。 */
    fun isMaintenance(): Boolean = _status.value.maintenance

    /** 是否强制下线。 */
    fun isForced(): Boolean = _status.value.forced

    /** 是否应该阻止用户操作。 */
    fun shouldBlock(): Boolean = isMaintenance() && isForced()

    /** 获取维护消息。 */
    fun maintenanceMessage(): String = _status.value.message

    /** 强制刷新远程状态。 */
    suspend fun refresh() {
        val p = port ?: return
        try {
            val newStatus = p.refresh()
            _status.value = newStatus
        } catch (e: Exception) {
            // 保持现有状态
        }
    }

    /** 初始化时拉取一次状态。 */
    suspend fun init() {
        val p = port ?: return
        try {
            val newStatus = p.fetchStatus()
            _status.value = newStatus
        } catch (e: Exception) {
            // 保持默认状态
        }
    }
}
