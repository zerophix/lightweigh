package com.xcwk.scalehub.feature

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.feature.report.DoctorMeasurement
import com.xcwk.scalehub.feature.report.DoctorReportInput
import com.xcwk.scalehub.feature.report.buildDoctorReport
import org.junit.Test

/** T-061：只汇总数据，不出现诊断/建议类表述。 */
class DoctorReportTest {

    @Test
    fun `空数据只给表头不给占位数值`() {
        val text = buildDoctorReport(DoctorReportInput())
        assertThat(text).contains("暂无测量记录")
        assertThat(text).contains("数据不足")
        assertThat(text).doesNotContain("0.0 kg")
    }

    @Test
    fun `完整数据包含四个小节`() {
        val text = buildDoctorReport(
            DoctorReportInput(
                memberName = "林悦",
                age = 30,
                heightCm = 168.0,
                bmi = 22.1,
                periodDays = 30,
                weightChangeKg = -2.8,
                measurements = listOf(
                    DoctorMeasurement("2026-10-01", 65.2, 28.4),
                    DoctorMeasurement("2026-10-20", 62.4, 27.1),
                ),
            ),
        )
        assertThat(text).contains("一、基本信息")
        assertThat(text).contains("二、最近测量")
        assertThat(text).contains("三、近 30 天变化")
        assertThat(text).contains("体重下降 2.8 kg")
        assertThat(text).contains("2026-10-20：体重 62.4 kg，体脂 27.1%")
    }

    @Test
    fun `体重上升与持平分别表述`() {
        assertThat(buildDoctorReport(DoctorReportInput(weightChangeKg = 1.2)))
            .contains("体重增加 1.2 kg")
        assertThat(buildDoctorReport(DoctorReportInput(weightChangeKg = 0.01)))
            .contains("基本持平")
    }

    @Test
    fun `不包含诊断或建议类词汇`() {
        val text = buildDoctorReport(
            DoctorReportInput(measurements = listOf(DoctorMeasurement("2026-10-20", 62.4))),
        )
        listOf("建议就医", "疑似", "诊断为", "风险较高", "请服用").forEach {
            assertThat(text).doesNotContain(it)
        }
        assertThat(text).contains("不含诊断与治疗建议")
    }

    @Test
    fun `最多列出最近十条`() {
        val many = (1..15).map { DoctorMeasurement("2026-10-${it.toString().padStart(2, '0')}", 60.0 + it) }
        val text = buildDoctorReport(DoctorReportInput(measurements = many))
        assertThat(text).contains("2026-10-15")
        assertThat(text).doesNotContain("2026-10-05：")
    }
}
