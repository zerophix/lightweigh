package com.xcwk.scalehub.feature.device

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xcwk.scalehub.App
import com.xcwk.scalehub.application.ble.BlePort
import com.xcwk.scalehub.application.ble.BleScanListener
import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.domain.protocol.Protocols
import com.xcwk.scalehub.domain.repository.DeviceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * device — ViewModel：扫描与绑定编排（旧库 BindActivity 移植，行号随注释）。
 * 只有协议层认出的广播才进发现列表（:143-150 Protocols.match 过滤）；
 * 15s 扫描超时停止（:40 SCAN_TIMEOUT_MS）。权限与蓝牙开关门在 trigger 层。
 */
class DeviceViewModel(
    private val ble: BlePort,
    private val devices: DeviceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceUiState())
    val state: StateFlow<DeviceUiState> = _state.asStateFlow()

    private var timeoutJob: Job? = null

    init {
        refresh()
    }

    fun onIntent(intent: DeviceIntent) {
        when (intent) {
            DeviceIntent.ToggleScan -> if (_state.value.scanning) stopScan() else startScan()
            DeviceIntent.Pause -> stopScan()
            is DeviceIntent.Bind -> bind(intent.found)
            is DeviceIntent.QrBind -> bindQr(intent.name, intent.mac)
            is DeviceIntent.Unbind -> {
                devices.unbind(intent.mac)
                refresh()
            }
            is DeviceIntent.Activate -> {
                devices.activate(intent.mac)
                refresh()
            }
            DeviceIntent.Refresh -> refresh()
        }
    }

    override fun onCleared() {
        timeoutJob?.cancel()
        ble.stopScan()
    }

    private fun startScan() {
        val found = mutableListOf<FoundScale>()
        _state.update { it.copy(found = found, scanning = true, message = null, notice = "请踩亮体脂秤，确保设备处于可发现状态") }
        ble.scan(
            object : BleScanListener {
                override fun onScanStarted() {
                    _state.update { it.copy(notice = "请踩亮体脂秤，确保设备处于可发现状态") }
                }

                override fun onScanStop() {
                    timeoutJob?.cancel()
                    val count = _state.value.found.size
                    _state.update {
                        it.copy(
                            scanning = false,
                            notice = if (count == 0) "未发现附近体脂秤，请确认体脂秤已亮屏唤醒并靠近手机" else it.notice,
                        )
                    }
                }

                override fun onScanFailed(reason: String) {
                    _state.update { it.copy(notice = reason, scanning = false) }
                }

                override fun onDeviceFound(name: String, mac: String, rssi: Int, scanRecord: ByteArray?) {
                    if (scanRecord == null) return
                    val match = Protocols.match(scanRecord)
                    if (match == null) {
                        // 未被任何协议认出的广播——排障关键证据（v2 与旧库一致的静默丢弃，仅加日志）
                        Log.d(
                            "BLE_SCAN",
                            "unmatched device: name=$name mac=$mac hex=" +
                                scanRecord.joinToString("") { "%02X".format(it) },
                        )
                        return
                    }
                    Log.d("BLE_SCAN", "matched: name=$name mac=$mac protocol=${match.protocolId}")
                    val current = _state.value.found.filter { it.mac != mac }
                    _state.update {
                        it.copy(
                            found = current + FoundScale(
                                name, mac, rssi, match.summary, match.protocolId,
                                alreadyBound = devices.contains(mac),
                            ),
                            notice = "发现 ${current.size + 1} 台体脂秤",
                        )
                    }
                }
            },
        )
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT_MS)
            ble.stopScan()
        }
    }

    private fun stopScan() {
        timeoutJob?.cancel()
        ble.stopScan()
    }

    /** 扫码路径固定 okok-1（旧库 finishBind(qr.name, qr.mac, PROTOCOL_OKOK_1)）。 */
    private fun bindQr(name: String, mac: String) {
        stopScan()
        val existed = devices.contains(mac)
        devices.bind(name.ifBlank { "智能体脂秤" }, mac, ScaleDevice.PROTOCOL_OKOK_1)
        refresh()
        _state.update { it.copy(message = (if (existed) "已切换到 " else "已绑定 ") + (devices.device(mac)?.name ?: mac)) }
    }

    private fun bind(found: FoundScale) {
        stopScan()
        val existed = devices.contains(found.mac)
        devices.bind(found.name, found.mac, found.protocolId)
        refresh()
        _state.update { it.copy(message = (if (existed) "已切换到 " else "已绑定 ") + (devices.device(found.mac)?.name ?: found.name)) }
    }

    private fun refresh() {
        _state.update { it.copy(bound = devices.devices()) }
    }

    companion object {
        const val SCAN_TIMEOUT_MS = 15_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                DeviceViewModel(app.container.bleLink, app.container.deviceRepository)
            }
        }
    }
}
