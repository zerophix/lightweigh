package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * po — 已绑定体脂秤。MAC 为主键（一台秤一行）。
 * 协议/算法编号只存本机。
 *
 * boundAt 仍是 TEXT（云端 bound_at 日期串，本 PR 不改云列）。
 * updatedAt / unboundAt 为 epoch ms UTC（DEC-S14 / DEC-S15）：解绑写 unboundAt，不删行。
 */
@Entity(tableName = "devices")
data class ScaleDevicePo(
    @PrimaryKey val mac: String,
    val name: String,
    val boundAt: String,
    val active: Boolean,
    val protocolId: String,
    val algorithmId: String,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0L,
    val unboundAt: Long? = null,
)
