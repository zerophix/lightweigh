package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository

/**
 * query — 体测报告读模型。规则本体在 BodyAnalysis（旧库 BodyMetrics 全套移植），
 * 本类只负责取数与组装：
 *  - 目标记录 + 升序前一条（差值参照）
 *  - 全部输入取自记录快照（male/age/height 可空 → 走旧库各自未录入分支，不借当前档案）
 *  - 手动体重（无阻抗）按旧库口径只保留体重与 BMI（analysis 内部处理）
 */
data class MeasurementReport(
    val record: MeasureRecord,
    val analysis: BodyAnalysisResult,
    /** 上一条同口径记录的评分（对 prev 复跑 analysis 取得；无 prev 或无评分为 null）。 */
    val prevScore: Int? = null,
) {
    val missingProfile: List<String> get() = analysis.missing

    /** 免责两态（需求：体测仅供参考；无阻抗如实说明）。 */
    val disclaimer: String
        get() = if (record.impedance <= 0) {
            "这次测量没有阻抗，只保留体重和 BMI，不生成体成分分析"
        } else {
            "体重、体脂率为实测记录；其余指标基于公认公式估算，仅供参考，不构成医疗建议"
        }

    /** 评分较上次变化（原型评分卡 pill）：两端都有评分才给，否则 null。 */
    val scoreDelta: Int?
        get() = if (analysis.score != null && prevScore != null) analysis.score - prevScore else null
}

class ReportQuery(private val repository: MeasurementRepository) {

    /** 按测量时间戳取报告；无此记录返回 null。 */
    suspend fun report(time: Long): MeasurementReport? {
        val record = repository.between(time, time + 1).firstOrNull() ?: return null
        val prev = repository.between(0L, time).lastOrNull()
        val analysis = buildBodyAnalysis(
            male = record.male ?: false,
            ageYearsIn = record.ageYears,
            heightCmIn = record.heightCm,
            weight = record.weight,
            fatRateIn = record.fat,
            prevWeightIn = prev?.weight,
            prevFatIn = prev?.fat,
            impedanceOhm = record.impedance,
            algorithm = record.algorithm,
        )
        // 上期评分：对 prev 复跑同一规则（评分只依赖记录自身 bmi/体脂/内脏脂肪，
        // 不吃 prevWeight/prevFat，故差值入参传 null 不影响得分）；规则不旁落到 UI。
        val prevScore = prev?.let { p ->
            buildBodyAnalysis(
                male = p.male ?: false,
                ageYearsIn = p.ageYears,
                heightCmIn = p.heightCm,
                weight = p.weight,
                fatRateIn = p.fat,
                prevWeightIn = null,
                prevFatIn = null,
                impedanceOhm = p.impedance,
                algorithm = p.algorithm,
            ).score
        }
        return MeasurementReport(record, analysis, prevScore)
    }
}
