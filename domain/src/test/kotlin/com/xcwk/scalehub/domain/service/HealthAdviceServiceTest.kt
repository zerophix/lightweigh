package com.xcwk.scalehub.domain.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HealthAdviceService 黄金向量测试
 *
 * 验证建议文案生成与原始 BodyAnalysis.kt adviceOf/adviceOfBmi 完全一致。
 */
class HealthAdviceServiceTest {

    private val adviceService = HealthAdviceService()
    private val bodyCompService = BodyCompositionService()
    private val maleFatStd = bodyCompService.fatStandard(true)

    @Test
    fun `整体建议_BMI偏瘦`() {
        val advice = adviceService.overallAdvice(0, 15.0, maleFatStd)
        assertEquals("体重偏轻，注意均衡饮食与力量训练，适当增加优质蛋白摄入。", advice)
    }

    @Test
    fun `整体建议_BMI标准_体脂达标`() {
        val advice = adviceService.overallAdvice(1, 18.0, maleFatStd)
        assertEquals("身体数据处于标准区间，继续保持当前饮食与运动习惯。", advice)
    }

    @Test
    fun `整体建议_BMI标准_体脂超标`() {
        val advice = adviceService.overallAdvice(1, 25.0, maleFatStd)
        // body fat > std.max (20) -> 进入 else 分支
        assertEquals("继续规律测量，保持健康生活习惯。", advice)
    }

    @Test
    fun `整体建议_BMI偏胖`() {
        val advice = adviceService.overallAdvice(2, 18.0, maleFatStd)
        assertEquals("体重略微超标，每天早起步行 10 分钟，少吃零食油炸食品，七分饱即可。", advice)
    }

    @Test
    fun `整体建议_BMI肥胖`() {
        val advice = adviceService.overallAdvice(3, 25.0, maleFatStd)
        assertEquals("体重超标较多，建议控制油炸与甜食摄入，坚持有氧运动结合力量训练，循序渐进减重。", advice)
    }

    @Test
    fun `整体建议_BMI重度肥胖`() {
        val advice = adviceService.overallAdvice(4, 30.0, maleFatStd)
        assertEquals("体重超标较多，建议控制油炸与甜食摄入，坚持有氧运动结合力量训练，循序渐进减重。", advice)
    }

    @Test
    fun `整体建议_无BMI`() {
        val advice = adviceService.overallAdvice(null, 15.0, maleFatStd)
        assertEquals("完善身高档案后，即可生成完整的体测分析与建议。", advice)
    }

    @Test
    fun `单项建议_标准声调_返回保持习惯`() {
        val advice = adviceService.metricAdvice("BMI", 1)
        assertEquals("继续保持当前生活习惯。", advice)
    }

    @Test
    fun `单项建议_BMI异常_返回控制热量`() {
        val advice = adviceService.metricAdvice("BMI", 2)
        assertEquals("控制总热量摄入，增加有氧运动频率，循序渐进回归标准区间。", advice)

        val advice3 = adviceService.metricAdvice("BMI", 3)
        assertEquals("控制总热量摄入，增加有氧运动频率，循序渐进回归标准区间。", advice3)
    }

    @Test
    fun `单项建议_体脂率异常_返回减少油脂`() {
        val advice = adviceService.metricAdvice("体脂率", 2)
        assertEquals("减少油脂与精制糖摄入，增加力量训练提升肌肉占比。", advice)
    }

    @Test
    fun `单项建议_骨骼肌率异常_返回抗阻训练`() {
        val advice = adviceService.metricAdvice("骨骼肌率", 2)
        assertEquals("加强抗阻力量训练，配合足量蛋白质摄入。", advice)
    }

    @Test
    fun `单项建议_基础代谢异常_返回规律三餐`() {
        val advice = adviceService.metricAdvice("基础代谢", 2)
        assertEquals("规律三餐、增加肌肉量、保证睡眠可有效提升基础代谢。", advice)
    }

    @Test
    fun `单项建议_未知指标_返回通用建议`() {
        val advice = adviceService.metricAdvice("未知指标", 2)
        assertEquals("建议结合饮食和运动逐步调整。", advice)
    }

