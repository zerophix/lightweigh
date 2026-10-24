package com.xcwk.scalehub.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.feature.sync.FeedbackDraft
import com.xcwk.scalehub.feature.sync.HelpCenterRoute
import com.xcwk.scalehub.feature.sync.HelpCenterStep
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow

/**
 * 接入波（第三批・设置区）的入口登记表 —— 「设置页上的哪一行，通向哪一屏」。
 *
 * ## 为什么单列一份
 * 本批接入的四屏（[SdkListScreen] / [DataRecoveryScreen] / [MaintenanceScreen] /
 * [HelpCenterRoute]）都是呈现式组件，不知道自己在导航里的位置。入口文案、归属分组、
 * 目标屏三者写在一处，[SettingsScreen] 只引一行 [SettingsEntryRows]，目标屏只在
 * [SettingsEntriesRoute] 的装配点出现一次。
 *
 * ## 边界
 * 登记表与查找函数是本文件前半段的纯 Kotlin（零 Compose 依赖），入口→屏的映射可在
 * JVM 单测里穷举；后半段才是 Compose 呈现与路由。导航状态留在设置页内部
 * （[SettingsEntriesRoute] 的 openedId），不改 [com.xcwk.scalehub.ui.shell.MainShell]。
 */

/** 入口所属分组（枚举顺序即设置页里的呈现顺序）。 */
enum class SettingsEntryGroup {
    /** 隐私与数据：隐私协议卡片内的补充入口。 */
    PrivacyAndData,

    /** 帮助：帮助中心 / 意见反馈 / 问题诊断。 */
    Help,

    /** 其他：仅维护态出现。 */
    Other,

    /** 显示：外观与语言（设置主页「显示」分组）。 */
    Display,
}

/** 入口指向的目标屏（与 [SettingsSdkRoute] / [HelpCenterRoute] 的装配点一一对应）。 */
enum class SettingsEntryTarget(val screen: String) {
    SdkList("SdkListScreen"),
    DataRecovery("DataRecoveryScreen"),
    Maintenance("MaintenanceScreen"),
    HelpCenter("HelpCenterRoute"),
    Appearance("AppearanceSettingsScreen"),
    Language("LanguageSettingsScreen"),
}

/**
 * 一条设置页入口。
 *
 * @param id 稳定标识（进程重建 / 埋点用，不随文案改动）
 * @param group 归属分组，决定它落在设置页哪一段
 * @param title 入口行标题
 * @param hint 入口行副标题（说明点进去能看到什么）
 * @param target 目标屏
 * @param helpStep 帮助中心族的入口态（仅 [SettingsEntryTarget.HelpCenter] 使用）
 * @param maintenanceOnly 只在维护态呈现（维护模式不常驻设置页）
 */
data class SettingsEntry(
    val id: String,
    val group: SettingsEntryGroup,
    val title: String,
    val hint: String,
    val target: SettingsEntryTarget,
    val helpStep: HelpCenterStep? = null,
    val maintenanceOnly: Boolean = false,
)

/** 本批入口清单。文案即设置页上那一行，改文案只改这里。 */
object SettingsEntries {
    val SdkList = SettingsEntry(
        id = "settings.sdkList",
        group = SettingsEntryGroup.PrivacyAndData,
        title = "第三方 SDK 清单",
        hint = "查看应用内使用的 SDK、用途与可选授权",
        target = SettingsEntryTarget.SdkList,
    )

    val DataRecovery = SettingsEntry(
        id = "settings.dataRecovery",
        group = SettingsEntryGroup.PrivacyAndData,
        title = "数据恢复",
        hint = "从云端按时间段恢复记录",
        target = SettingsEntryTarget.DataRecovery,
    )

    val HelpCenter = SettingsEntry(
        id = "settings.helpCenter",
        group = SettingsEntryGroup.Help,
        title = "帮助中心",
        hint = "常见问题排查，先自助后反馈",
        target = SettingsEntryTarget.HelpCenter,
        helpStep = HelpCenterStep.Help,
    )

    val Feedback = SettingsEntry(
        id = "settings.feedback",
        group = SettingsEntryGroup.Help,
        title = "意见反馈",
        hint = "描述问题并附上诊断信息",
        target = SettingsEntryTarget.HelpCenter,
        helpStep = HelpCenterStep.Feedback,
    )

    val Diagnostics = SettingsEntry(
        id = "settings.diagnostics",
        group = SettingsEntryGroup.Help,
        title = "问题诊断",
        hint = "查看可复制的应用与网络诊断摘要",
        target = SettingsEntryTarget.HelpCenter,
        helpStep = HelpCenterStep.Diagnostics,
    )

    val Maintenance = SettingsEntry(
        id = "settings.maintenance",
        group = SettingsEntryGroup.Other,
        title = "服务维护中",
        hint = "云同步与 AI 功能暂停，查看预计恢复时间",
        target = SettingsEntryTarget.Maintenance,
        maintenanceOnly = true,
    )

