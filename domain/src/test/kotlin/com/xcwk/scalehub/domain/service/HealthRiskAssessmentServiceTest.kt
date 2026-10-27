package com.xcwk.scalehub.domain.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HealthRiskAssessmentService 黄金向量测试
 *
 * 验证 7 大健康风险评估与原始 BodyAnalysis.kt risksOf 完全一致。
 */
class HealthRiskAssessmentServiceTest {

    private val riskService = HealthRiskAssessmentService()
    private val bodyCompService = BodyCompositionService()
    private val maleFatStd = bodyCompService.fatStandard(true)
    private val femaleFatStd = bodyCompService.fatStandard(false)

    @Test
    fun `肥胖症风险_BMI边界值验证`() {
        assertEquals(1, riskService.assessObesity(18.0)?.tone)  // 偏瘦 -> 低风险
        assertEquals(1, riskService.assessObesity(22.0)?.tone)  // 标准 -> 低风险
        assertEquals(2, riskService.assessObesity(25.0)?.tone)  // 偏胖 -> 中风险
        assertEquals(3, riskService.assessObesity(28.0)?.tone)  // 肥胖 -> 高风险
        assertEquals(3, riskService.assessObesity(35.0)?.tone)  // 重度肥胖 -> 高风险
        assertNull(riskService.assessObesity(null))
    }

    @Test
    fun `脂肪肝风险_内脏脂肪与体脂率判定`() {
        // 无阻抗 -> null
        assertNull(riskService.assessFattyLiver(false, 10.0, 20.0, maleFatStd))

        // 有阻抗，内脏脂肪>14 -> 高风险
        assertEquals(3, riskService.assessFattyLiver(true, 15.0, 20.0, maleFatStd)?.tone)
        // 体脂>标准+8(28) -> 高风险
        assertEquals(3, riskService.assessFattyLiver(true, 10.0, 30.0, maleFatStd)?.tone)

        // 内脏脂肪>9 -> 中风险
        assertEquals(2, riskService.assessFattyLiver(true, 10.0, 20.0, maleFatStd)?.tone)
        // 体脂>标准(20) 但 <=标准+8(28) -> 中风险
        assertEquals(2, riskService.assessFattyLiver(true, 5.0, 25.0, maleFatStd)?.tone)

        // 正常 -> 低风险
        assertEquals(1, riskService.assessFattyLiver(true, 5.0, 15.0, maleFatStd)?.tone)
    }

    @Test
    fun `高血压风险_BMI与年龄判定`() {
        // BMI<24 -> 低风险
        assertEquals(1, riskService.assessHypertension(22.0, 30)?.tone)
        assertEquals(1, riskService.assessHypertension(22.0, 50)?.tone)

        // BMI>=24 且 <28 -> 中风险
        assertEquals(2, riskService.assessHypertension(25.0, 30)?.tone)
        assertEquals(2, riskService.assessHypertension(25.0, 50)?.tone)

        // BMI>=28 且 年龄<40 -> 中风险
        assertEquals(2, riskService.assessHypertension(29.0, 30)?.tone)

        // BMI>=28 且 年龄>=40 -> 高风险
        assertEquals(3, riskService.assessHypertension(29.0, 40)?.tone)
        assertEquals(3, riskService.assessHypertension(32.0, 50)?.tone)

        assertNull(riskService.assessHypertension(null, 30))
    }

    @Test
    fun `糖尿病风险_BMI与体脂率判定`() {
        // BMI<24 -> 低风险
        assertEquals(1, riskService.assessDiabetes(22.0, 15.0, maleFatStd)?.tone)
        assertEquals(1, riskService.assessDiabetes(22.0, 30.0, maleFatStd)?.tone)

        // BMI>=24 且 <28 -> 中风险
        assertEquals(2, riskService.assessDiabetes(25.0, 15.0, maleFatStd)?.tone)
        assertEquals(2, riskService.assessDiabetes(25.0, 30.0, maleFatStd)?.tone)

        // BMI>=28 且 体脂>标准 -> 高风险
        assertEquals(3, riskService.assessDiabetes(29.0, 25.0, maleFatStd)?.tone)
        assertEquals(3, riskService.assessDiabetes(32.0, 30.0, maleFatStd)?.tone)

        // BMI>=28 但 体脂<=标准 -> 中风险
        assertEquals(2, riskService.assessDiabetes(29.0, 18.0, maleFatStd)?.tone)

        assertNull(riskService.assessDiabetes(null, 20.0, maleFatStd))
    }

