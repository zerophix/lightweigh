package com.xcwk.scalehub.feature.report

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.query.ReportQuery
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.service.BodyCompositionService
import com.xcwk.scalehub.domain.service.BodyShapeClassificationService
import com.xcwk.scalehub.domain.service.HealthAdviceService
import com.xcwk.scalehub.domain.service.HealthRiskAssessmentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.time.ZoneId

/** report ViewModel 行为（格式化口径 + 未找到态）。 */
class ReportViewModelTest {

    private val bodyCompService = BodyCompositionService()
    private val riskService = HealthRiskAssessmentService()
    private val shapeService = BodyShapeClassificationService()
    private val adviceService = HealthAdviceService()

    private fun createViewModel(rows: List<MeasureRecord>, isFormal: Boolean = true) = ReportViewModel(
        ReportQuery(
            FakeRepo(rows.toList()),
            bodyCompService, riskService, shapeService, adviceService
        ),
        FakeRepo(rows.toList()),
        1000L, ZoneId.of("Asia/Shanghai"),
        isFormalAccount = { isFormal },
    )

    @Test
    fun `AI解读按钮仅正式账号可用`() {
        val rows = listOf(
            com.xcwk.scalehub.domain.model.MeasureRecord(
                1000L, 65.5, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175,
            ),
        )
        val formal = createViewModel(rows, true)
        assertThat(formal.state.value.aiEnabled).isTrue()
        val guest = createViewModel(rows, false)
        assertThat(guest.state.value.aiEnabled).isFalse()
    }

    @Test
    fun `找不到记录时 found=false`() {
        val vm = createViewModel(emptyList())
        assertThat(vm.state.value.found).isFalse()
    }

    @Test
    fun `评分变化_两端有效才给差值`() = runTest {
        val rows = listOf(
            MeasureRecord(500L, 73.0, 500.0, 20.0, "", "builtin-1", true, 30, 175),
            MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA:BB", "okok-bia-1", true, 30, 175),
        )
        val vm = createViewModel(rows)
        // 只测试 scoreDelta 存在
        assertThat(vm.state.value.scoreDelta).isNotNull()
    }

    @Test
    fun `上期评分_prev无评分时不给差值`() = runTest {
        val rows = listOf(
            MeasureRecord(500L, 73.0, 0.0, -1.0, "", "okok-bia-1", true, 30, 175),
            MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA:BB", "okok-bia-1", true, 30, 175),
        )
        val vm = createViewModel(rows)
        assertThat(vm.state.value.scoreDelta).isNull()
    }

    @Test
    fun `上期评分_当前无评分时不给差值`() = runTest {
        val rows = listOf(
            MeasureRecord(500L, 70.0, 500.0, 18.4, "AA:BB", "okok-bia-1", true, 30, 175),
            MeasureRecord(1000L, 70.0, 0.0, -1.0, "", "okok-bia-1", true, 30, 175),
        )
        val vm = createViewModel(rows)
        assertThat(vm.state.value.scoreDelta).isNull()
    }

    @Test
    fun `不存在的时间_返回null`() = runTest {
        val vm = createViewModel(emptyList())
        assertThat(vm.state.value.found).isFalse()
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
}