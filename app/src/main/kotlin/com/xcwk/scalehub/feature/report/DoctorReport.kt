package com.xcwk.scalehub.feature.report

import java.util.Locale

/**
 * 医生报告（T-061）。
 *
 * 边界（合规优先）：**只做数据汇总，不做诊断，不给医疗建议**。
 * 报告里的每一行都必须能由输入数据直接推出；任何"建议就医/疑似/风险"类
 * 表述都不出现在生成结果里。
 */
data class DoctorMeasurement(
    val dateText: String,
    val weightKg: Double,
    val fatPercent: Double? = null,
)

data class DoctorReportInput(
    val memberName: String = "",
    val age: Int? = null,
    val heightCm: Double? = null,
    val bmi: Double? = null,
    /** 统计窗口天数（如 30）。 */
    val periodDays: Int = 30,
    /** 窗口内体重变化（末 - 首），无数据为 null。 */
    val weightChangeKg: Double? = null,
    /** 最近若干次测量，按时间升序。 */
    val measurements: List<DoctorMeasurement> = emptyList(),
)

/** 生成纯文本报告；没有数据时只输出表头与"暂无数据"，不补占位数值。 */
fun buildDoctorReport(input: DoctorReportInput): String {
    val sb = StringBuilder()
    sb.appendLine("体重与体成分数据汇总")
    sb.appendLine("（仅供就诊时参考，不含诊断与治疗建议）")
    sb.appendLine()

    sb.appendLine("一、基本信息")
    sb.appendLine("姓名：${input.memberName.ifBlank { "未填写" }}")
    sb.appendLine("年龄：${input.age?.let { "$it 岁" } ?: "未填写"}")
    sb.appendLine("身高：${input.heightCm?.let { one(it) + " cm" } ?: "未填写"}")
    sb.appendLine("BMI：${input.bmi?.let { one(it) } ?: "未记录"}")
    sb.appendLine()

    sb.appendLine("二、最近测量")
    if (input.measurements.isEmpty()) {
        sb.appendLine("暂无测量记录")
    } else {
        input.measurements.takeLast(10).forEach { m ->
            val fat = m.fatPercent?.let { "，体脂 ${one(it)}%" } ?: ""
            sb.appendLine("${m.dateText}：体重 ${one(m.weightKg)} kg$fat")
        }
    }
    sb.appendLine()

    sb.appendLine("三、近 ${input.periodDays} 天变化")
    val change = input.weightChangeKg
    sb.appendLine(
        when {
            change == null -> "数据不足，无法计算变化"
            change > 0.05 -> "体重增加 ${one(change)} kg"
            change < -0.05 -> "体重下降 ${one(-change)} kg"
            else -> "体重基本持平"
        },
    )
    sb.appendLine()
    sb.appendLine("四、说明")
    sb.appendLine("以上数据由家用体脂秤记录，仅作趋势参考；测量结果受饮水、进食、时间等因素影响。")
    return sb.toString().trimEnd()
}

private fun one(v: Double): String = String.format(Locale.CHINA, "%.1f", v)
