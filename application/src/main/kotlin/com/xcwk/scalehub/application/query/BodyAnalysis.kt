package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.algorithm.OkokBia
import java.util.Locale

/**
 * 体成分综合分析 —— 逐位移植旧库 BodyMetrics.build（qk-health-android
 * BodyMetrics.java:45-547，行号随注释），纯函数无 IO。
 *
 * v2 语义适配（仅两处，均已声明）：
 *  - 年龄来源 = 记录输入快照的 ageYears（旧库为当前档案 year-birthYear）——
 *    快照即计算输入，报告自解释（R2 口径）
 *  - 入参全部来自单条记录（male/age/height 为快照，可空），缺项走旧库各自的
 *    未录入/缺档分支，不借当前档案补算
 *
 * tone 约定（同旧库）：0=未录入 1=标准 2=注意 3=超标（UI 按此上色）。
 */

data class AnalysisMetric(
    val name: String,
    val valueText: String,   // 已格式化；"--" = 无数值
    val unit: String,
    val status: String,      // "标准"/"不足"/"未录入"...
    val tone: Int,
    val delta: String? = null,  // 较上次带符号值
)

data class AnalysisRisk(val name: String, val levelText: String, val tone: Int)

data class BodyAnalysisResult(
    val prevWeight: Double?,
    val prevFat: Double?,
    val bmi: Double?,
    val bmiLevel: Int?,
    val score: Int?,
    val advice: String,
    val metrics: List<AnalysisMetric>,
    val goodLines: List<String>,
    val warnLines: List<String>,
    val missing: List<String>,
    val bodyShape: String?,
    val fatControl: Double?,
    val muscleControl: Double?,
    val standardWeight: Double?,
    val risks: List<AnalysisRisk>,
)

/** BMI 五级标签（BodyMetrics:15）。 */
val BMI_LABELS = listOf("偏瘦", "标准", "偏胖", "肥胖", "重度肥胖")

/** BMI 分级（BodyMetrics:20-33）。 */
internal fun bmiLevelOf(bmi: Double): Int = when {
    bmi < 18.5 -> 0
    bmi < 24 -> 1
    bmi < 28 -> 2
    bmi < 32 -> 3
    else -> 4
}

internal data class BandRange(val min: Double, val max: Double)

