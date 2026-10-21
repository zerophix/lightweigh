package com.xcwk.scalehub.ui.debug

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcwk.scalehub.BuildConfig
import com.xcwk.scalehub.application.ble.BleDebugLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * 蓝牙调试悬浮球 + 日志面板（仅 debug 构建，随 [BuildConfig.DEBUG] 门控）。
 *
 * - 数据源 [BleDebugLog]：2 万行环形缓冲，AndroidBleLink/MeasureSession 双写入。
 * - 球可拖动、松手吸附左右边缘；点击展开全屏面板。
 * - 面板：LazyColumn 滚动 + 一键复制 + 清空 + 跟随底部（用户上滑暂停跟随）。
 */
@Composable
fun DebugOverlay() {
    if (!BuildConfig.DEBUG) return
    var panelOpen by remember { mutableStateOf(false) }
    val x = remember { mutableFloatStateOf(Float.NaN) }
    val y = remember { mutableFloatStateOf(Float.NaN) }

    Box(Modifier.fillMaxSize()) {
        if (!panelOpen) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val ballPx = with(density) { 48.dp.toPx() }
                val marginPx = with(density) { 12.dp.toPx() }
                val maxW = constraints.maxWidth.toFloat().coerceAtLeast(ballPx)
                val maxH = constraints.maxHeight.toFloat().coerceAtLeast(ballPx)
                if (x.floatValue.isNaN()) {
                    x.floatValue = maxW - ballPx - marginPx
                    y.floatValue = maxH * 0.4f
                }
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(x.floatValue.roundToInt(), y.floatValue.roundToInt()) }
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC159B72))
                        .pointerInput(maxW, maxH) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    x.floatValue = (x.floatValue + dragAmount.x).coerceIn(0f, maxW - ballPx)
                                    y.floatValue = (y.floatValue + dragAmount.y).coerceIn(0f, maxH - ballPx)
                                },
                                onDragEnd = {
                                    val half = (maxW - ballPx) / 2f
                                    x.floatValue = if (x.floatValue < half) marginPx else (maxW - ballPx - marginPx)
                                },
                            )
                        }
                        .clickable { panelOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.BugReport, contentDescription = "调试日志", tint = Color.White)
                }
            }
        }
        if (panelOpen) {
            Box(Modifier.fillMaxSize()) {
                // 点击面板外的遮罩区域关闭
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000))
                        .clickable { panelOpen = false },
                )
                DebugPanel(
                    onClose = { panelOpen = false },
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.62f)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(Color(0xF20B1F1A))
                        .padding(8.dp),
                )
            }
        }
    }
}

/** 底部小窗日志面板：轮询快照渲染，支持复制/清空/自动贴底。 */
@Composable
private fun DebugPanel(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val lines by produceState(initialValue = emptyList<String>()) {
        while (true) {
            value = BleDebugLog.snapshot()
            delay(400)
        }
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "BLE 调试日志 ${lines.size}/${BleDebugLog.MAX_LINES}",
                color = Color(0xFFB7F0D9),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            PanelAction(Icons.Outlined.ContentCopy, "复制") {
                try {
                    clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                    Toast.makeText(context, "已复制 ${lines.size} 行", Toast.LENGTH_SHORT).show()
                } catch (ex: Exception) {
                    Toast.makeText(context, "复制失败: ${ex.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
                }
            }
            PanelAction(Icons.Outlined.DeleteSweep, "清空") { BleDebugLog.clear() }
            PanelAction(Icons.Outlined.Close, "关闭", onClick = onClose)
        }
        val listState = rememberLazyListState()
        var follow by remember { mutableStateOf(true) }
        LaunchedEffect(listState) {
            snapshotFlow {
                val info = listState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= info.totalItemsCount - 2
            }.collectLatest { follow = it }
        }
        LaunchedEffect(lines.size) {
            if (follow && lines.isNotEmpty()) {
                listState.scrollToItem(lines.size - 1)
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 6.dp),
            state = listState,
        ) {
            itemsIndexed(lines) { _, line ->
                Text(
                    line,
                    color = Color(0xFF9FE8C8),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun PanelAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .padding(start = 10.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x33159B72))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFFB7F0D9), modifier = Modifier.size(16.dp))
        Text(label, color = Color(0xFFB7F0D9), fontSize = 9.sp)
    }
}
