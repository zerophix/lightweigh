package com.xcwk.scalehub.application.auth

/**
 * 隐私授权状态端口（M2a）—— 抽象隐私协议的持久化与撤回。
 *
 * 实现者负责本地存储授权结果；应用层只编排语义。
 */
interface PrivacyAuthStatePort {
    /** 是否已同意隐私协议。 */
    fun isAgreed(): Boolean

    /** 持久化同意结果。 */
    fun saveAgreed()

    /** 撤回授权（用于设置页）。 */
    fun revokeAgreed()

    /** 同意版本（用于判断是否需要重新授权）。 */
    fun agreedVersion(): String?

    /** 记录同意时间戳。 */
    fun agreedTime(): Long
}
