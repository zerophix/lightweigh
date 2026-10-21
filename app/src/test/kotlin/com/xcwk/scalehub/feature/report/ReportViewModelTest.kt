package com.xcwk.scalehub.feature.report

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.query.ReportQuery
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.time.ZoneId

/** report ViewModel 行为（格式化口径 + 未找到态）。 */
class ReportViewModelTest {

    @Test
    fun `AI解读按钮仅正式账号可用`() {
        val rows = listOf(
            com.xcwk.scalehub.domain.model.MeasureRecord(
                1000L, 65.5, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175,
            ),
        )
        val formal = ReportViewModel(
            ReportQuery(FakeRepo(rows.toList())), FakeRepo(rows.toList()), 1000L, ZoneId.of("Asia/Shanghai"),
            isFormalAccount = { true },
        )
        assertThat(formal.state.value.aiEnabled).isTrue()
        val guest = ReportViewModel(
            ReportQuery(FakeRepo(rows.toList())), FakeRepo(rows.toList()), 1000L, ZoneId.of("Asia/Shanghai"),
            isFormalAccount = { false },
        )
        assertThat(guest.state.value.aiEnabled).isFalse()
    }


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

    private class FakeRepo(private val rows: List<MeasureRecord>) : MeasurementRepository, MeasurementQueryPort {
        override suspend fun save(record: MeasureRecord, memberId: String) = throw UnsupportedOperationException()
        override suspend fun recent(limit: Int) = rows.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            rows.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
        override suspend fun delete(time: Long) = throw UnsupportedOperationException()
    }

    private fun vm(time: Long, vararg rows: MeasureRecord) =
        ReportViewModel(ReportQuery(FakeRepo(rows.toList())), FakeRepo(rows.toList()), time, ZoneId.of("Asia/Shanghai"))

    @Test
    fun `报告格式化_大数字与比上次`() {
        val vm = vm(
            2000L,
            MeasureRecord(1000L, 71.5, 500.0, 19.2, "", "builtin-1", true, 30, 175),
            MeasureRecord(2000L, 70.0, 500.0, 18.4, "", "builtin-1", true, 30, 175),
        )
        val s = vm.state.value
        assertThat(s.found).isTrue()
        assertThat(s.weightText).isEqualTo("70.0")
        assertThat(s.deltaLine).isEqualTo("比上次  体重 -1.5 公斤  体脂率 -0.8 %")
        assertThat(s.bmiText).isEqualTo("22.9")
        assertThat(s.bmiLabel).isEqualTo("标准")
        assertThat(s.disclaimer).contains("不构成医疗建议")
        assertThat(s.missingText).isNull()
    }

    @Test
    fun `无阻抗_免责两态切换`() {
        val vm = vm(1000L, MeasureRecord(1000L, 70.0, 0.0, -1.0, "", "builtin-1", true, 30, 175))
        assertThat(vm.state.value.disclaimer).contains("没有阻抗")
        assertThat(vm.state.value.deltaLine).contains("--")   // 首条无上次
    }

    @Test
    fun `记录不存在_未找到态`() {
        val vm = vm(1000L, MeasureRecord(1000L, 70.0, 0.0, -1.0, "", "builtin-1"))
        val s = ReportViewModel(ReportQuery(FakeRepo(emptyList())), FakeRepo(emptyList()), 999L, ZoneId.of("Asia/Shanghai")).state.value
        assertThat(s.loading).isFalse()
        assertThat(s.found).isFalse()
        assertThat(vm.state.value.found).isTrue()   // 对照：原记录在
    }
}
