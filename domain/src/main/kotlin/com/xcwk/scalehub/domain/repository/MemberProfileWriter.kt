package com.xcwk.scalehub.domain.repository

import com.xcwk.scalehub.domain.model.MemberProfile

/**
 * 成员档案写入端口（M4 我的切片）。
 * 语义承诺：save 成功 = 本地已持久化；云同步是独立端口职责（离线优先）。
 * 入参必须是通过构造守卫的完整档案（未设置态不入库）。
 */
interface MemberProfileWriter {
    suspend fun save(profile: MemberProfile)
}