    @Test
    fun `分类建议_生成优项与关注项`() {
        val metrics = listOf(
            HealthAdviceService.MetricInput("BMI", "22.5", "", "标准", 1),
            HealthAdviceService.MetricInput("体脂率", "25.0", "%", "肥胖", 3),
            HealthAdviceService.MetricInput("骨骼肌率", "35.0", "%", "充足", 1),
            HealthAdviceService.MetricInput("内脏脂肪", "12.0", "级", "偏高", 2),
            HealthAdviceService.MetricInput("未录入指标", "--", "", "未录入", 0)
        )

        val result = adviceService.classifyAdvice(metrics)

        // 优项：BMI(标准)、骨骼肌率(充足)
        assertEquals(2, result.goodLines.size)
        assertTrue(result.goodLines[0].contains("BMI"))
        assertTrue(result.goodLines[1].contains("骨骼肌率"))

        // 关注项：体脂率(肥胖)、内脏脂肪(偏高)
        assertEquals(2, result.warnLines.size)
        assertTrue(result.warnLines[0].contains("体脂率"))
        assertTrue(result.warnLines[1].contains("内脏脂肪"))

        // 未录入指标被跳过
        assertFalse(result.goodLines.any { it.contains("未录入指标") })
        assertFalse(result.warnLines.any { it.contains("未录入指标") })
    }

    @Test
    fun `状态标签_BMI标签`() {
        assertEquals("偏瘦", HealthAdviceService.StatusLabels.bmiLabel(0))
        assertEquals("标准", HealthAdviceService.StatusLabels.bmiLabel(1))
        assertEquals("偏胖", HealthAdviceService.StatusLabels.bmiLabel(2))
        assertEquals("肥胖", HealthAdviceService.StatusLabels.bmiLabel(3))
        assertEquals("重度肥胖", HealthAdviceService.StatusLabels.bmiLabel(4))
        assertEquals("未知", HealthAdviceService.StatusLabels.bmiLabel(99))
    }

    @Test
    fun `状态标签_肥胖度标签`() {
        assertEquals("偏低", HealthAdviceService.StatusLabels.obesityLabel(-15.0))
        assertEquals("标准", HealthAdviceService.StatusLabels.obesityLabel(5.0))
        assertEquals("标准", HealthAdviceService.StatusLabels.obesityLabel(-5.0))
        assertEquals("超重", HealthAdviceService.StatusLabels.obesityLabel(15.0))
        assertEquals("肥胖", HealthAdviceService.StatusLabels.obesityLabel(25.0))
    }

    @Test
    fun `状态标签_体脂率标签`() {
        assertEquals("偏低", HealthAdviceService.StatusLabels.fatLabel(8.0, maleFatStd))
        assertEquals("标准", HealthAdviceService.StatusLabels.fatLabel(15.0, maleFatStd))
        assertEquals("肥胖", HealthAdviceService.StatusLabels.fatLabel(25.0, maleFatStd))
    }

    @Test
    fun `声调计算_BMI声调`() {
        assertEquals(2, HealthAdviceService.Tones.bmiTone(0))
        assertEquals(1, HealthAdviceService.Tones.bmiTone(1))
        assertEquals(2, HealthAdviceService.Tones.bmiTone(2))
        assertEquals(3, HealthAdviceService.Tones.bmiTone(3))
        assertEquals(3, HealthAdviceService.Tones.bmiTone(4))
    }

    @Test
    fun `声调计算_体脂声调`() {
        assertEquals(2, HealthAdviceService.Tones.fatTone(8.0, maleFatStd))
        assertEquals(1, HealthAdviceService.Tones.fatTone(15.0, maleFatStd))
        assertEquals(2, HealthAdviceService.Tones.fatTone(25.0, maleFatStd))
    }

    @Test
    fun `数值格式化_保留小数位`() {
        assertEquals("22.5", HealthAdviceService.Format.fmt(22.5, 1))
        assertEquals("22.50", HealthAdviceService.Format.fmt(22.5, 2))
        assertEquals("22", HealthAdviceService.Format.fmt(22.0, 0))
        assertEquals("--", HealthAdviceService.Format.fmt(null, 1))
        assertEquals("--", HealthAdviceService.Format.fmt(Double.NaN, 1))
    }

    @Test
    fun `数值格式化_差值带符号`() {
        assertEquals("+2.5", HealthAdviceService.Format.signed(25.0, 22.5, 1))
        assertEquals("-1.2", HealthAdviceService.Format.signed(20.0, 21.2, 1))
        assertNull(HealthAdviceService.Format.signed(25.0, null, 1))
    }
}