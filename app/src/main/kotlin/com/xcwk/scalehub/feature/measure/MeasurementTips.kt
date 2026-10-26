package com.xcwk.scalehub.feature.measure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 测量小贴士（T-052）。
 *
 * 文案是**单一真源**：设备页的贴士卡（[com.xcwk.scalehub.feature.device.DeviceTipsCard]）
 * 与首页轮播都读这一份，避免两处各自改写后不一致。
 */
val MEASUREMENT_TIPS: List<String> = listOf(
    "光脚站上秤，双脚覆盖所有电极片",
    "测量前避免剧烈运动或淋浴",
    "保持身体静止，等待数值稳定",
    "每次测量尽量在同一时间",
)

/**
 * 首页贴士轮播：横向可滑，一条一卡。
 * 不自动轮播——自动切换会在用户阅读时跳走，属于反模式。
 */
@Composable
fun MeasurementTipsCarousel(
    modifier: Modifier = Modifier,
    onOpenHelp: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "正确测量小贴士",
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
            onOpenHelp?.let {
                QkButton(text = "测量帮助", style = QkButtonStyle.Ghost, onClick = it)
            }
        }
        LazyRow(
            modifier = Modifier.padding(top = QkSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
        ) {
            itemsIndexed(MEASUREMENT_TIPS) { index, tip ->
                QkCard(modifier = Modifier.width(220.dp)) {
                    Text(
                        text = "${index + 1}/${MEASUREMENT_TIPS.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = QkColor.Muted,
                    )
                    Text(
                        text = tip,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Fg,
                    )
                }
            }
        }
    }
}

@Preview(name = "测量贴士轮播", showBackground = true, widthDp = 393)
@Composable
private fun MeasurementTipsCarouselPreview() {
    QkTheme {
        MeasurementTipsCarousel(modifier = Modifier.padding(QkSpacing.Page))
    }
}
