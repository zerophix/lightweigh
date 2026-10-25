package com.xcwk.scalehub.ui.components

import com.xcwk.scalehub.ui.components.QkHeaderStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
enum class QkPillTone { Green, Amber, Blue, Gray, Red }

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
    padding: Dp = QkSpacing.CardPad,
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
    Column(modifier = body.padding(padding)) { content() }
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
        QkPillTone.Red -> QkColor.DangerSoft to QkColor.Danger
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

/** 圆形图标泡：38dp 正圆 + 浅底 + 线性图标（设计稿里「今日饮食」「喝水」「跳绳」的图标样式）。 */
@Composable
fun QkIconBubble(
    icon: ImageVector,
    tone: QkGlyphTone = QkGlyphTone.Green,
    size: Dp = 38.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tone.bg()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tone.fg(),
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/** 图标按钮里的线性图标（页签、返回、通知等通用小图标）。 */
@Composable
fun QkIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = QkColor.IconInk,
    size: Dp = 22.dp,
    contentDescription: String? = null,
) {
    Icon(icon, contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}

/** 小胶囊按钮（设计稿里「记录饮食 / 蛋白质 / 蔬菜 / 更多」这类浅底图标胶囊）。 */
@Composable
fun QkActionChip(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(QkColor.MintSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = QkColor.Primary, modifier = Modifier.size(14.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = QkColor.Primary, maxLines = 1)
    }
}

/** 双列小卡（无进度条版）：标题 + 主值 + 说明（设计稿「跳绳 / 腰围 / 习惯」）。 */
@Composable
fun QkMiniCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: ImageVector? = null,
    iconTone: QkGlyphTone = QkGlyphTone.Green,
    valueStyle: androidx.compose.ui.text.TextStyle? = null,
    onClick: (() -> Unit)? = null,
) {
    QkCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                QkIconBubble(icon, iconTone, 32.dp)
                Box(modifier = Modifier.width(9.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg, modifier = Modifier.weight(1f))
        }
        Text(
            value,
            modifier = Modifier.padding(top = 8.dp),
            style = valueStyle ?: MaterialTheme.typography.headlineSmall,
            color = QkColor.Fg,
        )
        if (caption != null) {
            Text(
                caption,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }
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

/**
 * 圆形图标按钮（原型 .icon-btn）。
 *
 * 无障碍（T-070）：按钮内只有图标字形，TalkBack 读不到任何信息，
 * 故 [contentDescription] 是**必填**参数（`gov-a11y-check.py` 的 A11Y-001 强制），
 * 并由 `semantics` 挂到可点击容器上。
 */
@Composable
fun QkIconButton(
    glyph: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(QkColor.Card)
            .border(1.dp, QkColor.Border, CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
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
    icon: ImageVector? = null,
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
            when {
                icon != null -> QkIconBubble(icon, tone, glyphSize)
                glyph != null -> QkGlyph(glyph, tone, glyphSize)
            }
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
                    // 最小高度而非固定高度：200% 字体缩放下胶囊可以撑高，不裁字
                    .heightIn(min = 34.dp)
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

/**
 * 身体成分四段条（设计稿「偏瘦 / 标准 / 偏重 / 肥胖」）。
 *
 * 四段实色 + 4dp 间隙，命中段中心有一个白点指示当前所处区间；标签在条下均分。
 * 注意标签口径用「偏重」而非「偏胖」——与设计稿一致，[active] 传业务值，
 * 由 [labels] 的顺序对齐，映射在调用侧完成。
 */
@Composable
fun QkBodyBar(
    labels: List<String>,
    active: String?,
    modifier: Modifier = Modifier,
) {
    val colors = listOf(QkColor.BmiThin, QkColor.BmiNormal, QkColor.BmiOver, QkColor.BmiObese)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                val hit = label == active
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.getOrElse(index) { QkColor.Muted }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hit) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
            labels.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (label == active) QkColor.Primary else QkColor.Muted,
                    textAlign = TextAlign.Center,
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

// ---- 页面级组件（原型各页反复出现的版式原语，DEC-026 批次新增） ----

/**
 * 详情页页标题（原型 .page-title：21px / 800）。
 *
 * 只给压栈的二级页用（历史/报告/设置等——它们进 NavState 栈，壳层品牌栏让位）；
 * 四个页签页由壳层 QkTopBar 承担标题，页面内不再重造。
 */
@Composable
fun QkPageTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall,
        color = QkColor.Fg,
    )
}

/** 详情页页头版式（三档，收敛全库 14 份各自实现的返回条）。 */
enum class QkHeaderStyle {
    /** 大标题（原型 .page-title）：历史/报告/设置等主详情页。 */
    Page,

    /** 次一级标题（titleLarge）：认证、隐私、固件、行动计划等流程页。 */
    Title,

    /** 居中标题（titleMedium）：验证码/邮箱等窄流程页。 */
    Centered,
}

/**
 * 详情页页头：返回钮 + 标题 + 可选右侧动作。
 *
 * 为什么要有它：14 个 feature 各写了一份几乎相同的返回条（相似度 85%~99%），
 * 改一处样式要改 14 处，且已经出现 34dp/小标题/居中三种漂移。统一走这里。
 */
@Composable
fun QkScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    style: QkHeaderStyle = QkHeaderStyle.Page,
    action: (@Composable () -> Unit)? = null,
) {
    val vertical = if (style == QkHeaderStyle.Page) {
        Modifier.padding(vertical = QkSpacing.Md)
    } else {
        Modifier.padding(top = QkSpacing.Md)
    }
    Row(
        modifier = modifier.fillMaxWidth().then(vertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkBackButton(onClick = onBack)
        when (style) {
            QkHeaderStyle.Page -> QkPageTitle(
                title = title,
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
            QkHeaderStyle.Title -> Text(
                title,
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Sm),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
            )
            QkHeaderStyle.Centered -> Text(
                title,
                modifier = Modifier.weight(1f).padding(end = QkSpacing.Md),
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
        }
        if (style == QkHeaderStyle.Centered) {
            // 右侧留出与返回钮等宽的占位，保证标题视觉居中
            Spacer(modifier = Modifier.width(QkSpacing.Xl))
        } else {
            action?.invoke()
        }
    }
}

/** 压栈页的返回圆钮（原型 .icon-btn 口径：34dp 白底细边圆）。 */
@Composable
fun QkBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(QkColor.Card)
            .border(1.dp, QkColor.Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        QkIcon(QkIcons.Back, size = 20.dp, contentDescription = "返回")
    }
}

/**
 * 报告页评分环（原型 .score-ring）：84dp 圆环，底环 + 按分数比例的绿色弧。
 *
 * 原型只把顶段染成主绿（border-top-color），这里改为按 score/100 画弧——
 * 环要表达「分」的多少，固定一段彩色弧在 60 分和 90 分上长得一样，失去了图形意义。
 */
@Composable
fun QkScoreRing(score: Int, color: Color = QkColor.Primary, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = QkColor.ScoreTrack,
                radius = radius,
                center = center,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * (score.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$score",
                style = MaterialTheme.typography.headlineSmall,
                color = color,
            )
            Text(
                "分",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(bottom = 4.dp, start = 1.dp),
            )
        }
    }
}

