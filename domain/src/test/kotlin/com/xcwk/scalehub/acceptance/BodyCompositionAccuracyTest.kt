package com.xcwk.scalehub.acceptance

import com.xcwk.scalehub.domain.algorithm.OkokBia
import com.xcwk.scalehub.domain.service.BodyCompositionService
import com.xcwk.scalehub.domain.service.BodyShapeClassificationService
import com.xcwk.scalehub.domain.service.HealthAdviceService
import com.xcwk.scalehub.domain.service.HealthRiskAssessmentService
import com.xcwk.scalehub.domain.model.MeasureRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 功能级验收测试：体成分计算准确性（纯 Domain 层）
 *
 * 验收标准：
 * 1. 所有域服务计算结果与原始 OkokBia.analyze 完全一致（黄金向量）
 * 2. 体型分类 3x3 矩阵边界值正确
 * 3. 7 大健康风险评估阈值正确
 * 4. MeasureRecord 聚合根业务行为正确委托域服务
 */
class BodyCompositionAccuracyTest {

    private val bodyCompService = BodyCompositionService()
    private val riskService = HealthRiskAssessmentService()
    private val shapeService = BodyShapeClassificationService()
    private val adviceService = HealthAdviceService()

    // ===== 黄金向量：固定输入 → 固定输出（对齐 OkokBia.analyze）=====

    @Test
    fun `OKOK分析_男性_30岁_175cm_70kg_500欧姆_黄金向量`() {
        val biaResult = OkokBia.analyze(true, 30, 175, 70.0, 500.0)
        assertNotNull(biaResult)

        val svcResult = bodyCompService.analyzeOkok(true, 30, 175, 70.0, 500.0)
        assertNotNull(svcResult)

        val bia = biaResult!!
        val svc = svcResult!!
        assertEquals(bia.fatPercent, svc.fatPercent!!, 0.01)
        assertEquals(bia.musclePercent!!, svc.musclePercent!!, 0.01)
        assertEquals(bia.muscleKg!!, svc.muscleKg!!, 0.01)
        assertEquals(bia.boneKg!!, svc.boneKg!!, 0.01)
        assertEquals(bia.waterPercent!!, svc.waterPercent!!, 0.01)
        assertEquals(bia.waterKg!!, svc.waterKg!!, 0.01)
        assertEquals(bia.proteinPercent!!, svc.proteinPercent!!, 0.01)
        assertEquals(bia.visceral!!, svc.visceralFat!!, 0.01)
        assertEquals(bia.bmr!!, svc.bmr!!, 0.01)
        assertEquals(bia.bodyAge, svc.bodyAge)
    }

    @Test
    fun `OKOK分析_女性_25岁_160cm_55kg_600欧姆_黄金向量`() {
        val biaResult = OkokBia.analyze(false, 25, 160, 55.0, 600.0)
        assertNotNull(biaResult)

        val svcResult = bodyCompService.analyzeOkok(false, 25, 160, 55.0, 600.0)
        assertNotNull(svcResult)

        val bia = biaResult!!
        val svc = svcResult!!
        assertEquals(bia.fatPercent, svc.fatPercent!!, 0.01)
        assertEquals(bia.musclePercent!!, svc.musclePercent!!, 0.01)
        assertEquals(bia.visceral!!, svc.visceralFat!!, 0.01)
        assertEquals(bia.bodyAge, svc.bodyAge)
    }

    @Test
    fun `通用估算路径_男性_30岁_175cm_70kg_20%体脂_500欧姆_黄金向量`() {
        val est = bodyCompService.estimateGeneral(
            male = true, age = 30, heightCm = 175,
            weightKg = 70.0, fatPercent = 20.0, impedanceOhm = 500.0
        )
        assertNotNull(est.fatWeight)
        assertEquals(14.0, est.fatWeight!!, 0.01)
        assertNotNull(est.leanWeight)
        assertEquals(56.0, est.leanWeight!!, 0.01)
        assertNotNull(est.boneWeight)
        assertEquals(3.85, est.boneWeight!!, 0.01)
        assertNotNull(est.bmr)
        assertTrue(est.bmr!! > 0)
    }

    // ===== 体型分类 3x3 矩阵边界值验证 =====

