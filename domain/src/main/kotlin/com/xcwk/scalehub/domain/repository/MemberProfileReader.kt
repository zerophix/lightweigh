package com.xcwk.scalehub.domain.repository

import com.xcwk.scalehub.domain.model.UserUnitPreferences

/**
 * 成员档案读取端口（用例的编排输入 + 我的页展示）。
 * ageYears 语义：出生年缺失时返回 0（守卫在实现侧），算法对无效输入自会给出无效结果；
 * 月日齐备时为周岁（生日未到减一），缺失时退化为年差（旧库口径）——见 MemberProfile.ageFrom。
 * birthYear/birthMonth/birthDay/nickname 未设置时分别为 0/0/0/空串（"未填写"展示语义）。
 */
interface MemberProfileReader {
    fun male(): Boolean
    fun ageYears(): Int
    fun heightCm(): Int
    fun birthYear(): Int
    fun birthMonth(): Int
    fun birthDay(): Int
    fun nickname(): String

    /** 单位偏好（重量/长度单位、是否应用到衍生指标）。 */
    fun unitPreferences(): UserUnitPreferences
}
