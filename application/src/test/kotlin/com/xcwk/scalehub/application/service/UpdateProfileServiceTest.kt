package com.xcwk.scalehub.application.service

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.command.UpdateProfileCommand
import com.xcwk.scalehub.application.query.ProfileQuery
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MemberProfileWriter
import com.xcwk.scalehub.domain.types.DomainException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 更新档案用例 + 档案查询读模型（M4 我的切片）。
 * 语义对照旧库 ProfilePages 保存分支（昵称 trim().take(20)：ProfilePages.kt:491）。
 */
class UpdateProfileServiceTest {

    private class FakeWriter : MemberProfileWriter {
        var saved: MemberProfile? = null
        override suspend fun save(profile: MemberProfile) {
            saved = profile
        }
    }

    private class FakeReader(
        private val height: Int = 175,
        private val year: Int = 1996,
    ) : MemberProfileReader {
        override fun male(): Boolean = true
        override fun ageYears(): Int = if (year > 0) 30 else 0
        override fun heightCm(): Int = height
        override fun birthYear(): Int = year
        override fun birthMonth(): Int = if (year > 0) 5 else 0
        override fun birthDay(): Int = if (year > 0) 20 else 0
        override fun nickname(): String = ""
    }

    private val writer = FakeWriter()
    private val service = UpdateProfileService(writer)

    @Test
    fun `正常保存_昵称归一为trim20`() = runTest {
        val cmd = UpdateProfileCommand(true, 1996, 5, 20, 175, "  " + "长".repeat(30) + "  ")
        val p = service.execute(cmd)
        assertThat(p.nickname.length).isEqualTo(20)     // take(20) 旧库口径
        assertThat(writer.saved).isEqualTo(p)
    }

    @Test
    fun `落库后发档案变更事件`() = runTest {
        // 档案是体成分计算的输入，变更后报告页与我的页需重算（DEC-034 组 A 新增行为）
        val bus = com.xcwk.scalehub.application.DataRefreshBus()
        val svc = UpdateProfileService(writer, bus)
        val got = mutableListOf<com.xcwk.scalehub.application.DataChange>()
        val job = launch { bus.changed.collect { got += it } }
        runCurrent()
        svc.execute(UpdateProfileCommand(true, 1996, 5, 20, 175, "小明"))
        runCurrent()
        assertThat(got).containsExactly(com.xcwk.scalehub.application.DataChange.Profile)
        assertThat(got.all { it in com.xcwk.scalehub.application.DataChange.LOCAL_WRITES }).isTrue()
        job.cancel()
    }

    @Test
    fun `越界输入_failfast且不落盘`() {
        assertThrows(DomainException::class.java) {
            runBlocking { service.execute(UpdateProfileCommand(true, 1996, 5, 20, 99, "")) }
        }
        assertThat(writer.saved).isNull()
    }

    @Test
    fun `查询读模型_未设身高态`() {
        val query = ProfileQuery(FakeReader(height = 0, year = 0))
        val c = query.current()
        assertThat(c.heightSet).isFalse()
        assertThat(c.birthSet).isFalse()
        assertThat(c.ageYears).isEqualTo(0)
    }

    @Test
    fun `查询读模型_完整态`() {
        val c = ProfileQuery(FakeReader()).current()
        assertThat(c.heightSet).isTrue()
        assertThat(c.birthSet).isTrue()
        assertThat(c.birthYear).isEqualTo(1996)
        assertThat(c.birthMonth).isEqualTo(5)
        assertThat(c.birthDay).isEqualTo(20)
        assertThat(c.heightCm).isEqualTo(175)
    }
}
