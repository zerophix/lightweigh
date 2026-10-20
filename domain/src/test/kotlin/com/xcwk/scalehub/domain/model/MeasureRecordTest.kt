package com.xcwk.scalehub.domain.model

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.types.DomainException
import com.xcwk.scalehub.domain.types.FixedClock
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
    fun `超出上限的阻抗被拒绝`() {
        record(impedance = 9999.0)
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

    // ---- create 工厂（DEC-034 组 C：创建编排收敛 + 时间戳由 Clock 盖章）----

    @Test
    fun `create 的时间戳来自时钟而非系统时间`() {
        val clock = FixedClock(1_700_000_000_000)
        val a = MeasureRecord.create(70.0, 500.0, null, "AA:BB", "builtin-1", true, 30, 175, clock)
        clock.advance(60_000)
        val b = MeasureRecord.create(70.0, 500.0, null, "AA:BB", "builtin-1", true, 30, 175, clock)

        assertThat(a.time).isEqualTo(1_700_000_000_000)
        assertThat(b.time).isEqualTo(1_700_000_060_000)
    }

    @Test
    fun `create 有阻抗时按算法算体脂_黄金向量`() {
        // 男 30 / 175cm / 70kg / 500Ω → 18.1%（与旧库 finishSave 真机验证值一致）
        val r = MeasureRecord.create(
            70.0, 500.0, null, "AA:BB", "builtin-1", true, 30, 175, FixedClock(1),
        )
        assertThat(r.fat).isWithin(0.05).of(18.1)
        assertThat(r.algorithm).isEqualTo("builtin-1")
    }

    @Test
    fun `create 无阻抗且无手动体脂时记未计算哨兵`() {
        val r = MeasureRecord.create(
            70.0, 0.0, null, "AA:BB", "builtin-1", true, 30, 175, FixedClock(1),
        )
        assertThat(r.fat).isEqualTo(MeasureRecord.FAT_NOT_CALCULATED)
    }

    @Test
    fun `create 无阻抗时采用手动录入体脂`() {
        // 旧库 saveManualWeight 的 extra 口径：直录体脂
        val r = MeasureRecord.create(
            70.0, 0.0, 22.5, "AA:BB", "builtin-1", true, 30, 175, FixedClock(1),
        )
        assertThat(r.fat).isWithin(0.001).of(22.5)
    }

    @Test
    fun `create 遇未知算法编号回落 builtin`() {
        val r = MeasureRecord.create(
            70.0, 500.0, null, "AA:BB", "no-such-algo", true, 30, 175, FixedClock(1),
        )
        // 未知编号回落 builtin-1 后体脂仍算得出（不因编号无效就丢结果）
        assertThat(r.algorithm).isEqualTo("no-such-algo")
        assertThat(r.fat).isWithin(0.05).of(18.1)
    }

    @Test
    fun `create 的档案快照随记录持久化`() {
        val r = MeasureRecord.create(
            70.0, 500.0, null, "AA:BB", "builtin-1", true, 30, 175, FixedClock(1),
        )
        assertThat(r.male).isTrue()
        assertThat(r.ageYears).isEqualTo(30)
        assertThat(r.heightCm).isEqualTo(175)
    }
}