    val Appearance = SettingsEntry(
        id = "settings.appearance",
        group = SettingsEntryGroup.Display,
        title = "外观",
        hint = "主题模式：浅色 / 深色 / 跟随系统",
        target = SettingsEntryTarget.Appearance,
    )

    val Language = SettingsEntry(
        id = "settings.language",
        group = SettingsEntryGroup.Display,
        title = "语言",
        hint = "界面语言：简体中文 / 繁體中文 / English",
        target = SettingsEntryTarget.Language,
    )

    /** 全部入口（顺序即设置页内的呈现顺序）。 */
    val All: List<SettingsEntry> = listOf(
        SdkList,
        DataRecovery,
        HelpCenter,
        Feedback,
        Diagnostics,
        Maintenance,
        Appearance,
        Language,
    )

    /** 某分组下的入口；维护类入口只有维护态才呈现。 */
    fun visible(group: SettingsEntryGroup, maintenanceActive: Boolean = false): List<SettingsEntry> =
        All.filter { it.group == group && isVisible(it, maintenanceActive) }

    /** 按稳定 id 回查入口（进程重建 / 埋点共用）。 */
    fun find(id: String): SettingsEntry? = All.firstOrNull { it.id == id }

    /** 该入口此刻是否可点（维护类入口在非维护态隐藏）。 */
    fun isVisible(entry: SettingsEntry, maintenanceActive: Boolean): Boolean =
        !entry.maintenanceOnly || maintenanceActive
}

/** 入口 id → 目标屏。宿主按 id 查找唯一目标，避免两条入口各自写死屏名。 */
fun settingsEntryTargets(maintenanceActive: Boolean = false): Map<String, SettingsEntryTarget> =
    SettingsEntries.All
        .filter { SettingsEntries.isVisible(it, maintenanceActive) }
        .associate { it.id to it.target }

/**
 * 单个入口 id → 目标屏；id 未知或此刻不可见时返回 null（宿主据此不跳转）。
 */
fun settingsEntryTargetOf(id: String, maintenanceActive: Boolean = false): SettingsEntryTarget? {
    val entry = SettingsEntries.find(id) ?: return null
    return if (SettingsEntries.isVisible(entry, maintenanceActive)) entry.target else null
}

/** 某分组下此刻可点的入口 id 列表（测试与埋点共用）。 */
fun settingsEntryIdsFor(group: SettingsEntryGroup, maintenanceActive: Boolean = false): List<String> =
    SettingsEntries.visible(group, maintenanceActive).map { it.id }

/**
 * 设置页内部的单屏栈：null = 设置主页，否则为已打开入口的 id。
 *
 * 只做「打开 / 返回」两件事，越界 id（未知或已不可见）一律忽略，保持原状态。
 */
data class SettingsEntriesNav(val openedId: String? = null) {

    /** 当前打开的入口（主页为 null）。 */
    val openedEntry: SettingsEntry? get() = openedId?.let(SettingsEntries::find)

    /** 打开入口；不可见或未知时原样返回（幂等）。 */
    fun open(id: String, maintenanceActive: Boolean = false): SettingsEntriesNav =
        if (settingsEntryTargetOf(id, maintenanceActive) == null) this else copy(openedId = id)

    /** 回到设置主页。 */
    fun back(): SettingsEntriesNav = if (openedId == null) this else SettingsEntriesNav()
}

// ---------------------------------------------------------------------------
// 呈现：入口行与设置页内部路由装配
// ---------------------------------------------------------------------------

/** 渲染某分组下此刻可见的入口行（设置页正文用；文案与顺序来自 [SettingsEntries]）。 */
@Composable
fun SettingsEntryRows(
    group: SettingsEntryGroup,
    onOpenEntry: (String) -> Unit,
    maintenanceActive: Boolean = false,
) {
    val entries = SettingsEntries.visible(group, maintenanceActive)
    entries.forEachIndexed { index, entry ->
        QkListRow(
            title = entry.title,
            subtitle = entry.hint,
            icon = entryIcon(entry),
            glyphSize = 32.dp,
            showDivider = index != entries.lastIndex,
            onClick = { onOpenEntry(entry.id) },
            trailing = { QkIcon(QkIcons.ChevronRight, size = 18.dp) },
        )
    }
}

/**
 * 设置页装配点：在设置主页与四个目标屏之间做单屏切换。
 *
 * 账号三流程仍交给 [SettingsViewModel]；SDK / 恢复 / 帮助中心的状态由各屏自身或
 * 本装配点的本地状态持有——本文件不调用网络与存储，真实数据接入由宿主替换回调。
 *
 * @param maintenanceState 服务维护状态（宿主注入；未接探测前默认非维护态）
 * @param diagnosticsSummary 诊断摘要文本（空串 = 尚未接入，不呈现复制入口）
 */
