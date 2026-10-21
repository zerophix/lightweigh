package com.xcwk.scalehub.ui.shell

/**
 * 导航状态单一事实源（DEC-021 支柱 2 的收口实现）。
 *
 * 旧实现把「当前在哪」拆成 7 个并列的布尔/长整型（tab / showQuick / showAi / showScale /
 * reportTime / featureName / recordType），页面优先级靠 if 的书写顺序维持：报告页的显示
 * 条件已经写成 `!showAi && !showScale && reportTime >= 0 && featureName.isEmpty()`，
 * 新增一层覆盖页必须回头修改既有条件。此处收敛为「页签 + 模态记录表单 + 覆盖页栈」：
 *
 *  - 页签是根：切换页签即回到根，同时清空记录表单与覆盖页（旧实现只清了前两者，
 *    导致切页签后功能页/报告页仍压在栈上——属缺陷，此处一并修正）。
 *  - 记录表单是模态替换层，不进栈：下层页面不参与组合，避免测量会话在录入表单期间
 *    继续持有蓝牙连接（与旧实现的行为一致）。
 *  - 覆盖页是栈：栈顶即当前页，返回 = 出栈，不再需要 `!A && !B` 的优先级链。
 *
 * 本文件是纯 Kotlin（零 Compose 依赖），状态转移可在 JVM 单测中穷举；
 * 与 Compose 的序列化适配（Saver）放在 MainShell.kt，避免把 UI 依赖带进状态模型。
 */

enum class MainTab(val label: String) {
    HOME("首页"),
    TREND("趋势"),
    DEVICE("设备"),
    MINE("我的"),
}

/** 模态记录表单（全屏替换层）。type 见 RECORD_TYPE_TITLES，preset 为预设值。 */
data class NavRecord(val type: String, val preset: String = "")

/** 覆盖页（叠放层，列表顺序即绘制顺序）。 */
sealed interface NavOverlay {
    data object Ai : NavOverlay
    data object Scale : NavOverlay
    data class Report(val time: Long) : NavOverlay
    data class Feature(val name: String) : NavOverlay

    /** 趋势 → 历史记录（原 TrendRoute 的局部布尔，P1 并入）。 */
    data object History : NavOverlay

    /** 我的 → 设置（原 MineRoute 的局部布尔，P1 并入）。 */
    data object Settings : NavOverlay

    /** 我的 → 成员管理（原 MineRoute 的局部布尔，P1 并入）。 */
    data object Members : NavOverlay

    // ---- DEC-043 新增覆盖页 ----
    data object Habit : NavOverlay
    data object Guide : NavOverlay
    data object HistoryCompare : NavOverlay
    data object UnitSettings : NavOverlay
    data object PrivacySettings : NavOverlay
    data object SyncStatus : NavOverlay
    data object PasswordLogin : NavOverlay
    data object SetPassword : NavOverlay
}

/** 导航意图。所有页面跳转都必须表达成这里的一种事件。 */
sealed interface NavEvent {
    /** 切到某页签：回到根，清空记录表单与覆盖页。 */
    data class SelectTab(val tab: MainTab) : NavEvent

    /** 打开记录表单（模态替换层，清空覆盖页）。 */
    data class OpenRecord(val type: String, val preset: String = "") : NavEvent

    data object CloseRecord : NavEvent

    /** 压入一层覆盖页。 */
    data class Push(val overlay: NavOverlay) : NavEvent

    /** 替换栈顶（称重页测完直接看报告，不叠两层）。 */
    data class ReplaceTop(val overlay: NavOverlay) : NavEvent

    /** 返回一层：先关记录表单，再出栈；栈空时不动（由 BackHandler 决定是否放行给系统）。 */
    data object Back : NavEvent

    data object CloseAll : NavEvent
}

data class NavState(
    val tab: MainTab = MainTab.HOME,
    val record: NavRecord? = null,
    val overlays: List<NavOverlay> = emptyList(),
) {
    /** 栈顶覆盖页；记录表单存在时它盖在一切之上，故不参与此属性。 */
    val top: NavOverlay? get() = overlays.lastOrNull()

    /** 是否存在可返回的层——BackHandler 的 enabled 与页签选中态都以此为准。 */
    val canPop: Boolean get() = record != null || overlays.isNotEmpty()

    fun reduce(event: NavEvent): NavState = when (event) {
        is NavEvent.SelectTab -> NavState(tab = event.tab)

        is NavEvent.OpenRecord -> NavState(tab = tab, record = NavRecord(event.type, event.preset))

        NavEvent.CloseRecord -> copy(record = null)

        is NavEvent.Push -> copy(record = null, overlays = overlays + event.overlay)

        is NavEvent.ReplaceTop -> copy(
            record = null,
            overlays = if (overlays.isEmpty()) {
                listOf(event.overlay)
            } else {
                overlays.dropLast(1) + event.overlay
            },
        )

        NavEvent.Back -> when {
            record != null -> copy(record = null)
            overlays.isNotEmpty() -> copy(overlays = overlays.dropLast(1))
            else -> this
        }

        NavEvent.CloseAll -> copy(record = null, overlays = emptyList())
    }
}
