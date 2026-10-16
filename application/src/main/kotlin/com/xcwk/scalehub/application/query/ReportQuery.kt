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
) {
    val missingProfile: List<String> get() = analysis.missing

    /** 免责两态（需求：体测仅供参考；无阻抗如实说明）。 */
    val disclaimer: String
        get() = if (record.impedance <= 0) {
            "这次测量没有阻抗，只保留体重和 BMI，不生成体成分分析"
        } else {
            "体重、体脂率为实测记录；其余指标基于公认公式估算，仅供参考，不构成医疗建议"
        }
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
        return MeasurementReport(record, analysis)
    }
}
