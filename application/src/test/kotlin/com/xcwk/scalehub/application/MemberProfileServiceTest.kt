package com.xcwk.scalehub.application

import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.model.UserUnitPreferences
import com.xcwk.scalehub.domain.model.WeightUnit
import com.xcwk.scalehub.domain.model.LengthUnit
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MemberProfileWriter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 成员档案服务行为测试。
 *
 * 验证：空态判定、字段校验、保存成功/失败。
 */
class MemberProfileServiceTest {

    private class FakeReaderWriter : MemberProfileReader, MemberProfileWriter {
        var saved: MemberProfile? = null
        var height = 0
        var birthYear = 0
        var birthMonth = 0
        var birthDay = 0
        var male = true
        var nickname = ""

        override fun male() = male
        override fun ageYears(): Int = if (birthYear > 0) 2024 - birthYear else 0
        override fun heightCm() = height
        override fun birthYear() = birthYear
        override fun birthMonth() = birthMonth
        override fun birthDay() = birthDay
        override fun nickname() = nickname
        override fun unitPreferences(): UserUnitPreferences = UserUnitPreferences(WeightUnit.KG, LengthUnit.CM, true)

        override suspend fun save(profile: MemberProfile) {
            saved = profile
            height = profile.heightCm
            birthYear = profile.birthYear
            birthMonth = profile.birthMonth
            birthDay = profile.birthDay
            male = profile.male
            nickname = profile.nickname
        }

        override suspend fun saveUnitPreferences(prefs: UserUnitPreferences) {}
    }

    @Test
    fun `isEmpty when height and birthYear are both zero`() = runTest {
        val repo = FakeReaderWriter()
        val service = MemberProfileService(repo, repo)

        assertThat(service.isEmpty()).isTrue()
    }

    @Test
    fun `isEmpty is false when height is set`() = runTest {
        val repo = FakeReaderWriter().apply { height = 170 }
        val service = MemberProfileService(repo, repo)

        assertThat(service.isEmpty()).isFalse()
    }

    @Test
    fun `save returns Empty state when no input`() = runTest {
        val repo = FakeReaderWriter()
        val service = MemberProfileService(repo, repo)

        val result = service.save(false, 0, 0, 0, 0, "")

        assertThat(result.ok).isFalse()
        assertThat(result.state).isEqualTo(ProfileState.Empty)
    }

    @Test
    fun `save returns Dirty when only height filled`() = runTest {
        val repo = FakeReaderWriter()
        val service = MemberProfileService(repo, repo)

        val result = service.save(false, 0, 0, 0, 170, "")

        assertThat(result.ok).isFalse()
        assertThat(result.state).isEqualTo(ProfileState.Dirty)
    }

    @Test
    fun `save returns Complete when valid data`() = runTest {
        val repo = FakeReaderWriter()
        val service = MemberProfileService(repo, repo)

        val result = service.save(true, 1990, 6, 15, 170, "测试用户")

        assertThat(result.ok).isTrue()
        assertThat(result.state).isEqualTo(ProfileState.Complete)
        assertThat(repo.saved?.nickname).isEqualTo("测试用户")
    }

    @Test
    fun `save catches DomainException for invalid height`() = runTest {
        val repo = FakeReaderWriter()
        val service = MemberProfileService(repo, repo)

        val result = service.save(true, 1990, 6, 15, 50, "")

        assertThat(result.ok).isFalse()
        assertThat(result.state).isEqualTo(ProfileState.Dirty)
    }

    @Test
    fun `current returns profile summary`() = runTest {
        val repo = FakeReaderWriter().apply {
            height = 170
            birthYear = 1990
            birthMonth = 6
            birthDay = 15
            male = true
            nickname = "测试"
        }
        val service = MemberProfileService(repo, repo)

        val summary = service.current()

        assertThat(summary.heightCm).isEqualTo(170)
        assertThat(summary.birthYear).isEqualTo(1990)
        assertThat(summary.nickname).isEqualTo("测试")
    }
}
