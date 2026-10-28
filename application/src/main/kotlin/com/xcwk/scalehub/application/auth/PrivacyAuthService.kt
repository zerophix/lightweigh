package com.xcwk.scalehub.application.auth

/**
 * application service — 隐私授权编排。
 *
 * 职责：
 *  - 检查是否已同意隐私协议
 *  - 持久化同意结果
 *  - 撤回授权（清除同意状态）
 *  - 判断是否需要重新授权（版本变更）
 *
 * 旧库 PrivacyStore 语义移植。
 */
class PrivacyAuthService(
    private val state: PrivacyAuthStatePort,
) {

    companion object {
        /** 隐私协议当前版本。 */
        const val CURRENT_VERSION = "v2.1"
    }

    /** 是否已同意隐私协议。 */
    fun isAgreed(): Boolean = state.isAgreed()

    /**
     * 保存同意结果。
     * @return 是否保存成功
     */
    fun saveAgreed(): Boolean = runCatching {
        state.saveAgreed()
        true
    }.getOrDefault(false)

    /**
     * 撤回授权（用于设置页）。
     * 清除同意状态，下次启动需重新授权。
     */
    fun revoke(): Boolean = runCatching {
        state.revokeAgreed()
        true
    }.getOrDefault(false)

    /**
     * 是否需要重新授权。
     * 当本地记录的版本低于当前版本时，需要重新授权。
     */
    fun needsReauth(): Boolean {
        val agreedVersion = state.agreedVersion() ?: return true
        return agreedVersion != CURRENT_VERSION
    }

    /** 授权摘要信息（用于设置页显示）。 */
    fun summary(): PrivacyAuthSummary {
        return PrivacyAuthSummary(
            agreed = state.isAgreed(),
            version = state.agreedVersion() ?: "",
            agreedTime = state.agreedTime(),
        )
    }
}

/** 授权摘要。 */
data class PrivacyAuthSummary(
    val agreed: Boolean,
    val version: String,
    val agreedTime: Long,
)