    @Test
    fun `体型分类_男性_9大类全覆盖_黄金向量`() {
        val maleStd = bodyCompService.fatStandard(true)
        
        // 第1行：体脂偏低 (fat < 10)
        assertEquals("消瘦型", shapeService.classify(8.0, 17.0, maleStd)?.shapeName)
        assertEquals("低脂肪型", shapeService.classify(8.0, 22.0, maleStd)?.shapeName)
        assertEquals("运动员型", shapeService.classify(8.0, 26.0, maleStd)?.shapeName)
        
        // 第2行：体脂标准 (10 <= fat <= 20)
        assertEquals("苗条肌肉型", shapeService.classify(15.0, 17.0, maleStd)?.shapeName)
        assertEquals("苗条型", shapeService.classify(15.0, 22.0, maleStd)?.shapeName)
        assertEquals("健康型", shapeService.classify(15.0, 26.0, maleStd)?.shapeName)
        
        // 第3行：体脂偏高 (fat > 20)
        assertEquals("隐形肥胖型", shapeService.classify(25.0, 17.0, maleStd)?.shapeName)
        assertEquals("偏胖型", shapeService.classify(25.0, 22.0, maleStd)?.shapeName)
        assertEquals("肥胖型", shapeService.classify(25.0, 26.0, maleStd)?.shapeName)
    }

    @Test
    fun `体型分类_女性_9大类全覆盖_黄金向量`() {
        val femaleStd = bodyCompService.fatStandard(false)
        
        assertEquals("消瘦型", shapeService.classify(16.0, 17.0, femaleStd)?.shapeName)
        assertEquals("低脂肪型", shapeService.classify(16.0, 22.0, femaleStd)?.shapeName)
        assertEquals("运动员型", shapeService.classify(16.0, 26.0, femaleStd)?.shapeName)
        assertEquals("苗条肌肉型", shapeService.classify(22.0, 17.0, femaleStd)?.shapeName)
        assertEquals("苗条型", shapeService.classify(22.0, 22.0, femaleStd)?.shapeName)
        assertEquals("健康型", shapeService.classify(22.0, 26.0, femaleStd)?.shapeName)
        assertEquals("隐形肥胖型", shapeService.classify(30.0, 17.0, femaleStd)?.shapeName)
        assertEquals("偏胖型", shapeService.classify(30.0, 22.0, femaleStd)?.shapeName)
        assertEquals("肥胖型", shapeService.classify(30.0, 26.0, femaleStd)?.shapeName)
    }

    @Test
    fun `体型分类_边界值_临界点正确`() {
        val maleStd = bodyCompService.fatStandard(true)
        
        // 体脂率边界：10.0 是标准下界 (进入第2行)
        assertEquals("苗条型", shapeService.classify(10.0, 22.0, maleStd)?.shapeName)  // 不再是低脂肪型(第1行)
        
        // 体脂率边界：20.0 是标准上界 (仍在标准区间)
        assertEquals("苗条型", shapeService.classify(20.0, 22.0, maleStd)?.shapeName)  // 不再是偏胖型
        assertEquals("偏胖型", shapeService.classify(20.1, 22.0, maleStd)?.shapeName)
        
        // BMI边界：18.5, 24.0, 28.0
        // fat=8.0 (第1行: fat<10), BMI=18.49 -> col 0 -> 消瘦型
        assertEquals("消瘦型", shapeService.classify(8.0, 18.49, maleStd)?.shapeName)
        // fat=8.0 (第1行), BMI=18.5 -> col 1 (标准) -> 低脂肪型
        assertEquals("低脂肪型", shapeService.classify(8.0, 18.5, maleStd)?.shapeName)
        // fat=8.0 (第1行), BMI=24.0 -> col 2 (偏胖) -> 运动员型
        assertEquals("运动员型", shapeService.classify(8.0, 24.0, maleStd)?.shapeName)
        // fat=15.0 (第2行: 10-20), BMI=18.49 -> col 0 -> 苗条肌肉型
        assertEquals("苗条肌肉型", shapeService.classify(15.0, 18.49, maleStd)?.shapeName)
        // fat=15.0 (第2行), BMI=24.0 -> col 2 -> 健康型
        assertEquals("健康型", shapeService.classify(15.0, 24.0, maleStd)?.shapeName)
        // fat=25.0 (第3行: >20), BMI=18.49 -> col 0 -> 隐形肥胖型
        assertEquals("隐形肥胖型", shapeService.classify(25.0, 18.49, maleStd)?.shapeName)
        // fat=25.0 (第3行), BMI=24.0 -> col 2 -> 肥胖型
        assertEquals("肥胖型", shapeService.classify(25.0, 24.0, maleStd)?.shapeName)
    }

    // ===== 7 大健康风险评估阈值验证 =====

    @Test
    fun `肥胖症风险_BMI阈值_黄金向量`() {
        val maleStd = bodyCompService.fatStandard(true)
        
        assertEquals(1, riskService.assessObesity(18.4)?.tone)  // 偏瘦 → 低风险
        assertEquals(1, riskService.assessObesity(22.0)?.tone)  // 标准 → 低风险
        assertEquals(2, riskService.assessObesity(25.0)?.tone)  // 偏胖 → 中风险
        assertEquals(3, riskService.assessObesity(28.0)?.tone)  // 肥胖 → 高风险
        assertEquals(3, riskService.assessObesity(35.0)?.tone)  // 重度肥胖 → 高风险
        assertNull(riskService.assessObesity(null))
    }

