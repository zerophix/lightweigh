package com.xcwk.scalehub.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkColor

/**
 * member-sheet — 首页成员快捷切换弹层（DEC-043-J，参考 Members.kt:72）。
 * 底部弹层，点昵称弹出，快速切换测量用户。
 */
@Composable
fun MemberSheet(
    members: List<String>,
    currentMember: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onManage: () -> Unit = {},
) {
    QkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "切换测量用户",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
            QkButton(
                text = "管理",
                style = QkButtonStyle.Ghost,
                onClick = { onDismiss(); onManage() },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        members.forEach { member ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(member); onDismiss() }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    member,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (member == currentMember) QkColor.Primary else QkColor.Fg,
                )
                if (member == currentMember) {
                    Text("✓", style = MaterialTheme.typography.bodyMedium, color = QkColor.Primary)
                }
            }
        }
    }
}

/**
 * offline-banner — 离线横幅（DEC-043-J，参考 HomeScreen.kt:131-138）。
 */
@Composable
fun OfflineBanner(isOnline: Boolean) {
    if (!isOnline) {
        Text(
            "当前离线，数据将在联网后同步",
            modifier = Modifier
                .fillMaxWidth()
                .background(QkColor.Accent.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Accent,
        )
    }
}