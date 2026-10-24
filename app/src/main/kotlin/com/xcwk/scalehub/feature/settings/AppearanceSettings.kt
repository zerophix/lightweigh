package com.xcwk.scalehub.feature.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkStroke

/**
 * 主题模式三选 —— 文案与顺序逐字取自原型 v-appearance（浅色 / 深色 / 跟随系统）。
 *
 * 图形：原型用 emoji（日、月、齿轮）表意，源码禁 emoji，改用 Material 线性图标等价；
 * 日/月两枚 QkIcons 没有，按 MembersScreen 先例直接 import，齿轮复用 [QkIcons.Setting]。
 */
enum class ThemeMode(val label: String, val icon: ImageVector) {
    Light("浅色", Icons.Outlined.LightMode),
    Dark("深色", Icons.Outlined.DarkMode),
    System("跟随系统", QkIcons.Setting),
}

/** 页底提示（原型 v-appearance `.banner` 原文）。 */
internal val APPEARANCE_NOTICE_LINE = "深色模式和跟随系统将在后续版本提供。"

/** 品牌色预览下的说明（原型 v-appearance `.hint` 原文）。 */
internal val BRAND_PREVIEW_HINT = "轻衡绿 #258C77 · 墨绿 #203D37"

/**
 * 外观设置状态快照（[selected] 点选 / [saved] 已保存）。
 * 原型选中态落在「浅色」，故默认值同为 [ThemeMode.Light]。
 */
data class AppearanceSettingsUiState(
    val selected: ThemeMode = ThemeMode.Light,
    val saved: ThemeMode = ThemeMode.Light,
) {
    /** 是否存在尚未保存的点选。 */
    val changed: Boolean get() = selected != saved
}

/** 选择经意图上报宿主 ViewModel（AC-S5：Screen 零业务逻辑）。 */
sealed interface AppearanceSettingsIntent {
    data class SelectTheme(val mode: ThemeMode) : AppearanceSettingsIntent
    data object Save : AppearanceSettingsIntent
}

/** 纯状态转移：点选只改内存态，保存才落 [AppearanceSettingsUiState.saved]。 */
fun AppearanceSettingsUiState.reduce(intent: AppearanceSettingsIntent): AppearanceSettingsUiState =
    when (intent) {
        is AppearanceSettingsIntent.SelectTheme -> copy(selected = intent.mode)
        AppearanceSettingsIntent.Save -> if (changed) copy(saved = selected) else this
    }

/**
 * 外观设置页（T-091 · Wave 1 · 呈现式组件）。
 *
 * ## 原型
 * `design/prototypes/interactive/24-remaining-gaps.html` → `v-appearance`
 * > 外观 | 主题模式 | 浅色（选中）/ 深色 / 跟随系统
 * > 品牌色预览 | 轻衡绿 #258C77 · 墨绿 #203D37
 * > 深色模式和跟随系统将在后续版本提供。 | 保存
 *
 * ## 验收（AC-S5 语言与外观设置）
 * - 主题模式三选、当前项有选中标识（2px 主绿描边 + 薄荷底，原型选中态口径）
 * - 提示与色值说明逐字取自原型，见 [APPEARANCE_NOTICE_LINE] / [BRAND_PREVIEW_HINT]
 * - 选择经 [AppearanceSettingsIntent] 上报；不建 ViewModel、不访问 repository，
 *   持久化与原型 data-toast「外观设置已保存」归宿主 ViewModel 处理
 */
@Composable
fun AppearanceSettingsScreen(
    state: AppearanceSettingsUiState,
    onIntent: (AppearanceSettingsIntent) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md, bottom = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(title = "外观", modifier = Modifier.weight(1f).padding(start = QkSpacing.Md))
        }

        QkSectionHead(title = "主题模式", modifier = Modifier.padding(top = QkSpacing.Lg))
        Spacer(Modifier.height(QkSpacing.Md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            ThemeMode.entries.forEach { mode ->
                ThemeModeTile(
                    mode = mode,
                    selected = state.selected == mode,
                    onClick = { onIntent(AppearanceSettingsIntent.SelectTheme(mode)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        QkSectionHead(title = "品牌色预览", modifier = Modifier.padding(top = QkSpacing.Lg))
        Spacer(Modifier.height(QkSpacing.Md))
        QkCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
                    BrandSwatch(QkColor.Primary)
                    BrandSwatch(QkColor.Mint)
                    BrandSwatch(QkColor.Fg)
                    BrandSwatch(QkColor.Secondary)
                }
                Spacer(Modifier.height(QkSpacing.Md))
                Text(
                    text = BRAND_PREVIEW_HINT,
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(QkSpacing.CardGap))
        SettingsNoticeBanner(lines = listOf(APPEARANCE_NOTICE_LINE))

        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = "保存",
            modifier = Modifier.fillMaxWidth(),
            onClick = { onIntent(AppearanceSettingsIntent.Save) },
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/**
 * 主题模式选项卡（原型三块并排卡：选中 = 2px 主绿描边 + 品牌柔底，未选 = 1px 细边）。
 */
@Composable
private fun ThemeModeTile(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(QkRadius.Tile)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) QkColor.Mint else Color.Transparent)
            .border(
                width = if (selected) QkStroke.Selected else QkStroke.Thin,
                color = if (selected) QkColor.Primary else QkColor.Border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(QkSpacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QkIcon(
            icon = mode.icon,
            tint = QkColor.Fg,
            size = QkSpacing.Xl,
            contentDescription = null,
        )
        Spacer(Modifier.height(QkSpacing.Md))
        Text(
            text = mode.label,
            style = MaterialTheme.typography.titleSmall,
            color = QkColor.Fg,
        )
    }
}

/**
 * 品牌色预览色块（原型 48×48 / 12 圆角；四色依次是 --brand / --brand-soft / --ink / --iconbg，
 * 映射为 Qk token —— 全仓硬编码色值只允许出现在 QkTheme.kt）。
 */
@Composable
private fun BrandSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(QkSpacing.Xl * 2f) // 原型 48 色块
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(color),
    )
}
