package com.xcwk.scalehub.feature.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-105 设备详情 / 设置页（原型 04 `v-settings`）纯逻辑单测。
 *
 * 覆盖可自动判定的部分：
 * - 信息行渲染完整性（标签 / 值 / 标签胶囊 / 箭头），逐字对齐原型
 * - 解绑二次确认流程（[deviceDetailReduce]：请求 → 取消 / 确认）
 * - 回调触发路由（[deviceDetailRowAction] → 各 onXxx 动作，无入口行不得给箭头）
 *
 * 视觉落位（圆角 / 间距是否与原型像素一致）不在此测——`:app` 无 compose ui-test 基建，
 * 属已声明盲区（与 device 模块既有测试同口径）。
 */
class DeviceDetailScreenTest {

    private fun state(
        name: String = "轻衡 S1",
        connected: Boolean = true,
        batteryPercent: Int = 82,
    ) = DeviceDetailUiState(
        name = name,
        connected = connected,
        batteryPercent = batteryPercent,
    )

    // ---- 信息行渲染完整性 ----

    @Test
    fun `信息行五条按原型顺序排列`() {
        val rows = deviceDetailRows(state())
        assertThat(rows.map { it.label }).containsExactly(
            "设备名称",
            "体重单位",
            "固件版本",
            "协议信息",
            "绑定成员",
        ).inOrder()
    }

    @Test
    fun `设备名称行值取注入名称`() {
        val row = deviceDetailRows(state(name = "主卧秤")).first { it.kind == DeviceDetailRowKind.Name }
        assertThat(row.label).isEqualTo("设备名称")
        assertThat(row.value).isEqualTo("主卧秤")
        assertThat(row.chevron).isTrue()
    }

    @Test
    fun `体重单位行逐字对齐原型且无箭头`() {
        val row = deviceDetailRows(state()).first { it.kind == DeviceDetailRowKind.WeightUnit }
        assertThat(row.label).isEqualTo("体重单位")
        assertThat(row.value).isEqualTo("公斤 (kg)")
        // 回调契约无单位切换，行不给箭头（避免死入口）
        assertThat(row.chevron).isFalse()
    }

    @Test
    fun `固件行展示版本与最新标签`() {
        val row = deviceDetailRows(state()).first { it.kind == DeviceDetailRowKind.Firmware }
        assertThat(row.label).isEqualTo("固件版本")
        assertThat(row.value).isEqualTo("v1.2.0")
        assertThat(row.tag).isEqualTo("最新")
        assertThat(row.chevron).isFalse()
    }

    @Test
    fun `固件非最新时标签转为可更新`() {
        val rows = deviceDetailRows(state().copy(firmwareUpToDate = false, firmwareVersion = "v1.1.0"))
        val row = rows.first { it.kind == DeviceDetailRowKind.Firmware }
        assertThat(row.value).isEqualTo("v1.1.0")
        assertThat(row.tag).isEqualTo(UPDATE_AVAILABLE_TAG)
    }

    @Test
    fun `协议行与绑定成员行由注入值派生`() {
        val rows = deviceDetailRows(state().copy(protocolId = "0x22", linkedMemberCount = 2))
        assertThat(rows.first { it.kind == DeviceDetailRowKind.Protocol }.value).isEqualTo("协议 0x22")
        assertThat(rows.first { it.kind == DeviceDetailRowKind.Members }.value).isEqualTo("已关联 2 位成员")
        assertThat(rows.first { it.kind == DeviceDetailRowKind.Members }.chevron).isTrue()
    }

    // ---- 连接态文案（设备卡） ----

    @Test
    fun `已连接文案逐字对齐原型`() {
        assertThat(deviceDetailStatusText(connected = true, batteryPercent = 82))
            .isEqualTo("● 已连接 · 电量 82%")
    }

    @Test
    fun `未连接不显示电量残影`() {
        assertThat(deviceDetailStatusText(connected = false, batteryPercent = 82))
            .isEqualTo("○ 未连接 · 电量 82%")
    }

    @Test
    fun `电量越界不编造数值`() {
        assertThat(deviceDetailStatusText(connected = true, batteryPercent = -1))
            .isEqualTo("● 已连接")
        assertThat(deviceDetailStatusText(connected = true, batteryPercent = 140))
            .isEqualTo("● 已连接")
    }

    // ---- 解绑二次确认流程 ----

