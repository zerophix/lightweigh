package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.repository.MemberProfileReader

/** 我的页展示读模型（直接按表单字段形状，UI 免组装）。 */
data class CurrentProfile(
    val male: Boolean,
    val ageYears: Int,
    val heightCm: Int,
    val birthYear: Int,
    val birthMonth: Int,
    val birthDay: Int,
    val nickname: String,
) {
    /** 未设置态（需求文档："未设身高时可从首页进入我的资料"）。 */
    val heightSet: Boolean get() = heightCm > 0
    val birthSet: Boolean get() = birthYear > 0
}

/** query — 读当前档案。 */
class ProfileQuery(private val reader: MemberProfileReader) {

    fun current(): CurrentProfile = CurrentProfile(
        male = reader.male(),
        ageYears = reader.ageYears(),
        heightCm = reader.heightCm(),
        birthYear = reader.birthYear(),
        birthMonth = reader.birthMonth(),
        birthDay = reader.birthDay(),
        nickname = reader.nickname(),
    )
}
