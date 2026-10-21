package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.model.HealthLog
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * query — 健康记录读模型（首页卡片/加号后续消费）。
 * 类型中文名与单位随旧库 RecordScreen 字段口径（Pages.kt:466-530）：
 * 饮食=摄入热量千卡、运动=时长分钟(消耗千卡为副值)、喝水=ml、体围=腰围cm(臀围副值)、
 * 血糖=mmol/L、血压=收缩压/舒张压、习惯=名称本身；weight 不入健康记录（走测量路径）。
 */
data class HealthLogItem(
    val time: Long,
    val timeText: String,   // "MM-dd HH:mm"
    val typeLabel: String,  // "饮食"/"运动"...
    val valueText: String,  // "1500 千卡" / "30 分钟 · 320 千卡" / "120/80 mmHg" / 习惯名
)

class HealthLogQuery(
    private val port: HealthLogQueryPort,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun recent(limit: Int = 5): List<HealthLogItem> =
        port.recent(limit).map { it.toItem() }

    private fun HealthLog.toItem(): HealthLogItem = HealthLogItem(
        time = time,
        timeText = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(zone).format(Instant.ofEpochMilli(time)),
        typeLabel = LABEL[type] ?: type,
        valueText = valueTextOf(),
    )

    private fun HealthLog.valueTextOf(): String = when (type) {
        "diet" -> "${num(value)} 千卡"
        "exercise" -> "${num(value)} 分钟" + if (value2 > 0) " · ${num(value2)} 千卡" else ""
        "water" -> "${num(value)} ml"
        "waist" -> "${num(value)} cm" + if (value2 > 0) " · 臀围 ${num(value2)} cm" else ""
        "glucose" -> "${num(value)} mmol/L"
        "blood_pressure" -> "${num(value)}/${num(value2)} mmHg"
        "habit" -> label.ifBlank { "习惯" }
        else -> num(value)
    }

    private fun num(v: Double): String =
        if (v == Math.floor(v) && Math.abs(v) < 1e9) v.toLong().toString()
        else "%.1f".format(Locale.US, v)

    private companion object {
        val LABEL = mapOf(
            "diet" to "饮食", "exercise" to "运动", "water" to "喝水",
            "waist" to "体围", "glucose" to "血糖",
            "blood_pressure" to "血压", "habit" to "习惯",
        )
    }
}