internal fun buildBodyAnalysis(
    male: Boolean,
    ageYearsIn: Int?,
    heightCmIn: Int?,
    weight: Double,
    fatRateIn: Double?,
    prevWeightIn: Double?,
    prevFatIn: Double?,
    impedanceOhm: Double,
    algorithm: String,
): BodyAnalysisResult {
    val age = ageYearsIn?.takeIf { it in 0..127 }          // :48-52
    val fat0 = fatRateIn?.takeIf { it >= 0 }               // :53
    val previousWeight = prevWeightIn?.takeIf { it > 0 }   // :54
    val previousFat = prevFatIn?.takeIf { it >= 0 }        // :55

    val missing = mutableListOf<String>()                  // :57-63
    val heightCm = heightCmIn ?: 0
    if (heightCm <= 0) missing.add("身高")
    if (age == null) missing.add("出生年")

    val bmi = if (heightCm > 0) weight / Math.pow(heightCm / 100.0, 2.0) else null   // :65
    val level = bmi?.let(::bmiLevelOf)                                          // :66
    val fatStd = if (male) BandRange(10.0, 20.0) else BandRange(18.0, 28.0)     // :67

    val stdWeight = if (heightCm > 0) {                                          // :69-72
        if (male) (heightCm - 80) * 0.7 else (heightCm - 70) * 0.6
    } else null
    val obesity = if (stdWeight != null && stdWeight != 0.0) {
        ((weight - stdWeight) / stdWeight) * 100.0
    } else null                                                                  // :73-74

    val hasImpedance = impedanceOhm > 0                                          // :76
    var fatWeight: Double? = null
    var lean: Double? = null
    var muscleRate: Double? = null
    var muscleWeight: Double? = null
    var skeletal: Double? = null
    var skeletalWeight: Double? = null
    var bone: Double? = null
    var bmr: Double? = null
    var protein: Double? = null
    var waterRate: Double? = null
    var waterWeight: Double? = null
    var visceral: Double? = null
    var bodyAge: Int? = null
    var okok = false

    var fat = fat0
    if (hasImpedance && fat != null && !OkokBia.ID.equals(algorithm)) {           // :91-110 通用估算路径
        fatWeight = weight * fat / 100.0
        lean = weight - fatWeight
        val muscleFactor = if (male) 0.78 else 0.75
        muscleRate = (lean / weight) * 100.0 * muscleFactor
        muscleWeight = weight * muscleRate / 100.0
        bone = weight * 0.055
        if (age != null && heightCm > 0) {
            bmr = if (male) 10 * weight + 6.25 * heightCm - 5 * age + 5.0
            else 10 * weight + 6.25 * heightCm - 5 * age - 161.0
        }
        protein = (lean * 0.19 / weight) * 100.0
        waterRate = (lean * 0.73 / weight) * 100.0
        waterWeight = weight * waterRate / 100.0
        visceral = fat * 0.35
        if (age != null && bmi != null) {
            bodyAge = Math.round(age + (bmi - 22) * 0.8 + (fat - (fatStd.min + fatStd.max) / 2.0) * 0.3).toInt()
        }
    }
    if (hasImpedance && OkokBia.ID.equals(algorithm) && age != null) {           // :111-129 okok 路径
        val bia = OkokBia.analyze(male, age, heightCm, weight, impedanceOhm)
        if (bia != null) {
            okok = true
            if (fat != null) {
                fatWeight = weight * fat / 100.0
                lean = weight - fatWeight
            }
            muscleRate = bia.musclePercent
            muscleWeight = bia.muscleKg
            bone = bia.boneKg
            bmr = bia.bmr
            protein = bia.proteinPercent
            waterRate = bia.waterPercent
            waterWeight = bia.waterKg
            visceral = bia.visceral
            bodyAge = bia.bodyAge
        }
    }
    if (lean != null && lean > 0) {                                              // :130-139 骨骼肌
        skeletalWeight = if (male) 0.59765708 * lean - 1.91104591
        else 0.59740084 * lean - 2.18052530
        if (skeletalWeight <= 0.0) skeletalWeight = lean * 0.598
        skeletal = skeletalWeight / weight * 100.0
    }

    val score = if (hasImpedance && bmi != null && (fat != null || okok)) {       // :141-162
        var s = 100.0
        if (bmi < 18.5) s -= minOf(40.0, (18.5 - bmi) * 4)
        if (bmi > 24) s -= minOf(40.0, (bmi - 24) * 5)
        if (fat != null) {
            if (fat < fatStd.min) s -= minOf(30.0, (fatStd.min - fat) * 3)
            if (fat > fatStd.max) s -= minOf(30.0, (fat - fatStd.max) * 2)
        }
        if (visceral != null && visceral > 9) s -= minOf(20.0, (visceral - 9) * 2)
        maxOf(20, Math.round(s).toInt())
    } else null

    if (!hasImpedance) fat = null                                                 // :164-166（评分后清空）

    val prevBmi = if (previousWeight != null && heightCm > 0)                     // :168-175
        previousWeight / Math.pow(heightCm / 100.0, 2.0) else null
    val prevObesity = if (previousWeight != null && stdWeight != null && stdWeight != 0.0)
        ((previousWeight - stdWeight) / stdWeight) * 100.0 else null

    val metrics = mutableListOf<AnalysisMetric>()                                 // :177-269 指标全表
    if (bmi != null && level != null) {
        val tone = if (level == 1) 1 else if (level == 0 || level == 2) 2 else 3
        metrics += AnalysisMetric("BMI", fmt(bmi, 1), "", BMI_LABELS[level], tone, signed(bmi, prevBmi, 1))
    } else {
        metrics += AnalysisMetric("BMI", "--", "", "未录入", 0)
    }
    if (obesity != null) {
        val tone = if (Math.abs(obesity) <= 10) 1 else if (obesity > 0 && obesity <= 20) 2 else 3
        val label = when {
            Math.abs(obesity) <= 10 -> "标准"
            obesity > 20 -> "肥胖"
            obesity > 10 -> "超重"
            else -> "偏低"
        }
        metrics += AnalysisMetric("肥胖度", fmt(obesity, 1), "", label, tone, signed(obesity, prevObesity, 1))
    } else {
        metrics += AnalysisMetric("肥胖度", "--", "", "未录入", 0)
    }
    if (hasImpedance) {
        if (fat != null) {
            metrics += AnalysisMetric("体脂率", fmt(fat, 1), "%", fatLabel(fat, fatStd), toneOf(fat, fatStd))
        } else {
            metrics += AnalysisMetric("体脂率", "--", "%", "未录入", 0)
        }
        metrics += AnalysisMetric("脂肪重量", fmt(fatWeight, 1), "公斤",
            if (fat != null) fatLabel(fat, fatStd) else "未录入", if (fat != null) toneOf(fat, fatStd) else 0)

        val skeletalRange = if (male) BandRange(28.0, 38.0) else BandRange(22.0, 32.0)   // :200-204
        val skeletalLabel = when {
            skeletal == null -> "未录入"
            skeletal < skeletalRange.min -> "不足"
            else -> "充足"
        }
        val skeletalTone = if (skeletal == null) 0 else if (skeletal < skeletalRange.min) 2 else 1
        metrics += AnalysisMetric("骨骼肌率", fmt(skeletal, 1), "%", skeletalLabel, skeletalTone)
        metrics += AnalysisMetric("骨骼肌重量", fmt(skeletalWeight, 1), "公斤", skeletalLabel, skeletalTone)

        val muscleRange = if (male) BandRange(32.0, 56.0) else BandRange(28.0, 52.0)     // :206-216
        val muscleLabel = muscleRate?.let { bandLabel(it, muscleRange, "不足", "偏高", "标准") } ?: "未录入"
        val muscleTone = muscleRate?.let { toneOf(it, muscleRange) } ?: 0
        metrics += AnalysisMetric("肌肉率", fmt(muscleRate, 1), "%", muscleLabel, muscleTone)
        val muscleMassLabel = if (muscleRate == null) "未录入" else if (muscleRate < muscleRange.min) "不足" else "充足"
        val muscleMassTone = if (muscleRate == null) 0 else if (muscleRate < muscleRange.min) 2 else 1
        metrics += AnalysisMetric("肌肉重量", fmt(muscleWeight, 1), "公斤", muscleMassLabel, muscleMassTone)

        var boneLabel = "未录入"; var boneTone = 0                                    // :218-230
        if (bone != null && weight > 0) {
            if (bone / weight < 0.035) { boneLabel = "不足"; boneTone = 2 } else { boneLabel = "充足"; boneTone = 1 }
        }
        metrics += AnalysisMetric("骨量", fmt(bone, 1), "公斤", boneLabel, boneTone)

        val bmrStd = if (male) BandRange(1300.0, 1700.0) else BandRange(1100.0, 1500.0)  // :232-235
        metrics += AnalysisMetric("基础代谢", if (bmr == null) "--" else fmt(bmr, 0), "千卡",
            if (bmr == null) "未录入" else if (bmr < bmrStd.min) "未达标" else "达标",
            if (bmr == null) 0 else if (bmr < bmrStd.min) 2 else 1)
        metrics += AnalysisMetric("去脂体重", fmt(lean, 1), "公斤", if (lean != null) "参考" else "未录入", if (lean != null) 1 else 0)  // :236

        val proteinRange = BandRange(14.0, 20.0)                                       // :238-241
        metrics += AnalysisMetric("蛋白质", fmt(protein, 1), "%",
            protein?.let { bandLabel(it, proteinRange, "不足", "偏高", "标准") } ?: "未录入",
            protein?.let { toneOf(it, proteinRange) } ?: 0)
        val waterRange = if (male) BandRange(55.0, 65.0) else BandRange(50.0, 60.0)    // :242-246
        val waterLabel = waterRate?.let { bandLabel(it, waterRange, "不足", "偏高", "标准") } ?: "未录入"
        val waterTone = waterRate?.let { toneOf(it, waterRange) } ?: 0
        metrics += AnalysisMetric("水分率", fmt(waterRate, 1), "%", waterLabel, waterTone)
        metrics += AnalysisMetric("水含量", fmt(waterWeight, 1), "公斤", waterLabel, waterTone)

        var visceralLabel = "未录入"; var visceralTone = 0                              // :247-261
        if (visceral != null) {
            when {
                visceral <= 9 -> { visceralLabel = "标准"; visceralTone = 1 }
                visceral <= 14 -> { visceralLabel = "偏高"; visceralTone = 2 }
                else -> { visceralLabel = "超标"; visceralTone = 3 }
            }
        }
        metrics += AnalysisMetric("内脏脂肪", if (visceral == null) "--" else fmt(visceral, if (okok) 1 else 0), "级", visceralLabel, visceralTone)
        metrics += AnalysisMetric("身体年龄", if (bodyAge == null) "--" else fmt(bodyAge.toDouble(), 1), "岁",
            if (bodyAge == null || age == null) "未录入" else if (bodyAge <= age) "年轻" else "偏大",
            if (bodyAge == null || age == null) 0 else if (bodyAge <= age) 1 else 2)
    }
    metrics += AnalysisMetric("身高", if (heightCm > 0) heightCm.toString() else "--", "cm",
        if (heightCm > 0) "已录入" else "未录入", if (heightCm > 0) 1 else 0)
    metrics += AnalysisMetric("实际年龄", age?.toString() ?: "--", "岁",
        if (age == null) "未录入" else "已录入", if (age == null) 0 else 1)

    val good = mutableListOf<String>(); val warn = mutableListOf<String>()           // :271-283
    for (metric in metrics) {
        if (metric.valueText == "--") continue
        val line = "${metric.name} ${metric.valueText}${metric.unit}（${metric.status}）\n${adviceOf(metric)}"
        when (metric.tone) {
            1 -> good += line
            2, 3 -> warn += line
        }
    }

    val fatBand = if (fat == null) -1 else if (fat < fatStd.min) 0 else if (fat > fatStd.max) 2 else 1   // :285-287
    val bmiBand = if (bmi == null) -1 else if (bmi < 18.5) 0 else if (bmi < 24) 1 else 2
    val shape = if (fatBand < 0 || bmiBand < 0) null else SHAPES[fatBand][bmiBand]

    val fatControl = if (stdWeight != null && weight > 0 && fatWeight != null)        // :288-296
        stdWeight * (fatWeight / weight) - fatWeight else null
    val controlMuscle = if (okok) skeletalWeight else muscleWeight
    val muscleControl = if (stdWeight != null && weight > 0 && controlMuscle != null)
        stdWeight * (controlMuscle / weight) - controlMuscle else null

    val risks = risksOf(hasImpedance, bmi, fat, fatStd, visceral, bone, weight, age)  // :297

    return BodyAnalysisResult(
        prevWeight = previousWeight,
        prevFat = previousFat,
        bmi = bmi,
        bmiLevel = level,
        score = score,
        advice = adviceOfBmi(level, fat, fatStd),                                     // :300
        metrics = metrics,
        goodLines = good,
        warnLines = warn,
        missing = missing,
        bodyShape = shape,
        fatControl = fatControl,
        muscleControl = muscleControl,
        standardWeight = stdWeight,
        risks = risks,
    )
}