/**
 * 设备页蓝牙环 hero（原型 .device-hero / .bluetooth-ring）：
 * 150dp 外圈 1px 描边 + 两圈内嵌浅环（12dp / 25dp 处）+ 58dp 主绿圆心白图标。
 * 版式完全按原型实测值，环不带动画——原型即静态，扫描态由下方状态文案表达。
 */
@Composable
fun QkDeviceHero(
    status: String,
    sub: String,
    modifier: Modifier = Modifier,
    centerIcon: ImageVector = QkIcons.Bluetooth,
    centerTint: Color = Color.White,
    ringColor: Color = QkColor.Primary,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val edge = 1.dp.toPx()
                // 外描边
                drawCircle(
                    color = QkColor.RingEdge,
                    radius = (size.minDimension - edge) / 2f,
                    style = Stroke(width = edge),
                )
                // 两圈内嵌浅环：CSS inset shadow 12px 与 25px 两档的 Compose 等价物
                val band1 = 12.dp.toPx()
                val band2 = 13.dp.toPx()
                drawCircle(
                    color = QkColor.RingIn1,
                    radius = size.minDimension / 2f - band1 / 2f,
                    style = Stroke(width = band1),
                )
                drawCircle(
                    color = QkColor.RingIn2,
                    radius = size.minDimension / 2f - band1 - band2 / 2f,
                    style = Stroke(width = band2),
                )
                // 圆心主绿圆（58dp）
                drawCircle(
                    color = ringColor,
                    radius = 29.dp.toPx(),
                )
            }
            Icon(
                centerIcon,
                contentDescription = null,
                tint = centerTint,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            status,
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.titleSmall,
            color = QkColor.Fg,
        )
        Text(
            sub,
            modifier = Modifier.padding(top = 7.dp),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
    }
}

/**
 * 历史页月标头（原型 .history-date）：浅灰底圆角条，承一个字符串（月份或当日标题）。
 * 分组汇总信息（共减重/减脂）由调用方拼进 [text]，本组件不掺数据口径。
 */
@Composable
fun QkDateChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(QkColor.ChipBg)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = QkColor.ChipInk)
    }
}

/** 报告页风险行（原型 .risk）：状态点 + 名称 + 右侧档位胶囊。 */
@Composable
fun QkRiskRow(
    name: String,
    levelText: String,
    tone: QkPillTone = QkPillTone.Green,
    dotColor: Color = QkColor.Primary,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            name,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Fg,
        )
        QkPill(levelText, tone)
    }
}

/**
 * 门槛卡片：标题 + 说明 + 单个行动按钮，用于权限未授予 / 蓝牙未开启这类阻断态给出出口。
 * DeviceScreen 与 ScaleScreen 曾各有一份私有 GateCard（参数名 body/desc、配色
 * QkColor.Muted/MaterialTheme.colorScheme 各不相同），统一后强制走设计 token。
 */
@Composable
fun QkGateCard(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QkCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
        Text(
            body,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
        QkButton(
            text = action,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            onClick = onAction,
        )
    }
}
