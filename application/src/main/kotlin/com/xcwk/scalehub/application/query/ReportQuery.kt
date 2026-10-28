package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.domain.service.BodyCompositionService
import com.xcwk.scalehub.domain.service.BodyShapeClassificationService
import com.xcwk.scalehub.domain.service.HealthAdviceService
import com.xcwk.scalehub.domain.service.HealthRiskAssessmentService

/**
 * query — 体测报告读模型。规则本体在 BodyAnalysisQuery（委托 Domain 服务），
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

class ReportQuery(
    private val query: MeasurementQueryPort,
    private val bodyCompService: BodyCompositionService,
    private val riskService: HealthRiskAssessmentService,
    private val shapeService: BodyShapeClassificationService,
    private val adviceService: HealthAdviceService,
) {

    private val bodyAnalysisQuery = BodyAnalysisQuery(
        bodyCompService, riskService, shapeService, adviceService
    )

    /** 按测量时间戳取报告；无此记录返回 null。 */
    suspend fun report(time: Long): MeasurementReport? {
        val record = query.between(time, time + 1).firstOrNull() ?: return null
        val prev = query.between(0L, time).lastOrNull()
        val analysis = bodyAnalysisQuery.build(record, prev?.weight, prev?.fat)
        
        // 上期评分：对 prev 复跑同一规则
        val prevScore = prev?.let { p ->
            val prevRecord = MeasureRecord(
                p.time, p.weight, p.impedance, p.fat,
                p.mac, p.algorithm, p.male, p.ageYears, p.heightCm
            )
            bodyAnalysisQuery.build(prevRecord, null, null).score
        }
        return MeasurementReport(record, analysis, prevScore)
    }
}