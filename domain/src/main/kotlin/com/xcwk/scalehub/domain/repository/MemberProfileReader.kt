package com.xcwk.scalehub.domain.repository

/**
 * 成员档案读取端口（用例的编排输入）。
 * ageYears 语义：出生年缺失时返回 0（守卫在实现侧），算法对无效输入自会给出无效结果。
 */
interface MemberProfileReader {
    fun male(): Boolean
    fun ageYears(): Int
    fun heightCm(): Int
}