@Composable
fun SettingsEntriesRoute(
    onBack: () -> Unit,
    onOpenUnitSettings: () -> Unit = {},
    onOpenPrivacySettings: () -> Unit = {},
    onOpenSyncStatus: () -> Unit = {},
    onOpenSetPassword: () -> Unit = {},
    maintenanceState: MaintenanceUiState = MaintenanceUiState(active = false),
    diagnosticsSummary: String = "",
    onSubmitFeedback: (FeedbackDraft) -> Unit = {},
    onViewAnnouncement: () -> Unit = {},
    onOpenSdkPolicy: (String) -> Unit = {},
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    // 只存入口 id：进程重建后按登记表回查，不序列化整张表。
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    val nav = SettingsEntriesNav(openedId)
    var sdkState by remember { mutableStateOf(SdkListUiState()) }
    var recoveryState by remember { mutableStateOf(RecoveryUiState()) }
    var maintenance by remember { mutableStateOf(maintenanceState) }
    // 显示设置（外观 / 语言）：选择态在本装配点持有，持久化仍归宿主（两屏本身不带 VM）
    var appearanceState by remember { mutableStateOf(AppearanceSettingsUiState()) }
    var languageState by remember { mutableStateOf(LanguageSettingsUiState()) }
    val maintenanceActive = maintenance.active

    val state by vm.state.collectAsStateWithLifecycle()
    val entry = nav.openedEntry?.takeIf { SettingsEntries.isVisible(it, maintenanceActive) }
    val backToSettings: () -> Unit = { openedId = nav.back().openedId }

    if (entry == null) {
        SettingsScreen(
            state = state,
            onIntent = vm::onIntent,
            onBack = onBack,
            onOpenUnitSettings = onOpenUnitSettings,
            onOpenPrivacySettings = onOpenPrivacySettings,
            onOpenSyncStatus = onOpenSyncStatus,
            onOpenSetPassword = onOpenSetPassword,
            onOpenEntry = { id -> openedId = nav.open(id, maintenanceActive).openedId },
            maintenanceActive = maintenanceActive,
        )
    } else when (entry.target) {
        SettingsEntryTarget.SdkList -> SettingsSdkListRoute(
            state = sdkState,
            onToggleSdkConsent = { id, granted -> sdkState = sdkState.withSdkConsent(id, granted) },
            onOpenSdkPolicy = onOpenSdkPolicy,
            onBack = backToSettings,
        )

        SettingsEntryTarget.DataRecovery -> SettingsDataRecoveryRoute(
            state = recoveryState,
            // 真实恢复进度由宿主接 RestoreAccountService 回灌，装配点不伪造进度。
            onStartRecovery = {},
            onCancelRecovery = { recoveryState = recoveryState.reduce(RecoveryIntent.Reset) },
            onBack = backToSettings,
            onCustomRangeChange = { start, end ->
                recoveryState = recoveryState.reduce(RecoveryIntent.ChangeCustomRange(start, end))
            },
            onScopeSelected = { option ->
                recoveryState = recoveryState.reduce(RecoveryIntent.SelectScope(option))
            },
        )

        SettingsEntryTarget.Maintenance -> MaintenanceRoute(
            state = maintenance,
            onExitMaintenance = {
                maintenance = maintenance.applyMaintenanceRequest(MaintenanceRequest.Exit)
            },
            onViewAnnouncement = onViewAnnouncement,
            onBack = backToSettings,
        )

        SettingsEntryTarget.HelpCenter -> key(entry.id) {
            HelpCenterRoute(
                onBack = backToSettings,
                onSubmitFeedback = onSubmitFeedback,
                initialStep = entry.helpStep ?: HelpCenterStep.Help,
                diagnosticsSummary = diagnosticsSummary,
            )
        }

        SettingsEntryTarget.Appearance -> AppearanceSettingsScreen(
            state = appearanceState,
            onIntent = { appearanceState = appearanceState.reduce(it) },
            onBack = backToSettings,
        )

        SettingsEntryTarget.Language -> LanguageSettingsScreen(
            state = languageState,
            onIntent = { languageState = languageState.reduce(it) },
            onBack = backToSettings,
        )
    }
}

/** 入口图标（按目标屏与帮助中心入口态选取，图标不参与登记表纯数据）。 */
private fun entryIcon(entry: SettingsEntry): ImageVector = when {
    entry.id == SettingsEntries.SdkList.id -> QkIcons.Share
    entry.id == SettingsEntries.DataRecovery.id -> QkIcons.Restore
    entry.id == SettingsEntries.Maintenance.id -> QkIcons.Bell
    entry.id == SettingsEntries.Appearance.id -> QkIcons.Setting
    entry.id == SettingsEntries.Language.id -> QkIcons.More
    entry.helpStep == HelpCenterStep.Diagnostics -> QkIcons.Cache
    entry.helpStep == HelpCenterStep.Feedback -> QkIcons.Send
    else -> QkIcons.About
}
