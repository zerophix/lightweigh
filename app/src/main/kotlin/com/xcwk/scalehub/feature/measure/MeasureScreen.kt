package com.xcwk.scalehub.feature.measure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.application.query.HomeGoalState
import com.xcwk.scalehub.feature.sync.SYNC_CENTER_ENTRY_TITLE
import com.xcwk.scalehub.feature.sync.SyncCenterRoute
import com.xcwk.scalehub.feature.trend.TrendPointUi
import com.xcwk.scalehub.ui.components.QkActionChip
import com.xcwk.scalehub.ui.components.QkAreaChart
import com.xcwk.scalehub.ui.components.QkBodyBar
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkMiniCard
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkProgress
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.components.QkWheelPicker
import com.xcwk.scalehub.ui.shell.WiringEntries
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * feature.measure — 首页（设计稿「首页」屏）。
 *
 * 版式按设计稿自上而下：体重卡（指标格 + 四段条 + 双按钮）→ AI 询问 → 今日饮食 →
 * 目标 / 体重趋势 → 喝水 / 跳绳 → 习惯打卡 / 运动 → 腰围·轻断食·生理期 → 手动记录体重
 * → 最近测量 → 健康记录。
 *
 * 设计稿没有的模块（腰围 / 轻断食 / 生理期 / 手动记录 / 健康记录 / 热量明细）不是删掉，
 * 而是编进同一套卡片语法里：腰围并入体重卡的第三个指标格，轻断食与生理期用同尺寸的
 * 浅底卡承接，热量明细展开在「今日饮食」的四格内。
 */
/** 首页体重卡上的入口文案。 */
const val MEASURE_ENTRY_TITLE = "开始测量"

/** 首页可整屏覆盖的接入层；[Home] 即真实首页本身。 */
enum class MeasureHomeLayer {
    Home,
    SyncCenter,
}

