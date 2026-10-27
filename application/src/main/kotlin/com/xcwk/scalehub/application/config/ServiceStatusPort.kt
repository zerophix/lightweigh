package com.xcwk.scalehub.application.config

/**
 * 服务状态端口（M3）—— 抽象维护模式/服务可状态查询。
 *
 * 实现者负责从远程配置或本地获取服务状态；应用层只编排语义。
 */
interface ServiceStatusPort {
    /** 查询当前服务状态。 */
    suspend fun fetchStatus(): ServiceStatus

    /** 强制刷新远程状态。 */
    suspend fun refresh(): ServiceStatus

    /** 订阅服务状态变更（响应式）。 */
    fun statusFlow(): kotlinx.coroutines.flow.Flow<ServiceStatus>
}

/** 服务状态。 */
data class ServiceStatus(
    val maintenance: Boolean,           // 是否维护中
    val message: String,               // 维护/服务消息
    val startTime: Long,               // 维护开始时间
    val endTime: Long,                 // 预计结束时间
    val forced: Boolean,               // 是否强制下线
    val version: String,               // 当前服务器版本
)
