package com.xcwk.scalehub.feature

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.feature.member.ChildModePolicy
import com.xcwk.scalehub.feature.member.ageFromBirthDate
import com.xcwk.scalehub.feature.member.MemberFeature
import org.junit.Test
import java.time.LocalDate

/** T-071：未知年龄按儿童处理；未授权时 AI/分享/跨成员全部关闭。 */
class ChildModeTest {

    @Test
    fun `成年人不受限`() {
        assertThat(ChildModePolicy.isMinor(18)).isFalse()
        assertThat(ChildModePolicy.allows(MemberFeature.AiInsight, 30, false)).isTrue()
    }

    @Test
    fun `未知年龄按儿童处理`() {
        assertThat(ChildModePolicy.isMinor(null)).isTrue()
        assertThat(ChildModePolicy.allows(MemberFeature.ShareReport, null, false)).isFalse()
    }

    @Test
    fun `未成年人未授权时全部关闭`() {
        MemberFeature.entries.forEach {
            assertThat(ChildModePolicy.allows(it, 12, guardianConsent = false)).isFalse()
        }
    }

    @Test
    fun `授权后仍禁止跨成员查看`() {
        assertThat(ChildModePolicy.allows(MemberFeature.AiInsight, 12, true)).isTrue()
        assertThat(ChildModePolicy.allows(MemberFeature.ShareReport, 12, true)).isTrue()
        assertThat(ChildModePolicy.allows(MemberFeature.CrossMemberView, 12, true)).isFalse()
    }

    @Test
    fun `需要监护人提示的场景`() {
        assertThat(ChildModePolicy.needsGuardianPrompt(12, false)).isTrue()
        assertThat(ChildModePolicy.needsGuardianPrompt(12, true)).isFalse()
        assertThat(ChildModePolicy.needsGuardianPrompt(30, false)).isFalse()
    }

    @Test
    fun `周岁按生日是否已过计算`() {
        val today = LocalDate.of(2026, 10, 27)
        assertThat(ageFromBirthDate("2010-05-01", today)).isEqualTo(16)
        assertThat(ageFromBirthDate("2008-05-01", today)).isEqualTo(18)
        assertThat(ageFromBirthDate("2008-11-01", today)).isEqualTo(17)
    }

    @Test
    fun `非法或未来日期返回空`() {
        val today = LocalDate.of(2026, 10, 27)
        assertThat(ageFromBirthDate("", today)).isNull()
        assertThat(ageFromBirthDate("2027-01-01", today)).isNull()
        assertThat(ageFromBirthDate("not-a-date", today)).isNull()
    }
}
