package com.xcwk.scalehub.infrastructure.persistent.repository

import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.infrastructure.persistent.po.HealthLogPo
import com.xcwk.scalehub.infrastructure.persistent.po.MeasurementPo
import java.util.UUID

internal fun MeasurementPo.toMeasureRecord(): MeasureRecord = MeasureRecord(
    measuredAt, weightKg, impedanceOhm, fat, mac, algorithmId,
    male, ageYears, heightCm,
    recordId, updatedAt, deletedAt, syncStatus, note,
)

internal fun MeasureRecord.toMeasurementPo(memberId: String): MeasurementPo {
    val id = recordId.ifBlank { UUID.randomUUID().toString() }
    return MeasurementPo(
        recordId = id,
        measuredAt = time,
        weightKg = weight,
        impedanceOhm = impedance,
        fat = fat,
        mac = mac,
        algorithmId = algorithm,
        male = male,
        ageYears = ageYears,
        heightCm = heightCm,
        memberId = memberId,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncStatus = syncStatus,
        note = note,
    )
}

internal fun HealthLogPo.toHealthLog(): HealthLog = HealthLog(
    time = loggedAt,
    type = type,
    value = value,
    value2 = value2,
    label = label,
    note = note,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun HealthLog.toHealthLogPo(memberId: String): HealthLogPo = HealthLogPo(
    loggedAt = time,
    type = type,
    value = value,
    value2 = value2,
    label = label,
    note = note,
    memberId = memberId,
    updatedAt = if (updatedAt > 0) updatedAt else time,
    deletedAt = deletedAt,
)
