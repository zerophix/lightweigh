package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.algorithm.Algorithms
import com.xcwk.scalehub.domain.algorithm.OkokBia
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository

/**
 * query — 体测报告读模型（旧库 BodyMetrics/Pages 报告规则移植，DEC-022）。
 *
 * 忠实口径（行号出处）：
 *  - BMI = 体重 / (身高m)^2，身高缺失为 null（BodyMetrics:65）；分级阈值
 *    <18.5 偏瘦 / <24 标准 / <28 偏胖 / <32 肥胖 / 否则重度肥胖（BodyMetrics:15-33）
 *  - 缺档项：身高缺失 → "身高"，年龄/出生缺失 → "出生年"（BodyMetrics:57-62）
 *  - 比上次：体重差与体脂率差（Pages:615-616,663）
 *  - 体成分指标网格：体重/体脂率/水分率/肌肉率/骨量/内脏脂肪（Pages:856）
 *
 * 自解释语义（R2 输入快照的兑现点）：分析类指标只用本记录快照的
 * male/age/height 计算——快照缺失的历史行诚实返回 null 指标而非借当前档案补算。
 * 快照齐备且算法为 okok-bia 且阻抗>0 时取 OkokBia.analyze 全套。
 */
data class ReportMetric(
    val name: String,
    /** null = 未计算/缺输入，UI 显示 "--"。 */
    val value: Double?,
    val unit: String,
)

data class MeasurementReport(
    val record: MeasureRecord,
    val bmi: Double?,
    val bmiLevel: Int?,          // 0..4（见 BMI_LABELS）
    val bmiLabel: String?,       // "偏瘦".."重度肥胖"
    val prevWeight: Double?,
    val prevFat: Double?,
    val metrics: List<ReportMetric>,
    /** 档案缺项（展示"完善「X」档案"提示，去完善跳转待导航栈接入）。 */
    val missingProfile: List<String>,
) {
    companion object {
        val BMI_LABELS = listOf("偏瘦", "标准", "偏胖", "肥胖", "重度肥胖")
    }
}

/** BMI 分级（BodyMetrics.bmiLevel 逐位移植）。 */
internal fun bmiLevelOf(bmi: Double): Int = when {
    bmi < 18.5 -> 0
    bmi < 24 -> 1
    bmi < 28 -> 2
    bmi < 32 -> 3
    else -> 4
}

class ReportQuery(private val repository: MeasurementRepository) {

    /** 按测量时间戳取报告；无此记录返回 null。 */
    suspend fun report(time: Long): MeasurementReport? {
        val record = repository.between(time, time + 1).firstOrNull() ?: return null
        val prev = repository.between(0L, time).lastOrNull()

        val height = record.heightCm
        val bmi = if (height != null && height > 0) record.weight / Math.pow(height / 100.0, 2.0) else null

        val missing = mutableListOf<String>()
        if (height == null || height <= 0) missing.add("身高")
        if (record.ageYears == null || record.ageYears <= 0) missing.add("出生年")

        val analysis = okokAnalysisOf(record)
        val metrics = listOf(
            ReportMetric("体重", record.weight, "公斤"),
            ReportMetric("体脂率", record.fat.takeIf { it >= 0 }, "%"),
            ReportMetric("水分率", analysis?.waterPercent, "%"),
            ReportMetric("肌肉率", analysis?.musclePercent, "%"),
            ReportMetric("骨量", analysis?.boneKg, "kg"),
            ReportMetric("内脏脂肪", analysis?.visceral, ""),
        )

        return MeasurementReport(
            record = record,
            bmi = bmi,
            bmiLevel = bmi?.let(::bmiLevelOf),
            bmiLabel = bmi?.let { MeasurementReport.BMI_LABELS[bmiLevelOf(it)] },
            prevWeight = prev?.weight,
            prevFat = prev?.fat?.takeIf { it >= 0 }?.let { pf ->
                record.fat.takeIf { f -> f >= 0 }?.let { pf }   // 双方有效才给 prev（差值端 UI 复核）
            },
            metrics = metrics,
            missingProfile = missing,
        )
    }

    /** 仅快照齐备 + okok 算法 + 有阻抗时给出全套体成分（自解释语义）。 */
    private fun okokAnalysisOf(record: MeasureRecord): OkokBia.Analysis? {
        val male = record.male ?: return null
        val age = record.ageYears ?: return null
        val height = record.heightCm ?: return null
        if (record.impedance <= 0 || height <= 0 || age <= 0) return null
        val algorithm = Algorithms.of(record.algorithm)
        if (algorithm !== OkokBia.INSTANCE) return null
        return OkokBia.analyze(male, age, height, record.weight, record.impedance)
    }
}
