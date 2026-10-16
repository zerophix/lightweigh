package com.xcwk.scalehub.domain.protocol.packet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 单位换算向量（对抗评审 R2-Q6：此路径此前零向量覆盖）。
 * 每条向量击中的故障模式：
 *  - kg  : 小数位表解码错误 + 公斤直通路径被误加系数
 *  - 斤  : 2斤=1公斤 的换算系数错误（差一倍级）
 *  - lb  : 0.45359237 常数错误或精度丢失
 *  - st  : 英石/磅高低字节拆包语义错误（endian/移位）
 */
class WeightValueTest {

    // MessageFlags 位域: (raw>>3)&3=单位, (raw>>1)&3=小数位表(10=1位)
    private val kgOneDecimal = MessageFlags((2 shl 1) or (0 shl 3))   // kg, 1 位小数
    private val jinOneDecimal = MessageFlags((2 shl 1) or (1 shl 3))  // 斤, 1 位
    private val lbFlags = MessageFlags((2 shl 3))                      // lb（固定 1 位）
    private val stLbFlags = MessageFlags((3 shl 3))                    // st:lb

    @Test
    fun `公斤直通与小数位还原`() {
        assertThat(WeightValue(703, kgOneDecimal).kilograms()).isEqualTo(70.3)
    }

    @Test
    fun `斤两斤折一公斤`() {
        assertThat(WeightValue(1406, jinOneDecimal).kilograms()).isEqualTo(70.3)
    }

    @Test
    fun `磅乘英制常数`() {
        assertThat(WeightValue(1549, lbFlags).kilograms()).isEqualTo(70.3)
    }

    @Test
    fun `英石磅按高低字节拆包`() {
        // 高字节=11英石, 低字节=2(0.2磅)
        assertThat(WeightValue((11 shl 8) or 2, stLbFlags).kilograms()).isEqualTo(69.9)
    }
}
