package com.xcwk.scalehub.feature.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-103 配对阶段（连接中 / 连接成功 / 连接失败）的单测。
 *
 * 覆盖验收中**唯一可自动判定**的部分：
 * - 逐字文案（原型 `18-device-setup.html` 的 v-connecting / v-connected）
 * - 子状态机（[deviceConnectReduce]）：进度前进、成功、失败、重试、取消后的复位
 *
 * 视觉布局不在此测（需真机/截图）。
 */
class DeviceConnectFlowTest {

    // ---- v-connecting 文案 ----

    @Test
    fun `连接中文案逐字对齐原型`() {
        assertThat(TITLE_CONNECTING).isEqualTo("连接设备")
        assertThat(connectingHeadline("轻衡 S1")).isEqualTo("正在连接 轻衡 S1…")
        assertThat(CONNECTING_SUBLINE).isEqualTo("请保持手机靠近体脂秤，\n轻踩秤面确保唤醒")
        assertThat(ACTION_SIMULATE_SUCCESS).isEqualTo("模拟成功")
        assertThat(ACTION_CANCEL).isEqualTo("取消")
    }

    @Test
    fun `四步进度文案逐字对齐原型`() {
        assertThat(ConnectStage.values().map { connectStageLabel(it) }).containsExactly(
            "蓝牙已开启",
            "已发现设备",
            "建立连接…",
            "同步设备信息",
        ).inOrder()
    }

    // ---- v-connected 文案 ----

    @Test
    fun `连接成功文案逐字对齐原型`() {
        assertThat(TITLE_CONNECTED).isEqualTo("连接成功")
        assertThat(CONNECTED_TITLE).isEqualTo("连接成功")
        assertThat(connectedSubtitle("轻衡 S1")).isEqualTo("「轻衡 S1」已连接，\n正在检查固件版本…")
        assertThat(ACTION_GO_FIRMWARE).isEqualTo("继续")
    }

    // ---- 失败分支文案（原型未画，本流程补的出口） ----

    @Test
    fun `失败分支给出重试与返回两个出口`() {
        assertThat(TITLE_CONNECT_FAILED).isEqualTo("连接失败")
        assertThat(ACTION_RETRY).isEqualTo("重试")
        assertThat(ACTION_BACK_TO_CONFIRM).isEqualTo("返回设备列表")
        assertThat(connectFailureText("")).isEqualTo(CONNECT_FAILED_DEFAULT_REASON)
        assertThat(connectFailureText("蓝牙已断开")).isEqualTo("蓝牙已断开")
    }

    // ---- 行进条状态 ----

    @Test
    fun `行进条按当前阶段划分已完成进行中未开始`() {
        val current = ConnectStage.Establishing
        assertThat(connectStageState(ConnectStage.BluetoothOn, current)).isEqualTo(ConnectStageState.Done)
        assertThat(connectStageState(ConnectStage.DeviceFound, current)).isEqualTo(ConnectStageState.Done)
        assertThat(connectStageState(ConnectStage.Establishing, current)).isEqualTo(ConnectStageState.Active)
        assertThat(connectStageState(ConnectStage.SyncingInfo, current)).isEqualTo(ConnectStageState.Pending)
    }

    // ---- 子状态机 ----

    @Test
    fun `初始为连接中且停在建立连接`() {
        val state = DeviceConnectUiState()
        assertThat(state.step).isEqualTo(DeviceConnectStep.Connecting)
        assertThat(state.stage).isEqualTo(ConnectStage.Establishing)
        assertThat(state.failureReason).isEmpty()
    }

    @Test
    fun `进度可前进`() {
        val next = deviceConnectReduce(
            DeviceConnectUiState(),
            DeviceConnectIntent.StageChanged(ConnectStage.SyncingInfo),
        )
        assertThat(next.stage).isEqualTo(ConnectStage.SyncingInfo)
        assertThat(next.step).isEqualTo(DeviceConnectStep.Connecting)
    }

    @Test
    fun `迟到的低阶段事件不把进度拉回`() {
        val atSync = DeviceConnectUiState(stage = ConnectStage.SyncingInfo)
        val next = deviceConnectReduce(atSync, DeviceConnectIntent.StageChanged(ConnectStage.BluetoothOn))
        assertThat(next.stage).isEqualTo(ConnectStage.SyncingInfo)
    }

    @Test
    fun `连接中成功进已连接且行进条拉满`() {
        val next = deviceConnectReduce(DeviceConnectUiState(), DeviceConnectIntent.Succeeded)
        assertThat(next.step).isEqualTo(DeviceConnectStep.Connected)
        assertThat(next.stage).isEqualTo(ConnectStage.SyncingInfo)
    }

    @Test
    fun `已连接后忽略迟到的进度事件`() {
        val connected = deviceConnectReduce(DeviceConnectUiState(), DeviceConnectIntent.Succeeded)
        val next = deviceConnectReduce(connected, DeviceConnectIntent.StageChanged(ConnectStage.DeviceFound))
        assertThat(next.stage).isEqualTo(ConnectStage.SyncingInfo)
        assertThat(next.step).isEqualTo(DeviceConnectStep.Connected)
    }

    @Test
    fun `失败落到失败态并带原因`() {
        val next = deviceConnectReduce(
            DeviceConnectUiState(),
            DeviceConnectIntent.Failed("蓝牙已断开"),
        )
        assertThat(next.step).isEqualTo(DeviceConnectStep.Failed)
        assertThat(next.failureReason).isEqualTo("蓝牙已断开")
    }

    @Test
    fun `同步阶段失败也进失败态`() {
        val atSync = DeviceConnectUiState(stage = ConnectStage.SyncingInfo)
        val next = deviceConnectReduce(atSync, DeviceConnectIntent.Failed("读取设备信息失败"))
        assertThat(next.step).isEqualTo(DeviceConnectStep.Failed)
    }

    @Test
    fun `失败态不被迟到的成功事件静默改写`() {
        val failed = deviceConnectReduce(DeviceConnectUiState(), DeviceConnectIntent.Failed("连接超时"))
        val next = deviceConnectReduce(failed, DeviceConnectIntent.Succeeded)
        assertThat(next.step).isEqualTo(DeviceConnectStep.Failed)
    }

    @Test
    fun `重试复位进度并清空失败原因`() {
        val failed = deviceConnectReduce(
            DeviceConnectUiState(stage = ConnectStage.SyncingInfo),
            DeviceConnectIntent.Failed("连接超时"),
        )
        val next = deviceConnectReduce(failed, DeviceConnectIntent.Retry)
        assertThat(next.step).isEqualTo(DeviceConnectStep.Connecting)
        assertThat(next.stage).isEqualTo(ConnectStage.Establishing)
        assertThat(next.failureReason).isEmpty()
    }

    @Test
    fun `非失败态的重试不生效`() {
        val connecting = DeviceConnectUiState(stage = ConnectStage.DeviceFound)
        val next = deviceConnectReduce(connecting, DeviceConnectIntent.Retry)
        assertThat(next.stage).isEqualTo(ConnectStage.DeviceFound)
    }

    @Test
    fun `模拟成功仅在四步走完后可点`() {
        assertThat(canSimulateSuccess(DeviceConnectUiState())).isFalse()
        assertThat(canSimulateSuccess(DeviceConnectUiState(stage = ConnectStage.SyncingInfo))).isTrue()
        assertThat(
            canSimulateSuccess(
                deviceConnectReduce(DeviceConnectUiState(), DeviceConnectIntent.Succeeded),
            ),
        ).isFalse()
    }
}
