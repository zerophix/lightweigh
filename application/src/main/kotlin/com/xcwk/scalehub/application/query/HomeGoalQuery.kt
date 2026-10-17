package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.repository.GoalRepository
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import java.time.ZoneId
import java.util.Locale

/** 首页目标进度卡的可渲染状态（旧库 HomeScreen 计算口径 :102-131）。 */
data class HomeGoalState(
    val latestWeight: Double? = null,
    val latestFatShown: Boolean = false,
    val latestFat: Double = -1.0,
    val diffText: String? = null,
    val latestTime: Long = 0L,
    val bmi: Double? = null,
    val bmiSegment: String? = null,
    /** 早/午/晚千卡 + 运动消耗。 */
    val dietByMeal: Map<String, Int> = emptyMap(),
    val exerciseKcal: Int = 0,
    val calorieRemaining: Int = 0,
    val calorieGoal: Int = 2049,
    val waterTodayMl: Int = 0,
    val waterPercent: Int = 0,
    val waterGoal: Int = 2000,
    val habitCount: Int = 0,
    val waistCm: Double? = null,
    val ropeToday: Int = 0,
    /** 近 7 天体重变化（首末差）；点数不足为 null。 */
    val weekChange: Double? = null,
    /** 近 7 天体重序列（升序，迷你图用）。 */
    val weekWeights: List<Double> = emptyList(),
    /** 今日运动分项千卡（健走/跑步/骑行/跳绳）。 */
    val sportToday: Map<String, Int> = emptyMap(),
    val targetWeight: Float = 0f,
)

/**
 * application query — 首页目标进度（旧库 HomeScreen 计算段移植，行号随注释）。
 * 今日口径按注入时区取自然日；BMI 四段映射沿旧库（重度肥胖并入肥胖段）；
 * 近7天变化 = 区间内首末体重差（旧库 weekChange 口径）。
 */
class HomeGoalQuery(
    private val measurements: MeasurementRepository,
    private val healthLogs: HealthLogRepository,
    private val goals: GoalRepository,
    private val profile: MemberProfileReader,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun state(): HomeGoalState {
        val now = System.currentTimeMillis()
        val todayStart = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEnd = todayStart + 24 * 3600 * 1000L

        val weights = measurements.between(todayStart - 30L * 24 * 3600 * 1000L, now + 1)
        val latest = weights.lastOrNull()
        val prev = weights.getOrNull(weights.size - 2)

        val weekRows = measurements.between(now - 7L * 24 * 3600 * 1000L, now + 1)
        val weekChange = if (weekRows.size >= 2) weekRows.last().weight - weekRows.first().weight else null

        val height = profile.heightCm()
        val bmi = latest?.let { l ->
            if (height > 0) l.weight / ((height / 100.0) * (height / 100.0)) else null
        }

        val today = healthLogs.between(todayStart, todayEnd)
        val diet = today.filter { it.type == "diet" }
        val exercise = today.filter { it.type == "exercise" }
        val waterTotal = today.filter { it.type == "water" }.sumOf { it.value }.toInt()
        val habitCount = today.count { it.type == "habit" }
        val waist = today.filter { it.type == "waist" }.maxByOrNull { it.time }
        val rope = exercise.filter { it.label == "跳绳" }.sumOf { it.value }.toInt()

        val dietByMeal = listOf("早餐", "午餐", "晚餐").associateWith { meal ->
            diet.filter { it.label == meal }.sumOf { it.value }.toInt()
        }
        val exerciseKcal = exercise.sumOf { it.value2 }.toInt()
        val calorieGoal = goals.calorieGoal
        val waterGoal = goals.waterGoal

        return HomeGoalState(
            latestWeight = latest?.weight,
            latestFatShown = latest != null && latest.fat >= 0 && latest.impedance > 0,
            latestFat = latest?.fat ?: -1.0,
            diffText = if (latest != null && prev != null) {
                String.format(Locale.US, "%+.1f", latest.weight - prev.weight)
            } else {
                null
            },
            latestTime = latest?.time ?: 0L,
            bmi = bmi,
            bmiSegment = bmi?.let { segmentOf(it) },
            dietByMeal = dietByMeal,
            exerciseKcal = exerciseKcal,
            calorieRemaining = calorieGoal - diet.sumOf { it.value }.toInt() + exerciseKcal,
            calorieGoal = calorieGoal,
            waterTodayMl = waterTotal,
            waterPercent = (waterTotal * 100 / waterGoal.coerceAtLeast(1)).coerceIn(0, 100),
            waterGoal = waterGoal,
            habitCount = habitCount,
            waistCm = waist?.value,
            ropeToday = rope,
            weekChange = weekChange,
            weekWeights = weekRows.map { it.weight },
            sportToday = listOf("健走", "跑步", "骑行", "跳绳").associateWith { sport ->
                exercise.filter { it.label == sport }.sumOf { it.value }.toInt()
            },
            targetWeight = goals.targetWeight,
        )
    }

    /** BMI 四段（旧库 bmiSegments；重度肥胖并入肥胖段）。 */
    private fun segmentOf(bmi: Double): String = when {
        bmi < 18.5 -> "偏瘦"
        bmi < 24.0 -> "标准"
        bmi < 28.0 -> "偏胖"
        else -> "肥胖"
    }
}
