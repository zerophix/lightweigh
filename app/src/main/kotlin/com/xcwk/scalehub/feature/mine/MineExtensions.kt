package com.xcwk.scalehub.feature.mine

import androidx.compose.foundation.layout.Column
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
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * profile-extra — 我的资料扩展字段（DEC-043-M，参考 ProfilePages.kt:422-445）。
 * 个人简介 / 所在地 / 展示标签开关。
 */
@Composable
fun ProfileExtraCard(
    bio: String = "",
    location: String = "",
    showTag: Boolean = false,
    onBioChange: (String) -> Unit = {},
    onLocationChange: (String) -> Unit = {},
    onShowTagChange: (Boolean) -> Unit = {},
) {
    QkSectionHead(title = "更多资料", modifier = Modifier.padding(top = QkSpacing.Lg))
    QkCard(modifier = Modifier.padding(top = 2.dp)) {
        Text("个人简介", style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        androidx.compose.material3.OutlinedTextField(
            value = bio,
            onValueChange = onBioChange,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            placeholder = { Text("介绍一下自己", color = QkColor.Hint) },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("所在地", style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        androidx.compose.material3.OutlinedTextField(
            value = location,
            onValueChange = onLocationChange,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            placeholder = { Text("城市", color = QkColor.Hint) },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("展示标签", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = QkColor.Fg)
            Switch(checked = showTag, onCheckedChange = onShowTagChange)
        }
    }
}

/**
 * archive-waist — 身体围度（腰围/臀围）展示（DEC-043-N，参考 ProfilePages.kt:104,221）。
 */
@Composable
fun ArchiveWaistCard(
    waistCm: Double? = null,
    hipCm: Double? = null,
) {
    QkSectionHead(title = "身体围度", modifier = Modifier.padding(top = QkSpacing.Lg))
    QkCard(modifier = Modifier.padding(top = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WaistCell("腰围", waistCm, Modifier.weight(1f))
            WaistCell("臀围", hipCm, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WaistCell(name: String, value: Double?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
        Text(
            value?.let { "%.1f cm".format(it) } ?: "--",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            color = QkColor.Fg,
        )
    }
}

/**
 * archive-goal-progress — 目标进度条（DEC-043-N，参考 ProfilePages.kt:97 ArchiveScreen）。
 */
@Composable
fun ArchiveGoalProgress(
    currentWeight: Double? = null,
    targetWeight: Double? = null,
) {
    if (currentWeight == null || targetWeight == null || targetWeight <= 0) return
    QkSectionHead(title = "目标进度", modifier = Modifier.padding(top = QkSpacing.Lg))
    QkCard(modifier = Modifier.padding(top = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("当前", style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
                Text(
                    "%.1f 公斤".format(currentWeight),
                    style = MaterialTheme.typography.titleMedium,
                    color = QkColor.Fg,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("目标", style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
                Text(
                    "%.1f 公斤".format(targetWeight),
                    style = MaterialTheme.typography.titleMedium,
                    color = QkColor.Primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("差距", style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
                Text(
                    "%.1f 公斤".format(currentWeight - targetWeight),
                    style = MaterialTheme.typography.titleMedium,
                    color = QkColor.Accent,
                )
            }
        }
    }
}