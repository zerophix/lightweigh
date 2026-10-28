package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.model.Member
import java.time.Instant
import java.time.ZoneOffset

import com.xcwk.scalehub.domain.algorithm.OkokBia
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.service.BodyCompositionService
import com.xcwk.scalehub.domain.service.BodyShapeClassificationService
import com.xcwk.scalehub.domain.service.HealthAdviceService
import com.xcwk.scalehub.domain.service.HealthRiskAssessmentService
import java.util.Locale

/**
 * 体成分综合分析查询 —— Application 层查询处理器。
 *
 * 负责：
 *  1. 接收 MeasureRecord（聚合根，含计算输入快照）
 *  2. 委托给 Domain 层服务进行业务计算
 *  3. 将领域模型结果转换为 UI 所需的数据模型（AnalysisMetric、BodyAnalysisResult 等）
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

/** BMI 五级标签。 */
val BMI_LABELS = listOf("偏瘦", "标准", "偏胖", "肥胖", "重度肥胖")

/** BMI 分级。 */
internal fun bmiLevelOf(bmi: Double): Int = when {
    bmi < 18.5 -> 0
    bmi < 24 -> 1
    bmi < 28 -> 2
    bmi < 32 -> 3
    else -> 4
}

internal data class BandRange(val min: Double, val max: Double)

/**
 * 体成分分析查询处理器。
 *
 * 从 Application 层接收 MeasureRecord，委托给 Domain 服务计算，
 * 并将结果转换为 UI 友好的数据结构。
 */
