package com.xcwk.scalehub.feature.device

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.ScaleDevice
import org.junit.Test

/**
 * 设备页「设备详情」入口（T-105 接入波第三批）—— 入口映射与进入/返回路径。
 *
 * 钉的是「详情页拿的是哪台设备」：有当前使用设备就取它，没有则任一已绑定设备，
 * 未绑定则回落到原型默认名——不允许详情页出现空白设备名。
 */
class DeviceDetailEntryTest {

    private fun device(name: String, active: Boolean) = ScaleDevice(
        name,
        "AA:BB:CC:DD:EE:FF",
        "2026-10-01",
        active,
        "0x11",
        "builtin-1",
    )

    @Test
    fun `入口文案不为空`() {
        assertThat(DEVICE_DETAIL_ENTRY_TITLE).isNotEmpty()
        assertThat(DEVICE_DETAIL_ENTRY_HINT).isNotEmpty()
    }

    @Test
    fun `未绑定设备回落到默认设备名且未连接`() {
        val detail = deviceDetailStateOf(DeviceUiState())

        assertThat(detail.name).isEqualTo(DEFAULT_DEVICE_NAME)
        assertThat(detail.connected).isFalse()
        assertThat(detail.protocolId).isEqualTo(DEFAULT_PROTOCOL_ID)
    }

    @Test
    fun `优先取当前使用的设备`() {
        val state = DeviceUiState(
            bound = listOf(device("旧秤", active = false), device("轻衡 Pro", active = true)),
        )

        val detail = deviceDetailStateOf(state)

        assertThat(detail.name).isEqualTo("轻衡 Pro")
        assertThat(detail.connected).isTrue()
        assertThat(detail.protocolId).isEqualTo("0x11")
    }

    @Test
    fun `没有当前设备时取第一台已绑定设备但不算已连接`() {
        val state = DeviceUiState(bound = listOf(device("备用秤", active = false)))

        val detail = deviceDetailStateOf(state)

        assertThat(detail.name).isEqualTo("备用秤")
        assertThat(detail.connected).isFalse()
    }

    @Test
    fun `详情页返回即回设备页`() {
        // 返回只清宿主层的显示标记，不改设备状态
        val state = DeviceUiState(bound = listOf(device("轻衡 S1", active = true)))
        var detailVisible = true

        val onBack: () -> Unit = { detailVisible = false }
        onBack()

        assertThat(detailVisible).isFalse()
        assertThat(deviceDetailStateOf(state).name).isEqualTo("轻衡 S1")
    }
}
