package com.xcwk.scalehub.infrastructure.config

/**
 * 后端网络配置（DEC-028 任务 A 收敛）：
 * 云网关 / 云函数 / 认证会话三处此前各自硬编码同一 TCB 域名与网络参数，
 * 收敛到此单一来源，自建后端或切换环境时只改一处。
 *
 * BASE 为腾讯云开发（TCB）网关域名（旧库 Gateway.kt:13 沿用）。
 * T-018：BASE 改为可注入——app 层在 Application.onCreate 用 BuildConfig.BACKEND_BASE 赋值，
 * release/debug 可指向不同后端；默认值仅作兜底（如单元测试未初始化时）。
 */
object BackendConfig {
    /** 云网关/认证/AI 函数共用的 TCB 网关域名（由 app 层注入，T-018）。 */
    @Volatile
    var BASE: String = "https://bodyfat-d0gj8wdw1bfe9b421.api.tcloudbasegateway.com"

    /** 连接超时（毫秒）。 */
    const val CONNECT_TIMEOUT_MS = 15_000L

    /** 数据读写超时（毫秒），云网关/认证会话共用。 */
    const val READ_TIMEOUT_MS = 30_000L

    /** AI 函数读超时（毫秒），旧库 askNow readTimeoutMs=55s。 */
    const val AI_READ_TIMEOUT_MS = 55_000L

    /** 云网关 upsert 分块行数（旧库 Gateway.kt:35-49）。 */
    const val BATCH_SIZE = 40

    /** AI 多轮上下文取最近轮次（旧库 askNow takeLast(8)）。 */
    const val AI_CONTEXT_TURNS = 8

    /** AI 单条消息最大字符（旧库 trim(1500)）。 */
    const val AI_MAX_CHARS = 1500
}