@Composable
fun MeasureScreen(
    vm: MeasureViewModel = viewModel(factory = MeasureViewModel.Factory),
    onOpenScale: () -> Unit = {},
    onOpenReport: (Long) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onAskAi: (String) -> Unit = {},
    onOpenRecord: (String, String) -> Unit = { _, _ -> },
    onOpenFeature: (String) -> Unit = {},
    onOpenMine: () -> Unit = {},
    onOpenQuick: () -> Unit = {},
) {
    // 接入波：测量结果的分享 / 导出（T-078）全屏覆盖首页，返回即回首页
    var showShare by rememberSaveable { mutableStateOf(false) }
    if (showShare) {
        ResultShareRoute(onBack = { showShare = false })
        return
    }

    // 接入波第三批：首页整屏覆盖层（测量流程八态 / 数据同步 → 数据冲突）
    var layerName by rememberSaveable { mutableStateOf(MeasureHomeLayer.Home.name) }
    val closeLayer: () -> Unit = { layerName = MeasureHomeLayer.Home.name }
    val openLayer: (MeasureHomeLayer) -> Unit = { layerName = it.name }

    val state by vm.state.collectAsStateWithLifecycle()
    val unitPrefs by vm.unitPrefs.collectAsStateWithLifecycle()
    val home = state.home

    when (layerName) {
        MeasureHomeLayer.SyncCenter.name -> {
            SyncCenterRoute(onBack = closeLayer)
            return
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        // 无数据时给真实空态（不含演示数值/演示按钮）；有数据才画体重卡。
        // 空态与体重卡二选一，避免原型镜像那样「同一屏两套数据表达」。
        if (home.latestWeight == null && state.loaded) {
            HomeEmptyHeader(
                onStartMeasure = onOpenScale,
                onOpenHistory = onOpenHistory,
            )
        } else {
            WeightCard(
                home = home,
                unitPrefs = unitPrefs,
                onOpenReport = onOpenReport,
                onOpenHistory = onOpenHistory,
                onSetHeight = onOpenMine,
                // 首页主 CTA 走真实蓝牙测量（ScaleRoute）；原型镜像的演示流程不再可达，
                // 否则会出现「显示已保存 62.4kg 但首页仍无数据」的假成功。
                onStartMeasure = onOpenScale,
            )
        }
        // 接入波入口：分享本次结果 / 导出（T-078）、数据同步 → 数据冲突（T-094）
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
        ) {
            TextAction(SYNC_CENTER_ENTRY_TITLE) { openLayer(MeasureHomeLayer.SyncCenter) }
            TextAction(WiringEntries.ResultShare.title) { showShare = true }
        }
        AiAskRow(onAskAi = onAskAi)
        TodayDietCard(home = home, onOpenRecord = onOpenRecord, onOpenQuick = onOpenQuick)
        Row(modifier = Modifier.padding(top = QkSpacing.CardGap), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoalCard(home = home, modifier = Modifier.weight(1f), onSetTarget = { vm.onIntent(MeasureIntent.SetTargetWeight(it)) }, unitPrefs = unitPrefs)
            WeekTrendCard(home = home, modifier = Modifier.weight(1f), onOpenHistory = onOpenHistory, unitPrefs = unitPrefs)
        }
        Row(modifier = Modifier.padding(top = QkSpacing.CardGap), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WaterCard(home = home, modifier = Modifier.weight(1f), onRecord = { onOpenRecord("water", "") })
            QkMiniCard(
                title = "跳绳",
                value = "${home.ropeToday}",
                caption = if (home.ropeToday > 0) "今日累计" else "还没开始",
                icon = QkIcons.Rope,
                iconTone = QkGlyphTone.Blue,
                modifier = Modifier.weight(1f),
                onClick = { onOpenRecord("exercise", "跳绳") },
            )
        }
        Row(modifier = Modifier.padding(top = QkSpacing.CardGap), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HabitCard(home = home, modifier = Modifier.weight(1f), onRecord = { onOpenRecord("habit", "") })
            SportCard(home = home, modifier = Modifier.weight(1f), onOpenRecord = onOpenRecord)
        }
        Row(modifier = Modifier.padding(top = QkSpacing.CardGap), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TintCard(
                title = "轻断食",
                value = "16:8",
                caption = "尚未开始",
                icon = QkIcons.Fasting,
                tint = QkColor.MintSoft,
                ink = QkColor.Primary,
                modifier = Modifier.weight(1f),
                tone = QkGlyphTone.Green,
                onClick = { onOpenFeature("fasting") },
            )
            TintCard(
                title = "生理期",
                value = "未记录",
                caption = "点击记录周期",
                icon = QkIcons.Period,
                tint = QkColor.PurpleSoft,
                ink = QkColor.Purple,
                modifier = Modifier.weight(1f),
                tone = QkGlyphTone.Purple,
                onClick = { onOpenFeature("period") },
            )
        }
        ManualWeightCard(
            value = state.weightInput,
            saving = state.saving,
            onChange = { vm.onIntent(MeasureIntent.WeightChanged(it)) },
            onSave = { vm.onIntent(MeasureIntent.Save) },
            onScale = onOpenScale,
            unitPrefs = unitPrefs,
        )
        state.lastRecord?.let {
            Text(
                it,
                modifier = Modifier.padding(top = QkSpacing.Sm),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Primary,
            )
        }
        state.error?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Danger,
            )
        }

        QkSectionHead(
            title = "最近测量",
            modifier = Modifier.padding(top = QkSpacing.Lg, bottom = 2.dp),
            action = { TextAction("全部 ›", onOpenHistory) },
        )
        QkCard(padding = 6.dp) {
            if (state.recent.isEmpty()) {
                QkEmptyHint("还没有测量记录", modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp))
            } else {
                state.recent.take(4).forEachIndexed { index, item ->
                    if (index > 0) Line()
                    RecentRow(item = item, onClick = { onOpenReport(item.time) })
                }
            }
        }

        QkSectionHead(title = "健康记录", modifier = Modifier.padding(top = QkSpacing.Lg, bottom = 2.dp))
        QkCard(padding = 6.dp) {
            if (state.recentLogs.isEmpty()) {
                QkEmptyHint("今天还没有记录", modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp))
            } else {
                state.recentLogs.forEachIndexed { index, log ->
                    if (index > 0) Line()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QkIconBubble(iconOfLog(log.typeLabel), QkGlyphTone.Green, 30.dp)
                        Text(log.typeLabel, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                        Text(
                            log.valueText,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = QkColor.Muted,
                            maxLines = 1,
                        )
                        Text(log.timeText, style = MaterialTheme.typography.labelSmall, color = QkColor.Hint)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------- 体重卡

/**
 * 首页空态（真实数据驱动）：没有任何测量记录时展示。
 *
 * 只保留「引导 + 入口」，不放演示数值、不放原型里的「返回正常首页」跳转按钮：
 * 主 CTA 与「开始测量」一致，走真实蓝牙测量。
 */
@Composable
private fun HomeEmptyHeader(
    onStartMeasure: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = QkSpacing.Md)) {
        Text(
            "你好，开始记录吧",
            style = MaterialTheme.typography.headlineSmall,
            color = QkColor.Fg,
        )
        Text(
            "完成第一次测量，就能看到身体概览。",
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Muted,
        )
        QkCard(modifier = Modifier.padding(top = QkSpacing.Md), padding = QkSpacing.CardPad) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                QkIconBubble(QkIcons.Weight, QkGlyphTone.Green, 44.dp)
                Text(
                    "还没有测量数据",
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                    style = MaterialTheme.typography.titleMedium,
                    color = QkColor.Fg,
                )
                Text(
                    "连接体脂秤完成一次测量，或先手动记录一次体重。",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = QkSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QkButton(
                text = "开始第一次测量",
                modifier = Modifier.weight(1f),
                onClick = onStartMeasure,
            )
            QkButton(
                text = "历史记录",
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = onOpenHistory,
            )
        }
    }
}

@Composable
private fun WeightCard(
    home: HomeGoalState,
    unitPrefs: com.xcwk.scalehub.domain.model.UserUnitPreferences,
    onOpenReport: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onSetHeight: () -> Unit,
    onStartMeasure: () -> Unit,
) {
    var masked by rememberSaveable { mutableStateOf(false) }
    QkCard(padding = QkSpacing.CardPad) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("体重", style = MaterialTheme.typography.titleMedium, color = QkColor.Fg, modifier = Modifier.weight(1f))
            Text("隐藏", style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
            Switch(
                checked = !masked,
                onCheckedChange = { masked = !it },
                modifier = Modifier.padding(start = 6.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = QkColor.Primary,
                    checkedBorderColor = QkColor.Primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = QkColor.Secondary,
                    uncheckedBorderColor = QkColor.Secondary,
                ),
            )
        }
        Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = home.latestTime > 0L) { onOpenReport(home.latestTime) },
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        when {
                            masked -> "***"
                            home.latestWeight == null -> "--"
                            else -> com.xcwk.scalehub.application.format.QkFormat.weightStr(home.latestWeight!!, unitPrefs)
                        },
                        style = MaterialTheme.typography.displayLarge,
                        color = QkColor.Fg,
                    )
                }
                Text(
                    when {
                        home.latestWeight == null -> "点击下方 ＋ 添加体重"
                        masked -> "比上次 ***"
                        home.diffText == null -> "暂无对比数据"
                        else -> {
                            val down = home.diffText!!.startsWith("-")
                            val diffKg = home.diffText!!.toDoubleOrNull() ?: 0.0
                            val diffStr = com.xcwk.scalehub.application.format.QkFormat.weightValue(diffKg.absoluteValue, unitPrefs)
                            "比上次 ${if (down) "↓" else "↑"} ${diffStr} ${com.xcwk.scalehub.application.format.QkFormat.weightUnitLabel(unitPrefs)}"
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (home.diffText != null && !masked) {
                        if (home.diffText!!.startsWith("-")) QkColor.Primary else QkColor.AmberInk
                    } else {
                        QkColor.Muted
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricCell(
                    label = "体脂率",
                    value = if (masked || !home.latestFatShown) "--" else String.format(Locale.US, "%.1f%%", home.latestFat),
                )
                Box(modifier = Modifier.width(1.dp).height(34.dp).background(QkColor.LineSoft))
                MetricCell(
                    label = "BMI",
                    value = if (masked) "--" else home.bmi?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                    pill = home.bmiSegment?.let { if (it == "标准") "正常" else it },
                )
                Box(modifier = Modifier.width(1.dp).height(34.dp).background(QkColor.LineSoft))
                MetricCell(
                    label = "腰围",
                    value = if (home.waistCm == null) "--" else com.xcwk.scalehub.application.format.QkFormat.heightStr(home.waistCm!!, unitPrefs),
                )
            }
        }
        Box(modifier = Modifier.padding(top = QkSpacing.Md)) {
            QkBodyBar(
                labels = listOf("偏瘦", "标准", "偏重", "肥胖"),
                active = home.bmiSegment?.let { if (it == "偏胖") "偏重" else it },
            )
        }
        if (home.bmiSegment == null) {
            Text(
                "设置身高后计算 BMI",
                modifier = Modifier.padding(top = 6.dp).clickable(onClick = onSetHeight),
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Primary,
            )
        }
        Row(modifier = Modifier.padding(top = QkSpacing.Md), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QkButton(
                text = MEASURE_ENTRY_TITLE,
                modifier = Modifier.weight(1f),
                onClick = onStartMeasure,
            )
            QkButton(
                text = "查看报告",
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = { onOpenReport(home.latestTime) },
            )
            QkButton(
                text = "历史记录",
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = onOpenHistory,
            )
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, pill: String? = null) {
    Column(
        modifier = Modifier.padding(horizontal = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = QkColor.Muted)
        Text(
            value,
            modifier = Modifier.padding(top = 5.dp),
            style = MaterialTheme.typography.titleMedium,
            color = QkColor.Fg,
        )
        if (pill != null) {
            Box(modifier = Modifier.padding(top = 4.dp)) {
                QkPill(pill)
            }
        }
    }
}

// ---------------------------------------------------------------- AI 询问

@Composable
private fun AiAskRow(onAskAi: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = QkSpacing.CardGap)
            .clip(RoundedCornerShape(24.dp))
            .background(QkColor.Card)
            .clickable { onAskAi("") }
            .padding(start = 13.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkIcon(QkIcons.Ai, tint = QkColor.Primary, size = 18.dp)
        Text(
            "问问轻衡 AI：减脂期能吃西瓜吗？",
            modifier = Modifier.weight(1f).padding(start = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(QkColor.Primary),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(QkIcons.Send, tint = Color.White, size = 17.dp)
        }
    }
}

// ---------------------------------------------------------------- 今日饮食

@Composable
private fun TodayDietCard(
    home: HomeGoalState,
    onOpenRecord: (String, String) -> Unit,
    onOpenQuick: () -> Unit,
) {
    QkCard(padding = QkSpacing.CardPad) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkIcon(QkIcons.Vegetable, tint = QkColor.Primary, size = 16.dp)
            Text(
                "今日饮食",
                modifier = Modifier.padding(start = 5.dp),
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text("还可以吃 ", style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
            Text(
                "${home.calorieRemaining}",
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Primary,
            )
            Text(" 千卡", style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        }
        Row(modifier = Modifier.padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MealCell("早餐", home.dietByMeal["早餐"] ?: 0, QkIcons.Breakfast, QkGlyphTone.Green, Modifier.weight(1f)) {
                onOpenRecord("diet", "早餐")
            }
            MealCell("午餐", home.dietByMeal["午餐"] ?: 0, QkIcons.Lunch, QkGlyphTone.Green, Modifier.weight(1f)) {
                onOpenRecord("diet", "午餐")
            }
            MealCell("晚餐", home.dietByMeal["晚餐"] ?: 0, QkIcons.Dinner, QkGlyphTone.Green, Modifier.weight(1f)) {
                onOpenRecord("diet", "晚餐")
            }
            MealCell("运动", home.exerciseKcal, QkIcons.Exercise, QkGlyphTone.Blue, Modifier.weight(1f)) {
                onOpenRecord("exercise", "")
            }
        }
        Row(modifier = Modifier.padding(top = QkSpacing.Md), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QkActionChip("记录饮食", QkIcons.Meal) { onOpenRecord("diet", "") }
            QkActionChip("记录运动", QkIcons.Exercise) { onOpenRecord("exercise", "") }
            QkActionChip("记录饮水", QkIcons.Water) { onOpenRecord("water", "") }
            QkActionChip("更多", QkIcons.More, onClick = onOpenQuick)
        }
    }
}

@Composable
private fun MealCell(
    label: String,
    value: Int,
    icon: ImageVector,
    tone: QkGlyphTone,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QkIconBubble(icon, tone, 34.dp)
        Text(
            label,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )
        Text(
            "$value",
            style = MaterialTheme.typography.titleSmall,
            color = QkColor.Fg,
        )
    }
}

// ---------------------------------------------------------------- 目标 / 体重趋势

@Composable
private fun GoalCard(home: HomeGoalState, modifier: Modifier = Modifier, onSetTarget: (Float) -> Unit, unitPrefs: com.xcwk.scalehub.domain.model.UserUnitPreferences) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    QkCard(modifier = modifier, padding = QkSpacing.CardPad) {
        Text("目标", style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
        val gap = home.latestWeight?.let { it - home.targetWeight }
        Text(
            when {
                home.targetWeight <= 0f -> "未设置"
                gap == null -> "目标 ${com.xcwk.scalehub.application.format.QkFormat.weightStr(home.targetWeight.toDouble(), unitPrefs)}"
                gap > 0 -> "减重 ${com.xcwk.scalehub.application.format.QkFormat.weightStr(gap.toDouble(), unitPrefs)}"
                gap < 0 -> "增重 ${com.xcwk.scalehub.application.format.QkFormat.weightStr((-gap).toDouble(), unitPrefs)}"
                else -> "已达成"
            },
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
        )
        QkButton(
            text = "设置目标",
            modifier = Modifier.padding(top = 10.dp),
            onClick = { showPicker = true },
        )
    }
    if (showPicker) {
        val initial = home.targetWeight.toInt().coerceIn(30, 200).takeIf { home.targetWeight > 0 } ?: 60
        var picked by remember { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("设置目标体重") },
            text = {
                QkWheelPicker(
                    labels = (30..200).map { "${it} ${com.xcwk.scalehub.application.format.QkFormat.weightUnitLabel(unitPrefs)}" },
                    initialIndex = initial - 30,
                ) { picked = 30 + it }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetTarget(picked.toFloat())
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun WeekTrendCard(home: HomeGoalState, modifier: Modifier = Modifier, onOpenHistory: () -> Unit, unitPrefs: com.xcwk.scalehub.domain.model.UserUnitPreferences) {
    QkCard(modifier = modifier, padding = QkSpacing.CardPad, onClick = onOpenHistory) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("体重趋势", style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
            Text(
                " 7天",
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Muted,
            )
        }
        if (home.weekWeights.size >= 2) {
            Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    com.xcwk.scalehub.application.format.QkFormat.weightStr(home.weekWeights.first(), unitPrefs),
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Muted,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    com.xcwk.scalehub.application.format.QkFormat.weightStr(home.weekWeights.last(), unitPrefs),
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            QkAreaChart(
                points = home.weekWeights.mapIndexed { i, v -> TrendPointUi(i.toLong(), v) },
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(46.dp),
                height = 46,
                padY = 6,
            )
        } else {
            QkEmptyHint("记录两次以上可见趋势")
        }
        Text(
            home.weekChange?.let { "近 7 天 ${com.xcwk.scalehub.application.format.QkFormat.weightStr(it.toDouble(), unitPrefs)}" } ?: "近 7 天 --",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )
    }
}

// ---------------------------------------------------------------- 喝水 / 习惯 / 运动

@Composable
private fun WaterCard(home: HomeGoalState, modifier: Modifier = Modifier, onRecord: () -> Unit) {
    QkCard(modifier = modifier, padding = QkSpacing.CardPad) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkIconBubble(QkIcons.Water, QkGlyphTone.Blue, 30.dp)
            Text(
                "喝水",
                modifier = Modifier.padding(start = QkSpacing.Sm).weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
            Text("${home.waterPercent}%", style = MaterialTheme.typography.labelSmall, color = QkColor.Muted)
        }
        Row(modifier = Modifier.padding(top = QkSpacing.Sm), verticalAlignment = Alignment.Bottom) {
            Text("${home.waterTodayMl}", style = MaterialTheme.typography.headlineSmall, color = QkColor.Fg)
            Text(" ml", modifier = Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        }
        QkProgress(
            percent = home.waterPercent,
            color = QkColor.Blue,
            modifier = Modifier.padding(top = 9.dp),
        )
        QkActionChip("加水", QkIcons.Add, modifier = Modifier.padding(top = 9.dp), onClick = onRecord)
    }
}

@Composable
private fun HabitCard(home: HomeGoalState, modifier: Modifier = Modifier, onRecord: () -> Unit) {
    QkCard(modifier = modifier, padding = QkSpacing.CardPad, onClick = onRecord) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("习惯打卡", style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
            Text(
                " ${home.habitCount}/6",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Primary,
            )
            QkIcon(QkIcons.ChevronRight, size = 15.dp, tint = QkColor.Hint)
        }
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(6) { index ->
                val hit = index < home.habitCount
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (hit) QkColor.Primary else QkColor.Secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hit) QkIcon(QkIcons.Habit, tint = Color.White, size = 13.dp)
                }
            }
        }
        Text(
            "小习惯，大改变",
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )
    }
}

