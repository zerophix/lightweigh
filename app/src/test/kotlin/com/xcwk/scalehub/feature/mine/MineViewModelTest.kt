package com.xcwk.scalehub.feature.mine

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.query.HomeGoalQuery
import com.xcwk.scalehub.application.query.ProfileQuery
import com.xcwk.scalehub.application.service.UpdateProfileService
import com.xcwk.scalehub.domain.model.MeasureRecord
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

    @Test
    fun `滚轮选择_出生与身高落入表单态`() {
        val (vm, _) = vm(FakeReader())
        vm.onIntent(MineIntent.BirthPicked(1990, 6, 15))
        vm.onIntent(MineIntent.HeightPicked(180))
        val s = vm.state.value
        assertThat(s.birthYear).isEqualTo("1990")
        assertThat(s.birthMonth).isEqualTo("6")
        assertThat(s.birthDay).isEqualTo("15")
        assertThat(s.heightCm).isEqualTo("180")
        assertThat(s.error).isNull()
    }

    @Test
    fun `头像选图_保存成功即更新路径_失败给提示`() {
        val okWriter = FakeWriter(FakeReader())
        val okVm = MineViewModel(
            ProfileQuery(FakeReader()), UpdateProfileService(okWriter),
            saveAvatar = { _ -> "/data/avatar.jpg" },
        )
        okVm.onIntent(MineIntent.AvatarChosen("content://media/1"))
        assertThat(okVm.state.value.avatarPath).isEqualTo("/data/avatar.jpg")
        assertThat(okVm.state.value.error).isNull()

        val badVm = MineViewModel(
            ProfileQuery(FakeReader()), UpdateProfileService(FakeWriter(FakeReader())),
            saveAvatar = { null },
        )
        badVm.onIntent(MineIntent.AvatarChosen("content://media/2"))
        assertThat(badVm.state.value.avatarPath).isEmpty()
        assertThat(badVm.state.value.error).isEqualTo("头像保存失败，请换一张图片")
    }

    @Test
    fun `加载带回头像路径`() {
        val vm = MineViewModel(
            ProfileQuery(FakeReader()), UpdateProfileService(FakeWriter(FakeReader())),
            avatarPath = { "/data/avatar.jpg" },
        )
        assertThat(vm.state.value.avatarPath).isEqualTo("/data/avatar.jpg")
    }

    // ---- 健康数据概览卡（原型 profile 页 metric-grid；DEC-026 组 I）----

    private class FakeMeasureRepo(rows: List<com.xcwk.scalehub.domain.model.MeasureRecord>) :
        com.xcwk.scalehub.domain.repository.MeasurementRepository,
        com.xcwk.scalehub.application.query.MeasurementQueryPort {
        private val data = rows.toMutableList()
        override suspend fun save(record: com.xcwk.scalehub.domain.model.MeasureRecord, memberId: String) { data += record }
        override suspend fun recent(limit: Int) = data.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            data.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
        override suspend fun delete(time: Long) { data.removeAll { it.time == time } }
    }

    private class FakeLogRepo : com.xcwk.scalehub.domain.repository.HealthLogRepository,
        com.xcwk.scalehub.application.query.HealthLogQueryPort {
        override suspend fun save(log: com.xcwk.scalehub.domain.model.HealthLog, memberId: String) = Unit
        override suspend fun recent(limit: Int) = emptyList<com.xcwk.scalehub.domain.model.HealthLog>()
        override suspend fun between(fromInc: Long, toExc: Long) =
            emptyList<com.xcwk.scalehub.domain.model.HealthLog>()
    }

    private class FakeGoals : com.xcwk.scalehub.domain.repository.GoalRepository {
        override var targetWeight: Float = 0f
        override var calorieGoal: Int = 2049
        override var waterGoal: Int = 2000
    }

    private fun homeGoalQuery(rows: List<com.xcwk.scalehub.domain.model.MeasureRecord>, height: Int) =
        HomeGoalQuery(FakeMeasureRepo(rows), FakeLogRepo(), FakeGoals(), FakeReader(heightValue = height))

    @Test
    fun `概览卡_有测量给三指标`() {
        val reader = FakeReader(heightValue = 175, yearValue = 1996)
        val now = System.currentTimeMillis()
        val vm = MineViewModel(
            ProfileQuery(reader), UpdateProfileService(FakeWriter(reader)),
            homeGoalQuery = homeGoalQuery(
                listOf(MeasureRecord(now, 70.0, 500.0, 18.4, "AA:BB", "okok-bia-1")),
                height = 175,
            ),
        )
        val s = vm.state.value
        assertThat(s.hasOverview).isTrue()
        assertThat(s.overviewWeight).isEqualTo("70.0")
        assertThat(s.overviewFat).isEqualTo("18.4")     // 有阻抗 → 体脂可见
        assertThat(s.overviewBmi).isEqualTo("22.9")     // 70 / 1.75²
    }

    @Test
    fun `概览卡_无阻抗体脂给占位_无测量整卡隐藏`() {
        val reader = FakeReader(heightValue = 175, yearValue = 1996)
        val now = System.currentTimeMillis()
        val vmNoZ = MineViewModel(
            ProfileQuery(reader), UpdateProfileService(FakeWriter(reader)),
            homeGoalQuery = homeGoalQuery(
                listOf(MeasureRecord(now, 70.0, 0.0, -1.0, "AA:BB", "okok-bia-1")),
                height = 175,
            ),
        )
        assertThat(vmNoZ.state.value.hasOverview).isTrue()
        assertThat(vmNoZ.state.value.overviewFat).isEqualTo("--")

        val vmEmpty = MineViewModel(
            ProfileQuery(reader), UpdateProfileService(FakeWriter(reader)),
            homeGoalQuery = homeGoalQuery(emptyList(), height = 175),
        )
        assertThat(vmEmpty.state.value.hasOverview).isFalse()
    }
}
