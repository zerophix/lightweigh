package com.xcwk.scalehub.feature.measure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.application.query.HomeGoalState
import com.xcwk.scalehub.ui.components.QkAreaChart
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkWheelPicker
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import java.util.Locale

/**
 * feature.measure — 首页（四件套之 Screen）：旧库 HomeScreen 全家桶移植（H2）。
 * 体重卡（遮罩/比上次/BMI 段条）、AI 询问、热量记录、目标+体重趋势、喝水/跳绳、
 * 腰围/习惯、运动三卡、轻断食/生理期占位入口；保留手动记录与最近列表。
 */
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
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val home = state.home

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ---- 体重卡片（眼睛遮罩 + 比上次 + BMI 段条）----
        var masked by rememberSaveable { mutableStateOf(false) }
        QkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (masked) "显示" else "隐藏",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { masked = !masked }.padding(end = 6.dp),
                )
                Text(
                    if (home.latestTime == 0L) "暂无测量" else "今天有记录",
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.clickable(onClick = onOpenHistory), verticalAlignment = Alignment.CenterVertically) {
                    Text("历史记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Column(
                    modifier = Modifier.weight(1f).clickable(enabled = home.latestTime > 0L) {
                        onOpenReport(home.latestTime)
                    },
                ) {
                    if (home.latestWeight == null) {
                        Text("--", style = MaterialTheme.typography.displaySmall)
                        Text(
                            "点击底部 + 添加体重",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                if (masked) "***" else String.format(Locale.US, "%.1f", home.latestWeight),
                                style = MaterialTheme.typography.displayMedium,
                            )
                            Text(" 公斤", modifier = Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "比上次  体重 ${if (masked) "***" else (home.diffText ?: "--")} 公斤  " +
                                "体脂率 ${if (masked || !home.latestFatShown) "--" else String.format(Locale.US, "%.1f", home.latestFat)} %",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                BmiSegments(segment = home.bmiSegment, onSetHeight = onOpenMine)
            }
        }

        // ---- AI 询问 ----
        QkCard(modifier = Modifier.padding(top = 12.dp)) {
            var query by rememberSaveable { mutableStateOf("") }
            QkTextField(
                value = query,
                onValueChange = { if (it.length <= 200) query = it },
                placeholder = "问问轻刻 AI：减脂期能吃西瓜吗？",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                QkButton(
                    text = "发送",
                    enabled = text48(query),
                    onClick = { val text = query.trim(); query = ""; onAskAi(text) },
                )
            }
        }

        // ---- 热量记录 ----
        QkCard(modifier = Modifier.padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("热量记录", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(
                    "还可以吃 ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${home.calorieRemaining}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(" 千卡", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.padding(top = 10.dp)) {
                listOf("早餐" to "早餐", "午餐" to "午餐", "晚餐" to "晚餐", "运动" to null).forEach { (label, meal) ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (meal == null) "${home.exerciseKcal}" else "${home.dietByMeal[meal] ?: 0}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("+早餐" to "早餐", "+午餐" to "午餐", "+晚餐" to "晚餐").forEach { (label, meal) ->
                    QkButton(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = QkButtonStyle.Secondary,
                        onClick = { onOpenRecord("diet", meal) },
                    )
                }
                QkButton(
                    text = "+运动",
                    modifier = Modifier.weight(1f),
                    style = QkButtonStyle.Secondary,
                    onClick = { onOpenRecord("exercise", "") },
                )
            }
        }

        // ---- 目标 + 体重趋势 ----
        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            var showTarget by rememberSaveable { mutableStateOf(false) }
            QkCard(modifier = Modifier.weight(1f)) {
                Text("目标", style = MaterialTheme.typography.titleSmall)
                if (home.targetWeight > 0f) {
                    Text(
                        String.format(Locale.US, "目标 %.1f 公斤", home.targetWeight),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val gap = home.latestWeight?.let { it - home.targetWeight }
                    Text(
                        when {
                            gap == null -> "记录体重后查看进度"
                            gap > 0 -> String.format(Locale.US, "距目标还差 %.1f 公斤", gap)
                            else -> "已达成目标，继续保持"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "设置目标计划，轻刻帮你管理体重",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showTarget = true }) { Text("设置目标") }
                if (showTarget) {
                    val initial = home.targetWeight.toInt().coerceIn(30, 200).takeIf { home.targetWeight > 0 } ?: 60
                    var picked by androidx.compose.runtime.remember { mutableStateOf(initial) }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showTarget = false },
                        title = { Text("设置目标体重") },
                        text = {
                            QkWheelPicker(
                                labels = (30..200).map { "$it 公斤" },
                                initialIndex = initial - 30,
                            ) { picked = 30 + it }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                vm.onIntent(MeasureIntent.SetTargetWeight(picked.toFloat()))
                                showTarget = false
                            }) { Text("确定") }
                        },
                        dismissButton = { TextButton(onClick = { showTarget = false }) { Text("取消") } },
                    )
                }
            }
            QkCard(modifier = Modifier.weight(1f)) {
                Text("体重趋势", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (home.weekChange == null) "-- 公斤" else String.format(Locale.US, "%+.1f 公斤", home.weekChange),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("近7天变化", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (home.weekWeights.size >= 2) {
                    QkAreaChart(
                        points = home.weekWeights.mapIndexed { i, v ->
                            com.xcwk.scalehub.feature.trend.TrendPointUi(i.toLong(), v)
                        },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(48.dp),
                        height = 48,
                    )
                }
            }
        }

        // ---- 喝水 + 跳绳 ----
        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QkCard(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${home.waterTodayMl}", style = MaterialTheme.typography.headlineSmall)
                            Text(" ml", modifier = Modifier.padding(bottom = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "已完成 ${home.waterPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 水位进度条
                    Box(
                        Modifier.width(32.dp).height(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height((48 * home.waterPercent / 100).dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "喝水",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onOpenRecord("water", "") }
                            .padding(6.dp),
                    )
                }
            }
            QkCard(modifier = Modifier.weight(1f)) {
                Text("跳绳", style = MaterialTheme.typography.titleSmall)
                Text("${home.ropeToday}个", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { onOpenRecord("exercise", "跳绳") }) { Text("去记录") }
            }
        }

        // ---- 腰围 + 习惯 ----
        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QkCard(modifier = Modifier.weight(1f).clickable { onOpenRecord("waist", "") }) {
                Text("腰围", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (home.waistCm == null) "暂无数据" else String.format(Locale.US, "%.1f cm", home.waistCm),
                    modifier = Modifier.padding(top = 4.dp),
                    style = if (home.waistCm == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineSmall,
                    color = if (home.waistCm == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            QkCard(modifier = Modifier.weight(1f).clickable { onOpenRecord("habit", "") }) {
                Text("习惯", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${home.habitCount}", style = MaterialTheme.typography.headlineSmall)
                    Text(" /6 个", modifier = Modifier.padding(bottom = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("小习惯，大改变", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---- 运动三卡 ----
        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SportCard("健走", home.sportToday["健走"] ?: 0, Modifier.weight(1f)) { onOpenRecord("exercise", "健走") }
            SportCard("跑步", home.sportToday["跑步"] ?: 0, Modifier.weight(1f)) { onOpenRecord("exercise", "跑步") }
        }
        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SportCard("骑行", home.sportToday["骑行"] ?: 0, Modifier.weight(1f)) { onOpenRecord("exercise", "骑行") }
            QkCard(modifier = Modifier.weight(1f).clickable { onOpenFeature("fasting") }) {
                Text("轻断食", style = MaterialTheme.typography.titleSmall)
                Text(
                    "开始断食计划吧！",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- 生理期 ----
        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QkCard(modifier = Modifier.weight(1f).clickable { onOpenFeature("period") }) {
                Text("生理期", style = MaterialTheme.typography.titleSmall)
                Text(
                    "尚未记录",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }

        // ---- 手动记录体重（保留原有）----
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
        Text("记录体重", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
        QkButton(
            text = "蓝牙称重（连体脂秤）",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            onClick = onOpenScale,
        )
        QkTextField(
            value = state.weightInput,
            onValueChange = { vm.onIntent(MeasureIntent.WeightChanged(it)) },
            label = "体重 (kg)",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        QkButton(
            text = if (state.saving) "保存中…" else "保存",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = !state.saving,
            onClick = { vm.onIntent(MeasureIntent.Save) },
        )
        state.lastRecord?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
        state.error?.let {
            Text(it, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.error)
        }

        // ---- 最近测量 / 健康记录（保留原有，卡片化）----
        QkCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("最近测量", style = MaterialTheme.typography.titleMedium)
            if (state.recent.isEmpty()) {
                QkEmptyHint("还没有测量记录")
            } else {
                state.recent.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(item)
                }
            }
        }
        if (state.recentLogs.isNotEmpty()) {
            QkCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("健康记录", style = MaterialTheme.typography.titleMedium)
                state.recentLogs.forEach { log ->
                    Text(
                        "${log.timeText}  ${log.typeLabel} · ${log.valueText}",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun text48(query: String): Boolean = query.trim().isNotEmpty()

@Composable
private fun SportCard(name: String, todayKcal: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    QkCard(modifier = modifier.clickable(onClick = onClick)) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(
            if (todayKcal > 0) "今日 $todayKcal 千卡" else "暂无记录",
            modifier = Modifier.padding(top = 8.dp),
            style = if (todayKcal > 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
            color = if (todayKcal > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** BMI 四段条（旧库 bmiSegments 配色口径，token 化）。 */
@Composable
private fun BmiSegments(segment: String?, onSetHeight: () -> Unit) {
    if (segment == null) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.clickable(onClick = onSetHeight)) {
            Text("设置身高", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
            Text("设置后计算 BMI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val segments = listOf(
        "偏瘦" to QkColor.BmiThin,
        "标准" to QkColor.BmiNormal,
        "偏胖" to QkColor.BmiOver,
        "肥胖" to QkColor.BmiObese,
    )
    Row {
        segments.forEach { (label, color) ->
            val active = label == segment
            Box(
                Modifier.padding(horizontal = 2.dp)
                    .width(34.dp).height(22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (active) color else color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) androidx.compose.ui.graphics.Color.White else QkColor.SegmentInactive,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}
