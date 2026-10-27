package com.xcwk.scalehub.application.query

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.service.BodyCompositionService
import com.xcwk.scalehub.domain.service.BodyShapeClassificationService
import com.xcwk.scalehub.domain.service.HealthAdviceService
import com.xcwk.scalehub.domain.service.HealthRiskAssessmentService
import org.junit.Test

/**
 * BodyAnalysisQuery 单元测试 —— 使用真实 Domain 服务（纯函数，无副作用）。
 */
class BodyAnalysisQueryTest {

    private val bodyCompService = BodyCompositionService()
    private val riskService = HealthRiskAssessmentService()
    private val shapeService = BodyShapeClassificationService()
    private val adviceService = HealthAdviceService()

    private val query = BodyAnalysisQuery(bodyCompService, riskService, shapeService, adviceService)

    private fun build(
        male: Boolean = true,
        age: Int? = 30,
        height: Int? = 175,
        weight: Double = 70.0,
        fat: Double? = null,
        prevWeight: Double? = null,
        prevFat: Double? = null,
        impedance: Double = 500.0,
        algorithm: String = "builtin-1",
    ): BodyAnalysisResult {
        val record = MeasureRecord(
            1000L, weight, impedance, fat ?: MeasureRecord.FAT_NOT_CALCULATED,
            "AA:BB", algorithm, if (male) true else false, age, height
        )
        return query.build(record, null, null)
    }

    @Test
    fun `标准体重公式_男女两式`() {
        // 男 (175-80)*0.7=66.5；女 (175-70)*0.6=63.0
        val maleResult = build().standardWeight
        assertThat(maleResult).isWithin(1e-9).of(66.5)
        val femaleResult = build(male = false).standardWeight
        assertThat(femaleResult).isWithin(1e-9).of(63.0)
        assertThat(build(height = null).standardWeight).isNull()
    }

    @Test
    fun `评分_偏轻扣分与下限20`() {
        // bmi 16.0 偏轻：s=100-min(40,(18.5-16)*4=10)=90 → 90
        val leanCase = build(weight = 49.0, impedance = 0.0)   // 无阻抗 → 无评分
        assertThat(leanCase.score).isNull()
        // 有阻抗+体脂时才评分：bmi16 → 扣10(偏轻)，fat10 恰在下界不扣，内脏3.5不扣 → 90
        val s = build(weight = 49.0, fat = 10.0).score
        assertThat(s).isEqualTo(90)
        // 极端：bmi 40 + fat 40 → 100-40(bmi封顶)-30(fat封顶)-内脏扣减，下限 20
        val floor = build(weight = 123.0, fat = 40.0).score
        assertThat(floor).isEqualTo(20)
    }

    @Test
    fun `体型矩阵_九宫格边界`() {
        // fatBand x bmiBand → SHAPES[fatBand][bmiBand]
        val thin = build(weight = 50.0, fat = 8.0)      // fat低 bmi低 → 消瘦型
        assertThat(thin.bodyShape).isEqualTo("消瘦型")
        val athlete = build(weight = 78.0, fat = 9.0)   // fat低 bmi正常偏上(25.3) → 运动员型
        assertThat(athlete.bodyShape).isEqualTo("运动员型")
        val obese = build(weight = 100.0, fat = 35.0)   // fat高 bmi高 → 肥胖型
        assertThat(obese.bodyShape).isEqualTo("肥胖型")
    }

    @Test
    fun `风险表_无阻抗缺两项_骨量阈值`() {
        val noZ = build(impedance = 0.0, fat = null)
        val names = noZ.risks.map { it.name }
        assertThat(names).containsExactly("肥胖症", "高血压", "糖尿病", "痛风").inOrder()
        // 有阻抗补齐两项
        val withZ = build(fat = 15.0)
        assertThat(withZ.risks.map { it.name }).containsExactly(
            "肥胖症", "脂肪肝", "高血压", "糖尿病", "骨质疏松", "高血脂", "痛风",
        ).inOrder()
        // 骨质疏松阈值：bone=weight*0.055 → ratio 0.055 >0.045 → 低风险
        assertThat(withZ.risks.first { it.name == "骨质疏松" }.tone).isEqualTo(1)
    }

