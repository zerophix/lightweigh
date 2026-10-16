package com.xcwk.scalehub.application.service

import com.xcwk.scalehub.domain.repository.MeasurementRepository

/**
 * application service — 删除一条测量（历史页，删除前确认由 UI 承担——
 * 旧库需求：删除前确认、确认后执行）。时间戳即唯一键。
 */
class DeleteMeasurementService(
    private val repository: MeasurementRepository,
) {

    suspend fun execute(time: Long) {
        repository.delete(time)
    }
}
