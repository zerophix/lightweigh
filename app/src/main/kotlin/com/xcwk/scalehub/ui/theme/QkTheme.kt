package com.xcwk.scalehub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计 token —— 对齐 v2 交互原型（`docs/prototype/qingke_health_v2_interactive_prototype.html`）。
 *
 * 口径变化（相对旧库 QingkeTheme 移植版）：
 *  - 主绿由 `#2D8B56` 调为 `#159B72`（原型 --green），并引入薄荷底 `#E7F7F0` 与墨色 `#20312D`；
 *  - 卡片从「深阴影 + 16 圆角」改为「17 圆角 + 1px 细边 + 5.5% 轻阴影」，接近原型的
 *    `box-shadow: 0 8px 28px rgba(26,69,54,.055)`；
 *  - 字重体系显式化：大数字/页面标题 800，卡片标题 700，正文 400（原型 750/650 在
 *    Compose 的 FontWeight 阶梯上就近映射为 SemiBold/Bold，避免自造字重）。
 *
 * 页面禁止散落硬编码色值：一律经此 theme 或 Qk 组件取色。全仓硬编码色值只允许出现在本文件。
 */
object QkColor {
    // 底与字
    val Bg = Color(0xFFF4F7F6)          // --bg
    val Card = Color(0xFFFFFFFF)        // --white
    val Fg = Color(0xFF20312D)          // --ink
    val Muted = Color(0xFF87948F)       // --muted
    val Hint = Color(0xFFA8B3AE)
    val Border = Color(0xFFEDF1EF)      // --line
    val CardBorder = Color(0xFFEDF2EF)
    val LineSoft = Color(0xFFF0F3F1)    // 列表行分隔线

    // 主色
    val Primary = Color(0xFF159B72)     // --green
    val PrimaryDark = Color(0xFF087B59) // --green-dark
    val Mint = Color(0xFFE7F7F0)        // --mint
    val MintSoft = Color(0xFFEFF8F4)    // 次级按钮底
    val Secondary = Color(0xFFF0F4F2)   // 分段控件底

    // 辅助色（快捷记录四色）
    val Blue = Color(0xFF58A9E8)
    val BlueSoft = Color(0xFFEDF7FF)
    val Purple = Color(0xFF9C83EC)
    val PurpleSoft = Color(0xFFF3EFFF)
    val Accent = Color(0xFFF3A64A)      // --orange
    val AccentSoft = Color(0xFFFFF5E8)
    val Danger = Color(0xFFDC4C4C)
    val DangerSoft = Color(0xFFFCEBEB)

    // 组件内部用色（原型里散落的中性色，收口成 token）
    val AmberInk = Color(0xFFC17B1E)    // 琥珀胶囊文字
    val SegmentInactive = Color(0xFF77857E)
    val GhostInk = Color(0xFF64766E)    // 幽灵按钮文字
    val IconInk = Color(0xFF61736C)     // 圆形图标按钮

    // 输入框（原型 .field input）
    val FieldBg = Color(0xFFFBFCFB)
    val FieldBorder = Color(0xFFE5ECE8)
    val FieldLabel = Color(0xFF66766E)

    // 身体成分条四段（原型 .bodybar 渐变的四个停靠色）
    val BmiThin = Color(0xFF64B9F4)
    val BmiNormal = Color(0xFF43B99B)
    val BmiOver = Color(0xFFF4C75D)
    val BmiObese = Color(0xFFEF8B78)

    // 设备页蓝牙环（原型 .bluetooth-ring 三层：外描边 + 两圈内嵌浅环）
    val RingEdge = Color(0xFFC9ECDF)
    val RingIn1 = Color(0xFFF2FBF7)
    val RingIn2 = Color(0xFFE8F7F1)

    // 报告页评分环（原型 .score-ring 的底环色）
    val ScoreTrack = Color(0xFFD8F1E7)

    // 历史页月标头（原型 .history-date）
    val ChipBg = Color(0xFFF5F8F6)
    val ChipInk = Color(0xFF66766E)

    /** 卡片阴影色：rgba(26,69,54,.055) 的 Compose 表达。 */
    val Shadow = Color(0x121A4536)
}

/** 圆角刻度：卡片 17 / 按钮 12 / 图标块 13 / 分段 12-9（原型同值）。 */
object QkRadius {
    val Card = 17.dp
    val Button = 12.dp
    val Glyph = 13.dp
    val Segment = 12.dp
    val SegmentItem = 9.dp
    val NavFab = 16.dp
    val Field = 11.dp
}

/** 间距刻度。页面边距 14、卡片内边距 15、卡片间距 11（原型实测值）。 */
object QkSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp

    val Page = 14.dp
    val CardPad = 15.dp
    val CardGap = 11.dp
}

/**
 * 组件专用尺寸刻度（T-099 · 原型 09 v-skeleton / v-longpress 实测值收口）。
 * 页面禁止裸 `10.dp` 等字面量（gov-prototype-verify --strict 会拦），
 * 组件默认尺寸一律经此 token 取值。
 */
object QkComponentSize {
    // 骨架屏（原型 .sk line 三种高度 / circle 38 / block 110）
    val SkeletonLineTitle = 10.dp
    val SkeletonLineBigNumber = 34.dp
    val SkeletonLineSecondary = 8.dp
    val SkeletonAvatar = 38.dp
    val SkeletonBlock = 110.dp

    // 长按菜单（原型黑色菜单条 radius 10）
    val MenuRadius = 10.dp
}

/** 字号阶梯（原型 px 值直接映射为 sp）。 */
val QkTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
)

/**
 * 形状阶梯（Material3 的 Button 默认容器形状是 50% 胶囊且不由本表控制，
 * 故页面按钮需使用 [com.xcwk.scalehub.ui.components.QkButton] 才能落到原型的 12dp 圆角）。
 */
private val QkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(QkRadius.Button),
    large = RoundedCornerShape(QkRadius.Card),
    extraLarge = RoundedCornerShape(24.dp),
)

private val QkColorScheme = lightColorScheme(
    primary = QkColor.Primary,
    onPrimary = Color.White,
    primaryContainer = QkColor.Mint,
    onPrimaryContainer = QkColor.PrimaryDark,
    secondary = QkColor.PrimaryDark,
    onSecondary = Color.White,
    secondaryContainer = QkColor.MintSoft,
    onSecondaryContainer = QkColor.PrimaryDark,
    tertiary = QkColor.Blue,
    onTertiary = Color.White,
    background = QkColor.Bg,
    onBackground = QkColor.Fg,
    surface = QkColor.Card,
    onSurface = QkColor.Fg,
    surfaceVariant = QkColor.Secondary,
    onSurfaceVariant = QkColor.Muted,
    surfaceContainer = QkColor.Card,
    surfaceContainerHigh = QkColor.Card,
    surfaceContainerLow = QkColor.Bg,
    outline = QkColor.Border,
    outlineVariant = QkColor.LineSoft,
    error = QkColor.Danger,
    onError = Color.White,
    errorContainer = QkColor.DangerSoft,
    onErrorContainer = QkColor.Danger,
)

@Composable
fun QkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QkColorScheme,
        typography = QkTypography,
        shapes = QkShapes,
        content = content,
    )
}
