package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme
import kotlin.math.roundToInt

/**
 * 左滑删除。对齐原型 26-final-gaps.html `v-swipe-delete`。
 *
 * 露出的是红色「删除」。点删除先问「删除这条记录？」——
 * 删除后本地与云端同步移除，7 天内可恢复。取消只收起，不删数据。
 */

const val QkSwipeDeleteTitle = "删除这条记录？"
const val QkSwipeDeleteBody = "删除后本地与云端同步移除，7 天内可恢复。"
const val QkSwipeDeleteDone = "已删除 · 撤销"

fun clampSwipeOffset(offsetPx: Float, revealPx: Float): Float {
    if (revealPx <= 0f) return 0f
    return offsetPx.coerceIn(-revealPx, 0f)
}

/** 越过一半宽度就保持露出，否则收回。 */
fun settleSwipeOffset(offsetPx: Float, revealPx: Float): Float {
    if (revealPx <= 0f) return 0f
    return if (offsetPx <= -revealPx / 2f) -revealPx else 0f
}

@Composable
fun QkSwipeDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    startRevealed: Boolean = false,
    confirm: Boolean = true,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var ask by remember { mutableStateOf(false) }
    var primed by remember { mutableStateOf(false) }
    val revealDp = QkSpacing.Xl * 3 + QkSpacing.Sm
    val reveal = with(LocalDensity.current) { revealDp.toPx() }
    LaunchedEffect(startRevealed, reveal) {
        if (startRevealed && !primed) {
            offset = -reveal
            primed = true
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(QkColor.Danger),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                "删除",
                modifier = Modifier
                    .padding(horizontal = QkSpacing.Lg)
                    .clickable {
                        if (confirm) ask = true else onDelete()
                    },
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(clampSwipeOffset(offset, reveal).roundToInt(), 0) }
                .fillMaxWidth()
                .background(QkColor.Card)
                .pointerInput(reveal) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, drag ->
                            offset = clampSwipeOffset(offset + drag, reveal)
                        },
                        onDragEnd = { offset = settleSwipeOffset(offset, reveal) },
                    )
                },
        ) { content() }
    }
    if (ask) {
        QkSwipeDeleteDialog(
            onDismiss = { ask = false },
            onConfirm = {
                ask = false
                offset = 0f
                onDelete()
            },
        )
    }
}

@Composable
fun QkSwipeDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        QkCard {
            Text(QkSwipeDeleteTitle, style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
            Text(
                QkSwipeDeleteBody,
                modifier = Modifier.padding(top = QkSpacing.Sm),
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = QkSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
            ) {
                QkButton(
                    text = "取消",
                    style = QkButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                QkDangerButton(text = "删除", modifier = Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun QkDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(QkRadius.Button)
    Box(
        modifier = modifier
            .background(QkColor.Danger, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = QkSpacing.CardPad, vertical = QkSpacing.CardGap),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** 原型 v-swipe-delete。中间一行初始即为左滑露出删除。 */
@Composable
fun QkSwipeDeleteDemo(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    var removed by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(QkSpacing.CardGap),
    ) {
        QkPageTitle("滑动删除演示")
        QkCard {
            Text(
                "在真实设备上：左滑记录行 → 露出删除按钮。",
                color = QkColor.PrimaryDark,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!removed) {
            QkCard {
                QkListRow(title = "今天 07:30", subtitle = "体脂 28.6%", showDivider = false, trailing = {
                    Text("62.4 kg", color = QkColor.Fg, style = MaterialTheme.typography.titleSmall)
                })
            }
            QkSwipeDeleteRow(startRevealed = true, onDelete = {
                removed = true
                notice = QkSwipeDeleteDone
            }) {
                QkListRow(
                    title = "昨天 07:28",
                    subtitle = "体脂 28.8% · 左滑删除",
                    showDivider = false,
                    trailing = {
                        Text("62.7 kg", color = QkColor.Fg, style = MaterialTheme.typography.titleSmall)
                    },
                )
            }
            QkCard {
                QkListRow(title = "10/19 07:22", subtitle = "体脂 28.9%", showDivider = false, trailing = {
                    Text("62.8 kg", color = QkColor.Fg, style = MaterialTheme.typography.titleSmall)
                })
            }
        }
        notice?.let {
            Text(it, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            QkButton(
                text = "返回",
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = onBack,
            )
            QkDangerButton(
                text = "点击删除（弹窗）",
                modifier = Modifier.weight(1f),
                onClick = { ask = true },
            )
        }
    }
    if (ask) {
        QkSwipeDeleteDialog(
            onDismiss = { ask = false },
            onConfirm = {
                ask = false
                removed = true
                notice = QkSwipeDeleteDone
            },
        )
    }
}

@Preview
@Composable
private fun QkSwipeDeletePreview() {
    QkTheme { QkSwipeDeleteDemo() }
}
