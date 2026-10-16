package com.xcwk.scalehub.domain.types

/**
 * types — 领域层自有的错误类型（对应 Java 版 types 模块的 AppException）。
 *
 * Java 版把 types 独立成模块是为了跨服务共享；Android 单进程内无此需求，
 * 领域错误收进 domain.types，外层(application/trigger)负责转换为用户文案。
 * 领域内禁止抛 android.* 异常，也禁止吞异常返回魔法值(-1)。
 */
class DomainException(message: String) : RuntimeException(message)
