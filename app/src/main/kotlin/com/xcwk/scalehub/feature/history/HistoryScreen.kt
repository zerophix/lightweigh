@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xcwk.scalehub.feature.history

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import com.xcwk.scalehub.ui.components.segmentLabel
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkChip
import com.xcwk.scalehub.ui.components.QkDateChip
import com.xcwk.scalehub.ui.components.QkDivider
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkSegmented
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme
import java.time.Instant
import java.time.ZoneOffset

/**
 * history — 四件套之 Screen：无状态渲染。
 * 交互口径（需求文档）：删除前确认（AlertDialog），取消立刻关闭；
 * 月分组可折叠（默认收起，同旧库 collapsed）；M4 增按日筛选与导出。
 *
 * 版式按 v2 原型「历史记录」屏：返回圆钮 + 页标题；筛选卡内时段分段 + 按日胶囊；
 * 月标头用原型 .history-date 浅灰条（点击折叠）；条目行三列（日期时间 | 数值 | 增减）。
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onIntent: (HistoryIntent) -> Unit,
    onBack: () -> Unit = {},
    onOpenReport: (Long) -> Unit = {},
    onOpenCompare: () -> Unit = {},
    onOpenDetail: (Long) -> Unit = onOpenReport,
) {
    val context = LocalContext.current
    var showDayPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(
                title = "历史记录",
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
            QkButton(
                text = "对比",
                style = QkButtonStyle.Ghost,
                onClick = onOpenCompare,
                modifier = Modifier.padding(end = 4.dp),
            )
            QkButton(
                text = "导出",
                style = QkButtonStyle.Ghost,
                onClick = {
                    val text = buildExportText(state)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(share, "导出测量记录"))
                },
            )
        }

        // 筛选卡：时段分段（应用特有）+ 按日筛选入口
        QkCard {
            QkSegmented(
                items = TrendSegment.entries.map { segmentLabel(it) },
                selectedIndex = state.segment.ordinal,
                onSelect = { onIntent(HistoryIntent.SegmentChanged(TrendSegment.entries[it])) },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QkChip("按日筛选", state.selectedDay != null) { showDayPicker = true }
                if (state.selectedDay != null) {
                    Text(
                        state.selectedDay,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Primary,
                    )
                    Text(
                        "回到月视图",
                        modifier = Modifier.clickable { onIntent(HistoryIntent.ClearDay) }.padding(4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Primary,
                    )
                }
            }
        }

        state.emptyText?.let { QkEmptyHint(it, modifier = Modifier.padding(top = QkSpacing.Md)) }
        state.error?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Danger,
            )
        }

        if (state.selectedDay != null) {
            // 按日视图：原型 .history-date 头 + 条目行
            state.dayHeader?.let {
                QkDateChip(it, modifier = Modifier.padding(top = QkSpacing.Md))
            }
            QkCard(modifier = Modifier.padding(top = QkSpacing.Sm)) {
                state.dayRows.forEachIndexed { index, row ->
                    if (index > 0) QkDivider()
                    HistoryLine(row = row, onOpenDetail = onOpenDetail, onDelete = { onIntent(HistoryIntent.AskDelete(row.time)) })
                }
            }
        } else {
            state.months.forEach { month ->
                QkDateChip(
                    text = month.titleText,
                    modifier = Modifier
                        .padding(top = QkSpacing.Md)
                        .clickable { onIntent(HistoryIntent.ToggleMonth(month.month)) },
                )
                if (month.expanded) {
                    QkCard(modifier = Modifier.padding(top = QkSpacing.Sm)) {
                        month.rows.forEachIndexed { index, row ->
                            if (index > 0) QkDivider()
                            HistoryLine(row = row, onOpenDetail = onOpenDetail, onDelete = { onIntent(HistoryIntent.AskDelete(row.time)) })
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

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

/**
 * 单行渲染（原型 .history-item 三列：日期时间 | 数值 | 增减）。
 * 体脂放副行——原型本列只显示当前指标，本应用两个指标都要可见，按主副分层。
 */
@Composable
private fun HistoryLine(row: HistoryRowUi, onOpenDetail: (Long) -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail(row.time) }   // 需求：点击一条先看该次测量详情
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.timeText, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
            Text(
                "体脂 ${row.fatText}",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }
        Text(
            "${row.weightText} kg",
            style = MaterialTheme.typography.titleSmall,
            color = QkColor.Fg,
        )
        Text(
            row.deltaText ?: "—",
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (row.deltaText?.startsWith("-") == true) QkColor.Primary else QkColor.Muted,
        )
        TextButton(onClick = onDelete) {
            Text(
                "删除",
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Danger,
            )
        }
    }
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