private val SHAPES = arrayOf(                                                       // :304-308
    arrayOf("消瘦型", "低脂肪型", "运动员型"),
    arrayOf("苗条肌肉型", "苗条型", "健康型"),
    arrayOf("隐形肥胖型", "偏胖型", "肥胖型"),
)

private fun risksOf(                                                                // :310-344
    hasImpedance: Boolean, bmi: Double?, fat: Double?, fatStd: BandRange,
    visceral: Double?, bone: Double?, weight: Double, age: Int?,
): List<AnalysisRisk> {
    val risks = mutableListOf<AnalysisRisk>()
    if (bmi == null) return risks
    val obesityTone = if (bmi >= 28) 3 else if (bmi >= 24) 2 else 1
    risks += AnalysisRisk("肥胖症", riskText(obesityTone), obesityTone)
    var liver = 1
    if ((visceral != null && visceral > 14) || (fat != null && fat > fatStd.max + 8)) liver = 3
    else if ((visceral != null && visceral > 9) || (fat != null && fat > fatStd.max)) liver = 2
    if (hasImpedance) risks += AnalysisRisk("脂肪肝", riskText(liver), liver)
    val pressure = if (bmi >= 28 && age != null && age >= 40) 3 else if (bmi >= 24) 2 else 1
    risks += AnalysisRisk("高血压", riskText(pressure), pressure)
    val sugar = if (bmi >= 28 && fat != null && fat > fatStd.max) 3 else if (bmi >= 24) 2 else 1
    risks += AnalysisRisk("糖尿病", riskText(sugar), sugar)
    if (bone != null && weight > 0) {
        val ratio = bone / weight
        val tone = if (ratio < 0.035) 3 else if (ratio < 0.045) 2 else 1
        risks += AnalysisRisk("骨质疏松", riskText(tone), tone)
    }
    var lipid = 1
    if ((fat != null && fat > fatStd.max + 8) || (visceral != null && visceral > 14)) lipid = 3
    else if (fat != null && fat > fatStd.max) lipid = 2
    if (hasImpedance) risks += AnalysisRisk("高血脂", riskText(lipid), lipid)
    val gout = if (bmi >= 32) 3 else if (bmi >= 28) 2 else 1
    risks += AnalysisRisk("痛风", riskText(gout), gout)
    return risks
}

