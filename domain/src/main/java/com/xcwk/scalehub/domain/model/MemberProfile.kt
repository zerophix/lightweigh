package com.xcwk.scalehub.domain.model

import com.xcwk.scalehub.domain.types.DomainException
import java.time.LocalDate

/**
 * 成员档案值对象 —— 移植旧库 ProfilePages 编辑规则（M4 我的切片，DEC-022）。
 *
 * 构造期守卫（来源标注）：
 *  - heightCm ∈ [100,230]   [旧库身高滚轮 100..230：ProfilePages.kt:601,467]
 *  - birthMonth ∈ 1..12     [旧库 BirthSheet 校验：ProfilePages.kt:521]
 *  - birthDay ∈ 1..31       [旧库按 1..31 宽松校验：ProfilePages.kt:522]
 *  - birthYear ∈ [1930, 今年] [旧库年轮 coerceIn(1930,thisYear)：ProfilePages.kt:611,619]
 *  - nickname ≤ 20 字符      [旧库 trim().take(20)：ProfilePages.kt:491]
 *
 * 语义边界：未设置态（heightCm=0 等）是 Reader 侧默认值，不经过本构造——
 * 保存必须是完整合法档案；算法读侧对无效输入的行为不变（Reader 契约）。
 */
data class MemberProfile(
    val male: Boolean,
    val birthYear: Int,
    val birthMonth: Int,
    val birthDay: Int,
    val heightCm: Int,
    val nickname: String,
) {
    init {
        if (heightCm < 100 || heightCm > 230) {
            throw DomainException("身高 $heightCm cm 超出范围 100..230")
        }
        if (birthMonth !in 1..12) {
            throw DomainException("出生月 $birthMonth 非法（1..12）")
        }
        if (birthDay !in 1..31) {
            throw DomainException("出生日 $birthDay 非法（1..31）")
        }
        val thisYear = LocalDate.now().year
        if (birthYear < 1930 || birthYear > thisYear) {
            throw DomainException("出生年 $birthYear 非法（1930..$thisYear）")
        }
        if (nickname.length > 20) {
            throw DomainException("昵称过长（≤20 字符）")
        }
    }

    /** 完整生日的周岁（今天未到生日则减一）。 */
    fun ageYears(today: LocalDate = LocalDate.now()): Int =
        ageFrom(birthYear, birthMonth, birthDay, today)

    companion object {
        /**
         * 部件直算周岁（Reader 路径用，不要求构造完整）：
         * 出生年缺失返回 0（沿旧库守卫）；月日缺失（0 或越界）退化为年差（旧库口径）。
         */
        fun ageFrom(birthYear: Int, birthMonth: Int, birthDay: Int, today: LocalDate): Int {
            if (birthYear <= 0) return 0
            var age = today.year - birthYear
            val birthdayKnown = birthMonth in 1..12 && birthDay in 1..31
            if (birthdayKnown &&
                (today.monthValue < birthMonth || (today.monthValue == birthMonth && today.dayOfMonth < birthDay))
            ) {
                age--
            }
            return age.coerceAtLeast(0)
        }
    }
}
