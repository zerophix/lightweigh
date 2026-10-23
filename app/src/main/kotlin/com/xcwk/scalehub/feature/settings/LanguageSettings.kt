package com.xcwk.scalehub.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 语言设置（T-091 · Wave 1）。
 *
 * 原型 `design/prototypes/interactive/24-remaining-gaps.html` → `v-language`：
 * 界面语言 简体中文 / 繁體中文 / English / 日本語，当前项单选；
 * 提示「切换语言后需重启应用生效。部分翻译可能不完整，持续完善中。」；保存。
 *
 * Screen 只渲染 [LanguageUiState] 并上报 [LanguageIntent]。
 * 选择与保存由 [LanguageSettingsViewModel] 归约，不写偏好、不改既有设置文件。
 * 接入导航留给后续接线，避免本波改 SettingsScreen。
 */
enum class AppLanguage(val label: String) {
    SimplifiedChinese("简体中文"),
    TraditionalChinese("繁體中文"),
    English("English"),
    Japanese("日本語"),
}

internal object LanguageCopy {
    const val NAV_TITLE = "语言"
    const val SECTION = "界面语言"
    const val RESTART_LINE = "切换语言后需重启应用生效。"
    const val INCOMPLETE_LINE = "部分翻译可能不完整，持续完善中。"
    const val SAVE = "保存"
    const val SAVED_TOAST = "语言已切换，重启后生效"
}

data class LanguageUiState(
    val selected: AppLanguage = AppLanguage.SimplifiedChinese,
    /** 已点保存、待调用方生效的语言。未保存前保持进入页时的值。 */
    val applied: AppLanguage = AppLanguage.SimplifiedChinese,
    val message: String? = null,
)

sealed interface LanguageIntent {
    data class Select(val language: AppLanguage) : LanguageIntent
    data object Save : LanguageIntent
}

internal fun reduceLanguage(state: LanguageUiState, intent: LanguageIntent): LanguageUiState =
    when (intent) {
        is LanguageIntent.Select -> state.copy(selected = intent.language, message = null)
        LanguageIntent.Save -> state.copy(
            applied = state.selected,
            message = LanguageCopy.SAVED_TOAST,
        )
    }

class LanguageSettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(LanguageUiState())
    val state: StateFlow<LanguageUiState> = _state.asStateFlow()

    fun onIntent(intent: LanguageIntent) {
        _state.update { current -> reduceLanguage(current, intent) }
    }
}

@Composable
fun LanguageSettingsRoute(
    onBack: () -> Unit = {},
    vm: LanguageSettingsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LanguageSettingsScreen(state = state, onIntent = vm::onIntent, onBack = onBack)
}

@Composable
fun LanguageSettingsScreen(
    state: LanguageUiState,
    onIntent: (LanguageIntent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    SettingsOptionPage(
        title = LanguageCopy.NAV_TITLE,
        saveLabel = LanguageCopy.SAVE,
        message = state.message,
        onBack = onBack,
        onSave = { onIntent(LanguageIntent.Save) },
        modifier = modifier,
    ) {
        Text(
            text = LanguageCopy.SECTION,
            color = QkColor.Muted,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(QkSpacing.Sm))
        QkCard(padding = QkSpacing.Xs - QkSpacing.Xs) {
            AppLanguage.entries.forEachIndexed { index, language ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(QkSpacing.Xs / 4)
                            .background(QkColor.Border),
                    )
                }
                LanguageOptionRow(
                    label = language.label,
                    selected = state.selected == language,
                    onSelect = { onIntent(LanguageIntent.Select(language)) },
                )
            }
        }
        Spacer(Modifier.height(QkSpacing.Page))
        SettingsInfoBanner(
            lines = listOf(LanguageCopy.RESTART_LINE, LanguageCopy.INCOMPLETE_LINE),
        )
    }
}

/** 设置子页骨架：返回栏、可滚动正文、底部保存。语言页与外观页共用，避免两套页框。 */
@Composable
internal fun SettingsOptionPage(
    title: String,
    saveLabel: String,
    message: String?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Sm),
        ) {
            QkBackButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
            Text(
                text = title,
                modifier = Modifier.align(Alignment.Center),
                color = QkColor.Fg,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Md),
        ) {
            body()
        }
        Column(
            modifier = Modifier.padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Md),
        ) {
            QkButton(
                text = saveLabel,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSave,
            )
            if (message != null) {
                Spacer(Modifier.height(QkSpacing.Sm))
                Text(
                    text = message,
                    color = QkColor.Primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun SettingsInfoBanner(lines: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(QkColor.Mint)
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(QkIcons.About, tint = QkColor.Primary, size = QkSpacing.Lg)
        Spacer(Modifier.width(QkSpacing.Sm))
        Column(modifier = Modifier.weight(1f)) {
            lines.forEach { line ->
                Text(
                    text = line,
                    color = QkColor.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun LanguageOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.CardPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = QkColor.Fg,
            style = MaterialTheme.typography.titleSmall,
        )
        SelectionMark(selected)
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(QkSpacing.Xl - QkSpacing.Xs / 2)
            .border(
                BorderStroke(
                    width = QkSpacing.Xs / 4 + QkSpacing.Xs / 8,
                    color = if (selected) QkColor.Primary else QkColor.Border,
                ),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(QkSpacing.Md)
                    .clip(CircleShape)
                    .background(QkColor.Primary),
            )
        }
    }
}
