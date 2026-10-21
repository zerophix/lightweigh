package com.xcwk.scalehub.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * guide — 新手引导三步（DEC-043-F，参考 GuideScreen.kt:64-130）。
 * 资料 → 单位 → 绑秤。
 */
@Composable
fun GuideScreen(
    onDone: () -> Unit = {},
    onSkip: () -> Unit = {},
) {
    var step by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page),
    ) {
        // 进度指示
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg, bottom = QkSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= step) QkColor.Primary else QkColor.Border),
                )
            }
        }

        when (step) {
            0 -> GuideStep(
                title = "完善个人资料",
                body = "填写性别、出生日期、身高，AI 才能更准确地分析你的身体数据。",
                action = "下一步",
                onClick = { step = 1 },
            )
            1 -> GuideStep(
                title = "设置单位",
                body = "选择你习惯的重量和长度单位，可在设置中随时修改。",
                action = "下一步",
                onClick = { step = 2 },
            )
            2 -> GuideStep(
                title = "绑定体脂秤",
                body = "连接蓝牙体脂秤，开始测量体重、体脂率等身体数据。",
                action = "开始使用",
                onClick = onDone,
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        QkButton(
            text = "跳过引导",
            style = QkButtonStyle.Ghost,
            modifier = Modifier.fillMaxWidth().padding(bottom = QkSpacing.Lg),
            onClick = onSkip,
        )
    }
}

@Composable
private fun GuideStep(
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
) {
    QkCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Lg)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = QkColor.Fg)
            Text(
                body,
                modifier = Modifier.padding(top = QkSpacing.Md),
                style = MaterialTheme.typography.bodyMedium,
                color = QkColor.Muted,
            )
            Spacer(modifier = Modifier.height(QkSpacing.Lg))
            QkButton(text = action, modifier = Modifier.fillMaxWidth(), onClick = onClick)
        }
    }
}