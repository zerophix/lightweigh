package com.xcwk.scalehub.domain.model

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.types.DomainException
import org.junit.Test

/** 聚合不变量测试 — 每条构造守卫一个反例。 */
class MeasureRecordTest {

    private fun record(
        time: Long = 1_700_000_000_000,
        weight: Double = 70.0,
        impedance: Double = 500.0,
        fat: Double = -1.0,
        mac: String? = "AA:BB",
        algorithm: String? = "builtin-1",
    ) = MeasureRecord(time, weight, impedance, fat, mac, algorithm)

    @Test
    fun `合法测量可以创建`() {
        val r = record(fat = 18.1)
        assertThat(r.fat).isEqualTo(18.1)
    }

    @Test(expected = DomainException::class)
    fun `非法时间戳被拒绝`() {
        record(time = 0)
    }

    @Test(expected = DomainException::class)
    fun `超出量程的体重被拒绝`() {
        record(weight = 999.0)
    }

    @Test(expected = DomainException::class)
    fun `负阻抗被拒绝`() {
        record(impedance = -5.0)
    }

    @Test(expected = DomainException::class)
    fun `超出范围且非哨兵的体脂被拒绝`() {
        record(fat = 120.0)
    }

    @Test
    fun `哨兵与归一化语义保持`() {
        val r = record(mac = null, algorithm = null, fat = MeasureRecord.FAT_NOT_CALCULATED)
        assertThat(r.mac).isEmpty()
        assertThat(r.algorithm).isEqualTo("builtin-1")
        assertThat(r.fat).isEqualTo(-1.0)
    }
}
