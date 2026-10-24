package com.xcwk.scalehub.feature.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-080 设备重命名纯逻辑单测。
 *
 * 覆盖可自动判定的部分：
 * - 名称校验（[checkDeviceName]）：空 / 纯空白 / 超 20 字符 不可保存；trim 后计数
 * - 呈现状态派生（[DeviceRenameUiState.canSave]）与输入纯函数
 * - 文案逐字对齐原型 `24-remaining-gaps.html` 的 `v-rename-device`
 *
 * 布局与视觉不在本测范围（需真机/截图）。
 */
class DeviceRenameTest {

    // ---- 名称校验 ----

    @Test
    fun `空名称不可保存`() {
        assertThat(checkDeviceName("")).isEqualTo(DeviceNameCheck.Empty)
        assertThat(DeviceRenameUiState(input = "").canSave).isFalse()
    }

    @Test
    fun `纯空白视同空名称`() {
        assertThat(checkDeviceName("   ")).isEqualTo(DeviceNameCheck.Empty)
        assertThat(checkDeviceName("\n\t ")).isEqualTo(DeviceNameCheck.Empty)
    }

    @Test
    fun `正好二十个字符可保存`() {
        val twenty = "一二三四五六七八九十一二三四五六七八九十"
        assertThat(twenty.length).isEqualTo(DEVICE_NAME_MAX_LENGTH)
        assertThat(checkDeviceName(twenty)).isEqualTo(DeviceNameCheck.Ok)
        assertThat(DeviceRenameUiState(input = twenty).canSave).isTrue()
    }

    @Test
    fun `超过二十个字符不可保存`() {
        val over = "一二三四五六七八九十一二三四五六七八九十多"
        assertThat(over.length).isEqualTo(DEVICE_NAME_MAX_LENGTH + 1)
        assertThat(checkDeviceName(over)).isEqualTo(DeviceNameCheck.TooLong)
        assertThat(DeviceRenameUiState(input = over).canSave).isFalse()
    }

    @Test
    fun `首尾空白不计入长度且保存时被裁掉`() {
        val padded = "  " + "一二三四五六七八九十一二三四五六七八九十" + "  "
        assertThat(checkDeviceName(padded)).isEqualTo(DeviceNameCheck.Ok)
        assertThat(sanitizeDeviceName(padded)).isEqualTo("一二三四五六七八九十一二三四五六七八九十")
    }

    @Test
    fun `中英文数字混合名称可保存`() {
        assertThat(checkDeviceName("客厅体脂秤 S1 2026")).isEqualTo(DeviceNameCheck.Ok)
    }

    // ---- 呈现状态 ----

    @Test
    fun `输入纯函数只改草稿`() {
        val base = DeviceRenameUiState()
        val typed = deviceRenameInputChanged(base, "客厅体脂秤")
        assertThat(typed.input).isEqualTo("客厅体脂秤")
        assertThat(typed.currentName).isEqualTo(base.currentName)
        assertThat(typed.canSave).isTrue()
    }

    @Test
    fun `初始态为空输入且不可保存`() {
        val state = DeviceRenameUiState()
        assertThat(state.input).isEmpty()
        assertThat(state.canSave).isFalse()
        assertThat(state.currentName).isEqualTo(DEVICE_RENAME_CURRENT_VALUE)
    }

    // ---- 文案（原型逐字） ----

    @Test
    fun `重命名页文案逐字对齐原型`() {
        assertThat(DEVICE_RENAME_TITLE).isEqualTo("设备重命名")
        assertThat(DEVICE_RENAME_HEADLINE).isEqualTo("重命名设备")
        assertThat(DEVICE_RENAME_SUBLINE).isEqualTo("给你的体脂秤一个好记的名字")
        assertThat(DEVICE_RENAME_CURRENT_LABEL).isEqualTo("当前名称")
        assertThat(DEVICE_RENAME_CURRENT_VALUE).isEqualTo("轻衡 S1 · 序列号 ****8A2F")
        assertThat(DEVICE_RENAME_NEW_LABEL).isEqualTo("新名称")
        assertThat(DEVICE_RENAME_PLACEHOLDER).isEqualTo("输入新名称")
        assertThat(DEVICE_RENAME_HINT).isEqualTo("最多 20 个字符，支持中英文、数字")
        assertThat(DEVICE_RENAME_SAVE).isEqualTo("保存名称")
    }

    @Test
    fun `说明横幅分两行且语义完整`() {
        assertThat(DEVICE_RENAME_NOTE).isEqualTo("重命名仅影响显示名称，\n不影响设备连接和测量数据。")
        assertThat(DEVICE_RENAME_NOTE).contains("重命名仅影响显示名称")
        assertThat(DEVICE_RENAME_NOTE).contains("不影响设备连接和测量数据")
    }
}
