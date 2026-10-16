package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * po — 持久化对象。表结构对应 spec/contract/data-schema.json 的 measurements
 * （v2 起步列集；segments/mode 待 8 电极分支落地时随 version 升级，走 Migration）。
 * fat = -1 是"未计算"哨兵，与契约 default -1 一致。
 */
@Entity(tableName = "measurements")
data class MeasurementPo(
    @PrimaryKey val measuredAt: Long,
    val weightKg: Double,
    val impedanceOhm: Double,
    val fat: Double,
    val mac: String,
    val algorithmId: String,
)
