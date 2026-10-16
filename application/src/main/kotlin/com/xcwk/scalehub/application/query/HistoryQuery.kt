package com.xcwk.scalehub.application.query

import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * query — 历史记录（旧库 TrendPages 历史屏规则移植，DEC-022）：
 *  - 行序：升序配对 previous 后整体倒序展示（旧库 toRows：TrendPages.kt:542-545），
 *    previous 用于行内增减（较上一条体重差）
 *  - 时段过滤复用 TrendQuery.inSegment（单源）
 *  - 按日筛选：day 命中才收（旧库 line291 sameDay 过滤）
 *  - 月汇总与趋势页同源（monthlySummariesOf）
 * 报告跳转/两条对比/文字导出属后续切片（报告页未建），本查询只供列表与删除。
 */
data class HistoryRow(
    val record: MeasureRecord,
    /** 升序前一条（同过滤集内相邻），无则 null（首条）。 */
    val previous: MeasureRecord?,
)

class HistoryQuery(
    private val repository: MeasurementRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val trend = TrendQuery(repository, zone, now)

    suspend fun rows(
        segment: TrendSegment = TrendSegment.ALL_DAY,
        day: LocalDate? = null,
    ): List<HistoryRow> {
        val filtered = repository.between(0L, Long.MAX_VALUE)
            .filter { trend.inSegment(it.time, segment) }
            .filter { day == null || dateOf(it.time) == day }
            .sortedBy { it.time }
        return filtered
            .mapIndexed { index, record -> HistoryRow(record, filtered.getOrNull(index - 1)) }
            .asReversed()
    }

    /** 按月降序月汇总（时段过滤后，与趋势页同一实现）。 */
    suspend fun monthly(segment: TrendSegment = TrendSegment.ALL_DAY): List<MonthSummary> {
        val filtered = repository.between(0L, Long.MAX_VALUE)
            .filter { trend.inSegment(it.time, segment) }
        return monthlySummariesOf(filtered, ::dateOf)
    }

    private fun dateOf(time: Long): LocalDate =
        Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
}
