package com.xcwk.scalehub.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * 界面语言选项 —— 文案与顺序逐字取自原型 v-language（含 AC 未点名的「日本語」：
 * 原型 view 是布局与文案的单一真源，删掉它反而与 DoD「与原型一致」冲突）。
 */
enum class LanguageOption(val label: String) {
    SimplifiedChinese("简体中文"),
    TraditionalChinese("繁體中文"),
    English("English"),
    Japanese("日本語"),
}

/** 语言页底两条提示（原型 v-language `.banner` 原文，`<br>` 拆成两行渲染）。 */
internal val LANGUAGE_NOTICE_LINES = listOf(
    "切换语言后需重启应用生效。",
    "部分翻译可能不完整，持续完善中。",
)

/**
 * 语言设置状态快照。
 *
 * [selected] 是用户点选、[saved] 是已按下保存的值；语言真正生效要等重启，
 * 所以「运行中生效值」不属于本状态——它由进程重启时的初始化决定。
 */
data class LanguageSettingsUiState(
    val selected: LanguageOption = LanguageOption.SimplifiedChinese,
    val saved: LanguageOption = LanguageOption.SimplifiedChinese,
) {
    /** 是否存在尚未保存的点选（保存动作据此判要不要上报）。 */
    val changed: Boolean get() = selected != saved
}

/** 选择经意图上报宿主 ViewModel（AC-S5：Screen 零业务逻辑）。 */
sealed interface LanguageSettingsIntent {
    data class SelectLanguage(val option: LanguageOption) : LanguageSettingsIntent
    data object Save : LanguageSettingsIntent
}

/**
 * 纯状态转移：点选只改内存态，保存才落 [LanguageSettingsUiState.saved]。
 * 抽成无副作用函数是为了让「选项状态逻辑」可被 JVM 单测覆盖，
 * 宿主 ViewModel 接手时直接复用，不在 Screen 里重写一遍。
 */
fun LanguageSettingsUiState.reduce(intent: LanguageSettingsIntent): LanguageSettingsUiState =
    when (intent) {
        is LanguageSettingsIntent.SelectLanguage -> copy(selected = intent.option)
        LanguageSettingsIntent.Save -> if (changed) copy(saved = selected) else this
    }

/**
 * 语言设置页（T-091 · Wave 1 · 呈现式组件）。
 *
 * ## 原型
 * `design/prototypes/interactive/24-remaining-gaps.html` → `v-language`
 * > 语言 | 界面语言 | 简体中文 / 繁體中文 / English / 日本語（首项选中）
 * > 切换语言后需重启应用生效。部分翻译可能不完整，持续完善中。 | 保存
 *
 * ## 验收（AC-S5 语言与外观设置）
 * - AC 点名的三种语言齐备、当前项有选中标识（RadioButton 单选）
 * - 页底两条提示逐字取自原型，见 [LANGUAGE_NOTICE_LINES]
 * - 选择经 [LanguageSettingsIntent] 上报；本文件不建 ViewModel、不访问 repository，
 *   保存的持久化与原型 data-toast「语言已切换，重启后生效」都归宿主 ViewModel 处理
 */
@Composable
fun LanguageSettingsScreen(
    state: LanguageSettingsUiState,
    onIntent: (LanguageSettingsIntent) -> Unit,
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
            QkPageTitle(title = "语言", modifier = Modifier.weight(1f).padding(start = QkSpacing.Md))
        }

        QkSectionHead(title = "界面语言")
        Spacer(Modifier.height(QkSpacing.Sm))

        QkCard {
            LanguageOption.entries.forEachIndexed { index, option ->
                QkListRow(
                    title = option.label,
                    showDivider = index < LanguageOption.entries.lastIndex,
                    trailing = {
                        RadioButton(
                            selected = state.selected == option,
                            onClick = { onIntent(LanguageSettingsIntent.SelectLanguage(option)) },
                        )
                    },
                    onClick = { onIntent(LanguageSettingsIntent.SelectLanguage(option)) },
                )
            }
        }

        Spacer(Modifier.height(QkSpacing.CardGap))
        SettingsNoticeBanner(lines = LANGUAGE_NOTICE_LINES)

        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = "保存",
            modifier = Modifier.fillMaxWidth(),
            onClick = { onIntent(LanguageSettingsIntent.Save) },
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/**
 * 原型 `.banner` 提示条（theme.css：soft 底 + 12 圆角 + 品牌色信息图标 + 次级文本）。
 *
 * T-091 的语言页与外观页共用同一条样式，故只声明一次放在本文件——
 * 两文件同包可直接调用，无需新建第三个文件（任务限定只建两个 target_file）。
 */
@Composable
internal fun SettingsNoticeBanner(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(QkColor.Mint)
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(QkIcons.About, tint = QkColor.Primary, contentDescription = "提示")
        Column {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
            }
        }
    }
}
