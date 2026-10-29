@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xcwk.scalehub.feature.history

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkDateChip
import com.xcwk.scalehub.ui.components.QkDivider
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPageTitle
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
 * 版式按原型 v-history：返回圆钮 + 页标题 "全部记录"；右上角 "筛选" 打开成员筛选底部弹层；
 * 列表项：时间 | 体脂% · BMI | 体重 + 右箭头；长按删除；底部 "加载更多"。
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
    var showDayPicker by rememberSaveable { mutableStateOf(false) }
    var showMemberFilter by rememberSaveable { mutableStateOf(false) }

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
                title = "全部记录",
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
            Text(
                text = "筛选",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showMemberFilter = true }
                    .padding(end = QkSpacing.Md),
                style = MaterialTheme.typography.bodyMedium,
                color = QkColor.Primary,
                textAlign = TextAlign.End,
            )
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

        // 加载更多
        Text(
            text = "加载更多",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = QkSpacing.Lg)
                .clickable { /* TODO: load more */ },
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Primary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(28.dp))
    }

    // 成员筛选底部弹层（原型 v-history sheet-member）
    if (showMemberFilter) {
        androidx.compose.material3.BottomSheetScaffold(
            sheetContent = {
                Column(modifier = Modifier.padding(QkSpacing.Lg)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("按成员筛选", style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
                        Text(
                            "取消",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMemberFilter = false }
                                .padding(QkSpacing.Md),
                            style = MaterialTheme.typography.bodyMedium,
                            color = QkColor.Primary,
                            textAlign = TextAlign.End,
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(QkSpacing.Md))
                    Column {
                        // 全部成员
                        MemberFilterRow("全部成员", true) { showMemberFilter = false }
                        // 林悦（本人）
                        MemberFilterRow("林悦（本人）", false) { showMemberFilter = false }
                        // 妈妈
                        MemberFilterRow("妈妈", false) { showMemberFilter = false }
                        // 爸爸
                        MemberFilterRow("爸爸", false) { showMemberFilter = false }
                    }
                }
            },
            sheetPeekHeight = 0.dp,
            sheetShape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = QkColor.Card,
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize())
        }
    }

    // 删除前确认：取消立刻关闭，确认后执行（需求文档历史记录节）
    state.confirmDeleteTime?.let { time ->
        AlertDialog(
            onDismissRequest = { onIntent(HistoryIntent.CancelDelete) },
            title = { Text("删除这条记录？") },
            text = { Text("删除后本地与云端同步移除，7 天内可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = { onIntent(HistoryIntent.ConfirmDelete) },
                    enabled = !state.deleting,
                ) { Text(if (state.deleting) "删除中…" else "删除") }
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

@Composable
private fun MemberFilterRow(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = QkSpacing.Md, horizontal = QkSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = QkColor.Fg)
        if (isSelected) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Check,
                contentDescription = "已选中",
                tint = QkColor.Primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 单行渲染（原型 v-history：时间 | 体脂% · BMI | 体重 + 右箭头）。
 * 长按删除——由 QkLongPressMenu 或父层手势处理。
 */
@Composable
private fun HistoryLine(row: HistoryRowUi, onOpenDetail: (Long) -> Unit, onDelete: () -> Unit) {
    com.xcwk.scalehub.ui.components.QkListRow(
        title = row.timeText,
        subtitle = "体脂 ${row.fatText} · BMI ${row.bmiText ?: "—"}",
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${row.weightText} kg",
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Fg,
                )
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.ChevronRight,
                    contentDescription = "详情",
                    tint = QkColor.Muted,
                    modifier = Modifier.padding(start = QkSpacing.Sm).size(20.dp),
                )
            }
        },
        onClick = { onOpenDetail(row.time) },
    )
    // 长按删除菜单（简化：这里用点击删除按钮触发，实际应用中可接入 QkLongPressMenu）
    // onDelete 由父层在长按或编辑模式下调用
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
                            HistoryRowUi(0, "10-15 08:00", "72.0", "19.1%", "25.1", "-1.5"),
                            HistoryRowUi(0, "10-01 08:00", "73.5", "20.1%", "25.7", null),
                        ),
                    ),
                    HistoryMonthUi("2026-09", "2026年9月: 共增重2.0公斤  减脂--", false, emptyList()),
                ),
            ),
            onIntent = {},
        )
    }
}
