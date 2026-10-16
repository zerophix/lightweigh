package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * po — 已绑定体脂秤。MAC 为主键（一台秤一行）。
 * 协议/算法编号只存本机（沿旧库：云端 devices 表仅 owner_id/mac/name/bound_at/active，
 * 协议不随云走，恢复的设备默认 okok-1，旧库 CloudSync.kt:210-218）。
 */
@Entity(tableName = "devices")
data class ScaleDevicePo(
    @PrimaryKey val mac: String,
    val name: String,
    val boundAt: String,
    val active: Boolean,
    val protocolId: String,
    val algorithmId: String,
)
