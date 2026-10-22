package com.xcwk.scalehub.feature.measure.scale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.ble.BlePort
import com.xcwk.scalehub.application.debug.DebugLog
import com.xcwk.scalehub.application.measure.MeasureSession
import com.xcwk.scalehub.application.measure.MeasureState
import com.xcwk.scalehub.application.service.RecordMeasurementService
import com.xcwk.scalehub.domain.model.UserUnitPreferences
import com.xcwk.scalehub.domain.repository.DeviceRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * scale — 四件套之 ViewModel：宿主 MeasureSession 的薄壳（UI 事件转发 + 会话生命周期）。
 *
 * 会话生命周期（DEC-042）：ScaleViewModel 是 Activity 级的（自定义导航壳未按
 * destination 提供 ViewModelStoreOwner），overlay 弹出不触发 onCleared。为此：
 *  - 屏幕离开时调 [stop] 显式终结会话（ScaleRoute 的 DisposableEffect）；
 *  - [start] 检测到会话已是终态时重建新会话（Close 是终态不可复活），
 *    重进测量页才能重新开始，而不是拿着死会话空转。
 */
class ScaleViewModel(
    private val ble: BlePort,
    private val devices: DeviceRepository,
    private val recordService: RecordMeasurementService,
    private val profile: MemberProfileReader,
    private val lastWeightTenths: suspend () -> Int,
    private val skipWeightOnlyPrompt: () -> Boolean,
    private val memberSlot: () -> Int = { 1 },
) : ViewModel() {

    private var session = newSession()
    private var collectJob: kotlinx.coroutines.Job? = null

    /** 当前会话的状态流（重建会话时随 [session] 切换）。 */
    private val _state = MutableStateFlow(MeasureState())
    val state: StateFlow<MeasureState> = _state.asStateFlow()

    /** 单位偏好（响应式，切换成员/设置变更时自动刷新）。 */
    private val _unitPrefs = MutableStateFlow(profile.unitPreferences())
    val unitPrefs: StateFlow<UserUnitPreferences> = _unitPrefs

    init {
        // 把当前会话的状态转发出去；会话重建时旧 collect 先取消再挂新的
        collectSessionState()
        // 监听单位偏好变更（成员切换/设置修改）
        viewModelScope.launch {
            _unitPrefs.update { profile.unitPreferences() }
        }
    }

    private fun collectSessionState() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            session.state.collect { _state.value = it }
        }
    }

    private fun newSession() = MeasureSession(
        ble = ble,
        devices = devices,
        recordService = recordService,
        profile = profile,
        lastWeightTenths = lastWeightTenths,
        scope = viewModelScope,
        skipWeightOnlyPrompt = skipWeightOnlyPrompt,
        memberSlot = memberSlot,
    )

    fun start() {
        if (session.closed) {
            DebugLog.add("session", "vm: session was closed, recreating for new measurement")
            session = newSession()
            collectSessionState()
        }
        session.start()
    }

    /** 屏幕离开时终结会话（DEC-042）：停扫描/断连接/取消全部定时器。 */
    fun stop() {
        if (!session.closed) session.stop()
    }

    fun onIntent(intent: ScaleIntent) {
        when (intent) {
            ScaleIntent.ConfirmSave -> session.confirmSave()
            ScaleIntent.Retry -> session.retry()
            ScaleIntent.Reconnect -> session.reconnect()
            ScaleIntent.Stop -> stop()
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
                    ble = app.container.blePort,
                    devices = app.container.deviceRepository,
                    recordService = app.container.recordMeasurementService,
                    profile = app.container.profileReader,
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