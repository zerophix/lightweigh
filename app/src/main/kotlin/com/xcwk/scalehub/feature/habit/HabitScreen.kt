package com.xcwk.scalehub.feature.habit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.domain.model.HabitItem
import com.xcwk.scalehub.domain.model.HabitPeriod
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * habit — 习惯管理页（DEC-043-D）。
 * 按日查看打卡列表 + 习惯编辑器（名称/周期/次数/提醒）。
 *
 * UI 层只消费 [HabitItem] 与 [HabitIntent]，不直接依赖仓储/领域实体。
 */
@Composable
fun HabitScreen(
    onBack: () -> Unit = {},
    habits: List<HabitItem> = emptyList(),
    state: HabitUiState = HabitUiState(),
    onIntent: (HabitIntent) -> Unit = {},
) {
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
            QkPageTitle(title = "习惯管理", modifier = Modifier.weight(1f).padding(start = QkSpacing.Md))
            QkButton(
                text = "添加",
                style = QkButtonStyle.Secondary,
                onClick = { onIntent(HabitIntent.OpenEditor(null)) },
            )
        }

        if (habits.isEmpty()) {
            QkCard { QkEmptyHint("还没有习惯，点击右上角添加") }
        } else {
            QkSectionHead(title = "今日打卡", modifier = Modifier.padding(top = QkSpacing.Lg))
            habits.forEachIndexed { i, habit ->
                QkCard(modifier = Modifier.padding(top = if (i == 0) 2.dp else 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(habit.name, style = MaterialTheme.typography.bodyMedium, color = QkColor.Fg)
                            Text(
                                "${habit.period.label} · ${habit.completedCount}/${habit.targetCount} 次",
                                style = MaterialTheme.typography.bodySmall,
                                color = QkColor.Muted,
                            )
                        }
                        Switch(
                            checked = habit.isDone,
                            onCheckedChange = { onIntent(HabitIntent.ToggleDone(habit.id)) },
                        )
                    }
                }
            }
        }

        if (state.showEditor) {
            val editing = state.editingId?.let { id -> habits.find { it.id == id } }
            HabitEditor(
                initial = editing,
                onSave = { name, period, target, reminder, hour, minute ->
                    onIntent(HabitIntent.Save(
                        id = editing?.id,
                        name = name,
                        period = period.label,
                        targetCount = target,
                        reminderEnabled = reminder,
                        reminderHour = hour,
                        reminderMinute = minute,
                    ))
                },
                onCancel = { onIntent(HabitIntent.CloseEditor) },
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

/**
 * 习惯编辑器（新增/编辑）。
 * 字段约束于 [HabitItem] / [HabitPeriod] 一致。
 */
@Composable
private fun HabitEditor(
    initial: HabitItem?,
    onSave: (name: String, period: HabitPeriod, targetCount: Int, reminderEnabled: Boolean, reminderHour: Int, reminderMinute: Int) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var period by remember { mutableStateOf(initial?.period ?: HabitPeriod.DAILY) }
    var target by remember { mutableStateOf((initial?.targetCount ?: 1).toString()) }
    var reminder by remember { mutableStateOf(initial?.reminderEnabled ?: false) }
    var reminderHour by remember { mutableStateOf((initial?.reminderHour ?: 8).toString()) }
    var reminderMinute by remember { mutableStateOf((initial?.reminderMinute ?: 0).toString()) }

    QkSectionHead(title = if (initial != null) "编辑习惯" else "新习惯", modifier = Modifier.padding(top = QkSpacing.Lg))
    QkCard(modifier = Modifier.padding(top = 2.dp)) {
        Text("名称", style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        androidx.compose.material3.OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("周期", style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        Row(modifier = Modifier.padding(top = 4.dp)) {
            listOf(HabitPeriod.DAILY, HabitPeriod.WEEKLY, HabitPeriod.MONTHLY).forEach { p ->
                QkButton(
                    text = p.label,
                    style = if (period == p) QkButtonStyle.Primary else QkButtonStyle.Secondary,
                    modifier = Modifier.padding(end = 8.dp),
                    onClick = { period = p },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("目标次数", style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            QkButton(text = "-", style = QkButtonStyle.Secondary, onClick = {
                val v = (target.toIntOrNull() ?: 1) - 1
                target = v.coerceAtLeast(1).toString()
            })
            Text(
                target,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
            QkButton(text = "+", style = QkButtonStyle.Secondary, onClick = {
                val v = (target.toIntOrNull() ?: 1) + 1
                target = v.coerceAtMost(20).toString()
            })
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("提醒", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = QkColor.Fg)
            Switch(checked = reminder, onCheckedChange = { reminder = it })
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            QkButton(text = "取消", style = QkButtonStyle.Ghost, modifier = Modifier.weight(1f), onClick = onCancel)
            Spacer(modifier = Modifier.width(8.dp))
            QkButton(
                text = "保存",
                modifier = Modifier.weight(1f),
                enabled = name.isNotBlank(),
                onClick = {
                    val t = target.toIntOrNull() ?: 1
                    val h = reminderHour.toIntOrNull()?.coerceIn(0, 23) ?: 8
                    val m = reminderMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    onSave(name, period, t, reminder, h, m)
                },
            )
        }
    }
}
