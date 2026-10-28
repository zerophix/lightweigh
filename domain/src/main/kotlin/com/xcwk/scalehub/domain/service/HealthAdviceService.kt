package com.xcwk.scalehub.domain.service

/**
 * 健康建议生成域服务 —— 纯函数、无副作用、可 JVM 单测。
 *
 * 从 application.query.BodyAnalysis.kt 的 adviceOf / adviceOfBmi 迁移而来。
 * 根据指标声调（tone：1=标准 2=注意 3=超标/不足 0=未录入）生成针对性建议文案。
 */
class HealthAdviceService {

    /**
     * 综合建议：基于 BMI 等级与体脂率生成整体建议
     */
    fun overallAdvice(
        bmiLevel: Int?,          // 0=偏瘦 1=标准 2=偏胖 3=肥胖 4=重度肥胖
        fatPercent: Double?,
        fatStandard: BodyCompositionService.FatStandard
    ): String = when {
        bmiLevel == null -> "完善身高档案后，即可生成完整的体测分析与建议。"
        bmiLevel == 0 -> "体重偏轻，注意均衡饮食与力量训练，适当增加优质蛋白摄入。"
        bmiLevel == 1 && fatPercent != null && fatPercent <= fatStandard.max ->
            "身体数据处于标准区间，继续保持当前饮食与运动习惯。"
        bmiLevel == 2 -> "体重略微超标，每天早起步行 10 分钟，少吃零食油炸食品，七分饱即可。"
        bmiLevel >= 3 -> "体重超标较多，建议控制油炸与甜食摄入，坚持有氧运动结合力量训练，循序渐进减重。"
        else -> "继续规律测量，保持健康生活习惯。"
    }

    /**
     * 单项指标建议：基于指标名称与声调生成针对性建议
     *
     * @param metricName 指标名称（如 "BMI"、"体脂率"、"骨骼肌率" 等）
     * @param tone 声调：1=标准/达标 2=注意/偏高/偏低/不足 3=超标/严重不足 0=未录入
     * @return 建议文案
     */
    fun metricAdvice(metricName: String, tone: Int): String = when (tone) {
        1 -> "继续保持当前生活习惯。"
        else -> when (metricName) {
            "BMI" -> "控制总热量摄入，增加有氧运动频率，循序渐进回归标准区间。"
            "肥胖度" -> "减少多余热量堆积，建议每周 3 次以上中等强度运动。"
            "体脂率" -> "减少油脂与精制糖摄入，增加力量训练提升肌肉占比。"
            "脂肪重量" -> "通过热量缺口逐步降低脂肪重量，避免极端节食。"
            "骨骼肌率", "骨骼肌重量" -> "加强抗阻力量训练，配合足量蛋白质摄入。"
            "肌肉率", "肌肉重量" -> "建议每周 2-3 次力量训练并保证蛋白质摄入。"
            "基础代谢" -> "规律三餐、增加肌肉量、保证睡眠可有效提升基础代谢。"
            "蛋白质" -> "多吃鱼虾、蛋奶、豆制品等优质蛋白食物。"
            "水分率", "水含量" -> "每天少量多次饮水，运动前后及时补水。"
            "内脏脂肪" -> "减少精制碳水与酒精，坚持有氧运动。"
            "身体年龄" -> "规律作息、坚持运动、控制体脂可改善身体年龄。"
            "骨量" -> "增加钙质摄入，配合负重运动增强骨骼强度。"
            "去脂体重" -> "维持足量蛋白摄入与抗阻训练，保护去脂组织。"
            else -> "建议结合饮食和运动逐步调整。"
        }
    }

    /**
     * 好/坏分类建议：用于生成「优项」与「关注项」列表
     */
    data class ClassifiedAdvice(
        val goodLines: List<String>,   // tone=1 的指标建议
        val warnLines: List<String>    // tone=2,3 的指标建议
    )

    /**
     * 批量分类生成优项/关注项
     *
     * @param metrics 指标列表，每项含 name、valueText、unit、status、tone
     * @return 分类后的建议列表
     */
    fun classifyAdvice(metrics: List<MetricInput>): ClassifiedAdvice {
        val good = mutableListOf<String>()
        val warn = mutableListOf<String>()

        for (m in metrics) {
            if (m.valueText == "--") continue
            val line = "${m.name} ${m.valueText}${m.unit}（${m.status}）\n${metricAdvice(m.name, m.tone)}"
            when (m.tone) {
                1 -> good += line
                2, 3 -> warn += line
            }
        }
        return ClassifiedAdvice(good, warn)
    }

    /**
     * 指标输入模型（供 UI/Application 层传入）
     */
    data class MetricInput(
        val name: String,
        val valueText: String,   // 已格式化；"--" = 无数值
        val unit: String,
        val status: String,      // "标准"/"不足"/"未录入"...
        val tone: Int            // 1=标准 2=注意 3=超标 0=未录入
    )

    /**
     * 状态标签生成（复用 BodyAnalysis 的逻辑，保持一致性）
     */
    object StatusLabels {
        fun bmiLabel(level: Int): String = when (level) {
            0 -> "偏瘦"
            1 -> "标准"
            2 -> "偏胖"
            3 -> "肥胖"
            4 -> "重度肥胖"
            else -> "未知"
        }

        fun obesityLabel(obesityPercent: Double): String = when {
            Math.abs(obesityPercent) <= 10 -> "标准"
            obesityPercent > 20 -> "肥胖"
            obesityPercent > 10 -> "超重"
            else -> "偏低"
        }

        fun fatLabel(fatPercent: Double, std: BodyCompositionService.FatStandard): String = when {
            fatPercent < std.min -> "偏低"
            fatPercent > std.max -> "肥胖"
            else -> "标准"
        }