    @Test
    fun `脂肪肝风险_内脏脂肪与体脂率阈值_黄金向量`() {
        val maleStd = bodyCompService.fatStandard(true)
        
        // 无阻抗 → null
        assertNull(riskService.assessFattyLiver(false, 10.0, 20.0, maleStd))
        
        // 内脏脂肪 > 14 → 高风险
        assertEquals(3, riskService.assessFattyLiver(true, 15.0, 20.0, maleStd)?.tone)
        // 体脂 > 标准+8 (28) → 高风险
        assertEquals(3, riskService.assessFattyLiver(true, 10.0, 30.0, maleStd)?.tone)
        
        // 内脏脂肪 > 9 → 中风险
        assertEquals(2, riskService.assessFattyLiver(true, 10.0, 20.0, maleStd)?.tone)
        // 体脂 > 标准 (20) 但 <= 标准+8 (28) → 中风险
        assertEquals(2, riskService.assessFattyLiver(true, 5.0, 25.0, maleStd)?.tone)
        
        // 正常 → 低风险
        assertEquals(1, riskService.assessFattyLiver(true, 5.0, 15.0, maleStd)?.tone)
    }

    @Test
    fun `高血压风险_BMI年龄阈值_黄金向量`() {
        // BMI < 24 → 低风险
        assertEquals(1, riskService.assessHypertension(22.0, 30)?.tone)
        assertEquals(1, riskService.assessHypertension(22.0, 50)?.tone)
        
        // BMI >= 24 且 < 28 → 中风险
        assertEquals(2, riskService.assessHypertension(25.0, 30)?.tone)
        assertEquals(2, riskService.assessHypertension(25.0, 50)?.tone)
        
        // BMI >= 28 且 年龄 < 40 → 中风险
        assertEquals(2, riskService.assessHypertension(29.0, 30)?.tone)
        
        // BMI >= 28 且 年龄 >= 40 → 高风险
        assertEquals(3, riskService.assessHypertension(29.0, 40)?.tone)
        assertEquals(3, riskService.assessHypertension(32.0, 50)?.tone)
        
        assertNull(riskService.assessHypertension(null, 30))
    }

    @Test
    fun `糖尿病风险_BMI体脂阈值_黄金向量`() {
        val maleStd = bodyCompService.fatStandard(true)
        
        assertEquals(1, riskService.assessDiabetes(22.0, 15.0, maleStd)?.tone)
        assertEquals(1, riskService.assessDiabetes(22.0, 30.0, maleStd)?.tone)
        
        assertEquals(2, riskService.assessDiabetes(25.0, 15.0, maleStd)?.tone)
        assertEquals(2, riskService.assessDiabetes(25.0, 30.0, maleStd)?.tone)
        
        // BMI >= 28 且 体脂 > 标准 → 高风险
        assertEquals(3, riskService.assessDiabetes(29.0, 25.0, maleStd)?.tone)
        assertEquals(3, riskService.assessDiabetes(32.0, 30.0, maleStd)?.tone)
        
        // BMI >= 28 但 体脂 <= 标准 → 中风险
        assertEquals(2, riskService.assessDiabetes(29.0, 18.0, maleStd)?.tone)
        
        assertNull(riskService.assessDiabetes(null, 20.0, maleStd))
    }

    @Test
    fun `骨质疏松风险_骨量体重比阈值_黄金向量`() {
        assertEquals(3, riskService.assessOsteoporosis(1.5, 50.0)?.tone)  // 3%
        assertEquals(2, riskService.assessOsteoporosis(2.0, 50.0)?.tone)  // 4%
        assertEquals(1, riskService.assessOsteoporosis(2.5, 50.0)?.tone)  // 5%
        assertEquals(1, riskService.assessOsteoporosis(3.0, 60.0)?.tone)  // 5%
        
        assertNull(riskService.assessOsteoporosis(null, 50.0))
        assertNull(riskService.assessOsteoporosis(2.0, 0.0))
    }

    @Test
    fun `高血脂风险_体脂内脏阈值_黄金向量`() {
        val maleStd = bodyCompService.fatStandard(true)
        
        assertNull(riskService.assessHyperlipidemia(false, 30.0, maleStd, 10.0))
        
        assertEquals(3, riskService.assessHyperlipidemia(true, 38.0, maleStd, 5.0)?.tone)  // 38>28
        assertEquals(3, riskService.assessHyperlipidemia(true, 25.0, maleStd, 15.0)?.tone)
        
        assertEquals(2, riskService.assessHyperlipidemia(true, 25.0, maleStd, 5.0)?.tone)
        
        assertEquals(1, riskService.assessHyperlipidemia(true, 18.0, maleStd, 5.0)?.tone)
    }

