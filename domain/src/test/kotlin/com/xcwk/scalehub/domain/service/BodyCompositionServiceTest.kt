package com.xcwk.scalehub.domain.service

import com.xcwk.scalehub.domain.algorithm.OkokBia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * BodyCompositionService 黄金向量测试
 *
 * 验证所有计算结果与原始 BodyAnalysis.kt 与 OkokBia.analyze 完全一致。
 * 固定输入 → 固定输出，作为重构回归基线。
 */
class BodyCompositionServiceTest {

    private val service = BodyCompositionService()
    private val okokBia = OkokBia.INSTANCE

    @Test
    fun `标准体重计算_男性_175cm_等于_66_5kg`() {
        val result = service.standardWeight(true, 175)
        assertEquals(66.5, result!!, 0.01)
    }

    @Test
    fun `标准体重计算_女性_160cm_等于_54kg`() {
        val result = service.standardWeight(false, 160)
        assertEquals(54.0, result!!, 0.01)
    }

    @Test
    fun `BMI计算_70kg_175cm_等于_22_86`() {
        val result = service.bmi(70.0, 175)
        assertEquals(22.86, result!!, 0.01)
    }

    @Test
    fun `BMI分级_边界值验证`() {
        assertEquals(0, service.bmiLevel(18.4))
        assertEquals(1, service.bmiLevel(18.5))
        assertEquals(1, service.bmiLevel(23.9))
        assertEquals(2, service.bmiLevel(24.0))
        assertEquals(2, service.bmiLevel(27.9))
        assertEquals(3, service.bmiLevel(28.0))
        assertEquals(3, service.bmiLevel(31.9))
        assertEquals(4, service.bmiLevel(32.0))
        assertEquals(4, service.bmiLevel(35.0))
    }

    @Test
    fun `体脂标准区间_男性_10到20`() {
        val std = service.fatStandard(true)
        assertEquals(10.0, std.min, 0.01)
        assertEquals(20.0, std.max, 0.01)
    }

    @Test
    fun `体脂标准区间_女性_18到28`() {
        val std = service.fatStandard(false)
        assertEquals(18.0, std.min, 0.01)
        assertEquals(28.0, std.max, 0.01)
    }

    @Test
    fun `肥胖度计算_70kg_标准66_5kg_等于_5_26`() {
        val result = service.obesityPercent(70.0, 66.5)
        assertEquals(5.26, result!!, 0.01)
    }

    @Test
    fun `通用估算路径_男性_30岁_175cm_70kg_20体脂_500欧姆`() {
        val est = service.estimateGeneral(
            male = true,
            age = 30,
            heightCm = 175,
            weightKg = 70.0,
            fatPercent = 20.0,
            impedanceOhm = 500.0
        )
        assertNotNull(est.fatWeight)
        assertEquals(14.0, est.fatWeight!!, 0.01)
        assertNotNull(est.leanWeight)
        assertEquals(56.0, est.leanWeight!!, 0.01)
        assertNotNull(est.muscleRate)
        assertTrue(est.muscleRate!! > 0)
        assertNotNull(est.boneWeight)
        assertEquals(3.85, est.boneWeight!!, 0.01)
        assertNotNull(est.bmr)
        assertTrue(est.bmr!! > 0)
    }