class BodyAnalysisQuery(
    private val bodyCompService: BodyCompositionService,
    private val riskService: HealthRiskAssessmentService,
    private val shapeService: BodyShapeClassificationService,
    private val adviceService: HealthAdviceService,
) {

    /**
     * 构建体成分分析结果。
     *
     * @param record 测量记录聚合根（含计算输入快照）
     * @param prevWeight 上一条记录体重（用于差值显示）
     * @param prevFat 上一条记录体脂率（用于差值显示）
     * @return 体成分分析结果
     */
    fun build(record: MeasureRecord, prevWeight: Double?, prevFat: Double?): BodyAnalysisResult {
        val male = record.male ?: false
        val age = record.ageYears?.takeIf { it in 0..127 }
        val heightCm = record.heightCm ?: 0
        val weight = record.weight
        val fatRate = record.fat?.takeIf { it >= 0 }
        val hasImpedance = record.impedance > 0
        val algorithm = record.algorithm

        val missing = mutableListOf<String>()
        if (heightCm <= 0) missing.add("身高")
        if (age == null) missing.add("出生年")

        val bmi = if (heightCm > 0) weight / Math.pow(heightCm / 100.0, 2.0) else null
        val level = bmi?.let(::bmiLevelOf)
        val fatStd = bodyCompService.fatStandard(male)

        val stdWeight = if (heightCm > 0) {
            if (male) (heightCm - 80) * 0.7 else (heightCm - 70) * 0.6
        } else null
        val obesity = if (stdWeight != null && stdWeight != 0.0) {
            ((weight - stdWeight) / stdWeight) * 100.0
        } else null

        // 委托给 Domain 服务计算体成分
        val analysis = if (hasImpedance && fatRate != null && record.hasCalculationInput()) {
            // 构造临时 MeasureRecord 用于域服务计算
            val tempRecord = MeasureRecord(
                record.time, weight, record.impedance, fatRate ?: MeasureRecord.FAT_NOT_CALCULATED,
                record.mac, algorithm, record.male, record.ageYears, record.heightCm
            )
            tempRecord.calculateBodyComposition(bodyCompService)
        } else null

        val fatPercent = analysis?.fatPercent ?: fatRate
        val muscleRate = analysis?.musclePercent
        val muscleWeight = analysis?.muscleKg
        val boneWeight = analysis?.boneKg
        val bmr = analysis?.bmr
        val proteinPercent = analysis?.proteinPercent
        val waterRate = analysis?.waterPercent
        val waterWeight = analysis?.waterKg
        val visceralFat = analysis?.visceralFat
        val bodyAge = analysis?.bodyAge

        // 骨骼肌计算
        val leanWeight = analysis?.let { it.fatPercent?.let { weight - weight * it / 100.0 } } ?: 
            (if (hasImpedance && fatRate != null) weight * (1 - fatRate!! / 100.0) else null)
        val skeletal = if (leanWeight != null && leanWeight > 0) {
            bodyCompService.calculateSkeletalMuscle(male, leanWeight, weight)
        } else null

        // 评分计算
        val score = if (hasImpedance && bmi != null && (fatPercent != null || analysis != null)) {
            var s = 100.0
            if (bmi < 18.5) s -= minOf(40.0, (18.5 - bmi) * 4)
            if (bmi > 24) s -= minOf(40.0, (bmi - 24) * 5)
            if (fatPercent != null) {
                if (fatPercent < fatStd.min) s -= minOf(30.0, (fatStd.min - fatPercent) * 3)
                if (fatPercent > fatStd.max) s -= minOf(30.0, (fatPercent - fatStd.max) * 2)
            }
            if (visceralFat != null && visceralFat > 9) s -= minOf(20.0, (visceralFat - 9) * 2)
            maxOf(20, Math.round(s).toInt())
        } else null

        // 上期数据
        val prevBmi = if (prevWeight != null && heightCm > 0)
            prevWeight / Math.pow(heightCm / 100.0, 2.0) else null
        val prevObesity = if (prevWeight != null && stdWeight != null && stdWeight != 0.0)
            ((prevWeight - stdWeight) / stdWeight) * 100.0 else null

        // 构建指标列表
        val metrics = mutableListOf<AnalysisMetric>()
        
        // BMI
        if (bmi != null && level != null) {
            val tone = if (level == 1) 1 else if (level == 0 || level == 2) 2 else 3
            metrics += AnalysisMetric("BMI", fmt(bmi, 1), "", BMI_LABELS[level], tone, signed(bmi, prevBmi, 1))
        } else {
            metrics += AnalysisMetric("BMI", "--", "", "未录入", 0)
        }

        // 肥胖度
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

        // 有阻抗时的体成分指标
        if (hasImpedance) {
            // 体脂率
            if (fatPercent != null) {
                metrics += AnalysisMetric("体脂率", fmt(fatPercent, 1), "%", fatLabel(fatPercent, fatStd), toneOf(fatPercent, BandRange(fatStd.min, fatStd.max)))
            } else {
                metrics += AnalysisMetric("体脂率", "--", "%", "未录入", 0)
            }
            // 脂肪重量
            val fatWeight = fatPercent?.let { weight * it / 100.0 }
            metrics += AnalysisMetric("脂肪重量", fmt(fatWeight, 1), "公斤",
                if (fatPercent != null) fatLabel(fatPercent, fatStd) else "未录入",
                if (fatPercent != null) toneOf(fatPercent, BandRange(fatStd.min, fatStd.max)) else 0)

            // 骨骼肌
            val skeletalRange = if (male) BandRange(28.0, 38.0) else BandRange(22.0, 32.0)
            val skeletalLabel = skeletal?.skeletalPercent?.let {
                if (it < skeletalRange.min) "不足" else "充足"
            } ?: "未录入"
            val skeletalTone = skeletal?.skeletalPercent?.let { if (it < skeletalRange.min) 2 else 1 } ?: 0
            metrics += AnalysisMetric("骨骼肌率", fmt(skeletal?.skeletalPercent, 1), "%", skeletalLabel, skeletalTone)
            metrics += AnalysisMetric("骨骼肌重量", fmt(skeletal?.skeletalWeight, 1), "公斤", skeletalLabel, skeletalTone)

            // 肌肉率
            val muscleRange = if (male) BandRange(32.0, 56.0) else BandRange(28.0, 52.0)
            val muscleLabel = muscleRate?.let { bandLabel(it, muscleRange, "不足", "偏高", "标准") } ?: "未录入"
            val muscleTone = muscleRate?.let { toneOf(it, muscleRange) } ?: 0
            metrics += AnalysisMetric("肌肉率", fmt(muscleRate, 1), "%", muscleLabel, muscleTone)
            val muscleMassLabel = if (muscleRate == null) "未录入" else if (muscleRate < muscleRange.min) "不足" else "充足"
            val muscleMassTone = if (muscleRate == null) 0 else if (muscleRate < muscleRange.min) 2 else 1
            metrics += AnalysisMetric("肌肉重量", fmt(muscleWeight, 1), "公斤", muscleMassLabel, muscleMassTone)

            // 骨量
            var boneLabel = "未录入"; var boneTone = 0
            if (boneWeight != null && weight > 0) {
                if (boneWeight / weight < 0.035) { boneLabel = "不足"; boneTone = 2 } else { boneLabel = "充足"; boneTone = 1 }
            }
            metrics += AnalysisMetric("骨量", fmt(boneWeight, 1), "公斤", boneLabel, boneTone)

            // 基础代谢
            val bmrStd = if (male) BandRange(1300.0, 1700.0) else BandRange(1100.0, 1500.0)
            metrics += AnalysisMetric("基础代谢", if (bmr == null) "--" else fmt(bmr, 0), "千卡",
                if (bmr == null) "未录入" else if (bmr < bmrStd.min) "未达标" else "达标",
                if (bmr == null) 0 else if (bmr < bmrStd.min) 2 else 1)
            metrics += AnalysisMetric("去脂体重", fmt(leanWeight, 1), "公斤", if (leanWeight != null) "参考" else "未录入", if (leanWeight != null) 1 else 0)

            // 蛋白质
            val proteinRange = BandRange(14.0, 20.0)
            metrics += AnalysisMetric("蛋白质", fmt(proteinPercent, 1), "%",
                proteinPercent?.let { bandLabel(it, proteinRange, "不足", "偏高", "标准") } ?: "未录入",
                proteinPercent?.let { toneOf(it, proteinRange) } ?: 0)
            // 水分
            val waterRange = if (male) BandRange(55.0, 65.0) else BandRange(50.0, 60.0)
            val waterLabel: String = if (waterRate != null) bandLabel(waterRate, waterRange, "不足", "偏高", "标准") else "未录入"
            val waterTone: Int? = if (waterRate != null) toneOf(waterRate, waterRange) else null
            val finalWaterTone = waterTone ?: 0
            metrics += AnalysisMetric("水分率", fmt(waterRate, 1), "%", waterLabel, finalWaterTone)
            metrics += AnalysisMetric("水含量", fmt(waterWeight, 1), "公斤", waterLabel, finalWaterTone)

            // 内脏脂肪
            var visceralLabel = "未录入"; var visceralTone = 0
            if (visceralFat != null) {
                when {
                    visceralFat <= 9 -> { visceralLabel = "标准"; visceralTone = 1 }
                    visceralFat <= 14 -> { visceralLabel = "偏高"; visceralTone = 2 }
                    else -> { visceralLabel = "超标"; visceralTone = 3 }
                }
            }
            metrics += AnalysisMetric("内脏脂肪", if (visceralFat == null) "--" else fmt(visceralFat, if (OkokBia.ID.equals(algorithm)) 1 else 0), "级", visceralLabel, visceralTone)
            metrics += AnalysisMetric("身体年龄", if (bodyAge == null) "--" else fmt(bodyAge.toDouble(), 1), "岁",
                if (bodyAge == null || age == null) "未录入" else if (bodyAge!! <= age!!) "年轻" else "偏大",
                if (bodyAge == null || age == null) 0 else if (bodyAge!! <= age!!) 1 else 2)
        }

        // 身高、年龄
        metrics += AnalysisMetric("身高", if (heightCm > 0) heightCm.toString() else "--", "cm",
            if (heightCm > 0) "已录入" else "未录入", if (heightCm > 0) 1 else 0)
        metrics += AnalysisMetric("实际年龄", age?.toString() ?: "--", "岁",
            if (age == null) "未录入" else "已录入", if (age == null) 0 else 1)

        // 优项/关注项分类
        val good = mutableListOf<String>(); val warn = mutableListOf<String>()
        for (metric in metrics) {
            if (metric.valueText == "--") continue
            val line = "${metric.name} ${metric.valueText}${metric.unit}（${metric.status}）\n${adviceOf(metric)}"
            when (metric.tone) {
                1 -> good += line
                2, 3 -> warn += line
            }
        }

        // 体型分类
        val shape = record.classifyBodyShape(shapeService, bodyCompService)?.shapeName

        // 脂肪/肌肉控制量
        val fatControl = if (stdWeight != null && weight > 0) {
            val fatWeight = fatPercent?.let { weight * it / 100.0 }
            if (fatWeight != null) stdWeight * (fatWeight / weight) - fatWeight else null
        } else null
        val muscleControl = if (stdWeight != null && weight > 0) {
            val controlMuscle = if (analysis != null) skeletal?.skeletalWeight else muscleWeight
            if (controlMuscle != null) stdWeight * (controlMuscle / weight) - controlMuscle else null
        } else null

        // 健康风险
        val risks = riskService.assessAll(
            hasImpedance, bmi, fatPercent, fatStd,
            analysis?.visceralFat, analysis?.boneKg, weight, age
        ).map { AnalysisRisk(it.name, it.levelText, it.tone) }

        return BodyAnalysisResult(
            prevWeight = prevWeight,
            prevFat = prevFat,
            bmi = bmi,
            bmiLevel = level,
            score = score,
            advice = adviceOfBmi(level, fatPercent, fatStd),
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

    // ===== 复用 BodyAnalysis.kt 的辅助函数 =====

    private fun fatLabel(value: Double, range: BodyCompositionService.FatStandard): String =
        bandLabel(value, BandRange(range.min, range.max), "偏低", "肥胖", "标准")

    private fun bandLabel(value: Double, range: BandRange, low: String, high: String, mid: String): String =
        when {
            value < range.min -> low
            value > range.max -> high
            else -> mid
        }

    private fun toneOf(value: Double, range: BandRange): Int =
        if (value < range.min || value > range.max) 2 else 1

    private fun signed(now: Double, previous: Double?, decimals: Int): String? {
        if (previous == null) return null
        return "%+.${decimals}f".format(Locale.US, now - previous)
    }

    private fun fmt(value: Double?, decimals: Int): String {
        if (value == null || value.isNaN()) return "--"
        return "%.${decimals}f".format(Locale.US, value)
    }

    private fun adviceOfBmi(level: Int?, fat: Double?, fatStd: BodyCompositionService.FatStandard): String = when {
        level == null -> "完善身高档案后，即可生成完整的体测分析与建议。"
        level == 0 -> "体重偏轻，注意均衡饮食与力量训练，适当增加优质蛋白摄入。"
        level == 1 && fat != null && fat <= fatStd.max -> "身体数据处于标准区间，继续保持当前饮食与运动习惯。"
        level == 2 -> "体重略微超标，每天早起步行 10 分钟，少吃零食油炸食品，七分饱即可。"
        level >= 3 -> "体重超标较多，建议控制油炸与甜食摄入，坚持有氧运动结合力量训练，循序渐进减重。"
        else -> "继续规律测量，保持健康生活习惯。"
    }

    private fun adviceOf(metric: AnalysisMetric): String = when (metric.tone) {
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

    private fun minOf(a: Double, b: Double) = if (a < b) a else b
    private fun maxOf(a: Int, b: Int) = if (a > b) a else b
}

// ===== 趋势查询 (T-136) =====

/**
 * 统一趋势图可绘制状态（AC-HT5）。
 *
 * - [Empty]：没有任何测量
 * - [InsufficientData]：仅 1 条测量 → 不画假趋势
 * - [Plottable]：≥2 条 → 可画
 */
enum class TrendState { Empty, InsufficientData, Plottable }

/**
 * 依据数据点数量判定趋势状态（AC-HT5）。
 * 纯函数，JVM 单测覆盖。
 */
fun trendStateOf(pointCount: Int): TrendState = when {
    pointCount <= 0 -> TrendState.Empty
    pointCount == 1 -> TrendState.InsufficientData
    else -> TrendState.Plottable
}

fun trendStateOf(records: List<MeasureRecord>): TrendState = trendStateOf(records.size)

/**
 * 可绘制的趋势点（UI 只负责渲染）。
 */
data class TrendPointUi(val x: Long, val value: Double)

/**
 * 依据「是否有记录」切分连续分段（AC-HT4）。
 */
fun segmentSeries(values: List<Double?>): List<List<Double>> {
    val segments = mutableListOf<MutableList<Double>>()
    var current = mutableListOf<Double>()
    for (v in values) {
        if (v == null) {
            if (current.isNotEmpty()) {
                segments += current
                current = mutableListOf<Double>()
            }
        } else {
            current += v
        }
    }
    if (current.isNotEmpty()) segments += current
    return segments
}

/** 周趋势结果。 */
data class WeeklyTrendResult(
    val trendState: TrendState,
    val segments: List<List<Double>>,
    val points: List<TrendPointUi>,
    val startDate: String,
    val endDate: String,
    val weightChangeText: String,
    val fatChangeText: String,
)

/** 月趋势结果。 */
data class MonthlyTrendResult(
    val trendState: TrendState,
    val segments: List<List<Double>>,
    val points: List<TrendPointUi>,
    val startDate: String,
    val endDate: String,
)

/** 全量趋势结果。 */
data class FullTrendResult(
    val trendState: TrendState,
    val segments: List<List<Double>>,
    val points: List<TrendPointUi>,
    val startDate: String,
    val endDate: String,
)

/**
 * 周趋势查询（原型 v-trend 页一周概览）。
 */
fun BodyAnalysisQuery.weeklyTrend(
    member: Member,
    records: List<MeasureRecord>,
): WeeklyTrendResult {
    val recent = records.takeLast(7)
    val trendState = trendStateOf(recent.size)
    val segments = segmentSeries(recent.map { it.weight.toDouble() })
    val points = recent.mapIndexed { i, r -> TrendPointUi(x = i.toLong(), value = r.weight.toDouble()) }

    val weightChange = if (recent.size >= 2) {
        val first = recent.first().weight.toDouble()
        val last = recent.last().weight.toDouble()
        val diff = last - first
        if (diff > 0) "+${String.format("%.1f", diff)}kg"
        else if (diff < 0) "${String.format("%.1f", diff)}kg"
        else "持平"
    } else "--"

    val fatChange = if (recent.size >= 2) {
        val first = recent.first().fat
        val last = recent.last().fat
        val firstValid = first != MeasureRecord.FAT_NOT_CALCULATED && first >= 0
        val lastValid = last != MeasureRecord.FAT_NOT_CALCULATED && last >= 0
        if (firstValid && lastValid) {
            val diff = last - first
            if (diff > 0) "+${String.format("%.1f", diff)}%"
            else if (diff < 0) "${String.format("%.1f", diff)}%"
            else "持平"
        } else "--"
    } else "--"

    val startDate = records.firstOrNull()?.time?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() } ?: ""
    val endDate = records.lastOrNull()?.time?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() } ?: ""

    return WeeklyTrendResult(
        trendState = trendState,
        segments = segments,
        points = points,
        startDate = startDate,
        endDate = endDate,
        weightChangeText = weightChange,
        fatChangeText = fatChange,
    )
}

/**
 * 月趋势查询（原型 v-trend 页四周概览）。
 */
fun BodyAnalysisQuery.monthlyTrend(
    member: Member,
    records: List<MeasureRecord>,
): MonthlyTrendResult {
    val recent = records.takeLast(28)
    val trendState = trendStateOf(recent.size)
    val segments = segmentSeries(recent.map { it.weight.toDouble() })
    val points = recent.mapIndexed { i, r -> TrendPointUi(x = i.toLong(), value = r.weight.toDouble()) }
    val startDate = records.firstOrNull()?.time?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() } ?: ""
    val endDate = records.lastOrNull()?.time?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() } ?: ""

    return MonthlyTrendResult(
        trendState = trendState,
        segments = segments,
        points = points,
        startDate = startDate,
        endDate = endDate,
    )
}

/**
 * 全量趋势查询（原型 v-trend 页全量概览）。
 */
fun BodyAnalysisQuery.fullTrend(
    member: Member,
    records: List<MeasureRecord>,
): FullTrendResult {
    val trendState = trendStateOf(records.size)
    val segments = segmentSeries(records.map { it.weight.toDouble() })
    val points = records.mapIndexed { i, r -> TrendPointUi(x = i.toLong(), value = r.weight.toDouble()) }
    val startDate = records.firstOrNull()?.time?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() } ?: ""
    val endDate = records.lastOrNull()?.time?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() } ?: ""

    return FullTrendResult(
        trendState = trendState,
        segments = segments,
        points = points,
        startDate = startDate,
        endDate = endDate,
    )
}