private fun riskText(tone: Int) = when (tone) { 3 -> "高风险"; 2 -> "中风险"; else -> "低风险" }  // :347-350

private fun signed(now: Double, previous: Double?, decimals: Int): String? {       // :352-357
    if (previous == null) return null
    return "%+.${decimals}f".format(Locale.US, now - previous)
}

private fun fatLabel(value: Double, range: BandRange): String =                     // :359-361
    bandLabel(value, range, "偏低", "肥胖", "标准")

private fun bandLabel(value: Double, range: BandRange, low: String, high: String, mid: String): String =
    when {                                                                          // :363-371
        value < range.min -> low
        value > range.max -> high
        else -> mid
    }

internal fun adviceOfBmi(level: Int?, fat: Double?, fatStd: BandRange): String =    // :373-390
    when {
        level == null -> "完善身高档案后，即可生成完整的体测分析与建议。"
        level == 0 -> "体重偏轻，注意均衡饮食与力量训练，适当增加优质蛋白摄入。"
        level == 1 && fat != null && fat <= fatStd.max -> "身体数据处于标准区间，继续保持当前饮食与运动习惯。"
        level == 2 -> "体重略微超标，每天早起步行 10 分钟，少吃零食油炸食品，七分饱即可。"
        level >= 3 -> "体重超标较多，建议控制油炸与甜食摄入，坚持有氧运动结合力量训练，循序渐进减重。"
        else -> "继续规律测量，保持健康生活习惯。"
    }

private fun adviceOf(metric: AnalysisMetric): String = when (metric.tone) {        // :392-425
    1 -> "继续保持当前生活习惯。"
    else -> when (metric.name) {
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
        else -> "建议结合饮食和运动逐步调整。"
    }
}

private fun toneOf(value: Double, range: BandRange): Int =                          // :427-429
    if (value < range.min || value > range.max) 2 else 1

private fun fmt(value: Double?, decimals: Int): String {                            // :441-446
    if (value == null || value.isNaN()) return "--"
    return "%.${decimals}f".format(Locale.US, value)
}
