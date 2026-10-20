package com.xcwk.scalehub.application.service

import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus

import com.xcwk.scalehub.domain.repository.MeasurementRepository

/**
 * application service — 删除一条测量（历史页，删除前确认由 UI 承担——
 * 旧库需求：删除前确认、确认后执行）。时间戳即唯一键。
 *
 * 删完发 [DataChange.Measurements]（与新增同一类型：订阅方关心的是"测量数据变了"，
 * 不必区分增删），刷 UI 与推云由容器的事件订阅承担。
 */
class DeleteMeasurementService(
    private val repository: MeasurementRepository,
    private val refreshBus: DataRefreshBus? = null,
) {

    suspend fun execute(time: Long) {
        repository.delete(time)
        refreshBus?.notifyChanged(DataChange.Measurements)
    }
}
