package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 滚轮选择器（旧库 Wheel :630-662 移植，M4 视觉轮）：
 * 44dp 行高 ×5 行视窗，滚动停稳后按中心行回调索引；选中行用主题 token 高亮。
 */
@Composable
fun QkWheelPicker(
    labels: List<String>,
    initialIndex: Int,
    modifier: Modifier = Modifier,
    onIndex: (Int) -> Unit,
) {
    val itemHeight = 44.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemPx = with(density) { itemHeight.roundToPx() }.coerceAtLeast(1)
    val scroll = remember(labels.size) {
        androidx.compose.foundation.ScrollState(
            initial = initialIndex.coerceIn(0, (labels.size - 1).coerceAtLeast(0)) * itemPx,
        )
    }
    val current = ((scroll.value + itemPx / 2) / itemPx).coerceIn(0, labels.lastIndex.coerceAtLeast(0))
    LaunchedEffect(scroll, labels.size) {
        snapshotFlow { scroll.value }.collect { value ->
            onIndex(((value + itemPx / 2) / itemPx).coerceIn(0, labels.lastIndex.coerceAtLeast(0)))
        }
    }
    Box(modifier.height(itemHeight * 5).clipToBounds()) {
        Box(
            Modifier.align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(scroll)
                .padding(vertical = itemHeight * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == current
                Box(Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = if (selected) 20.sp else 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
