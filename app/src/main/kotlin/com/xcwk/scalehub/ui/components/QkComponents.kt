package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * Qk 组件库 —— 对齐 v2 交互原型的视觉语言（DEC-021 支柱 5，原型版重建）。
 *
 * 纪律不变：页面只声明「用哪个组件」，不各自拼装视觉；组件坏了修一处。
 * 本次新增的是原型里反复出现的六种原语：卡片、按钮三态、胶囊、图标块、列表行、分段控件。
 */

/** 图标块的四种色调（原型 .glyph / .glyph.blue / .purple / .orange）。 */
enum class QkGlyphTone { Green, Blue, Purple, Orange, Gray }

/** 胶囊色调。 */
enum class QkPillTone { Green, Amber, Blue, Gray }

private fun QkGlyphTone.bg(): Color = when (this) {
    QkGlyphTone.Green -> QkColor.Mint
    QkGlyphTone.Blue -> QkColor.BlueSoft
    QkGlyphTone.Purple -> QkColor.PurpleSoft
    QkGlyphTone.Orange -> QkColor.AccentSoft
    QkGlyphTone.Gray -> QkColor.Secondary
}

private fun QkGlyphTone.fg(): Color = when (this) {
    QkGlyphTone.Green -> QkColor.Primary
    QkGlyphTone.Blue -> QkColor.Blue
    QkGlyphTone.Purple -> QkColor.Purple
    QkGlyphTone.Orange -> QkColor.Accent
    QkGlyphTone.Gray -> QkColor.Muted
}

/** 卡片容器：17 圆角 + 1px 细边 + 轻阴影（原型 .card）。 */
@Composable
fun QkCard(
    modifier: Modifier = Modifier,
    padding: Int = 15,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(QkRadius.Card)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(QkColor.Card)
        .border(1.dp, QkColor.CardBorder, shape)
    val body = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(modifier = body.padding(padding.dp)) { content() }
}

/** 区块标题 + 右侧动作（原型 .section-head）。 */
@Composable
fun QkSectionHead(
    title: String,
    caption: String? = null,
    action: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = QkColor.Muted)
            }
        }
        action?.invoke()
    }
}

/** 胶囊标签（原型 .pill）。 */
@Composable
fun QkPill(text: String, tone: QkPillTone = QkPillTone.Green, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        QkPillTone.Green -> QkColor.Mint to QkColor.Primary
        QkPillTone.Amber -> QkColor.AccentSoft to QkColor.AmberInk
        QkPillTone.Blue -> QkColor.BlueSoft to QkColor.Blue
        QkPillTone.Gray -> QkColor.Secondary to QkColor.Muted
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

/** 图标块：38dp 圆角方块 + 居中文字（原型 .glyph）。 */
@Composable
fun QkGlyph(text: String, tone: QkGlyphTone = QkGlyphTone.Green, size: Dp = 38.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(QkRadius.Glyph))
            .background(tone.bg()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = tone.fg(),
            fontSize = if (size > 44.dp) 20.sp else 16.sp,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/** 按钮三态（原型 .btn / .btn.secondary / .btn.ghost）。 */
enum class QkButtonStyle { Primary, Secondary, Ghost }

@Composable
fun QkButton(
    text: String,
    modifier: Modifier = Modifier,
    style: QkButtonStyle = QkButtonStyle.Primary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(QkRadius.Button)
    val (bg, fg, border) = when (style) {
        QkButtonStyle.Primary -> Triple(QkColor.Primary, Color.White, null)
        QkButtonStyle.Secondary -> Triple(QkColor.MintSoft, QkColor.Primary, null)
        QkButtonStyle.Ghost -> Triple(QkColor.Card, QkColor.GhostInk, BorderStroke(1.dp, QkColor.Border))
    }
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg.copy(alpha = bg.alpha * alpha))
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = fg.copy(alpha = fg.alpha * alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 输入框（原型 .field）：标签在框外上方，框内 11dp 圆角 + 1px 细边 + 近白底，聚焦转主绿边。
 *
 * 用 BasicTextField 而非 M3 OutlinedTextField——M3 的标签是浮动标签（未聚焦时落在框内、
 * 聚焦时切到边框缺口），与原型「label 恒在框外」的排版对不上。光标、IME、选中、
 * 无障碍语义仍由 BasicTextField 提供，这里只负责框体与标签位置。
 */
@Composable
fun QkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(QkRadius.Field)
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.FieldLabel,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = QkColor.Fg),
            cursorBrush = SolidColor(QkColor.Primary),
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (enabled) QkColor.FieldBg else QkColor.Bg)
                .border(1.dp, if (focused) QkColor.Primary else QkColor.FieldBorder, shape)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = QkColor.Hint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** 圆形图标按钮（原型 .icon-btn）。 */
@Composable
fun QkIconButton(glyph: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(QkColor.Card)
            .border(1.dp, QkColor.Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = QkColor.IconInk, style = MaterialTheme.typography.titleMedium)
    }
}

/** 列表行（原型 .list-row）：图标块 + 主副标题 + 右侧内容。 */
@Composable
fun QkListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    glyph: String? = null,
    tone: QkGlyphTone = QkGlyphTone.Green,
    glyphSize: Dp = 38.dp,
    trailing: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            if (glyph != null) QkGlyph(glyph, tone, glyphSize)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Muted,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            trailing?.invoke()
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(QkColor.LineSoft))
        }
    }
}

/** 分段控件（原型 .segment）：底部浅灰槽 + 白色选中块。 */
@Composable
fun QkSegmented(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Segment))
            .background(QkColor.Secondary)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(QkRadius.SegmentItem))
                    .background(if (active) QkColor.Card else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) QkColor.Primary else QkColor.SegmentInactive,
                )
            }
        }
    }
}

/** 细进度条（原型小卡里的 5px 圆角条）。 */
@Composable
fun QkProgress(percent: Int, color: Color = QkColor.Primary, modifier: Modifier = Modifier, height: Dp = 5.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(QkColor.LineSoft),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
        )
    }
}

/** 指标格（原型 .metric）：标签 + 值。 */
@Composable
fun QkMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    pill: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(QkColor.Bg)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = QkColor.Muted)
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
            if (unit != null) Text(unit, style = MaterialTheme.typography.labelSmall, color = QkColor.Muted)
            if (pill != null) QkPill(pill)
        }
    }
}

// ---- 以下为旧组件，签名保持不变（既有页面仍在用） ----

/** 可选中胶囊（范围/时段筛选等场景）：选中态薄荷底 + 主绿字，未选中白底细边。 */
@Composable
fun QkChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .padding(end = QkSpacing.Sm)
            .clip(shape)
            .background(if (selected) QkColor.Mint else QkColor.Card)
            .border(1.dp, if (selected) QkColor.Mint else QkColor.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) QkColor.Primary else QkColor.GhostInk,
        )
    }
}

/** 区块标题 + 可选日期副标（趋势块头等场景）。 */
@Composable
fun QkSectionTitle(title: String, caption: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
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

/** 竖线分隔（保留旧用法：部分页面用它做行分隔）。 */
@Composable
fun QkDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(QkColor.LineSoft))
}

/** 固定宽度占位（旧代码里用 Spacer 的等价物，避免页面重复写 width/height）。 */
@Composable
fun QkVSpace(height: Dp) {
    Box(modifier = Modifier.fillMaxWidth().height(height))
}

@Composable
fun QkHSpace(width: Dp) {
    Box(modifier = Modifier.width(width))
}
