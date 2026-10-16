package com.xcwk.scalehub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 设计 token —— DEC-021 支柱 5（task B）。
 * 品牌色板移植自旧库 QingkeTheme.kt:15-31（轻刻绿为产品主色，勿与登录页链接蓝混淆）。
 * 页面禁止散落硬编码色值：一律经此 theme 或 Qk 组件取色。
 */
object QkColor {
    val Bg = Color(0xFFFAFAFA)
    val Fg = Color(0xFF191A1B)
    val Card = Color.White
    val Primary = Color(0xFF2D8B56)      // 轻刻绿
    val Muted = Color(0xFF76797E)
    val Secondary = Color(0xFFF0F5F2)
    val Border = Color(0xFFE6E6E8)
    val Accent = Color(0xFFE85D04)
    val Hint = Color(0xFF9B9EA3)
}

/** 间距刻度（4/8/12/16/24），页面与组件按此取值。 */
object QkSpacing {
    val Xs = androidx.compose.ui.unit.Dp(4f)
    val Sm = androidx.compose.ui.unit.Dp(8f)
    val Md = androidx.compose.ui.unit.Dp(12f)
    val Lg = androidx.compose.ui.unit.Dp(16f)
    val Xl = androidx.compose.ui.unit.Dp(24f)
}

@Composable
fun QkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = QkColor.Primary,
            onPrimary = Color.White,
            background = QkColor.Bg,
            surface = QkColor.Card,
            onSurface = QkColor.Fg,
            onSurfaceVariant = QkColor.Muted,
            outline = QkColor.Border,
            error = QkColor.Accent,
        ),
        content = content,
    )
}