@Composable
private fun SportCard(home: HomeGoalState, modifier: Modifier = Modifier, onOpenRecord: (String, String) -> Unit) {
    QkCard(modifier = modifier, padding = QkSpacing.CardPad) {
        Text("运动", style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SportItem("健走", QkIcons.Walk, home.sportToday["健走"] ?: 0, Modifier.weight(1f)) {
                onOpenRecord("exercise", "健走")
            }
            SportItem("跑步", QkIcons.Run, home.sportToday["跑步"] ?: 0, Modifier.weight(1f)) {
                onOpenRecord("exercise", "跑步")
            }
            SportItem("骑行", QkIcons.Bike, home.sportToday["骑行"] ?: 0, Modifier.weight(1f)) {
                onOpenRecord("exercise", "骑行")
            }
        }
    }
}

@Composable
private fun SportItem(
    label: String,
    icon: ImageVector,
    kcal: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QkIconBubble(icon, QkGlyphTone.Green, 30.dp)
        Text(
            label,
            modifier = Modifier.padding(top = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )
        Text(
            if (kcal > 0) "$kcal" else "--",
            style = MaterialTheme.typography.labelSmall,
            color = if (kcal > 0) QkColor.Fg else QkColor.Hint,
        )
    }
}

/** 浅底小卡（设计稿「轻断食 / 生理期」）：整卡染色，图标与标题同色系。 */
@Composable
private fun TintCard(
    title: String,
    value: String,
    caption: String,
    icon: ImageVector,
    tint: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    tone: QkGlyphTone = QkGlyphTone.Green,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(QkRadius.Card))
            .background(tint)
            .clickable(onClick = onClick)
            .padding(QkSpacing.CardPad),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkIconBubble(icon, tone, 28.dp)
            Text(
                title,
                modifier = Modifier.padding(start = 7.dp).weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
        }
        Text(
            value,
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.headlineSmall,
            color = ink,
        )
        Text(
            caption,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )
    }
}

