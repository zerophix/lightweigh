package com.xcwk.scalehub.application.query

/**
 * application service — 报告导出与医生报告编排。
 *
 * 职责：
 *  - 基于 ReportQuery 生成的 MeasurementReport，导出为文本/ Markdown
 *  - 生成医院标准格式的体检报告片段
 *  - 管理报告生成状态
 *
 * 依赖 ReportQuery（已有）提供数据（含 BodyAnalysisResult）。
 */
class ReportService(
    private val reportQuery: ReportQuery,
) {

    /**
     * 导出单份测量报告为 Markdown。
     */
    suspend fun exportMarkdown(time: Long): ReportExportResult {
        val report = reportQuery.report(time) ?: return ReportExportResult(false, "", "未找到该次测量记录")
        val analysis = report.analysis

        val sb = StringBuilder()
        sb.append("# 体测报告\n\n")
        sb.append("**测量时间**：${formatTime(report.record.time)}**\n\n")
        sb.append("## 基础数据\n\n")
        sb.append("| 指标 | 数值 |\n|---|---|\n")
        sb.append("| 体重 | ${report.record.weight} kg |\n")
        sb.append("| BMI | ${analysis.bmi?.let { String.format("%.1f", it) } ?: "--"} |\n")

        if (report.record.hasImpedance()) {
            sb.append("| 体脂率 | ${report.record.fat.takeIf { it >= 0 }?.let { String.format("%.1f%%", it) } ?: "--"} |\n")
        }

        sb.append("\n## 综合评分\n\n")
        sb.append(analysis.score?.let { "$it 分" } ?: "暂无评分\n\n")

        sb.append("\n## 体型分类\n\n")
        sb.append(analysis.bodyShape ?: "未分类\n\n")

        sb.append("\n## 健康建议\n\n")
        sb.append(analysis.advice)

        return ReportExportResult(true, sb.toString(), "")
    }

    /**
     * 生成医生报告片段（用于医生版）。
     * 包含关键健康指标与风险评估。
     */
    suspend fun exportDoctorReport(time: Long): DoctorReportResult {
        val report = reportQuery.report(time) ?: return DoctorReportResult(false, "", "未找到该次测量记录")
        val analysis = report.analysis

        val sb = StringBuilder()
        sb.append("轻衡体检报告单\n")
        sb.append("时间：${formatTime(report.record.time)}\n")
        sb.append("患者ID：${report.record.time.hashCode() % 100000}\n\n")
        sb.append("== 关键指标 ==\n")
        sb.append("体重：${report.record.weight} kg\n")
        analysis.bmi?.let { sb.append("BMI：${String.format("%.1f", it)}\n") }
        if (report.record.hasImpedance()) {
            report.record.fat.takeIf { it >= 0 }?.let { sb.append("体脂率：${String.format("%.1f", it)}%\n") }
        }
        analysis.score?.let { sb.append("体质评分：$it\n") }
        sb.append("\n== 风险评估 ==\n")
        analysis.risks.forEach { risk ->
            sb.append("${risk.name}：${risk.levelText}\n")
        }
        sb.append("\n== 建议 ==\n")
        sb.append(analysis.advice)

        return DoctorReportResult(true, sb.toString(), "")
    }

    /**
     * 导出多份报告为批量格式。
     */
    suspend fun exportBatch(times: List<Long>): BatchExportResult {
        val contents = mutableListOf<String>()
        val failures = mutableListOf<String>()
        for (time in times) {
            val result = exportMarkdown(time)
            if (result.ok) {
                contents.add(result.content)
            } else {
                failures.add("time=$time: ${result.message}")
            }
        }
        return BatchExportResult(
            successCount = contents.size,
            totalCount = times.size,
            content = contents.joinToString("\n\n"),
            failures = failures,
        )
    }

    private fun formatTime(time: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(time))
    }
}

/** 导出结果。 */
data class ReportExportResult(
    val ok: Boolean,
    val content: String,
    val message: String,
)

/** 医生报告结果。 */
data class DoctorReportResult(
    val ok: Boolean,
    val content: String,
    val message: String,
)

/** 批量导出结果。 */
data class BatchExportResult(
    val successCount: Int,
    val totalCount: Int,
    val content: String,
    val failures: List<String>,
)