        fun skeletalLabel(skeletalPercent: Double?, male: Boolean): String {
            val range = if (male) BodyCompositionService.FatStandard(28.0, 38.0) else BodyCompositionService.FatStandard(22.0, 32.0)
            return skeletalPercent?.let {
                if (it < range.min) "不足" else "充足"
            } ?: "未录入"
        }

        fun muscleLabel(muscleRate: Double?, male: Boolean): String {
            val range = if (male) BodyCompositionService.FatStandard(32.0, 56.0) else BodyCompositionService.FatStandard(28.0, 52.0)
            return muscleRate?.let {
                if (it < range.min) "不足" else if (it > range.max) "偏高" else "标准"
            } ?: "未录入"
        }

        fun boneLabel(boneWeight: Double?, weightKg: Double): String = if (boneWeight != null && weightKg > 0) {
            if (boneWeight / weightKg < 0.035) "不足" else "充足"
        } else "未录入"

        fun bmrLabel(bmr: Double?, male: Boolean): String {
            val range = if (male) BodyCompositionService.FatStandard(1300.0, 1700.0) else BodyCompositionService.FatStandard(1100.0, 1500.0)
            return bmr?.let {
                if (it < range.min) "未达标" else "达标"
            } ?: "未录入"
        }

        fun proteinLabel(protein: Double?): String {
            val range = BodyCompositionService.FatStandard(14.0, 20.0)
            return protein?.let {
                if (it < range.min) "不足" else if (it > range.max) "偏高" else "标准"
            } ?: "未录入"
        }

        fun waterLabel(waterRate: Double?, male: Boolean): String {
            val range = if (male) BodyCompositionService.FatStandard(55.0, 65.0) else BodyCompositionService.FatStandard(50.0, 60.0)
            return waterRate?.let {
                if (it < range.min) "不足" else if (it > range.max) "偏高" else "标准"
            } ?: "未录入"
        }

        fun visceralLabel(visceral: Double?): String = visceral?.let {
            when {
                it <= 9 -> "标准"
                it <= 14 -> "偏高"
                else -> "超标"
            }
        } ?: "未录入"

        fun bodyAgeLabel(bodyAge: Int?, age: Int?): String = if (bodyAge != null && age != null) {
            if (bodyAge <= age) "年轻" else "偏大"
        } else "未录入"
    }

    /**
     * 声调计算（复用 BodyAnalysis 的逻辑，保持一致性）
     */
    object Tones {
        fun bmiTone(level: Int): Int = if (level == 1) 1 else if (level == 0 || level == 2) 2 else 3

        fun obesityTone(obesityPercent: Double): Int = if (Math.abs(obesityPercent) <= 10) 1 else if (obesityPercent > 0 && obesityPercent <= 20) 2 else 3

        fun fatTone(fatPercent: Double, std: BodyCompositionService.FatStandard): Int = if (fatPercent < std.min || fatPercent > std.max) 2 else 1

        fun skeletalTone(skeletalPercent: Double?, male: Boolean): Int {
            val range = if (male) BodyCompositionService.FatStandard(28.0, 38.0) else BodyCompositionService.FatStandard(22.0, 32.0)
            return skeletalPercent?.let { if (it < range.min) 2 else 1 } ?: 0
        }

        fun muscleTone(muscleRate: Double?, male: Boolean): Int {
            val range = if (male) BodyCompositionService.FatStandard(32.0, 56.0) else BodyCompositionService.FatStandard(28.0, 52.0)
            return muscleRate?.let { if (it < range.min || it > range.max) 2 else 1 } ?: 0
        }

        fun boneTone(boneWeight: Double?, weightKg: Double): Int = if (boneWeight != null && weightKg > 0) {
            if (boneWeight / weightKg < 0.035) 2 else 1
        } else 0

        fun bmrTone(bmr: Double?, male: Boolean): Int {
            val range = if (male) BodyCompositionService.FatStandard(1300.0, 1700.0) else BodyCompositionService.FatStandard(1100.0, 1500.0)
            return bmr?.let { if (it < range.min) 2 else 1 } ?: 0
        }

        fun proteinTone(protein: Double?): Int {
            val range = BodyCompositionService.FatStandard(14.0, 20.0)
            return protein?.let { if (it < range.min || it > range.max) 2 else 1 } ?: 0
        }

        fun waterTone(waterRate: Double?, male: Boolean): Int {
            val range = if (male) BodyCompositionService.FatStandard(55.0, 65.0) else BodyCompositionService.FatStandard(50.0, 60.0)
            return waterRate?.let { if (it < range.min || it > range.max) 2 else 1 } ?: 0
        }

        fun visceralTone(visceral: Double?): Int = visceral?.let {
            when {
                it <= 9 -> 1
                it <= 14 -> 2
                else -> 3
            }
        } ?: 0

        fun bodyAgeTone(bodyAge: Int?, age: Int?): Int = if (bodyAge != null && age != null) {
            if (bodyAge <= age) 1 else 2
        } else 0

        fun recordedTone(hasValue: Boolean): Int = if (hasValue) 1 else 0
    }

    /**
     * 数值格式化工具
     */
    object Format {
        private val LOCALE = java.util.Locale.US

        fun fmt(value: Double?, decimals: Int): String {
            if (value == null || value.isNaN()) return "--"
            return "%.${decimals}f".format(LOCALE, value)
        }

        fun signed(now: Double, previous: Double?, decimals: Int): String? {
            if (previous == null) return null
            return "%+.${decimals}f".format(LOCALE, now - previous)
        }
    }
}