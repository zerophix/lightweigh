package com.xcwk.scalehub.feature.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 成就 / 激励（T-069）。
 *
 * 只有两类成就是**可判定**的，因此只做这两类：
 * - 连续测量天数（按本地日期去重，断一天即归零）
 * - 减重里程碑（按累计减重量跨档）
 *
 * 不做的：徽章墙、积分、排行榜——没有数据源与验收口径，做了就是假 UI。
 */
enum class Milestone(val kg: Int, val label: String) {
    First(1, "轻了 1 kg"),
    Three(3, "轻了 3 kg"),
    Five(5, "轻了 5 kg"),
    Ten(10, "轻了 10 kg"),
    Twenty(20, "轻了 20 kg"),
    ;

    companion object {
        /** 从高到低排列，便于取"已达成的最高一档"。 */
        fun descending(): List<Milestone> = entries.sortedByDescending { it.kg }
    }
}

/**
 * 连续测量天数。
 *
 * 规则（可判定）：
 * - 同一天多次测量只算一天；
 * - 从 `today` 往前连续计数；今天没测但昨天测了，仍从昨天算（当天还没到秤前不算断）；
 * - 今天和昨天都没测 → 0。
 *
 * @param measuredAtMillis 测量时间戳（任意顺序、可重复）
 * @param todayEpochDay 今天（由调用方传入，便于测试与跨时区一致）
 */
fun currentStreakDays(
    measuredAtMillis: List<Long>,
    todayEpochDay: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Int {
    if (measuredAtMillis.isEmpty()) return 0
    val days = measuredAtMillis
        .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }
        .toSet()

    var cursor = when {
        days.contains(todayEpochDay) -> todayEpochDay
        days.contains(todayEpochDay - 1) -> todayEpochDay - 1
        else -> return 0
    }
    var streak = 0
    while (days.contains(cursor)) {
        streak++
        cursor--
    }
    return streak
}

/** 已减重量（正数）；数据缺失或增重返回 null（不把"没减"说成成就）。 */
fun lostWeightKg(startKg: Double?, currentKg: Double?): Double? {
    if (startKg == null || currentKg == null) return null
    val lost = startKg - currentKg
    return if (lost >= Milestone.First.kg) lost else null
}

/** 已达成的最低一档里程碑；未达标返回 null。 */
fun reachedMilestone(lostKg: Double?): Milestone? {
    if (lostKg == null) return null
    return Milestone.descending().firstOrNull { lostKg >= it.kg }
}

/** 下一档距离（还差多少 kg）；已到顶返回 null。 */
fun nextMilestoneGap(lostKg: Double?): Pair<Milestone, Double>? {
    val lost = lostKg ?: 0.0
    val next = Milestone.entries.sortedBy { it.kg }.firstOrNull { lost < it.kg } ?: return null
    return next to (next.kg - lost)
}

/**
 * 首页成就条：连续测量 + 最近里程碑。
 * 数据不足时不渲染（调用方用返回值判定），本组件只在有内容时被调用。
 */
@Composable
fun AchievementStrip(
    streakDays: Int,
    milestone: Milestone?,
    modifier: Modifier = Modifier,
) {
    if (streakDays <= 0 && milestone == null) return
    QkCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = if (streakDays > 0) "连续测量 $streakDays 天" else "本周还没有连续记录",
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Fg,
                )
                Text(
                    text = if (streakDays > 0) "保持节奏，别让今天断档" else "今天称一次就能开始",
                    modifier = Modifier.padding(top = QkSpacing.Xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
            milestone?.let { QkPill(text = it.label) }
        }
    }
}

@Preview(name = "成就 · 连续 5 天 + 里程碑", showBackground = true, widthDp = 393)
@Composable
private fun AchievementStripPreview() {
    QkTheme {
        AchievementStrip(
            streakDays = 5,
            milestone = Milestone.Three,
            modifier = Modifier.padding(QkSpacing.Page),
        )
    }
}

/** 便于测试与调用方：把毫秒时间转成本地 epochDay。 */
fun epochDayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

/** 今天的本地 epochDay。 */
fun todayEpochDay(zone: ZoneId = ZoneId.systemDefault()): Long =
    LocalDate.now(zone).toEpochDay()
