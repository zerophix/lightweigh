package com.xcwk.scalehub.feature.measure.scale

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.ai.AiPort
import com.xcwk.scalehub.application.ai.AiService
import com.xcwk.scalehub.application.query.MeasurementQuery
import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.application.service.DeleteMeasurementService
import com.xcwk.scalehub.application.sync.CloudSessionPort
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * T-118 v-result / v-ai-report 覆盖层 ViewModel 行为测试。
 *
 * 口径：差值、重复判定、AI 解读标题全部由真实记录推导；
 * 无上次记录时不得出现「较上次」语义，也不得编造体脂差值。
 */
class MeasureFlowViewModelTest {

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

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

    private class FakeStore(rows: List<MeasureRecord>) : MeasurementQueryPort, MeasurementRepository {
        val all = rows.toMutableList()
        val deleted = mutableListOf<Long>()
        override suspend fun save(record: MeasureRecord, memberId: String) { all += record }
        override suspend fun recent(limit: Int) = all.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            all.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
        override suspend fun delete(time: Long) {
            deleted += time
            all.removeAll { it.time == time }
        }
    }

    private class FakeProfile(private val nickname: String = "林悦") : MemberProfileReader {
        override fun male() = true
        override fun ageYears() = 30
        override fun heightCm() = 165
        override fun birthYear() = 1990
        override fun birthMonth() = 5
        override fun birthDay() = 20
        override fun nickname() = nickname
        override fun unitPreferences() = com.xcwk.scalehub.domain.model.UserUnitPreferences()
    }

    private class FakeSession(private val formal: Boolean = true) : CloudSessionPort {
        override fun isFormalAccount() = formal
        override fun accessToken(): String? = null
        override fun subject(token: String): String? = null
        override fun signOut() {}
    }

    private class FakeAi(private val fail: Boolean = false) : AiPort {
        var lastTurns: List<Pair<String, Boolean>>? = null
        override suspend fun ask(turns: List<Pair<String, Boolean>>): Pair<Boolean, String> {
            lastTurns = turns
            return if (fail) false to "当前网络不可用，请联网后使用AI功能" else true to "整体变化平稳，继续保持。"
        }
    }

    private fun rec(t: Long, weight: Double, fat: Double = -1.0, heightCm: Int? = null) =
        MeasureRecord(t, weight, 500.0, fat, "AA:BB", "builtin-1", null, null, heightCm)

    private val t0 = java.time.LocalDate.of(2026, 10, 21).atTime(7, 30).atZone(ZONE).toInstant().toEpochMilli()

    private class Env(
        rows: List<MeasureRecord>,
        formal: Boolean = true,
        aiFail: Boolean = false,
    ) {
        val store = FakeStore(rows)
        val ai = FakeAi(aiFail)
        val vm = MeasureFlowViewModel(
            measurementQuery = MeasurementQuery(store),
            deleteService = DeleteMeasurementService(store),
            aiService = AiService(FakeSession(formal), ai),
            profileReader = FakeProfile(),
            zone = ZONE,
        )
    }

    @Test
    fun `保存后读取上次记录算真实差值并给出时间文案`() {
        val prev = rec(t0 - 86_400_000L, 62.7, 28.8)
        val now = rec(t0, 62.4, 28.6)
        val env = Env(listOf(prev, now))
        env.vm.onSaved(t0, 62.4)
        val s = env.vm.flow.value
        assertThat(s.loading).isFalse()
        assertThat(s.step).isEqualTo(MeasureFlowStep.Result)
        assertThat(s.hasPrev).isTrue()
        assertThat(s.weightChange).isWithin(0.001).of(-0.3)
        assertThat(s.fatChange).isWithin(0.001).of(-0.2)
        assertThat(s.memberName).isEqualTo("林悦")
        assertThat(s.timeText).isEqualTo("10月21日 07:30")
        assertThat(s.dupPrevTime).isNull()
    }