    @Test
    fun `OKOK分析_男性_30岁_175cm_70kg_500欧姆_与OkokBia一致`() {
        val biaResult = OkokBia.analyze(true, 30, 175, 70.0, 500.0)
        assertNotNull(biaResult)

        val serviceResult = service.analyzeOkok(true, 30, 175, 70.0, 500.0)
        assertNotNull(serviceResult)

        val bia = biaResult!!
        val svc = serviceResult!!
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
    fun `OKOK分析_女性_25岁_160cm_55kg_600欧姆_与OkokBia一致`() {
        val biaResult = OkokBia.analyze(false, 25, 160, 55.0, 600.0)
        assertNotNull(biaResult)

        val serviceResult = service.analyzeOkok(false, 25, 160, 55.0, 600.0)
        assertNotNull(serviceResult)

        val bia = biaResult!!
        val svc = serviceResult!!
        assertEquals(bia.fatPercent, svc.fatPercent!!, 0.01)
        assertEquals(bia.musclePercent!!, svc.musclePercent!!, 0.01)
        assertEquals(bia.visceral!!, svc.visceralFat!!, 0.01)
        assertEquals(bia.bodyAge, svc.bodyAge)
    }

    @Test
    fun `OKOK分析_无效输入_返回null`() {
        assertNull(service.analyzeOkok(true, 30, 0, 70.0, 500.0))
        assertNull(service.analyzeOkok(true, 0, 175, 70.0, 500.0))
        assertNull(service.analyzeOkok(true, 30, 175, 0.0, 500.0))
        assertNull(service.analyzeOkok(true, 30, 175, 70.0, 0.0))
    }

    @Test
    fun `骨骼肌计算_男性_去脂56kg_体重70kg`() {
        val result = service.calculateSkeletalMuscle(true, 56.0, 70.0)
        assertNotNull(result.skeletalWeight)
        assertNotNull(result.skeletalPercent)
        assertTrue(result.skeletalWeight!! > 0)
        assertTrue(result.skeletalPercent!! > 0)
    }

    @Test
    fun `综合评分_标准男性_有阻抗_有体脂_有BMI_返回分数`() {
        val score = service.calculateScore(
            hasImpedance = true,
            bmi = 22.0,
            fatPercent = 15.0,
            visceralFat = 5.0,
            male = true
        )
        assertNotNull(score)
        assertTrue(score!! >= 20 && score!! <= 100)
    }

    @Test
    fun `综合评分_无阻抗_返回null`() {
        val score = service.calculateScore(
            hasImpedance = false,
            bmi = 22.0,
            fatPercent = 15.0,
            visceralFat = 5.0,
            male = true
        )
        assertNull(score)
    }

    @Test
    fun `体型分类_各区间验证`() {
        // 男性
        assertEquals("消瘦型", service.classifyBodyShape(8.0, 17.0, true))
        assertEquals("低脂肪型", service.classifyBodyShape(8.0, 22.0, true))
        assertEquals("运动员型", service.classifyBodyShape(8.0, 26.0, true))
        assertEquals("苗条肌肉型", service.classifyBodyShape(15.0, 17.0, true))
        assertEquals("苗条型", service.classifyBodyShape(15.0, 22.0, true))
        assertEquals("健康型", service.classifyBodyShape(15.0, 26.0, true))
        assertEquals("隐形肥胖型", service.classifyBodyShape(25.0, 17.0, true))
        assertEquals("偏胖型", service.classifyBodyShape(25.0, 22.0, true))
        assertEquals("肥胖型", service.classifyBodyShape(25.0, 26.0, true))

        // 女性
        assertEquals("消瘦型", service.classifyBodyShape(16.0, 17.0, false))
        assertEquals("低脂肪型", service.classifyBodyShape(16.0, 22.0, false))
        assertEquals("运动员型", service.classifyBodyShape(16.0, 26.0, false))
        assertEquals("苗条肌肉型", service.classifyBodyShape(22.0, 17.0, false))
        assertEquals("苗条型", service.classifyBodyShape(22.0, 22.0, false))
        assertEquals("健康型", service.classifyBodyShape(22.0, 26.0, false))
        assertEquals("隐形肥胖型", service.classifyBodyShape(30.0, 17.0, false))
        assertEquals("偏胖型", service.classifyBodyShape(30.0, 22.0, false))
        assertEquals("肥胖型", service.classifyBodyShape(30.0, 26.0, false))
    }

    @Test
    fun `脂肪控制量_标准66_5kg_当前70kg_脂肪14kg_等于_负_0_7kg`() {
        val result = service.fatControl(66.5, 70.0, 14.0)
        assertNotNull(result)
        assertEquals(-0.7, result!!, 0.01)
    }
}