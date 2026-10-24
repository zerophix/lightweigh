package com.xcwk.scalehub.feature.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 配对状态机（T-104 · 原型 04-device.html 的 v-pairing / v-pair-success / v-pair-fail）。
 *
 * 覆盖：
 * - 三态推进：配对中 → 成功 / 失败
 * - 失败可重试（重新搜索回到配对中）
 * - 成功不可回退（再收到失败/重试/进入事件仍是成功）
 * - 三个 view 的文案逐字断言
 */
class PairingStateTest {

    @Test
    fun `waiting advances to success`() {
        val waiting = pairingReduce(null, PairingIntent.Start("轻衡 S1"))
        assertThat(waiting).isEqualTo(PairingState.Pairing("轻衡 S1"))

        val success = pairingReduce(waiting, PairingIntent.Succeeded)
        assertThat(success).isEqualTo(PairingState.Success("轻衡 S1"))
    }

    @Test
    fun `waiting advances to fail`() {
        val waiting = pairingReduce(null, PairingIntent.Start("轻衡 S1"))
        val failed = pairingReduce(waiting, PairingIntent.Failed)
        assertThat(failed).isEqualTo(PairingState.Fail("轻衡 S1"))
    }

    @Test
    fun `fail can retry and retry keeps same device`() {
        val failed = PairingState.Fail("轻衡 S1")
        val retried = pairingReduce(failed, PairingIntent.Retry)
        assertThat(retried).isEqualTo(PairingState.Pairing("轻衡 S1"))
        // 重试后仍可再失败一次，说明不是一次性死路
        assertThat(pairingReduce(retried, PairingIntent.Failed)).isEqualTo(PairingState.Fail("轻衡 S1"))
    }

    @Test
    fun `success cannot go back to waiting`() {
        val success = PairingState.Success("轻衡 S1")
        assertThat(pairingReduce(success, PairingIntent.Retry)).isEqualTo(success)
        assertThat(pairingReduce(success, PairingIntent.Failed)).isEqualTo(success)
        assertThat(pairingReduce(success, PairingIntent.Start("轻衡 S1 Pro"))).isEqualTo(success)
    }

    @Test
    fun `retry only works from fail and start only from list page`() {
        val waiting = PairingState.Pairing("轻衡 S1")
        assertThat(pairingReduce(waiting, PairingIntent.Retry)).isEqualTo(waiting)
        assertThat(pairingReduce(waiting, PairingIntent.Start("轻衡 S1 Pro"))).isEqualTo(waiting)
        assertThat(pairingReduce(PairingState.Fail("轻衡 S1"), PairingIntent.Start("轻衡 S1 Pro")))
            .isEqualTo(PairingState.Fail("轻衡 S1"))
    }

    @Test
    fun `exit leaves the flow from every state`() {
        assertThat(pairingReduce(PairingState.Pairing("轻衡 S1"), PairingIntent.Exit)).isNull()
        assertThat(pairingReduce(PairingState.Success("轻衡 S1"), PairingIntent.Exit)).isNull()
        assertThat(pairingReduce(PairingState.Fail("轻衡 S1"), PairingIntent.Exit)).isNull()
        assertThat(pairingReduce(null, PairingIntent.Exit)).isNull()
    }

    @Test
    fun `v-pairing copy matches prototype word by word`() {
        assertThat(PAIRING_TITLE).isEqualTo("连接设备")
        assertThat(pairingConnectingText(DEFAULT_PAIRING_DEVICE)).isEqualTo("正在连接 轻衡 S1…")
        assertThat(PAIRING_KEEP_NEAR).isEqualTo("请保持手机靠近体脂秤")
        assertThat(PAIRING_SIMULATE_FAIL).isEqualTo("模拟失败/超时")
        assertThat(PAIRING_SIMULATE_SUCCESS).isEqualTo("模拟成功")
    }

    @Test
    fun `v-pair-success copy matches prototype word by word`() {
        assertThat(PAIRING_SUCCESS_TITLE).isEqualTo("连接成功")
        assertThat(pairingSuccessBody(DEFAULT_PAIRING_DEVICE))
            .isEqualTo("「轻衡 S1」已连接。\n现在可以开始第一次测量。")
        assertThat(PAIRING_START_MEASURE).isEqualTo("开始测量")
        assertThat(PAIRING_BACK_TO_DEVICE).isEqualTo("返回设备管理")
    }

    @Test
    fun `v-pair-fail copy matches prototype word by word`() {
        assertThat(PAIRING_TITLE).isEqualTo("连接设备")
        assertThat(PAIRING_FAIL_TITLE).isEqualTo("连接失败")
        assertThat(pairingFailBody(DEFAULT_PAIRING_DEVICE))
            .isEqualTo("未能连接到「轻衡 S1」。\n请确认秤已唤醒、距离 1 米内。")
        assertThat(PAIRING_RETRY).isEqualTo("重新搜索")
        assertThat(PAIRING_BACK).isEqualTo("返回")
    }
}
