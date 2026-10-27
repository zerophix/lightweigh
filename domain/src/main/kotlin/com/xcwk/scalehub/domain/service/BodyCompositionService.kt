package com.xcwk.scalehub.domain.service

import com.xcwk.scalehub.domain.algorithm.BodyComposition
import com.xcwk.scalehub.domain.algorithm.OkokBia

/**
 * 体成分计算域服务 —— 纯函数、无副作用、可 JVM 单测。
 *
 * 从 application.query.BodyAnalysis.kt (v2) 与 domain.algorithm.OkokBia.java 迁移而来。
 * 所有公式按公开说明独立实现，已保存的 builtin-1 记录不重算。
 *
 * 设计约束：
 * - 无 Android/框架依赖，纯 Kotlin/JVM
 * - 所有方法为纯函数：相同输入必然相同输出
 * - 算法精度与 OkokBia.analyze 完全一致（黄金向量对齐）
 * - 入参单位：heightCm=厘米、weightKg=公斤、impedanceOhm=欧姆
 */
class BodyCompositionService(
    private val okokBia: BodyComposition = OkokBia.INSTANCE
) {

    /**
     * 标准体重区间（kg）
     * 男：(身高-80)×0.7  女：(身高-70)×0.6
     */
    fun standardWeight(male: Boolean, heightCm: Int): Double? {
        return if (heightCm > 0) {
            if (male) (heightCm - 80) * 0.7 else (heightCm - 70) * 0.6
        } else null
    }

    /**
     * BMI 计算
     */
    fun bmi(weightKg: Double, heightCm: Int): Double? {
        return if (heightCm > 0) weightKg / Math.pow(heightCm / 100.0, 2.0) else null
    }

    /**
     * BMI 五级分级：0=偏瘦 1=标准 2=偏胖 3=肥胖 4=重度肥胖
     */
    fun bmiLevel(bmi: Double): Int = when {
        bmi < 18.5 -> 0
        bmi < 24 -> 1
        bmi < 28 -> 2
        bmi < 32 -> 3
        else -> 4
    }

    /**
     * 体脂率标准区间
     */
    data class FatStandard(val min: Double, val max: Double)
    fun fatStandard(male: Boolean): FatStandard = if (male) FatStandard(10.0, 20.0) else FatStandard(18.0, 28.0)

    /**
     * 肥胖度计算：(体重-标准体重)/标准体重 × 100%
     */
    fun obesityPercent(weightKg: Double, stdWeight: Double?): Double? {
        return if (stdWeight != null && stdWeight != 0.0) {
            ((weightKg - stdWeight) / stdWeight) * 100.0
        } else null
    }

    /**
     * 通用估算路径（非 OKOK 协议，有阻抗时）
     * 基于体脂率反推各项指标
     */
    data class GeneralEstimation(
        val fatWeight: Double?,      // 脂肪重量
        val leanWeight: Double?,     // 去脂体重
        val muscleRate: Double?,     // 肌肉率 %
        val muscleWeight: Double?,   // 肌肉重量
        val boneWeight: Double?,     // 骨量
        val bmr: Double?,            // 基础代谢
        val proteinPercent: Double?, // 蛋白质率 %
        val waterRate: Double?,      // 水分率 %
        val waterWeight: Double?,    // 水含量
        val visceralFat: Double?,    // 内脏脂肪等级
        val bodyAge: Int?            // 身体年龄
    )

    fun estimateGeneral(
        male: Boolean,
        age: Int?,
        heightCm: Int,
        weightKg: Double,
        fatPercent: Double,
        impedanceOhm: Double
    ): GeneralEstimation {
        val hasImpedance = impedanceOhm > 0
        var fatWeight: Double? = null
        var lean: Double? = null
        var muscleRate: Double? = null
        var muscleWeight: Double? = null
        var bone: Double? = null
        var bmr: Double? = null
        var protein: Double? = null
        var waterRate: Double? = null
        var waterWeight: Double? = null
        var visceral: Double? = null
        var bodyAge: Int? = null

        if (hasImpedance && fatPercent >= 0) {
            fatWeight = weightKg * fatPercent / 100.0
            lean = weightKg - fatWeight!!
            val muscleFactor = if (male) 0.78 else 0.75
            muscleRate = (lean!! / weightKg) * 100.0 * muscleFactor
            muscleWeight = weightKg * muscleRate!! / 100.0
            bone = weightKg * 0.055
            if (age != null && heightCm > 0) {
                bmr = if (male)
                    10 * weightKg + 6.25 * heightCm - 5 * age + 5.0
                else
                    10 * weightKg + 6.25 * heightCm - 5 * age - 161.0
            }
            protein = (lean!! * 0.19 / weightKg) * 100.0
            waterRate = (lean * 0.73 / weightKg) * 100.0
            waterWeight = weightKg * waterRate!! / 100.0
            visceral = fatPercent * 0.35
            if (age != null) {
                val bmiVal = bmi(weightKg, heightCm)
                val fatStd = fatStandard(male)
                if (bmiVal != null) {
                    val raw = age + (bmiVal - 22) * 0.8 + (fatPercent - (fatStd.min + fatStd.max) / 2.0) * 0.3
                    bodyAge = Math.round(raw).toInt()
                }
            }
        }

        return GeneralEstimation(
            fatWeight = fatWeight,
            leanWeight = lean,
            muscleRate = muscleRate,
            muscleWeight = muscleWeight,
            boneWeight = bone,
            bmr = bmr,
            proteinPercent = protein,
            waterRate = waterRate,
            waterWeight = waterWeight,
            visceralFat = visceral,
            bodyAge = bodyAge
        )
    }

    /**
     * OKOK 协议路径：调用 OkokBia.analyze 获取完整分析
     */
    data class OkokAnalysis(
        val fatPercent: Double,
        val musclePercent: Double?,
        val muscleKg: Double?,
        val boneKg: Double?,
        val waterPercent: Double?,
        val waterKg: Double?,
        val proteinPercent: Double?,
        val visceralFat: Double?,
        val bmr: Double?,
        val bodyAge: Int
    )

    fun analyzeOkok(
        male: Boolean,
        age: Int,
        heightCm: Int,
        weightKg: Double,
        impedanceOhm: Double
    ): OkokAnalysis? {
        val bia = OkokBia.analyze(male, age, heightCm, weightKg, impedanceOhm)
        return bia?.let {
            OkokAnalysis(
                fatPercent = it.fatPercent,
                musclePercent = it.musclePercent,
                muscleKg = it.muscleKg,
                boneKg = it.boneKg,
                waterPercent = it.waterPercent,
                waterKg = it.waterKg,
                proteinPercent = it.proteinPercent,
                visceralFat = it.visceral,
                bmr = it.bmr,
                bodyAge = it.bodyAge
            )
        }
    }

    /**
     * 骨骼肌计算（基于去脂体重）
     */
    data class SkeletalMuscle(
        val skeletalWeight: Double?,
        val skeletalPercent: Double?
    )

    fun calculateSkeletalMuscle(male: Boolean, leanWeight: Double?, weightKg: Double): SkeletalMuscle {
        var skeletalWeight: Double? = null
        var skeletalPercent: Double? = null
        if (leanWeight != null && leanWeight > 0) {
            skeletalWeight = if (male)
                0.59765708 * leanWeight - 1.91104591
            else
                0.59740084 * leanWeight - 2.18052530
            if (skeletalWeight!! <= 0.0) skeletalWeight = leanWeight * 0.598
            skeletalPercent = skeletalWeight!! / weightKg * 100.0
        }
        return SkeletalMuscle(skeletalWeight, skeletalPercent)
    }

    /**
     * 综合评分计算（0-100，最低 20）
     */
    fun calculateScore(
        hasImpedance: Boolean,
        bmi: Double?,
        fatPercent: Double?,
        visceralFat: Double?,
        male: Boolean
    ): Int? {
        return if (hasImpedance && bmi != null && (fatPercent != null || visceralFat != null)) {
            var s = 100.0
            if (bmi < 18.5) s -= minOf(40.0, (18.5 - bmi) * 4)
            if (bmi > 24) s -= minOf(40.0, (bmi - 24) * 5)
            if (fatPercent != null) {
                val std = fatStandard(male)
                if (fatPercent < std.min) s -= minOf(30.0, (std.min - fatPercent) * 3)
                if (fatPercent > std.max) s -= minOf(30.0, (fatPercent - std.max) * 2)
            }
            if (visceralFat != null && visceralFat > 9) s -= minOf(20.0, (visceralFat - 9) * 2)
            maxOf(20, Math.round(s).toInt())
        } else null
    }

    /**
     * 体型分类 3x3 矩阵
     * 行：脂肪等级(0=偏低 1=标准 2=偏高)
     * 列：BMI等级(0=偏瘦 1=标准 2=偏胖/肥胖)
     */
    private val SHAPES = arrayOf(
        arrayOf("消瘦型", "低脂肪型", "运动员型"),
        arrayOf("苗条肌肉型", "苗条型", "健康型"),
        arrayOf("隐形肥胖型", "偏胖型", "肥胖型")
    )

    fun classifyBodyShape(fatPercent: Double?, bmi: Double?, male: Boolean): String? {
        val fatBand = if (fatPercent == null) -1 else if (fatPercent < fatStandard(male).min) 0 else if (fatPercent > fatStandard(male).max) 2 else 1
        val bmiBand = if (bmi == null) -1 else if (bmi < 18.5) 0 else if (bmi < 24) 1 else 2
        return if (fatBand < 0 || bmiBand < 0) null else SHAPES[fatBand][bmiBand]
    }

    /**
     * 脂肪控制量：标准体重下的目标脂肪重量 - 当前脂肪重量
     */
    fun fatControl(stdWeight: Double?, weightKg: Double, fatWeight: Double?): Double? {
        return if (stdWeight != null && weightKg > 0 && fatWeight != null)
            stdWeight * (fatWeight / weightKg) - fatWeight
        else null
    }

    /**
     * 肌肉控制量：标准体重下的目标肌肉重量 - 当前肌肉重量
     * OKOK 协议用骨骼肌，通用估算用肌肉重量
     */
    fun muscleControl(stdWeight: Double?, weightKg: Double, muscleWeight: Double?, useSkeletal: Boolean): Double? {
        val controlMuscle = if (useSkeletal) muscleWeight else muscleWeight // 调用方决定传 skeletalWeight 还是 muscleWeight
        return if (stdWeight != null && weightKg > 0 && controlMuscle != null)
            stdWeight * (controlMuscle / weightKg) - controlMuscle
        else null
    }

    companion object {
        private fun minOf(a: Double, b: Double) = if (a < b) a else b
        private fun maxOf(a: Int, b: Int) = if (a > b) a else b
    }
}