    @Test
    fun `痛风风险_BMI阈值_黄金向量`() {
        assertEquals(1, riskService.assessGout(22.0)?.tone)
        assertEquals(1, riskService.assessGout(25.0)?.tone)
        assertEquals(2, riskService.assessGout(28.0)?.tone)
        assertEquals(2, riskService.assessGout(30.0)?.tone)
        assertEquals(3, riskService.assessGout(32.0)?.tone)
        assertEquals(3, riskService.assessGout(35.0)?.tone)
        assertNull(riskService.assessGout(null))
    }

    // ===== MeasureRecord 聚合根业务行为验收 =====

    @Test
    fun `MeasureRecord_计算体成分_委托域服务_OKOK路径`() {
        val record = MeasureRecord(
            1000L, 70.0, 500.0, 18.4, "AA:BB", "okok-bia-1", true, 30, 175
        )
        
        val analysis = record.calculateBodyComposition(bodyCompService)
        assertNotNull(analysis)
        assertEquals(18.4, analysis!!.fatPercent, 0.01)
        assertNotNull(analysis.musclePercent)
        assertNotNull(analysis.visceralFat)
        assertTrue(analysis.bodyAge > 0)
    }

    @Test
    fun `MeasureRecord_计算体成分_委托域服务_通用估算路径`() {
        val record = MeasureRecord(
            1000L, 70.0, 500.0, 18.4, "AA:BB", "builtin-1", true, 30, 175
        )
        
        val analysis = record.calculateBodyComposition(bodyCompService)
        assertNotNull(analysis)
        assertEquals(18.4, analysis!!.fatPercent, 0.01)
        assertNotNull(analysis.musclePercent)
    }

    @Test
    fun `MeasureRecord_计算体成分_无阻抗_返回null`() {
        val record = MeasureRecord(
            1000L, 70.0, 0.0, -1.0, "AA:BB", "builtin-1", true, 30, 175
        )
        
        val analysis = record.calculateBodyComposition(bodyCompService)
        assertNull(analysis)
    }

    @Test
    fun `MeasureRecord_评估健康风险_返回7项风险`() {
        val record = MeasureRecord(
            1000L, 70.0, 500.0, 18.4, "AA:BB", "okok-bia-1", true, 30, 175
        )
        
        val risks = record.assessHealthRisks(riskService, bodyCompService)
        assertEquals(7, risks.size)
        
        val names = risks.map { it.name }.toSet()
        assertEquals(
            setOf("肥胖症", "脂肪肝", "高血压", "糖尿病", "骨质疏松", "高血脂", "痛风"),
            names
        )
    }

    @Test
    fun `MeasureRecord_体型分类_返回ShapeResult`() {
        val record = MeasureRecord(
            1000L, 70.0, 500.0, 18.4, "AA:BB", "okok-bia-1", true, 30, 175
        )
        
        val shape = record.classifyBodyShape(shapeService, bodyCompService)
        assertNotNull(shape)
        assertNotNull(shape!!.shapeName)
        assertNotNull(shape.description)
        assertTrue(shape.shapeName in BodyShapeClassificationService.ALL_SHAPES)
    }

    @Test
    fun `MeasureRecord_综合建议_返回非空文案`() {
        val record = MeasureRecord(
            1000L, 70.0, 500.0, 18.4, "AA:BB", "okok-bia-1", true, 30, 175
        )
        
        val advice = record.getOverallAdvice(adviceService, bodyCompService)
        assertNotNull(advice)
        assertTrue(advice.isNotEmpty())
    }

    @Test
    fun `MeasureRecord_指标建议_异常指标返回针对性建议`() {
        val record = MeasureRecord(
            1000L, 70.0, 500.0, 18.4, "AA:BB", "okok-bia-1", true, 30, 175
        )
        
        val bmiAdvice = record.getMetricAdvice(adviceService, "BMI", 2)
        assertEquals("控制总热量摄入，增加有氧运动频率，循序渐进回归标准区间。", bmiAdvice)
        
        val fatAdvice = record.getMetricAdvice(adviceService, "体脂率", 3)
        assertEquals("减少油脂与精制糖摄入，增加力量训练提升肌肉占比。", fatAdvice)
        
        val standardAdvice = record.getMetricAdvice(adviceService, "BMI", 1)
        assertEquals("继续保持当前生活习惯。", standardAdvice)
    }
}