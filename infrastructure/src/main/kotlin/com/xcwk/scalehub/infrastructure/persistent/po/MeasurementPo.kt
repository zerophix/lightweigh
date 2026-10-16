package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * po — 持久化对象。表结构对应 spec/contract/data-schema.json 的 measurements。
 * fat = -1 是"未计算"哨兵（契约 default -1）。
 * v2 新增三列：体脂计算输入快照（可空，v1 旧数据为 NULL）。
 */
@Entity(tableName = "measurements")
data class MeasurementPo(
    @PrimaryKey val measuredAt: Long,
    val weightKg: Double,
    val impedanceOhm: Double,
    val fat: Double,
    val mac: String,
    val algorithmId: String,
    val male: Boolean?,
    val ageYears: Int?,
    val heightCm: Int?,
)
