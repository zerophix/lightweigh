package com.xcwk.scalehub.feature.device

import com.xcwk.scalehub.domain.model.ScaleDevice

/** device — 四件套之 UiState（绑定列表 + 扫描发现）。 */
data class FoundScale(
    val name: String,
    val mac: String,
    val rssi: Int,
    val summary: String,
    val protocolId: String,
    val alreadyBound: Boolean,
)

data class DeviceUiState(
    val bound: List<ScaleDevice> = emptyList(),
    val found: List<FoundScale> = emptyList(),
    val scanning: Boolean = false,
    val notice: String = "",
    val message: String? = null,
)

sealed interface DeviceIntent {
    data object ToggleScan : DeviceIntent
    data class Bind(val found: FoundScale) : DeviceIntent
    data class Unbind(val mac: String) : DeviceIntent
    data class Activate(val mac: String) : DeviceIntent
    data object Refresh : DeviceIntent
}