    @Test
    fun `脂肪_无阻抗被清空但评分后口径`() {
        // 手动体脂 21 + 无阻抗：评分 null、指标无体脂项
        val r = build(fat = 21.0, impedance = 0.0)
        assertThat(r.metrics.map { it.name }).doesNotContain("体脂率")
        assertThat(r.score).isNull()
    }

    @Test
    fun `通用估算系数`() {
        // fat18.4% → 内脏=18.4*0.35=6.44→"6"（decimals0 非okok）; 去脂=70-12.88=57.1
        val r = build(algorithm = "builtin-1", fat = 18.4)
        val visceral = r.metrics.first { it.name == "内脏脂肪" }
        println("DEBUG: 内脏脂肪 valueText = '${visceral.valueText}', unit = '${visceral.unit}', decimals = 0")
        assertThat(visceral.valueText).isEqualTo("6")
        val lean = r.metrics.first { it.name == "去脂体重" }
        println("DEBUG: 去脂体重 valueText = '${lean.valueText}'")
        assertThat(lean.valueText).isEqualTo("57.1")
    }

    @Test
    fun `建议文_BMI各档命中`() {
        val bodyCompService = BodyCompositionService()
        val fatStd = bodyCompService.fatStandard(true)
        
        // BMI 0 (偏瘦) - 体重 49kg, 身高 175cm, 无阻抗
        val record1 = MeasureRecord(1000L, 49.0, 0.0, MeasureRecord.FAT_NOT_CALCULATED, "", "builtin-1", true, 30, 175)
        val r1 = BodyAnalysisQuery(BodyCompositionService(), HealthRiskAssessmentService(), BodyShapeClassificationService(), HealthAdviceService()).build(record1, null, null)
        assertThat(r1.advice).contains("偏轻")
        
        // BMI 2 (偏胖) - 体重 76kg, 有阻抗
        val record2 = MeasureRecord(1000L, 76.0, 500.0, 18.4, "AA:BB", "builtin-1", true, 30, 175)
        val r2 = BodyAnalysisQuery(BodyCompositionService(), HealthRiskAssessmentService(), BodyShapeClassificationService(), HealthAdviceService()).build(record2, null, null)
        assertThat(r2.advice).contains("略微超标")
        
        // BMI 3 (肥胖) - 体重 100kg
        val record3 = MeasureRecord(1000L, 100.0, 500.0, 30.0, "AA:BB", "builtin-1", true, 30, 175)
        val r3 = BodyAnalysisQuery(BodyCompositionService(), HealthRiskAssessmentService(), BodyShapeClassificationService(), HealthAdviceService()).build(record3, null, null)
        assertThat(r3.advice).contains("较多")
        
        // 无 BMI - 身高为 null
        val record4 = MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA:BB", "builtin-1", true, 30, null)
        val r4 = BodyAnalysisQuery(BodyCompositionService(), HealthRiskAssessmentService(), BodyShapeClassificationService(), HealthAdviceService()).build(record4, null, null)
        assertThat(r4.advice).contains("完善身高档案")
    }

    @Test
    fun `好坏行按tone分桶`() {
        // bmi22.9 标准 + fat18.4 偏高(fatStd男10-20→标准内?) fat18.4在10-20 → 标准 → good多
        val r = build(fat = 18.4)
        assertThat(r.goodLines).isNotEmpty()
        assertThat(r.goodLines.first()).contains("（标准）")
        assertThat(r.goodLines.first()).contains("继续保持当前生活习惯")
    }

    @Test
    fun `年龄越界置空`() {
        val r = build(age = 130)
        assertThat(r.missing).contains("出生年")
        assertThat(r.metrics.first { it.name == "实际年龄" }.valueText).isEqualTo("--")
    }
}