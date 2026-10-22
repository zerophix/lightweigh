package com.xcwk.scalehub.domain.repository

import com.xcwk.scalehub.domain.model.Member

/**
 * 本机测量用户仓储（旧库 MemberStore 语义切面）。
 * 至少保留一个用户；新增即成为当前用户（旧库 add :85 save(list, id)）。
 * 组合档案读写端口（单位偏好随用户档案同生命周期）。
 */
interface MemberRepository :
    MemberProfileReader,
    MemberProfileWriter {
    fun members(): List<Member>

    fun activeId(): String

    /** 下发给秤的用户编号（旧库 activeSlot :46-49）。 */
    fun activeSlot(): Int

    /** 新增并切换为当前用户；满 8 人返回 null。 */
    fun add(nickname: String): Member?

    fun switch(id: String)

    fun rename(id: String, nickname: String)

    /** 至少保留一人；删的是当前用户时改选列表第一个（旧库 remove :99-118）。 */
    fun remove(id: String): Boolean
}
