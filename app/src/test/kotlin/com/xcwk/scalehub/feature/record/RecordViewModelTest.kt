package com.xcwk.scalehub.feature.record

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.command.RecordHealthLogCommand
import com.xcwk.scalehub.application.command.RecordMeasurementCommand
import com.xcwk.scalehub.application.service.RecordHealthLogService
import com.xcwk.scalehub.application.service.RecordMeasurementService
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MemberProfileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * record 表单行为（8 类语义随旧库 RecordScreen Pages.kt:466-530）。
 */
class RecordViewModelTest {

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

    private class FakeLogRepo : HealthLogRepository {
        val saved = mutableListOf<HealthLog>()
        override suspend fun save(log: HealthLog, memberId: String) {
            saved += log
        }
        override suspend fun recent(limit: Int) = saved.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long): List<HealthLog> = emptyList()
    }

    private class FakeMeasureRepo : MeasurementRepository {
        val saved = mutableListOf<MeasureRecord>()
        override suspend fun save(record: MeasureRecord, memberId: String) {
            saved += record
        }
        override suspend fun recent(limit: Int) = saved.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            saved.filter { it.time >= fromInc && it.time < toExc }
        override suspend fun delete(time: Long) = throw UnsupportedOperationException()
    }

    private class FakeProfile : MemberProfileReader, MemberProfileWriter {
        override fun male() = true
        override fun ageYears() = 30
        override fun heightCm() = 175
        override fun birthYear() = 1996
        override fun birthMonth() = 5
        override fun birthDay() = 20
        override fun nickname() = ""
        override suspend fun save(profile: MemberProfile) = throw UnsupportedOperationException()
    }

    private val logRepo = FakeLogRepo()
    private val measureRepo = FakeMeasureRepo()
    private val vm = { type: String ->
        RecordViewModel(
            type,
            RecordHealthLogService(logRepo, memberId = { "1" }),
            RecordMeasurementService(measureRepo, FakeProfile(), memberId = { "1" }),
        )
    }

    @Test
    fun `配置表_标题与字段随旧库`() {
        val diet = vm("diet").state.value
        assertThat(diet.title).isEqualTo("记饮食")
        assertThat(diet.primaryLabel).isEqualTo("摄入热量 千卡")
        assertThat(diet.presets).containsExactly("早餐", "午餐", "晚餐", "加餐").inOrder()
        val water = vm("water").state.value
        assertThat(water.quickValues).containsExactly(200, 250, 300, 500).inOrder()
        val habit = vm("habit").state.value
        assertThat(habit.isHabit).isTrue()
    }

    @Test
    fun `非习惯类_非正数拦截旧库文案`() {
        val v = vm("water")
        v.onIntent(RecordIntent.ValueChanged("0"))
        v.onIntent(RecordIntent.Save)
        assertThat(v.state.value.error).isEqualTo("请输入有效数字")
        assertThat(logRepo.saved).isEmpty()
        v.onIntent(RecordIntent.ValueChanged("abc"))
        v.onIntent(RecordIntent.Save)
        assertThat(v.state.value.error).isEqualTo("请输入有效数字")
        assertThat(logRepo.saved).isEmpty()
    }

    @Test
    fun `饮食保存_预设与副值`() = runTest {
        val v = vm("diet")
        v.onIntent(RecordIntent.PresetPicked("午餐"))
        v.onIntent(RecordIntent.ValueChanged("1500"))
        v.onIntent(RecordIntent.Save)
        assertThat(v.state.value.saved).isTrue()
        val saved = logRepo.saved.single()
        assertThat(saved.type).isEqualTo("diet")
        assertThat(saved.value).isEqualTo(1500.0)
        assertThat(saved.label).isEqualTo("午餐")
    }

    @Test
    fun `喝水快捷量与副值兜底`() = runTest {
        val v = vm("water")
        v.onIntent(RecordIntent.QuickPicked(300))
        v.onIntent(RecordIntent.Save)
        assertThat(logRepo.saved.single().value).isEqualTo(300.0)
        val e = vm("exercise")
        e.onIntent(RecordIntent.ValueChanged("45"))
        e.onIntent(RecordIntent.Save)
        assertThat(e.state.value.saved).isTrue()
        assertThat(logRepo.saved.last().value2).isEqualTo(0.0)
    }

    @Test
    fun `习惯保存_名称即记录主体`() = runTest {
        val v = vm("habit")
        v.onIntent(RecordIntent.ValueChanged("  早睡  "))
        v.onIntent(RecordIntent.Save)
        assertThat(v.state.value.saved).isTrue()
        val saved = logRepo.saved.single()
        assertThat(saved.value).isEqualTo(1.0)
        assertThat(saved.label).isEqualTo("早睡")
    }

    @Test
    fun `习惯缺名称被拒`() {
        val v = vm("habit")
        v.onIntent(RecordIntent.ValueChanged("   "))
        v.onIntent(RecordIntent.Save)
        assertThat(v.state.value.error).isNotNull()
        assertThat(logRepo.saved).isEmpty()
    }

    @Test
    fun `体重路径_走测量用例且体脂直录`() = runTest {
        val v = vm("weight")
        v.onIntent(RecordIntent.ValueChanged("72.5"))
        v.onIntent(RecordIntent.ExtraChanged("21.0"))
        v.onIntent(RecordIntent.Save)
        assertThat(v.state.value.saved).isTrue()
        val rec = measureRepo.saved.single()
        assertThat(rec.weight).isWithin(1e-9).of(72.5)
        assertThat(rec.impedance).isWithin(1e-9).of(0.0)
        assertThat(rec.fat).isWithin(1e-9).of(21.0)
        assertThat(logRepo.saved).isEmpty()
    }

    @Test
    fun `体重体脂可空缺省哨兵`() = runTest {
        val v = vm("weight")
        v.onIntent(RecordIntent.ValueChanged("70"))
        v.onIntent(RecordIntent.Save)
        assertThat(measureRepo.saved.single().fat).isWithin(1e-9).of(-1.0)
    }
}
