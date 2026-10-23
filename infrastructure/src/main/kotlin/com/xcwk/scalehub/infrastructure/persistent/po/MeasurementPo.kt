package com.xcwk.scalehub.infrastructure.persistent.po

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * po — 测量行（DEC-057 / Room v8）。
 *
 * PK = recordId（客户端 UUID）。measuredAt 保留为采样时刻，
 * 业务防重 UNIQUE(memberId, measuredAt)。冲突与水位看 updatedAt。
 * deletedAt 为软删墓碑；syncStatus 0/1/2；note 默认空串。
 * fat = -1 是「未计算」哨兵。
 */
@Entity(
    tableName = "measurements",
    indices = [
        Index(value = ["memberId", "measuredAt"], unique = true),
        Index(value = ["updatedAt"]),
        Index(value = ["syncStatus"]),
    ],
)
data class MeasurementPo(
    @PrimaryKey val recordId: String,
    val measuredAt: Long,
    val weightKg: Double,
    val impedanceOhm: Double,
    val fat: Double,
    val mac: String,
    val algorithmId: String,
    val male: Boolean?,
    val ageYears: Int?,
    val heightCm: Int?,
    @ColumnInfo(defaultValue = "1")
    val memberId: String,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0")
    val syncStatus: Int,
    @ColumnInfo(defaultValue = "''")
    val note: String,
)