    @Test
    fun `首次记录不出现较上次语义`() {
        val env = Env(listOf(rec(t0, 62.4)))
        env.vm.onSaved(t0, 62.4)
        val s = env.vm.flow.value
        assertThat(s.hasPrev).isFalse()
        assertThat(s.weightChange).isNull()
        assertThat(s.fatChange).isNull()
        assertThat(reportHeadline(s)).isEqualTo("首次记录已保存，积累连续记录后再看变化。")
    }

    @Test
    fun `上次体脂未测出时不编造体脂差值`() {
        val env = Env(listOf(rec(t0 - 86_400_000L, 62.7, -1.0), rec(t0, 62.4, 28.6)))
        env.vm.onSaved(t0, 62.4)
        assertThat(env.vm.flow.value.fatChange).isNull()
    }

    @Test
    fun `三分钟内重复测量记录上一条时间与窗口`() {
        val env = Env(listOf(rec(t0 - 2 * 60_000L, 62.5, 28.7), rec(t0, 62.4, 28.6)))
        env.vm.onSaved(t0, 62.4)
        val s = env.vm.flow.value
        assertThat(s.dupPrevTime).isEqualTo(t0 - 2 * 60_000L)
        assertThat(s.dupPrevText).isEqualTo("10月21日 07:28")
    }

    @Test
    fun `替换上次记录即删除上一条`() {
        val env = Env(listOf(rec(t0 - 60_000L, 62.5, 28.7), rec(t0, 62.4, 28.6)))
        env.vm.onSaved(t0, 62.4)
        env.vm.replacePrev()
        assertThat(env.store.deleted).containsExactly(t0 - 60_000L)
        assertThat(env.vm.flow.value.dupPrevTime).isNull()
    }

    @Test
    fun `BMI 用本次记录身高计算`() {
        val env = Env(listOf(rec(t0, 62.4, -1.0, heightCm = 160)))
        env.vm.onSaved(t0, 62.4)
        // 62.4 / 1.6² = 24.375 → 24.4
        assertThat(env.vm.flow.value.bmiText).isEqualTo("24.4")
    }

    @Test
    fun `打开AI报告时携带真实差值上下文`() {
        val env = Env(listOf(rec(t0 - 86_400_000L, 62.7, 28.8), rec(t0, 62.4, 28.6)))
        env.vm.onSaved(t0, 62.4)
        env.vm.openReport()
        val s = env.vm.flow.value
        assertThat(s.step).isEqualTo(MeasureFlowStep.Report)
        assertThat(s.reportText).isEqualTo("整体变化平稳，继续保持。")
        val question = env.ai.lastTurns!!.single().first
        assertThat(question).contains("下降")
        assertThat(question).contains("62.4")
    }

    @Test
    fun `未登录时AI解读返回服务端统一文案并可重试`() {
        val env = Env(listOf(rec(t0, 62.4)), formal = false, aiFail = true)
        env.vm.onSaved(t0, 62.4)
        env.vm.openReport()
        assertThat(env.vm.flow.value.reportError).isEqualTo("请先使用手机号登录后再提问")
        val retrying = Env(listOf(rec(t0, 62.4)))
        retrying.vm.onSaved(t0, 62.4)
        retrying.vm.openReport()
        assertThat(retrying.vm.flow.value.reportText).isNotNull()
    }

    @Test
    fun `两项同向下降时用原型口径标题`() {
        val s = MeasureFlowState(
            loading = false,
            hasPrev = true,
            weightChange = -0.3,
            fatChange = -0.2,
            weightText = "62.4",
        )
        assertThat(reportHeadline(s)).isEqualTo("两项读数小幅下降，不等于脂肪减少。")
    }

    @Test
    fun `重复窗口常量与原型三分钟一致`() {
        assertThat(MeasureFlowViewModel.DUP_WINDOW_MS).isEqualTo(3 * 60 * 1000L)
    }
}