    @Test
    fun `点删除设备只打开确认弹层不上交解绑`() {
        val next = deviceDetailReduce(state(), DeviceDetailIntent.RequestUnbind)
        assertThat(next.state.unbindConfirmVisible).isTrue()
        assertThat(next.action).isNull()
    }

    @Test
    fun `弹层取消关闭且不上交解绑`() {
        val opened = deviceDetailReduce(state(), DeviceDetailIntent.RequestUnbind).state
        val next = deviceDetailReduce(opened, DeviceDetailIntent.CancelUnbind)
        assertThat(next.state.unbindConfirmVisible).isFalse()
        assertThat(next.action).isNull()
    }

    @Test
    fun `弹层确认关闭并上交解绑动作`() {
        val opened = deviceDetailReduce(state(), DeviceDetailIntent.RequestUnbind).state
        val next = deviceDetailReduce(opened, DeviceDetailIntent.ConfirmUnbind)
        assertThat(next.state.unbindConfirmVisible).isFalse()
        assertThat(next.action).isEqualTo(DeviceDetailAction.Unbind)
    }

    @Test
    fun `解绑确认标题带设备名逐字对齐原型`() {
        assertThat(deviceDetailUnbindTitle("轻衡 S1")).isEqualTo("解绑「轻衡 S1」？")
    }

    @Test
    fun `未打开弹层时取消是幂等的`() {
        val next = deviceDetailReduce(state(), DeviceDetailIntent.CancelUnbind)
        assertThat(next.state).isEqualTo(state())
        assertThat(next.action).isNull()
    }

    // ---- 回调触发路由 ----

    @Test
    fun `行点击路由到对应回调`() {
        assertThat(deviceDetailRowAction(DeviceDetailRowKind.Name)).isEqualTo(DeviceDetailAction.Rename)
        assertThat(deviceDetailRowAction(DeviceDetailRowKind.Firmware))
            .isEqualTo(DeviceDetailAction.OpenFirmware)
        assertThat(deviceDetailRowAction(DeviceDetailRowKind.Members))
            .isEqualTo(DeviceDetailAction.OpenMemberLink)
    }

    @Test
    fun `无入口的行不给动作也不给箭头`() {
        val rows = deviceDetailRows(state())
        val actionless = rows.filter { deviceDetailRowAction(it.kind) == null }
        assertThat(actionless.map { it.kind })
            .containsExactly(DeviceDetailRowKind.WeightUnit, DeviceDetailRowKind.Protocol)
        assertThat(actionless.none { it.chevron }).isTrue()
    }

    @Test
    fun `行点击永不直接触发解绑`() {
        val actions = DeviceDetailRowKind.values().mapNotNull { deviceDetailRowAction(it) }
        assertThat(actions).doesNotContain(DeviceDetailAction.Unbind)
    }

    // ---- 断开连接（本页乐观态） ----

    @Test
    fun `断开连接只改连接态不影响其它字段`() {
        val before = state()
        val next = deviceDetailReduce(before, DeviceDetailIntent.Disconnect)
        assertThat(next.state.connected).isFalse()
        assertThat(next.state.name).isEqualTo(before.name)
        assertThat(next.state.firmwareVersion).isEqualTo(before.firmwareVersion)
        assertThat(next.action).isNull()
    }

    // ---- 文案常量逐字对齐原型 v-settings ----

    @Test
    fun `界面文案逐字对齐原型`() {
        assertThat(DEVICE_DETAIL_TITLE).isEqualTo("设备设置")
        assertThat(DEVICE_DETAIL_DISCONNECT).isEqualTo("断开连接")
        assertThat(DEVICE_DETAIL_DELETE).isEqualTo("删除设备")
        assertThat(DEVICE_DETAIL_NOTE).isEqualTo("删除设备不会删除已同步的测量记录。")
        assertThat(DEVICE_DETAIL_UNBIND_BODY).isEqualTo("解绑后需重新配对才能测量。")
        assertThat(DEVICE_DETAIL_UNBIND_BODY_STRONG).isEqualTo("已同步的测量记录不受影响")
        assertThat(DEVICE_DETAIL_UNBIND_CANCEL).isEqualTo("取消")
        assertThat(DEVICE_DETAIL_UNBIND_CONFIRM).isEqualTo("解绑")
        assertThat(DEVICE_DETAIL_TAG_LATEST).isEqualTo("最新")
    }
}
