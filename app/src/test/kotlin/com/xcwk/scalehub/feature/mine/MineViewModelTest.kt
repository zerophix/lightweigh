package com.xcwk.scalehub.feature.mine

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.query.ProfileQuery
import com.xcwk.scalehub.application.service.UpdateProfileService
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MemberProfileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * mine ViewModel 行为测试（M4 我的切片三关之一）。
 * 覆盖：加载回填与未设提示、非法数字拦截、保存成功刷新汇总。
 */
class MineViewModelTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupMain() {
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }

        @AfterClass
        @JvmStatic
        fun tearDownMain() {
            Dispatchers.resetMain()
        }
    }

    private class FakeReader(
        var heightValue: Int = 0,
        var yearValue: Int = 0,
    ) : MemberProfileReader {
        override fun male() = true
        override fun ageYears() = if (yearValue > 0) 30 else 0
        override fun heightCm() = heightValue
        override fun birthYear() = yearValue
        override fun birthMonth() = if (yearValue > 0) 5 else 0
        override fun birthDay() = if (yearValue > 0) 20 else 0
        override fun nickname() = ""
    }

    private class FakeWriter(private val reader: FakeReader) : MemberProfileWriter {
        var saved: MemberProfile? = null
        override suspend fun save(profile: MemberProfile) {
            saved = profile
            // 回写读侧，模拟 ProfileStore 单源语义（保存后重读应见新值）
            reader.heightValue = profile.heightCm
            reader.yearValue = profile.birthYear
        }
    }

    private fun vm(reader: FakeReader): Pair<MineViewModel, FakeWriter> {
        val writer = FakeWriter(reader)
        val vm = MineViewModel(ProfileQuery(reader), UpdateProfileService(writer))
        return vm to writer
    }

    @Test
    fun `加载回填_未设身高显示提示`() {
        val (vm, _) = vm(FakeReader(heightValue = 0, yearValue = 0))
        val s = vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.unsetHint).isTrue()
        assertThat(s.heightCm).isEqualTo("")
        assertThat(s.summary).contains("--cm")
    }

    @Test
    fun `加载回填_完整档案`() {
        val (vm, _) = vm(FakeReader(heightValue = 175, yearValue = 1996))
        val s = vm.state.value
        assertThat(s.unsetHint).isFalse()
        assertThat(s.heightCm).isEqualTo("175")
        assertThat(s.birthYear).isEqualTo("1996")
        assertThat(s.summary).isEqualTo("男 | 30岁 | 175cm")
    }

    @Test
    fun `非数字输入拦截不落盘`() {
        val (vm, writer) = vm(FakeReader(heightValue = 175, yearValue = 1996))
        vm.onIntent(MineIntent.HeightChanged("一百七十五"))
        vm.onIntent(MineIntent.Save)
        assertThat(vm.state.value.error).contains("数字")
        assertThat(writer.saved).isNull()
    }

    @Test
    fun `保存成功刷新汇总与落盘`() {
        val (vm, writer) = vm(FakeReader(heightValue = 175, yearValue = 1996))
        vm.onIntent(MineIntent.HeightChanged("176"))
        vm.onIntent(MineIntent.NicknameChanged("  新昵称  "))
        vm.onIntent(MineIntent.Save)
        assertThat(vm.state.value.saved).isTrue()
        assertThat(writer.saved).isNotNull()
        assertThat(writer.saved!!.heightCm).isEqualTo(176)
        assertThat(writer.saved!!.nickname).isEqualTo("新昵称")
        assertThat(vm.state.value.summary).contains("176cm")
    }
}
