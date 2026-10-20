package com.xcwk.scalehub.feature.measure.scale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.ble.BlePort
import com.xcwk.scalehub.application.measure.MeasureSession
import com.xcwk.scalehub.application.measure.MeasureState
import com.xcwk.scalehub.application.service.RecordMeasurementService
import com.xcwk.scalehub.domain.repository.DeviceRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

/** scale — 四件套之 ViewModel：宿主 MeasureSession 的薄壳（UI 事件转发 + 会话生命周期）。 */
class ScaleViewModel(
    ble: BlePort,
    devices: DeviceRepository,
    recordService: RecordMeasurementService,
    profile: MemberProfileReader,
    lastWeightTenths: suspend () -> Int,
    skipWeightOnlyPrompt: () -> Boolean,
    memberSlot: () -> Int = { 1 },
) : ViewModel() {

    private val session = MeasureSession(
        ble = ble,
        devices = devices,
        recordService = recordService,
        profile = profile,
        lastWeightTenths = lastWeightTenths,
        scope = viewModelScope,
        skipWeightOnlyPrompt = skipWeightOnlyPrompt,
        memberSlot = memberSlot,
    )

    val state: StateFlow<MeasureState> get() = session.state

    fun start() = session.start()

    fun onIntent(intent: ScaleIntent) {
        when (intent) {
            ScaleIntent.ConfirmSave -> session.confirmSave()
            ScaleIntent.Retry -> session.retry()
            ScaleIntent.Reconnect -> session.reconnect()
            ScaleIntent.Stop -> session.stop()
        }
    }

    override fun onCleared() {
        session.stop()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                ScaleViewModel(
                    ble = app.container.bleLink,
                    devices = app.container.deviceRepository,
                    recordService = app.container.recordMeasurementService,
                    profile = app.container.profileStore,
                    lastWeightTenths = {
                        // 经查询服务读，不直捅仓储（与其他读路径同口径，DEC-034 组 E）
                        app.container.measurementQuery.recent(1).firstOrNull()
                            ?.let { record -> (record.weight * 10.0).roundToInt() } ?: 0
                    },
                    skipWeightOnlyPrompt = app.container.skipWeightOnlyPrompt,
                    memberSlot = app.container.activeMemberSlot,
                )
            }
        }
    }
}

/** scale — UI 事件（会话行为见 MeasureSession）。 */
sealed interface ScaleIntent {
    data object ConfirmSave : ScaleIntent
    data object Retry : ScaleIntent
    data object Reconnect : ScaleIntent
    data object Stop : ScaleIntent
}