    @Test
    fun `骨质疏松风险_骨量体重比判定`() {
        // 比值<0.035 -> 高风险
        assertEquals(3, riskService.assessOsteoporosis(1.5, 50.0)?.tone) // 3%

        // 比值 0.035-0.045 -> 中风险
        assertEquals(2, riskService.assessOsteoporosis(2.0, 50.0)?.tone) // 4%

        // 比值>=0.045 -> 低风险
        assertEquals(1, riskService.assessOsteoporosis(2.5, 50.0)?.tone) // 5%
        assertEquals(1, riskService.assessOsteoporosis(3.0, 60.0)?.tone) // 5%

        assertNull(riskService.assessOsteoporosis(null, 50.0))
        assertNull(riskService.assessOsteoporosis(2.0, 0.0))
    }

    @Test
    fun `高血脂风险_体脂与内脏脂肪判定`() {
        // 无阻抗 -> null
        assertNull(riskService.assessHyperlipidemia(false, 30.0, maleFatStd, 10.0))

        // 体脂>标准+8 或 内脏>14 -> 高风险
        assertEquals(3, riskService.assessHyperlipidemia(true, 38.0, maleFatStd, 5.0)?.tone) // 38>28
        assertEquals(3, riskService.assessHyperlipidemia(true, 25.0, maleFatStd, 15.0)?.tone)

        // 体脂>标准 -> 中风险
        assertEquals(2, riskService.assessHyperlipidemia(true, 25.0, maleFatStd, 5.0)?.tone)

        // 正常 -> 低风险
        assertEquals(1, riskService.assessHyperlipidemia(true, 18.0, maleFatStd, 5.0)?.tone)
    }

    @Test
    fun `痛风风险_BMI判定`() {
        assertEquals(1, riskService.assessGout(22.0)?.tone)
        assertEquals(1, riskService.assessGout(25.0)?.tone)
        assertEquals(2, riskService.assessGout(28.0)?.tone)
        assertEquals(2, riskService.assessGout(30.0)?.tone)
        assertEquals(3, riskService.assessGout(32.0)?.tone)
        assertEquals(3, riskService.assessGout(35.0)?.tone)
        assertNull(riskService.assessGout(null))
    }

    @Test
    fun `全量评估_标准男性_返回7项风险`() {
        val risks = riskService.assessAll(
            hasImpedance = true,
            bmi = 24.5,
            fatPercent = 22.0,
            fatStandard = maleFatStd,
            visceralFat = 10.0,
            boneWeight = 2.8,
            weightKg = 70.0,
            age = 35
        )
        assertEquals(7, risks.size)
        risks.forEach { risk ->
            assertNotNull(risk.name)
            assertTrue(risk.tone in 1..3)
            assertNotNull(risk.levelText)
        }
        // 验证名称包含预期的 7 项
        val names = risks.map { it.name }.toSet()
        assertEquals(
            setOf("肥胖症", "脂肪肝", "高血压", "糖尿病", "骨质疏松", "高血脂", "痛风"),
            names
        )
    }

    @Test
    fun `全量评估_无阻抗_跳过脂肪肝和高血脂`() {
        val risks = riskService.assessAll(
            hasImpedance = false,
            bmi = 24.5,
            fatPercent = 22.0,
            fatStandard = maleFatStd,
            visceralFat = 10.0,
            boneWeight = 2.8,
            weightKg = 70.0,
            age = 35
        )
        // 无阻抗时不评估脂肪肝和高血脂，应为 5 项
        assertEquals(5, risks.size)
        val names = risks.map { it.name }.toSet()
        assertEquals(
            setOf("肥胖症", "高血压", "糖尿病", "骨质疏松", "痛风"),
            names
        )
    }
}