// ---------------------------------------------------------------- 手动记录体重

@Composable
private fun ManualWeightCard(
    value: String,
    saving: Boolean,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onScale: () -> Unit,
    unitPrefs: com.xcwk.scalehub.domain.model.UserUnitPreferences,
) {
    val typed = value.isNotBlank()
    QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap), padding = QkSpacing.CardPad) {
        Text("手动记录体重", style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            QkTextField(
                value = value,
                onValueChange = onChange,
                placeholder = "请输入体重 (${com.xcwk.scalehub.application.format.QkFormat.weightUnitLabel(unitPrefs)})",
                modifier = Modifier.weight(1f),
            )
            // 一个按钮位两种语义：填了数字就是「保存」，空着就是「蓝牙称重」。
            // 设计稿只有一个绿色按钮，硬塞两个会挤掉输入框宽度。
            QkButton(
                text = if (typed) (if (saving) "保存中…" else "保存") else "蓝牙称重",
                enabled = !saving,
                onClick = { if (typed) onSave() else onScale() },
            )
        }
    }
}

// ---------------------------------------------------------------- 列表行

@Composable
private fun RecentRow(item: RecentMeasureItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QkIconBubble(QkIcons.Weight, QkGlyphTone.Green, 30.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text("体重 ${item.weightText}", style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
            item.fatText?.let {
                Text(
                    "体脂率 $it",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Muted,
                )
            }
        }
        Text(item.timeText, style = MaterialTheme.typography.labelSmall, color = QkColor.Hint)
        QkIcon(QkIcons.ChevronRight, size = 15.dp, tint = QkColor.Hint)
    }
}

@Composable
private fun Line() {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp).height(1.dp).background(QkColor.LineSoft))
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.clickable(onClick = onClick),
        style = MaterialTheme.typography.labelMedium,
        color = QkColor.Muted,
    )
}

private fun iconOfLog(typeLabel: String): ImageVector = when (typeLabel) {
    "饮食" -> QkIcons.Meal
    "运动" -> QkIcons.Exercise
    "饮水" -> QkIcons.Water
    "睡眠" -> QkIcons.Sleep
    "腰围" -> QkIcons.Waist
    "体脂" -> QkIcons.BodyFat
    "生理期" -> QkIcons.Period
    "习惯" -> QkIcons.Habit
    else -> QkIcons.More
}
