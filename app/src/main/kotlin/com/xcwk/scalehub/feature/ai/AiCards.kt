package com.xcwk.scalehub.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkColor

// ---- AI 结构化卡片（DEC-043-B，参考 AiChat.kt:341-410）----

/** 饮食卡（AiChat.kt:410+ DietCard）。 */
data class DietCard(
    val date: String,
    val meals: List<DietMeal>,
)

data class DietMeal(
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

/** 运动卡。 */
data class ExerciseCard(
    val name: String,
    val duration: Int,     // 分钟
    val calories: Int,
    val heartRate: Int? = null,
)

/** 指标解读卡。 */
data class MetricCard(
    val metricName: String,
    val value: String,
    val level: String,      // 偏低/标准/偏高
    val advice: String,
    val tone: Int,          // 0低 1标准 2高
)

/**
 * ai-diet-card — 结构化饮食卡（DEC-043-B）。
 * 日期切换 + 餐次列表 + 编辑/删除/保存按钮。
 */
@Composable
fun AiDietCard(
    card: DietCard,
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {},
) {
    QkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "饮食记录 ${card.date}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        card.meals.forEach { meal ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(meal.name, style = MaterialTheme.typography.bodyMedium, color = QkColor.Fg)
                    Text(
                        "蛋白质 ${meal.protein}g · 碳水 ${meal.carbs}g · 脂肪 ${meal.fat}g",
                        style = MaterialTheme.typography.labelSmall,
                        color = QkColor.Muted,
                    )
                }
                Text(
                    "${meal.calories} 千卡",
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Accent,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            QkButton(text = "编辑", style = QkButtonStyle.Secondary, modifier = Modifier.weight(1f), onClick = onEdit)
            Spacer(modifier = Modifier.width(8.dp))
            QkButton(text = "删除", style = QkButtonStyle.Ghost, modifier = Modifier.weight(1f), onClick = onDelete)
            Spacer(modifier = Modifier.width(8.dp))
            QkButton(text = "保存", modifier = Modifier.weight(1f), onClick = onSave)
        }
    }
}

/**
 * ai-exercise-card — 运动卡（DEC-043-B）。
 */
@Composable
fun AiExerciseCard(card: ExerciseCard) {
    QkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                Text(
                    "${card.duration} 分钟 · ${card.calories} 千卡",
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
            card.heartRate?.let {
                Text(
                    "心率 ${it} bpm",
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Accent,
                )
            }
        }
    }
}

/**
 * ai-metric-card — 指标解读卡（DEC-043-B，参考 AiChat.kt:161-180）。
 */
@Composable
fun AiMetricCard(card: MetricCard) {
    val toneColor = when (card.tone) {
        0 -> QkColor.Muted
        1 -> QkColor.Primary
        else -> QkColor.Accent
    }
    QkCard {
        Text(card.metricName, style = MaterialTheme.typography.labelSmall, color = QkColor.Muted)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                card.value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = toneColor,
            )
            Text(card.level, style = MaterialTheme.typography.bodySmall, color = toneColor)
        }
        Text(
            card.advice,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
    }
}

/**
 * ai-welcome — AI 欢迎块 + 示例问题（DEC-043-B，参考 AiChat.kt:341-362）。
 */
@Composable
fun AiWelcome(
    onExampleClick: (String) -> Unit = {},
) {
    QkCard {
        Text("你好，我是你的健康助理", style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
        Text(
            "我可以帮你分析身体数据、解答健康问题、记录饮食和运动。",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("试试这些问题：", style = MaterialTheme.typography.labelSmall, color = QkColor.Hint)
        listOf(
            "我今天的体脂率正常吗？",
            "帮我制定一个减脂计划",
            "早餐吃什么有助于减肥？",
            "如何提高基础代谢？",
        ).forEach { q ->
            Text(
                "· $q",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(QkColor.Mint)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Primary,
            )
        }
    }
}