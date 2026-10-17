@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xcwk.scalehub.feature.history

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.application.query.TrendSegment
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkChip
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.theme.QkTheme
import java.time.Instant
import java.time.ZoneOffset

/**
 * history — 四件套之 Screen：无状态渲染。
 * 交互口径（需求文档）：删除前确认（AlertDialog），取消立刻关闭；
 * 月分组可折叠（默认收起，同旧库 collapsed）；M4 增按日筛选与导出。
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onIntent: (HistoryIntent) -> Unit,
    onBack: () -> Unit = {},
    onOpenReport: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    var showDayPicker by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("历史记录", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TextButton(
                onClick = {
                    val text = buildExportText(state)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(share, "导出测量记录"))
                },
            ) { Text("导出") }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TrendSegment.entries.forEach { s ->
                QkChip(segmentLabel(s), state.segment == s && state.selectedDay == null) {
                    onIntent(HistoryIntent.SegmentChanged(s))
                }
            }
            QkChip("按日", state.selectedDay != null) { showDayPicker = true }
        }
        state.selectedDay?.let { day ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("按日：$day", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { onIntent(HistoryIntent.ClearDay) }) { Text("回到月视图") }
            }
        }
        state.emptyText?.let {
            QkEmptyHint(it)
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            if (state.selectedDay != null) {
                // 按日视图：平铺当日行（含删除与点入报告）
                QkCard(modifier = Modifier.padding(bottom = 10.dp)) {
                    Text(state.dayHeader ?: "", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                    state.dayRows.forEach { row ->
                        HistoryLine(row = row, onOpenReport = onOpenReport, onDelete = { onIntent(HistoryIntent.AskDelete(row.time)) })
                    }
                }
            } else {
                state.months.forEach { month ->
                    QkCard(modifier = Modifier.padding(bottom = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onIntent(HistoryIntent.ToggleMonth(month.month)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(month.titleText, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(if (month.expanded) "收起" else "展开", color = MaterialTheme.colorScheme.primary)
                        }
                        if (month.expanded) {
                            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                            month.rows.forEach { row ->
                                HistoryLine(row = row, onOpenReport = onOpenReport, onDelete = { onIntent(HistoryIntent.AskDelete(row.time)) })
                            }
                        }
                    }
                }
            }
        }
        state.error?.let { Text(it, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.error) }

        // 删除前确认：取消立刻关闭，确认后执行（需求文档历史记录节）
        state.confirmDeleteTime?.let { time ->
            AlertDialog(
                onDismissRequest = { onIntent(HistoryIntent.CancelDelete) },
                title = { Text("删除这条测量？") },
                text = { Text("删除后不可恢复。") },
                confirmButton = {
                    TextButton(
                        onClick = { onIntent(HistoryIntent.ConfirmDelete) },
                        enabled = !state.deleting,
                    ) { Text(if (state.deleting) "删除中…" else "确认删除") }
                },
                dismissButton = {
                    TextButton(onClick = { onIntent(HistoryIntent.CancelDelete) }) { Text("取消") }
                },
            )
        }
    }

    // 按日筛选：日期选择（DatePicker 的选中值为 UTC 零点，按 UTC 取 yyyy-MM-dd）
    if (showDayPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDayPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                            onIntent(HistoryIntent.PickDay(day))
                        }
                        showDayPicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDayPicker = false }) { Text("取消") } },
        ) { DatePicker(state = pickerState) }
    }
}

/** 单行渲染（月展开与按日视图共用）。 */
@Composable
private fun HistoryLine(row: HistoryRowUi, onOpenReport: (Long) -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable { onOpenReport(row.time) },   // 需求：点击一条打开该次测量的体测报告
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.timeText, style = MaterialTheme.typography.bodyMedium)
            Text(
                "体重 ${row.weightText} kg · 体脂 ${row.fatText}" +
                    (row.deltaText?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) {
            Text("删除", color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun segmentLabel(segment: TrendSegment): String = when (segment) {
    TrendSegment.ALL_DAY -> "全天"
    TrendSegment.MORNING -> "上午"
    TrendSegment.AFTERNOON -> "下午"
    TrendSegment.EVENING -> "晚上"
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    QkTheme {
        HistoryScreen(
            state = HistoryUiState(
                loading = false,
                months = listOf(
                    HistoryMonthUi(
                        month = "2026-10",
                        titleText = "2026年10月: 共减重3.0公斤  减脂1.0%",
                        expanded = true,
                        rows = listOf(
                            HistoryRowUi(0, "10-15 08:00", "72.0", "19.1%", "-1.5"),
                            HistoryRowUi(0, "10-01 08:00", "73.5", "20.1%", null),
                        ),
                    ),
                    HistoryMonthUi("2026-09", "2026年9月: 共增重2.0公斤  减脂--", false, emptyList()),
                ),
            ),
            onIntent = {},
        )
    }
}
