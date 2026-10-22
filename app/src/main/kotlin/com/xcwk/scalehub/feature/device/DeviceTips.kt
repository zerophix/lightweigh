package com.xcwk.scalehub.feature.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkColor

/**
 * device-tips — 设备页测量小贴士卡（DEC-043-L，参考 Pages.kt:175-190）。
 */
@Composable
fun DeviceTipsCard() {
    QkCard {
        Text("正确测量小贴士", style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
        Spacer(modifier = Modifier.height(8.dp))
        listOf(
            "光脚站上秤，双脚覆盖所有电极片",
            "测量前避免剧烈运动或淋浴",
            "保持身体静止，等待数值稳定",
            "每次测量尽量在同一时间",
        ).forEach { tip ->
            Text(
                "· $tip",
                modifier = Modifier.padding(vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }
    }
}

/**
 * device-recent — 最近测量时间 + 开始称重快捷按钮（DEC-043-L，参考 Pages.kt:129-190）。
 */
@Composable
fun DeviceRecentCard(
    recentTimeText: String?,
    onStartMeasure: () -> Unit,
) {
    QkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("最近测量", style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
                Text(
                    recentTimeText ?: "暂无记录",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = QkColor.Fg,
                )
            }
            QkButton(
                text = "开始称重",
                onClick = onStartMeasure,
            )
        }
    }
}