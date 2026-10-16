package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * query — 趋势查询。业务规则全部从旧库 qk-health-android TrendPages 抢救下沉
 * （DEC-022 组 B），每条标注出处行号；UI 只消费读模型。
 *
 * 过滤管道（旧库 TrendsScreen:94-99）：时段过滤 → 范围过滤 → 日末选取。
 * 两者均为纯 AND 过滤，次序可交换，结果等价。
 */

/** 范围 —— 旧库 recordsInRange（TrendPages.kt:519-527），边界为本地时区零点。 */
enum class TrendRange { TODAY, LAST_7, LAST_30, ALL }

/** 时段 —— 旧库 inSlot（TrendPages.kt:547-554）：上午 <12 点，下午 12..17，晚上 >=18。 */
enum class TrendSegment { ALL_DAY, MORNING, AFTERNOON, EVENING }

/** 序列口径 —— 旧库 dailySeries（TrendPages.kt:530-540）。 */
enum class TrendSeriesKind { WEIGHT, FAT }

/**
 * 日末点：一天一条；time 为被选中记录的时间戳（决定坐标与最高/最低归属日）。
 * 体脂口径先剔除 fat<0 的"未计算"记录再取当日最后一条有效值。
 */
data class TrendPoint(val time: Long, val value: Double)

/**
 * 趋势统计 —— 旧库 TrendBlock（TrendPages.kt:199-201）：
 * 高/低按值取，变化 = 最新日 − 最早日（单点时为 0，忠实旧库）。
 */
data class TrendStats(
    val points: List<TrendPoint>,
    val high: TrendPoint?,
    val low: TrendPoint?,
    val change: Double?,
)

/**
 * 月汇总 —— 旧库 MonthBanner/fatMonth（TrendPages.kt:455-458, 610-614）：
 * weightLoss > 0 = 月内共减重（首记录 − 末记录）；
 * fatDelta > 0 = 减脂；null = 首或末体脂为"未计算"哨兵（UI 显示 "--"）。
 */
data class MonthSummary(
    /** "yyyy-MM"，ISO 字典序即时间序；中文展示格式（yyyy年MM月）归 UI。 */
    val yearMonth: String,
    val weightLoss: Double,
    val fatDelta: Double?,
)

class TrendQuery(
    private val repository: MeasurementRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** 日末序列：范围+时段过滤后按本地日分组取末条，按时间升序。 */
    suspend fun dailySeries(
        range: TrendRange,
        segment: TrendSegment,
        kind: TrendSeriesKind,
    ): List<TrendPoint> {
        val records = repository.between(rangeStart(range), Long.MAX_VALUE)
            .filter { inSegment(it.time, segment) }
        return records.groupBy { localDate(it.time) }.values.mapNotNull { day ->
            val chosen = when (kind) {
                TrendSeriesKind.WEIGHT -> day.maxBy { it.time }
                TrendSeriesKind.FAT -> day.filter { it.fat >= 0 }.maxByOrNull { it.time }
            } ?: return@mapNotNull null
            TrendPoint(
                chosen.time,
                if (kind == TrendSeriesKind.WEIGHT) chosen.weight else chosen.fat,
            )
        }.sortedBy { it.time }
    }

    /** 高/低/变化统计（在日末点之上计算，口径见 TrendStats 注释）。 */
    suspend fun stats(range: TrendRange, segment: TrendSegment, kind: TrendSeriesKind): TrendStats {
        val points = dailySeries(range, segment, kind)
        return TrendStats(
            points = points,
            high = points.maxByOrNull { it.value },
            low = points.minByOrNull { it.value },
            change = points.firstOrNull()?.let { points.last().value - it.value },
        )
    }

    /** 月汇总（委托顶层 monthlySummariesOf，历史页同源消费）。 */
    fun monthlySummaries(records: List<MeasureRecord>): List<MonthSummary> =
        monthlySummariesOf(records, ::localDate)

    // ---- 本地时间语义（旧库 Calendar.getInstance 默认时区等价物）----

    internal fun localDate(time: Long): LocalDate =
        Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    private fun rangeStart(range: TrendRange): Long {
        val today = localDate(now())
        val startDay = when (range) {
            TrendRange.TODAY -> today
            TrendRange.LAST_7 -> today.minusDays(6)   // shiftDays(6)
            TrendRange.LAST_30 -> today.minusDays(29)  // shiftDays(29)
            TrendRange.ALL -> return 0L
        }
        return startDay.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    internal fun inSegment(time: Long, segment: TrendSegment): Boolean {
        if (segment == TrendSegment.ALL_DAY) return true
        val hour = Instant.ofEpochMilli(time).atZone(zone).hour
        return when (segment) {
            TrendSegment.MORNING -> hour < 12
            TrendSegment.AFTERNOON -> hour in 12..17
            TrendSegment.EVENING -> hour >= 18
            TrendSegment.ALL_DAY -> true
        }
    }

    
}

/**
 * 月汇总纯函数（历史页与趋势页同一事实源，DEC-022）：
 * weightLoss>0 = 月内共减重（首记录 − 末记录）；fatDelta null = 首或末体脂未计算。
 * records 由调用方决定筛选；按月降序（最新月在前，同旧库 groupBy 插入序）。
 * 分组键 "yyyy-MM"（旧库 monthKey；yyyy年MM月 展示格式归 UI）。
 */
internal fun monthlySummariesOf(
    records: List<MeasureRecord>,
    toLocalDate: (Long) -> LocalDate,
): List<MonthSummary> =
    records.groupBy { d ->
        val date = toLocalDate(d.time)
        "%04d-%02d".format(date.year, date.monthValue)
    }.map { (month, items) ->
        val oldest = items.minBy { it.time }
        val newest = items.maxBy { it.time }
        val fatDelta =
            if (oldest.fat < 0 || newest.fat < 0) null else oldest.fat - newest.fat
        MonthSummary(month, oldest.weight - newest.weight, fatDelta)
    }.sortedByDescending { it.yearMonth }
