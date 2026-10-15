package com.qingke.health.domain.model

/** 一条饮食、喝水或运动记录。 */
data class HealthLog(
    val time: Long,
    val type: String,
    val value: Double,
    val value2: Double,
    val label: String,
    val note: String
)
