package com.xcwk.scalehub.feature.member

/**
 * 儿童模式（T-071）。
 *
 * 口径（隐私优先，默认收紧）：
 * - 未成年人（< 18 岁）默认关闭 AI 解读、报告导出与跨成员分享；
 * - 只有监护人明确授权后才开放，授权状态由调用方持有并持久化；
 * - 年龄未知一律按未成年人处理（宁可多要一次授权）。
 */
enum class MemberFeature { AiInsight, ShareReport, CrossMemberView }

object ChildModePolicy {

    const val ADULT_AGE = 18

    /** 未知年龄按儿童处理。 */
    fun isMinor(age: Int?): Boolean = age == null || age < ADULT_AGE

    /**
     * 某项功能对当前成员是否可用。
     *
     * @param age 成员年龄；null 表示未知
     * @param guardianConsent 监护人是否已授权（仅未成年人需要）
     */
    fun allows(feature: MemberFeature, age: Int?, guardianConsent: Boolean): Boolean {
        if (!isMinor(age)) return true
        if (!guardianConsent) return false
        // 即使授权，跨成员数据仍不允许在儿童模式下暴露
        return feature != MemberFeature.CrossMemberView
    }

    /** 需要提示"已开启儿童模式"的场景：未成年人且未授权。 */
    fun needsGuardianPrompt(age: Int?, guardianConsent: Boolean): Boolean =
        isMinor(age) && !guardianConsent
}
