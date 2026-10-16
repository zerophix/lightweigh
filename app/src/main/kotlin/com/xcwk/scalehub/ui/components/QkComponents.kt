package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * Qk 基础组件库（DEC-021 支柱 5，task B 首批 5 个）。
 * 页面只声明使用哪个组件，不各自拼装视觉——组件坏了修一处。
 */

/** 可选中胶囊（范围/时段筛选等场景）。 */
@Composable
fun QkChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(if (selected) 2.dp else 1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(end = QkSpacing.Sm),
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 卡片容器：阴影 + 16 圆角 + 白底（旧库 qingkeCard 等价物，QingkeTheme.kt:25-29）。 */
@Composable
fun QkCard(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.06f))
            .clip(RoundedCornerShape(16.dp))
            .background(QkColor.Card)
            .padding(padding.dp),
    ) { content() }
}

/** 区块标题 + 可选日期副标（趋势块头等场景）。 */
@Composable
fun QkSectionTitle(title: String, caption: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (caption != null) {
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 三格统计行：最高 / 最低 / 变化（值与说明由调用方格式化完毕）。 */
@Composable
fun QkStatRow(high: String, highCaption: String, low: String, lowCaption: String, change: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm)) {
        StatCell(high, highCaption, Modifier.weight(1f))
        StatCell(low, lowCaption, Modifier.weight(1f))
        StatCell(change, "变化", Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 空态提示（旧库 TrendPages 空文案样式：muted 正文）。 */
@Composable
fun QkEmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = QkSpacing.Md),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}
