package com.xcwk.scalehub.domain.model

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.types.DomainException
import com.xcwk.scalehub.domain.types.FixedClock
import org.junit.Test

/**
 * 健康记录构造守卫行为测试（DEC-022 组 A）。
 * 规则出处：旧库 qk-health-android Pages.kt RecordScreen（456-545 行取证）。
 * 每条断言排除一类移植事故：类型表失真 / 校验放松 / 委托语义丢失。
 */
class HealthLogTest {

    private fun log(
        time: Long = 1_700_000_000_000L,
        type: String = "diet",
        value: Double = 1500.0,
        value2: Double = 0.0,
        label: String = "午餐",
        note: String = "",
    ) = HealthLog(time, type, value, value2, label, note)

    @Test
    fun `七类已知记录类型均可构造`() {
        val samples = listOf(
            "diet" to 1500.0, "exercise" to 30.0, "water" to 250.0,
            "waist" to 78.0, "glucose" to 5.2, "blood_pressure" to 120.0,
            "habit" to 1.0,
        )
        for ((type, value) in samples) {
            val label = if (type == "habit") "早睡" else ""
            assertThat(log(type = type, value = value, label = label).type).isEqualTo(type)
        }
    }

    @Test(expected = DomainException::class)
    fun `weight类型被拒绝入健康记录`() {
        // 旧库 weight 走 saveManualWeight 写测量表（Pages.kt:537），此守卫钉住委托语义
        log(type = "weight", value = 70.0)
    }

    @Test(expected = DomainException::class)
    fun `未知类型被拒绝`() {
        log(type = "steps")
    }

    @Test(expected = DomainException::class)
    fun `非习惯类主值为零被拒绝`() {
        // 旧库「请输入有效数字」（Pages.kt:532-535）
        log(type = "water", value = 0.0)
    }

    @Test(expected = DomainException::class)
    fun `非习惯类主值为负被拒绝`() {
        log(type = "diet", value = -100.0)
    }

    @Test(expected = DomainException::class)
    fun `习惯记录缺名称被拒绝`() {
        log(type = "habit", value = 1.0, label = "  ")
    }

    @Test(expected = DomainException::class)
    fun `时间戳非正被拒绝`() {
        log(time = 0L)
    }

    @Test(expected = DomainException::class)
    fun `副值为负被拒绝`() {
        log(type = "waist", value = 78.0, value2 = -1.0)
    }

    @Test
    fun `线格式与枚举双向对应`() {
        for (t in HealthLogType.entries) {
            assertThat(HealthLogType.fromWire(t.wire)).isEqualTo(t)
        }
        assertThat(HealthLogType.fromWire("weight")).isNull()
        assertThat(HealthLogType.fromWire("walk")).isNull()
    }

    // ---- create 工厂（DEC-034 组 C：归一化与时间盖章收敛到聚合）----

    @Test
    fun `create 习惯类主值归一为一_旧库口径`() {
        // 旧库 RecordScreen 保存分支恒存 1.0（Pages.kt:539 add("habit", 1.0, ...)）
        val log = HealthLog.create("habit", 5.0, 0.0, "早睡", "", FixedClock(1_700_000_000_000))
        assertThat(log.value).isEqualTo(1.0)
        assertThat(log.label).isEqualTo("早睡")
    }

    @Test
    fun `create 非习惯类保留主值`() {
        val log = HealthLog.create("water", 250.0, 0.0, "", "", FixedClock(1_700_000_000_000))
        assertThat(log.value).isEqualTo(250.0)
    }

    @Test
    fun `create 的时间戳来自时钟`() {
        val clock = FixedClock(1_700_000_000_000)
        val a = HealthLog.create("water", 250.0, 0.0, "", "", clock)
        clock.advance(1_000)
        val b = HealthLog.create("water", 250.0, 0.0, "", "", clock)

        assertThat(a.time).isEqualTo(1_700_000_000_000)
        assertThat(b.time).isEqualTo(1_700_000_001_000)
    }
}
