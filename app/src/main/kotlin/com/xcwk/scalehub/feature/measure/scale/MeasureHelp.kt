package com.xcwk.scalehub.feature.measure.scale

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkColor

/**
 * measure-help — 测量页帮助弹窗（DEC-043-K，参考 MeasureActivity.kt:1098-1130）。
 * 包含：正确测量姿势说明 + "跳过仅体重提示"勾选。
 */
@Composable
fun MeasureHelpCard(
    skipWeightOnlyPrompt: Boolean,
    onSkipWeightOnlyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    QkCard {
        Text("正确测量姿势", style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
        Spacer(modifier = Modifier.height(12.dp))
        listOf(
            "1. 光脚站上秤，双脚覆盖所有电极片",
            "2. 保持身体静止，等待数值稳定",
            "3. 双臂自然下垂，不要接触身体",
            "4. 测量前避免剧烈运动或淋浴",
        ).forEach { tip ->
            Text(
                tip,
                modifier = Modifier.padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "跳过仅体重提示",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = QkColor.Fg,
            )
            Switch(
                checked = skipWeightOnlyPrompt,
                onCheckedChange = onSkipWeightOnlyChange,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        QkButton(
            text = "知道了",
            modifier = Modifier.fillMaxWidth(),
            onClick = onDismiss,
        )
    }
}

/**
 * measure-member-switch — 测量中切换用户（DEC-043-K，参考 MeasureActivity.kt:1015-1032）。
 */
@Composable
fun MeasureMemberSwitch(
    currentMember: String,
    members: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    QkCard {
        Text("切换测量用户", style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
        Spacer(modifier = Modifier.height(12.dp))
        members.forEach { member ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    member,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (member == currentMember) QkColor.Primary else QkColor.Fg,
                )
                if (member == currentMember) {
                    Text("当前", style = MaterialTheme.typography.labelSmall, color = QkColor.Primary)
                } else {
                    QkButton(
                        text = "切换",
                        style = QkButtonStyle.Secondary,
                        onClick = { onSelect(member); onDismiss() },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        QkButton(
            text = "取消",
            style = QkButtonStyle.Ghost,
            modifier = Modifier.fillMaxWidth(),
            onClick = onDismiss,
        )
    }
}