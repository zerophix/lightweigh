package com.xcwk.scalehub.feature.device

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * device-rename — 设备重命名（T-080 · 原型 24 `v-rename-device`）。
 *
 * ## 原型布局
 * 导航条「设备重命名」→ hero「重命名设备 / 给你的体脂秤一个好记的名字」
 * → 当前名称卡（图标 + 「当前名称」+「轻衡 S1 · 序列号 ****8A2F」）
 * →「新名称」输入框 + 长度说明 → 说明横幅 →「保存名称」。
 *
 * ## 验收（AC-D5）
 * - 空名称与超过 20 个字符不可保存（[checkDeviceName] 纯函数，JVM 单测钉死）
 * - 重命名只改显示名称，不触碰连接与测量数据
 *
 * ## 边界（呈现式组件）
 * 本文件不读写设备档案：保存动作经 [DeviceRenameScreen] 的 `onRenameSaved(name)` 回调
 * 交给既有设备流程；组件只维护输入草稿与校验结果。
 */

/** 名称长度上限（原型说明「最多 20 个字符，支持中英文、数字」）。 */
const val DEVICE_NAME_MAX_LENGTH = 20

const val DEVICE_RENAME_TITLE = "设备重命名"
const val DEVICE_RENAME_HEADLINE = "重命名设备"
const val DEVICE_RENAME_SUBLINE = "给你的体脂秤一个好记的名字"
const val DEVICE_RENAME_CURRENT_LABEL = "当前名称"
const val DEVICE_RENAME_CURRENT_VALUE = "轻衡 S1 · 序列号 ****8A2F"
const val DEVICE_RENAME_NEW_LABEL = "新名称"
const val DEVICE_RENAME_PLACEHOLDER = "输入新名称"
const val DEVICE_RENAME_HINT = "最多 20 个字符，支持中英文、数字"
const val DEVICE_RENAME_NOTE = "重命名仅影响显示名称，\n不影响设备连接和测量数据。"
const val DEVICE_RENAME_SAVE = "保存名称"

/** 原型 .av / 当前名称卡图标圆底 40px；theme 无此档位，收口在文件顶部。 */
internal val DeviceAvatarSize = 40.dp

/** 名称校验结果。 */
enum class DeviceNameCheck {
    /** 可保存 */
    Ok,

    /** 空名称（含纯空白） */
    Empty,

    /** 超过 [DEVICE_NAME_MAX_LENGTH] 个字符 */
    TooLong,
}

/**
 * 名称校验纯函数 —— 首尾空白不计入长度（保存时同样先 trim）。
 *
 * 口径：先 trim 再判空、判长；中文与英文均按字符（code point 序列）计数。
 */
fun checkDeviceName(raw: String): DeviceNameCheck {
    val trimmed = raw.trim()
    return when {
        trimmed.isEmpty() -> DeviceNameCheck.Empty
        trimmed.length > DEVICE_NAME_MAX_LENGTH -> DeviceNameCheck.TooLong
        else -> DeviceNameCheck.Ok
    }
}

/** 保存时提交的名称（trim 后）。仅在校验为 [DeviceNameCheck.Ok] 时有意义。 */
fun sanitizeDeviceName(raw: String): String = raw.trim()

/** 重命名页呈现状态：当前名称（只读）+ 新名称草稿。 */
data class DeviceRenameUiState(
    val currentName: String = DEVICE_RENAME_CURRENT_VALUE,
    val input: String = "",
) {
    val check: DeviceNameCheck get() = checkDeviceName(input)
    val canSave: Boolean get() = check == DeviceNameCheck.Ok
}

/** 输入纯函数：只更新草稿，不校验不落库（校验由 [DeviceRenameUiState.canSave] 派生）。 */
fun deviceRenameInputChanged(state: DeviceRenameUiState, text: String): DeviceRenameUiState =
    state.copy(input = text)

/** 压栈页导航条（device 子页共用，返回钮 + 页标题）。 */
@Composable
internal fun DevicePageNavBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = QkSpacing.Md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkBackButton(onClick = onBack)
            Text(
                title,
                modifier = Modifier.padding(start = QkSpacing.Sm),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
            )
        }
    }
}

/** 提示横幅（原型 .banner：薄荷底圆角 + 线性信息图标 + 小字，两个 view 共用）。 */
@Composable
internal fun DeviceNoteBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Tile))
            .background(QkColor.Mint)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkIcon(
            QkIcons.About,
            tint = QkColor.Primary,
            size = QkSpacing.Lg + QkSpacing.Xs,
        )
        Text(
            text,
            modifier = Modifier.padding(start = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
        )
    }
}

/** hero 区（h1 标题 + h2 副标题，原型 .hero 左对齐）。 */
@Composable
internal fun DeviceHero(headline: String, subline: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(headline, style = MaterialTheme.typography.titleLarge, color = QkColor.Fg)
        Text(
            subline,
            modifier = Modifier.padding(top = QkSpacing.Xs),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.GhostInk,
        )
    }
}

/** 设备重命名页（原型 `v-rename-device`）。 */
@Composable
fun DeviceRenameScreen(
    state: DeviceRenameUiState,
    onNameChange: (String) -> Unit,
    onRenameSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        DevicePageNavBar(DEVICE_RENAME_TITLE, onBack)

        DeviceHero(
            headline = DEVICE_RENAME_HEADLINE,
            subline = DEVICE_RENAME_SUBLINE,
            modifier = Modifier.padding(bottom = QkSpacing.Lg),
        )

        // 当前名称卡：白色圆底图标 + 只读设备名（原型 .card.soft 第一行）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(QkRadius.Tile))
                .background(QkColor.Mint)
                .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(DeviceAvatarSize).clip(CircleShape).background(QkColor.Card),
                contentAlignment = Alignment.Center,
            ) {
                QkIcon(QkIcons.Device, tint = QkColor.Primary, size = DeviceAvatarSize * 0.5f)
            }
            Column(modifier = Modifier.padding(start = QkSpacing.Md)) {
                Text(DEVICE_RENAME_CURRENT_LABEL, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                Text(
                    state.currentName,
                    modifier = Modifier.padding(top = QkSpacing.Xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.GhostInk,
                )
            }
        }

        // 新名称输入 + 长度说明（超限时说明转警示色，仍沿用原型文案）
        QkTextField(
            value = state.input,
            onValueChange = onNameChange,
            modifier = Modifier.padding(top = QkSpacing.Md),
            label = DEVICE_RENAME_NEW_LABEL,
            placeholder = DEVICE_RENAME_PLACEHOLDER,
        )
        Text(
            DEVICE_RENAME_HINT,
            modifier = Modifier.padding(start = QkSpacing.Xs, top = QkSpacing.Sm),
            style = MaterialTheme.typography.labelSmall,
            color = if (state.check == DeviceNameCheck.TooLong) QkColor.Danger else QkColor.Hint,
        )

        DeviceNoteBanner(
            text = DEVICE_RENAME_NOTE,
            modifier = Modifier.padding(top = QkSpacing.Lg),
        )

        Spacer(modifier = Modifier.height(QkSpacing.Xl))
        QkButton(
            text = DEVICE_RENAME_SAVE,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSave,
            onClick = { onRenameSaved(sanitizeDeviceName(state.input)) },
        )
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}
