package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * po — 本机测量用户（DEC-S04）。无家庭云、无邀请字段。
 * active 同时仅一行应为当前成员；slot 在本机唯一。
 */
@Entity(
    tableName = "members",
    indices = [Index(value = ["slot"], unique = true)],
)
data class MemberPo(
    @PrimaryKey val id: String,
    val slot: Int,
    val nickname: String,
    val createdAt: Long,
    val active: Boolean,
    val updatedAt: Long,
)
