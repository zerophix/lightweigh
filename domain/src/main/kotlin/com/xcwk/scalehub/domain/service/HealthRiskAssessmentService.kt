package com.xcwk.scalehub.domain.service

/**
 * 健康风险评估域服务 —— 纯函数、无副作用、可 JVM 单测。
 *
 * 从 application.query.BodyAnalysis.kt 的 risksOf 函数迁移而来。
 * 评估 7 大健康风险：肥胖症、脂肪肝、高血压、糖尿病、骨质疏松、高血脂、痛风
 */
class HealthRiskAssessmentService {

    /**
     * 风险等级文案
     */
    private fun riskText(tone: Int): String = when (tone) {
        3 -> "高风险"
        2 -> "中风险"
        else -> "低风险"
    }

    /**
     * 风险评估结果
     */
    data class RiskResult(
        val name: String,
        val levelText: String,
        val tone: Int  // 1=低 2=中 3=高
    )

    /**
     * 评估所有健康风险
     *
     * @param hasImpedance 是否有阻抗数据（决定是否评估部分需阻抗的风险）
     * @param bmi BMI 值
     * @param fatPercent 体脂率
     * @param fatStandard 体脂率标准区间
     * @param visceralFat 内脏脂肪等级
     * @param boneWeight 骨量
     * @param weightKg 体重
     * @param age 年龄
     * @return 7 项风险评估结果
     */
    fun assessAll(
        hasImpedance: Boolean,
        bmi: Double?,
        fatPercent: Double?,
        fatStandard: BodyCompositionService.FatStandard,
        visceralFat: Double?,
        boneWeight: Double?,
        weightKg: Double,
        age: Int?
    ): List<RiskResult> {
        val risks = mutableListOf<RiskResult>()

        // 1. 肥胖症风险 —— 仅基于 BMI
        if (bmi != null) {
            val obesityTone = when {
                bmi >= 28 -> 3
                bmi >= 24 -> 2
                else -> 1
            }
            risks += RiskResult("肥胖症", riskText(obesityTone), obesityTone)
        }

        // 2. 脂肪肝风险 —— 需阻抗数据
        if (hasImpedance) {
            var liverTone = 1
            if ((visceralFat != null && visceralFat > 14) || (fatPercent != null && fatPercent > fatStandard.max + 8)) {
                liverTone = 3
            } else if ((visceralFat != null && visceralFat > 9) || (fatPercent != null && fatPercent > fatStandard.max)) {
                liverTone = 2
            }
            risks += RiskResult("脂肪肝", riskText(liverTone), liverTone)
        }

        // 3. 高血压风险
        if (bmi != null) {
            val pressureTone = when {
                bmi >= 28 && age != null && age >= 40 -> 3
                bmi >= 24 -> 2
                else -> 1
            }
            risks += RiskResult("高血压", riskText(pressureTone), pressureTone)
        }

        // 4. 糖尿病风险
        if (bmi != null) {
            val sugarTone = when {
                bmi >= 28 && fatPercent != null && fatPercent > fatStandard.max -> 3
                bmi >= 24 -> 2
                else -> 1
            }
            risks += RiskResult("糖尿病", riskText(sugarTone), sugarTone)
        }

        // 5. 骨质疏松风险 —— 需骨量数据
        if (boneWeight != null && weightKg > 0) {
            val ratio = boneWeight / weightKg
            val tone = when {
                ratio < 0.035 -> 3
                ratio < 0.045 -> 2
                else -> 1
            }
            risks += RiskResult("骨质疏松", riskText(tone), tone)
        }

        // 6. 高血脂风险 —— 需阻抗数据
        if (hasImpedance) {
            var lipidTone = 1
            if ((fatPercent != null && fatPercent > fatStandard.max + 8) || (visceralFat != null && visceralFat > 14)) {
                lipidTone = 3
            } else if (fatPercent != null && fatPercent > fatStandard.max) {
                lipidTone = 2
            }
            risks += RiskResult("高血脂", riskText(lipidTone), lipidTone)
        }

        // 7. 痛风风险
        if (bmi != null) {
            val goutTone = when {
                bmi >= 32 -> 3
                bmi >= 28 -> 2
                else -> 1
            }
            risks += RiskResult("痛风", riskText(goutTone), goutTone)
        }

        return risks
    }

    /**
     * 单项风险评估（用于细粒度测试）
     */
    fun assessObesity(bmi: Double?): RiskResult? = bmi?.let {
        val tone = when { it >= 28 -> 3; it >= 24 -> 2; else -> 1 }
        RiskResult("肥胖症", riskText(tone), tone)
    }

    fun assessFattyLiver(hasImpedance: Boolean, visceralFat: Double?, fatPercent: Double?, fatStandard: BodyCompositionService.FatStandard): RiskResult? {
        if (!hasImpedance) return null
        val tone = when {
            (visceralFat != null && visceralFat > 14) || (fatPercent != null && fatPercent > fatStandard.max + 8) -> 3
            (visceralFat != null && visceralFat > 9) || (fatPercent != null && fatPercent > fatStandard.max) -> 2
            else -> 1
        }
        return RiskResult("脂肪肝", riskText(tone), tone)
    }

    fun assessHypertension(bmi: Double?, age: Int?): RiskResult? = bmi?.let {
        val tone = when { it >= 28 && (age != null && age >= 40) -> 3; it >= 24 -> 2; else -> 1 }
        RiskResult("高血压", riskText(tone), tone)
    }

    fun assessDiabetes(bmi: Double?, fatPercent: Double?, fatStandard: BodyCompositionService.FatStandard): RiskResult? = bmi?.let {
        val tone = when { it >= 28 && (fatPercent != null && fatPercent > fatStandard.max) -> 3; it >= 24 -> 2; else -> 1 }
        RiskResult("糖尿病", riskText(tone), tone)
    }

    fun assessOsteoporosis(boneWeight: Double?, weightKg: Double): RiskResult? = if (boneWeight != null && weightKg > 0) {
        val ratio = boneWeight / weightKg
        val tone = when { ratio < 0.035 -> 3; ratio < 0.045 -> 2; else -> 1 }
        RiskResult("骨质疏松", riskText(tone), tone)
    } else null

    fun assessHyperlipidemia(hasImpedance: Boolean, fatPercent: Double?, fatStandard: BodyCompositionService.FatStandard, visceralFat: Double?): RiskResult? {
        if (!hasImpedance) return null
        val tone = when {
            (fatPercent != null && fatPercent > fatStandard.max + 8) || (visceralFat != null && visceralFat > 14) -> 3
            fatPercent != null && fatPercent > fatStandard.max -> 2
            else -> 1
        }
        return RiskResult("高血脂", riskText(tone), tone)
    }

    fun assessGout(bmi: Double?): RiskResult? = bmi?.let {
        val tone = when { it >= 32 -> 3; it >= 28 -> 2; else -> 1 }
        RiskResult("痛风", riskText(tone), tone)
    }
}