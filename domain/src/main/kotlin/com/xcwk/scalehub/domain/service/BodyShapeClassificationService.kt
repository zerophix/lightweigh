package com.xcwk.scalehub.domain.service

/**
 * 体型分类域服务 —— 纯函数、无副作用、可 JVM 单测。
 *
 * 从 application.query.BodyAnalysis.kt 的 SHAPES 3x3 矩阵迁移而来。
 * 基于体脂率等级（行）× BMI 等级（列）的 3x3 分类矩阵。
 */
class BodyShapeClassificationService {

    /**
     * 体型分类 3x3 矩阵
     * 行索引：脂肪等级 (0=偏低, 1=标准, 2=偏高)
     * 列索引：BMI 等级 (0=偏瘦, 1=标准, 2=偏胖/肥胖)
     */
    private val SHAPES: Array<Array<String>> = arrayOf(
        arrayOf("消瘦型", "低脂肪型", "运动员型"),
        arrayOf("苗条肌肉型", "苗条型", "健康型"),
        arrayOf("隐形肥胖型", "偏胖型", "肥胖型")
    )

    /**
     * 体脂率等级划分
     */
    enum class FatBand(val index: Int, val label: String) {
        LOW(0, "偏低"),
        NORMAL(1, "标准"),
        HIGH(2, "偏高")
    }

    /**
     * BMI 等级划分
     */
    enum class BmiBand(val index: Int, val label: String) {
        UNDERWEIGHT(0, "偏瘦"),
        NORMAL(1, "标准"),
        OVERWEIGHT(2, "偏胖/肥胖")
    }

    /**
     * 体型分类结果
     */
    data class ShapeResult(
        val shapeName: String,      // 如 "苗条型"
        val fatBand: FatBand,       // 脂肪等级
        val bmiBand: BmiBand,       // BMI 等级
        val description: String     // 人类可读描述
    )

    /**
     * 计算体型分类
     *
     * @param fatPercent 体脂率 %
     * @param bmi BMI 值
     * @param fatStandard 体脂率标准区间（用于确定脂肪等级）
     * @return 体型分类结果，若输入不足返回 null
     */
    fun classify(
        fatPercent: Double?,
        bmi: Double?,
        fatStandard: BodyCompositionService.FatStandard
    ): ShapeResult? {
        val fatBand = fatPercent?.let { determineFatBand(it, fatStandard) } ?: return null
        val bmiBand = bmi?.let { determineBmiBand(it) } ?: return null

        val shapeName = SHAPES[fatBand.index][bmiBand.index]
        val description = buildDescription(shapeName, fatBand, bmiBand)

        return ShapeResult(shapeName, fatBand, bmiBand, description)
    }

    /**
     * 仅通过 fatPercent 和 fatStandard 确定脂肪等级（用于测试）
     */
    fun determineFatBand(fatPercent: Double, fatStandard: BodyCompositionService.FatStandard): FatBand = when {
        fatPercent < fatStandard.min -> FatBand.LOW
        fatPercent > fatStandard.max -> FatBand.HIGH
        else -> FatBand.NORMAL
    }

    /**
     * 仅通过 BMI 确定 BMI 等级（用于测试）
     */
    fun determineBmiBand(bmi: Double): BmiBand = when {
        bmi < 18.5 -> BmiBand.UNDERWEIGHT
        bmi < 24 -> BmiBand.NORMAL
        else -> BmiBand.OVERWEIGHT
    }

    /**
     * 获取完整的 3x3 矩阵（用于 UI 展示或测试验证）
     */
    fun getShapeMatrix(): Array<Array<String>> = SHAPES

    /**
     * 获取指定坐标的体型名称（用于测试验证）
     */
    fun getShapeAt(fatBandIndex: Int, bmiBandIndex: Int): String? {
        if (fatBandIndex !in 0..2 || bmiBandIndex !in 0..2) return null
        return SHAPES[fatBandIndex][bmiBandIndex]
    }

    private fun buildDescription(shapeName: String, fatBand: FatBand, bmiBand: BmiBand): String {
        return when (shapeName) {
            "消瘦型" -> "体脂偏低且 BMI 偏瘦，建议增加营养摄入与力量训练"
            "低脂肪型" -> "体脂偏低但 BMI 标准，肌肉量可能不足，建议抗阻训练配合增肌饮食"
            "运动员型" -> "体脂偏低且 BMI 偏高，典型运动员体型，肌肉量充足"
            "苗条肌肉型" -> "体脂标准且 BMI 偏瘦，线条流畅，建议维持现有训练强度"
            "苗条型" -> "体脂标准且 BMI 标准，匀称体型，继续保持健康生活方式"
            "健康型" -> "体脂标准且 BMI 偏高，肌肉量良好，属健康偏壮实体型"
            "隐形肥胖型" -> "体脂偏高但 BMI 偏瘦，外观不胖但内脏脂肪可能超标，需有氧结合力量训练"
            "偏胖型" -> "体脂偏高且 BMI 标准，体重看似正常但体脂超标，建议控制体脂率"
            "肥胖型" -> "体脂偏高且 BMI 偏高，典型肥胖体型，需系统性减重干预"
            else -> "体型分类：$shapeName（体脂${fatBand.label}，BMI${bmiBand.label}）"
        }
    }

    companion object {
        /** 所有可能的体型名称列表 */
        val ALL_SHAPES = BodyShapeClassificationService().SHAPES.flatten().toList()
    }
}