package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * 长按菜单与长文展开。对齐原型 09-components-demo.html `v-longpress`
 * （轻衡 · 交互组件演示 · 弹层）。
 *
 * 菜单是墨色条：复制、分享、全选。选区用品牌浅底高亮。
 * 长文在「展开 / 收起」之间切换，不另写一段原型里没有的句子。
 */

enum class QkLongPressAction(val label: String) {
    Copy("复制"),
    Share("分享"),
    SelectAll("全选"),
}

const val QkLongPressTitle = "长按 / 复制 / 展开"
const val QkLongPressMenuLabel = "长按弹出菜单"
const val QkLongPressHint = "长按选中后弹出黑色菜单条；选区高亮为品牌色"
const val QkLongPressExpandLabel = "长文展开 / 收起"
const val QkLongPressBack = "返回组件演示"
const val QkLongPressBody =
    "体脂率是脂肪重量占体重的比例，比单看体重更能反映身体成分。"
const val QkLongPressHighlight = "体脂率"
const val QkLongPressCollapsed =
    "林悦，近 30 天体重下降 1.6 kg、体脂率下降 1.5 个百分点，趋势稳健。单次波动不代表脂肪变化，建议持续关注 7 天平均值。保持均衡饮食，按自身情况安排活动…"
const val QkLongPressExpanded =
    "林悦，近 30 天体重下降 1.6 kg、体脂率下降 1.5 个百分点，趋势稳健。单次波动不代表脂肪变化，建议持续关注 7 天平均值。保持均衡饮食，按自身情况安排活动。"

fun longPressToggleLabel(expanded: Boolean): String = if (expanded) "收起" else "展开"

@Composable
fun QkLongPressMenu(
    visible: Boolean,
    onAction: (QkLongPressAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(QkRadius.SegmentItem))
            .background(QkColor.Fg)
            .padding(horizontal = QkSpacing.Xs, vertical = QkSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkLongPressAction.entries.forEachIndexed { index, action ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(horizontal = QkSpacing.Xs)
                        .width(1.dp)
                        .height(QkSpacing.Md)
                        .background(Color.White.copy(alpha = 0.2f)),
                )
            }
            Text(
                action.label,
                modifier = Modifier
                    .clickable { onAction(action) }
                    .padding(horizontal = QkSpacing.Sm, vertical = QkSpacing.Xs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QkLongPressSelectable(
    text: String,
    onAction: (QkLongPressAction) -> Unit,
    modifier: Modifier = Modifier,
    highlight: String = QkLongPressHighlight,
    menuVisible: Boolean = false,
    onLongPress: () -> Unit = {},
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
        QkLongPressMenu(visible = menuVisible, onAction = onAction)
        val styled = buildAnnotatedString {
            val start = if (menuVisible) text.indexOf(highlight) else -1
            if (start < 0) {
                append(text)
            } else {
                append(text.substring(0, start))
                withStyle(SpanStyle(background = QkColor.Mint, color = QkColor.Fg)) {
                    append(highlight)
                }
                append(text.substring(start + highlight.length))
            }
        }
        Text(
            styled,
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
            color = QkColor.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun QkExpandableText(
    collapsed: String,
    expanded: String,
    expandedNow: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val body = if (expandedNow) expanded else collapsed
    val action = longPressToggleLabel(expandedNow)
    Text(
        buildAnnotatedString {
            append(body)
            withStyle(SpanStyle(color = QkColor.Primary)) { append(action) }
        },
        modifier = modifier.clickable(onClick = onToggle),
        color = QkColor.Muted,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** 原型 v-longpress。菜单默认展开，与原型画在一起的那一帧一致。 */
@Composable
fun QkLongPressDemo(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onAction: (QkLongPressAction) -> Unit = {},
) {
    var menu by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(QkSpacing.Md),
    ) {
        QkPageTitle(QkLongPressTitle)
        Text(QkLongPressMenuLabel, color = QkColor.FieldLabel, style = MaterialTheme.typography.labelMedium)
        QkCard {
            QkLongPressSelectable(
                text = QkLongPressBody,
                menuVisible = menu,
                onLongPress = { menu = true },
                onAction = {
                    onAction(it)
                    if (it != QkLongPressAction.SelectAll) menu = false
                },
            )
        }
        Text(QkLongPressHint, color = QkColor.Muted, style = MaterialTheme.typography.bodySmall)
        Text(QkLongPressExpandLabel, color = QkColor.FieldLabel, style = MaterialTheme.typography.labelMedium)
        QkCard {
            QkExpandableText(
                collapsed = QkLongPressCollapsed,
                expanded = QkLongPressExpanded,
                expandedNow = expanded,
                onToggle = { expanded = !expanded },
            )
        }
        QkButton(text = QkLongPressBack, modifier = Modifier.fillMaxWidth(), onClick = onBack)
    }
}

@Preview
@Composable
private fun QkLongPressPreview() {
    QkTheme { QkLongPressDemo() }
}
