package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkComponentSize
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * 长按弹出菜单（T-099 · Wave 0）。
 *
 * ## 原型
 * `design/prototypes/interactive/09-components-demo.html` → `v-longpress`
 * （导航标题「长按 / 复制 / 展开」）：
 * - 「长按弹出菜单」：长按选中后弹出黑色菜单条（示例项：复制 / 分享 / 全选）
 * - 提示原文「长按选中后弹出黑色菜单条；选区高亮为品牌色」
 *
 * ## 范围边界
 * 同 view 下的「长文展开 / 收起」不属本组件（归 AI 模块文案任务，T-099 明确排除）。
 *
 * ## 验收关联
 * 菜单深色底取 [QkColor.Fg]、文字白色取 [QkColor.Card]、竖分隔线为白色 20% 透明、
 * 圆角取 [QkComponentSize.MenuRadius]；选区高亮经 [QkBrandSelectionContainer]
 * 用品牌色 [QkColor.Primary]，全部走 Qk token，无硬编码色值/间距。
 *
 * ## 用法
 * ```
 * QkLongPressMenu(menuItems = listOf("复制", "分享", "全选"), onAction = vm::onMenu) {
 *     QkBrandSelectionContainer {
 *         Text("体脂率是脂肪重量占体重的比例…")   // 长按弹菜单，选中文本高亮为品牌色
 *     }
 * }
 * ```
 */

/** 选区高亮底色：品牌色 [QkColor.Primary] 的低透明表达（原型 rgba(37,140,119,.18)）。 */
private const val SELECTION_TINT_ALPHA = 0.18f

/** 菜单竖分隔线：白色 20% 透明（原型 rgba(255,255,255,.2)）。 */
private const val MENU_DIVIDER_ALPHA = 0.2f

/**
 * 长按菜单控制器 —— 纯逻辑，JVM 单测可直接驱动。
 *
 * 行为约定（与原型一致）：
 * - 长按 [show]：非空列表才弹出，展示顺序与传入一致；
 * - 点选 [select]：命中项回调**一次**并收起；未命中项忽略（不误触发）；
 * - [dismiss]：收起并清空菜单内容与选中下标（状态不残留）；
 * - [dividerAfter]：项间竖分隔线，最后一项后不画。
 */
class QkLongPressMenuController(private val onAction: (String) -> Unit) {
    var menuItems: List<String> by mutableStateOf(emptyList())
        private set
    var visible: Boolean by mutableStateOf(false)
        private set
    var selectedIndex: Int by mutableStateOf(-1)
        private set

    fun show(items: List<String>) {
        if (items.isEmpty()) return
        menuItems = items
        selectedIndex = -1
        visible = true
    }

    fun dismiss() {
        visible = false
        selectedIndex = -1
        menuItems = emptyList()
    }

    fun select(label: String) {
        val index = menuItems.indexOf(label)
        if (index < 0) return
        selectedIndex = index
        onAction(label)
        dismiss()
    }

    fun dividerAfter(index: Int): Boolean = index in 0 until menuItems.lastIndex
}

/**
 * 长按菜单容器：长按 [content] 弹出菜单条，点选菜单项回调 [onAction]。
 * 单击无语义；菜单外点击由 DropdownMenu 自带 dismiss 收起。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QkLongPressMenu(
    menuItems: List<String>,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val controller = remember { QkLongPressMenuController(onAction) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* 单击无语义，长按才弹菜单 */ },
                onLongClick = { controller.show(menuItems) },
            ),
        ) {
            content()
        }
        QkLongPressMenuPopup(controller = controller)
    }
}

/** 黑色菜单条：深色底 + 白字 + 竖分隔线 + 圆角，锚定长按目标（原型浮层样式）。 */
@Composable
private fun QkLongPressMenuPopup(controller: QkLongPressMenuController) {
    DropdownMenu(
        expanded = controller.visible,
        onDismissRequest = controller::dismiss,
        containerColor = QkColor.Fg,
        shape = RoundedCornerShape(QkComponentSize.MenuRadius),
    ) {
        Row {
            controller.menuItems.forEachIndexed { index, label ->
                if (controller.dividerAfter(index)) {
                    VerticalDivider(
                        color = QkColor.Card.copy(alpha = MENU_DIVIDER_ALPHA),
                        modifier = Modifier.padding(vertical = QkSpacing.Xs),
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = QkColor.Card,
                        )
                    },
                    onClick = { controller.select(label) },
                    contentPadding = PaddingValues(
                        horizontal = QkSpacing.Md,
                        vertical = QkSpacing.Xs,
                    ),
                )
            }
        }
    }
}

/**
 * 选区高亮容器：文本可选中，选中色调用品牌色 [QkColor.Primary]
 * （原型「选区高亮为品牌色」）。配合 [QkLongPressMenu] 构成完整的长按/复制体验。
 */
@Composable
fun QkBrandSelectionContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = QkColor.Primary,
            backgroundColor = QkColor.Primary.copy(alpha = SELECTION_TINT_ALPHA),
        ),
    ) {
        SelectionContainer(modifier = modifier, content = content)
    }
}
