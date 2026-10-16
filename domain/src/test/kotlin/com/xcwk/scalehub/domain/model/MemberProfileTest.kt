package com.xcwk.scalehub.domain.model

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.types.DomainException
import org.junit.Test
import java.time.LocalDate

/**
 * 档案值对象守卫行为测试（M4 我的切片，DEC-022）。
 * 出处：旧库 ProfilePages 滚轮与校验（行号见 MemberProfile 注释）。
 */
class MemberProfileTest {

    private fun profile(
        male: Boolean = true,
        birthYear: Int = 1996,
        birthMonth: Int = 5,
        birthDay: Int = 20,
        heightCm: Int = 175,
        nickname: String = "",
    ) = MemberProfile(male, birthYear, birthMonth, birthDay, heightCm, nickname)

    @Test
    fun `合法档案构造通过`() {
        val p = profile(nickname = "  轻刻  ")
        assertThat(p.heightCm).isEqualTo(175)
        assertThat(p.nickname).isEqualTo("  轻刻  ")   // 归一是服务层职责，构造不改写
    }

    @Test(expected = DomainException::class)
    fun `身高低于100被拒`() {
        profile(heightCm = 99)
    }

    @Test(expected = DomainException::class)
    fun `身高超230被拒`() {
        profile(heightCm = 231)
    }

    @Test(expected = DomainException::class)
    fun `出生月越界被拒`() {
        profile(birthMonth = 13)
    }

    @Test(expected = DomainException::class)
    fun `出生日越界被拒`() {
        profile(birthDay = 32)
    }

    @Test(expected = DomainException::class)
    fun `出生年早于1930被拒`() {
        profile(birthYear = 1929)
    }

    @Test(expected = DomainException::class)
    fun `出生年晚于今年被拒`() {
        profile(birthYear = LocalDate.now().year + 1)
    }

    @Test(expected = DomainException::class)
    fun `昵称超20字符被拒`() {
        profile(nickname = "a".repeat(21))
    }

    @Test
    fun `周岁_生日已过与未过`() {
        val today = LocalDate.of(2026, 5, 20)
        // 生日恰好今天 → 满岁
        assertThat(MemberProfile.ageFrom(1996, 5, 20, today)).isEqualTo(30)
        // 生日未到 → 减一
        assertThat(MemberProfile.ageFrom(1996, 5, 21, today)).isEqualTo(29)
        // 生日已过
        assertThat(MemberProfile.ageFrom(1996, 5, 19, today)).isEqualTo(30)
    }

    @Test
    fun `周岁_月日缺失退化为年差_旧库口径`() {
        val today = LocalDate.of(2026, 5, 20)
        assertThat(MemberProfile.ageFrom(2000, 0, 0, today)).isEqualTo(26)
        assertThat(MemberProfile.ageFrom(0, 0, 0, today)).isEqualTo(0)
    }
}
