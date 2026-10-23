package com.xcwk.scalehub.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 外观设置（T-091 · Wave 1）。
 *
 * 原型 `24-remaining-gaps.html` → `v-appearance`：
 * 主题模式三选（浅色 / 深色 / 跟随系统），当前项有描边；
 * 品牌色预览与「轻衡绿 #258C77 · 墨绿 #203D37」；
 * 「深色模式和跟随系统将在后续版本提供。」
 *
 * 色块只用 [QkColor]，说明文字保留原型原文。
 * 选择经 [AppearanceSettingsViewModel] 上报，Screen 不切换系统主题。
 */
enum class ThemeMode(val label: String, val glyph: String) {
    Light("浅色", "☀️"),
    Dark("深色", "🌙"),
    System("跟随系统", "⚙️"),
}

internal object AppearanceCopy {
    const val NAV_TITLE = "外观"
    const val SECTION_MODE = "主题模式"
    const val SECTION_BRAND = "品牌色预览"
    const val BRAND_HINT = "轻衡绿 #258C77 · 墨绿 #203D37"
    const val LATER_HINT = "深色模式和跟随系统将在后续版本提供。"
    const val SAVE = "保存"
    const val TOAST_DARK = "已切换到深色模式"
    const val TOAST_SYSTEM = "已切换到跟随系统"
    const val SAVED_TOAST = "外观设置已保存"
}

data class AppearanceUiState(
    val selected: ThemeMode = ThemeMode.Light,
    val applied: ThemeMode = ThemeMode.Light,
    val message: String? = null,
)

sealed interface AppearanceIntent {
    data class Select(val mode: ThemeMode) : AppearanceIntent
    data object Save : AppearanceIntent
}

/** 原型只在深色、跟随系统的卡片上给出 toast；浅色是进入页时的当前项。 */
internal fun appearanceSelectMessage(mode: ThemeMode): String? = when (mode) {
    ThemeMode.Light -> null
    ThemeMode.Dark -> AppearanceCopy.TOAST_DARK
    ThemeMode.System -> AppearanceCopy.TOAST_SYSTEM
}

internal fun reduceAppearance(state: AppearanceUiState, intent: AppearanceIntent): AppearanceUiState =
    when (intent) {
        is AppearanceIntent.Select -> state.copy(
            selected = intent.mode,
            message = appearanceSelectMessage(intent.mode),
        )
        AppearanceIntent.Save -> state.copy(
            applied = state.selected,
            message = AppearanceCopy.SAVED_TOAST,
        )
    }

class AppearanceSettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppearanceUiState())
    val state: StateFlow<AppearanceUiState> = _state.asStateFlow()

    fun onIntent(intent: AppearanceIntent) {
        _state.update { current -> reduceAppearance(current, intent) }
    }
}

@Composable
fun AppearanceSettingsRoute(
    onBack: () -> Unit = {},
    vm: AppearanceSettingsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    AppearanceSettingsScreen(state = state, onIntent = vm::onIntent, onBack = onBack)
}

@Composable
fun AppearanceSettingsScreen(
    state: AppearanceUiState,
    onIntent: (AppearanceIntent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    SettingsOptionPage(
        title = AppearanceCopy.NAV_TITLE,
        saveLabel = AppearanceCopy.SAVE,
        message = state.message,
        onBack = onBack,
        onSave = { onIntent(AppearanceIntent.Save) },
        modifier = modifier,
    ) {
        Text(
            text = AppearanceCopy.SECTION_MODE,
            color = QkColor.Muted,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(QkSpacing.Sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm + QkSpacing.Xs / 2),
        ) {
            ThemeMode.entries.forEach { mode ->
                ThemeModeTile(
                    mode = mode,
                    selected = state.selected == mode,
                    onSelect = { onIntent(AppearanceIntent.Select(mode)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(QkSpacing.Page + QkSpacing.Sm))
        Text(
            text = AppearanceCopy.SECTION_BRAND,
            color = QkColor.Muted,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(QkSpacing.Sm))
        QkCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md, Alignment.CenterHorizontally),
            ) {
                BrandSwatch(QkColor.Primary)
                BrandSwatch(QkColor.Mint)
                BrandSwatch(QkColor.Fg)
                BrandSwatch(QkColor.Secondary)
            }
            Spacer(Modifier.height(QkSpacing.Sm + QkSpacing.Xs / 2))
            Text(
                text = AppearanceCopy.BRAND_HINT,
                modifier = Modifier.fillMaxWidth(),
                color = QkColor.Muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(QkSpacing.Page))
        SettingsInfoBanner(lines = listOf(AppearanceCopy.LATER_HINT))
    }
}

@Composable
private fun ThemeModeTile(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(QkRadius.Card)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) QkColor.Mint else QkColor.Card)
            .border(
                BorderStroke(
                    width = if (selected) QkSpacing.Xs / 2 else QkSpacing.Xs / 4,
                    color = if (selected) QkColor.Primary else QkColor.Border,
                ),
                shape,
            )
            .clickable(onClick = onSelect)
            .padding(QkSpacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = mode.glyph, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(QkSpacing.Sm - QkSpacing.Xs / 2))
        Text(
            text = mode.label,
            color = QkColor.Fg,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun BrandSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(QkSpacing.Xl * 2)
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(color),
    )
}
