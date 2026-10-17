package com.xcwk.scalehub.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.xcwk.scalehub.App
import com.xcwk.scalehub.feature.ai.AiRoute
import com.xcwk.scalehub.feature.auth.AuthGateViewModel
import com.xcwk.scalehub.feature.auth.AuthScreen
import com.xcwk.scalehub.feature.device.DeviceRoute
import com.xcwk.scalehub.feature.featurepage.FeatureRoute
import com.xcwk.scalehub.feature.history.HistoryRoute
import com.xcwk.scalehub.feature.measure.MeasureScreen
import com.xcwk.scalehub.feature.measure.scale.ScaleRoute
import com.xcwk.scalehub.feature.member.MembersRoute
import com.xcwk.scalehub.feature.mine.MineRoute
import com.xcwk.scalehub.feature.record.RECORD_TYPE_TITLES
import com.xcwk.scalehub.feature.record.RecordRoute
import com.xcwk.scalehub.feature.report.ReportRoute
import com.xcwk.scalehub.feature.settings.SettingsRoute
import com.xcwk.scalehub.feature.trend.TrendRoute
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.components.QkGlyph
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkIconButton
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * M4 导航壳 —— 需求文档信息架构的底部四页签（首页/趋势/设备/我的）。
 *
 * 决策口径（DEC-021 执行记录）：
 *  - 页签切换是宿主级导航状态，由 shell 自持（对应导航库自身的状态域），
 *    业务状态仍归各 Feature 的 ViewModel——不违反"状态归 VM"纪律。
 *  - 导航状态自本次起收敛为 [NavState] 单一事实源（页签 + 记录表单 + 覆盖页栈），
 *    不再用并列布尔表达层级：优先级由栈序决定，返回键统一走 [NavEvent.Back]。
 *    navigation 库仍不引入——类型安全 Route 的 A3 假设要等详情栈稳定后再验证。
 *  - 图标取 material-icons-core 子集（DateRange 代趋势/List 代设备），
 *    蓝牙等 extended 图标随 M3 依赖引入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell() {
    // 首启门（需求 3.1）：未进入前只见登录/游客门——VM 提升到壳层保单实例
    val authVm: AuthGateViewModel = viewModel(factory = AuthGateViewModel.Factory)
    val authState by authVm.state.collectAsState()
    if (!authState.entered) {
        AuthScreen(state = authState, onIntent = authVm::onIntent)
        return
    }

    var nav by rememberSaveable(stateSaver = NavStateSaver) { mutableStateOf(NavState()) }
    // 快捷记录面板是控件态（瞬时弹层），不属于导航层：不进 NavState，也不参与返回栈。
    var showQuick by rememberSaveable { mutableStateOf(false) }

    val appContext = LocalContext.current.applicationContext as App
    val container = appContext.container

    // 系统返回键：栈内有层则出栈；栈空时不拦截，交给系统结束 Activity
    BackHandler(enabled = nav.canPop) { nav = nav.reduce(NavEvent.Back) }

    fun go(event: NavEvent) {
        nav = nav.reduce(event)
        showQuick = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 详情页自带返回头（← 标题），壳层品牌栏在其上会形成双标题；
            // 副标题也只描述四个页签，压栈后即失真。故栈内有层时让位。
            if (!nav.canPop) {
                QkTopBar(subtitle = subtitleFor(nav.tab))
            }
        },
        bottomBar = {
            QkBottomNav(
                tab = nav.tab,
                canPop = nav.canPop,
                onTab = { go(NavEvent.SelectTab(it)) },
                onAdd = {
                    nav = nav.reduce(NavEvent.CloseAll)
                    showQuick = true
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            val record = nav.record
            if (record != null) {
                // 记录表单是模态替换层：下层页面不参与组合（旧实现语义，避免蓝牙会话常驻）
                RecordRoute(
                    type = record.type,
                    preset = record.preset,
                    onDone = { go(NavEvent.CloseRecord) },
                )
            } else {
                when (nav.tab) {
                    MainTab.HOME -> MeasureScreen(
                        onOpenScale = { go(NavEvent.Push(NavOverlay.Scale)) },
                        onOpenReport = { time -> go(NavEvent.Push(NavOverlay.Report(time))) },
                        onOpenHistory = { go(NavEvent.SelectTab(MainTab.TREND)) },
                        onAskAi = { q ->
                            container.aiPrefillText = q
                            go(NavEvent.Push(NavOverlay.Ai))
                        },
                        onOpenRecord = { type, preset -> go(NavEvent.OpenRecord(type, preset)) },
                        onOpenFeature = { name -> go(NavEvent.Push(NavOverlay.Feature(name))) },
                        onOpenMine = { go(NavEvent.SelectTab(MainTab.MINE)) },
                        onOpenQuick = { showQuick = true },
                    )
                    MainTab.TREND -> TrendRoute(
                        onOpenHistory = { go(NavEvent.Push(NavOverlay.History)) },
                    )
                    MainTab.DEVICE -> DeviceRoute()
                    MainTab.MINE -> MineRoute(
                        onOpenSettings = { go(NavEvent.Push(NavOverlay.Settings)) },
                        onOpenMembers = { go(NavEvent.Push(NavOverlay.Members)) },
                        onOpenHistory = { go(NavEvent.Push(NavOverlay.History)) },
                        onOpenDevice = { go(NavEvent.SelectTab(MainTab.DEVICE)) },
                    )
                }
                // 覆盖页按栈序叠放：下层保持组合态，返回即回到刚才页面（需求口径）
                nav.overlays.forEach { overlay ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        OverlayHost(
                            overlay = overlay,
                            onBack = { go(NavEvent.Back) },
                            onAskAi = { go(NavEvent.Push(NavOverlay.Ai)) },
                            onReplaceWithReport = { time ->
                                go(NavEvent.ReplaceTop(NavOverlay.Report(time)))
                            },
                            onPushReport = { time -> go(NavEvent.Push(NavOverlay.Report(time))) },
                        )
                    }
                }
            }
        }
    }

    // 快捷记录面板（对齐原型 quick modal：AI 入口 + 8 项记录网格 + 绑定入口）
    if (showQuick) {
        ModalBottomSheet(
            onDismissRequest = { showQuick = false },
            // 半展态会把最后一行记录项切在屏幕边缘，像被裁掉了；面板本来就矮，直接全展。
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = QkColor.Bg,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "快捷记录",
                        style = MaterialTheme.typography.titleLarge,
                        color = QkColor.Fg,
                        modifier = Modifier.weight(1f),
                    )
                    QkIconButton("×") { showQuick = false }
                }

                QuickAiRow(
                    modifier = Modifier.padding(top = 16.dp),
                    onClick = { go(NavEvent.Push(NavOverlay.Ai)) },
                )

                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    RECORD_TYPE_TITLES.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            row.forEach { (type, title) ->
                                QuickTile(
                                    icon = quickIcon(type),
                                    label = quickLabel(title),
                                    tone = quickTone(type),
                                    modifier = Modifier.weight(1f),
                                    onClick = { go(NavEvent.OpenRecord(type)) },
                                )
                            }
                            repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }

                QkCard(
                    modifier = Modifier.padding(top = 12.dp),
                    onClick = { go(NavEvent.SelectTab(MainTab.DEVICE)) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QkGlyph("秤", QkGlyphTone.Green)
                        Column(modifier = Modifier.weight(1f).padding(start = 11.dp)) {
                            Text("绑定体脂秤", style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                            Text(
                                "搜索并绑定附近的体脂秤",
                                style = MaterialTheme.typography.bodySmall,
                                color = QkColor.Muted,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text("›", style = MaterialTheme.typography.titleMedium, color = QkColor.Muted)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/** 面板里的 AI 入口：整宽次级底，视觉上高于普通记录项。 */
@Composable
private fun QuickAiRow(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(QkColor.Mint)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkGlyph("✧", QkGlyphTone.Green)
        Column(modifier = Modifier.weight(1f).padding(start = 11.dp)) {
            Text("AI 记录", style = MaterialTheme.typography.titleSmall, color = QkColor.PrimaryDark)
            Text(
                "问减脂饮食与体测指标；游客需手机号登录",
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.PrimaryDark.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = QkColor.PrimaryDark)
    }
}

/** 记录项磁贴（原型 .quick）：圆形浅底图标 + 名词。 */
@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    tone: QkGlyphTone,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(QkColor.Card)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QkIconBubble(icon, tone, 36.dp)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.GhostInk,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun quickIcon(type: String): ImageVector = when (type) {
    "weight" -> QkIcons.Weight
    "diet" -> QkIcons.Meal
    "exercise" -> QkIcons.Exercise
    "water" -> QkIcons.Water
    "waist" -> QkIcons.Waist
    "glucose" -> QkIcons.Glucose
    "blood_pressure" -> QkIcons.BloodPressure
    "habit" -> QkIcons.Habit
    else -> QkIcons.More
}

private fun quickTone(type: String): QkGlyphTone = when (type) {
    "exercise", "water" -> QkGlyphTone.Blue
    "glucose", "blood_pressure" -> QkGlyphTone.Purple
    "waist" -> QkGlyphTone.Orange
    else -> QkGlyphTone.Green
}

/** 标题去掉动词前缀，磁贴里只留名词（「添加体重」→「体重」）。 */
private fun quickLabel(title: String): String = when {
    title.startsWith("添加") -> title.removePrefix("添加")
    title.startsWith("记") -> title.removePrefix("记")
    title.startsWith("去") -> title.removePrefix("去")
    else -> title
}

/** 覆盖页装配点：栈里每层只认自己的参数，宿主不判断优先级。 */
@Composable
private fun OverlayHost(
    overlay: NavOverlay,
    onBack: () -> Unit,
    onAskAi: () -> Unit,
    onReplaceWithReport: (Long) -> Unit,
    onPushReport: (Long) -> Unit,
) {
    when (overlay) {
        NavOverlay.Ai -> AiRoute(onBack = onBack)
        NavOverlay.Scale -> ScaleRoute(onBack = onBack, onOpenReport = onReplaceWithReport)
        is NavOverlay.Report -> ReportRoute(time = overlay.time, onBack = onBack, onAskAi = onAskAi)
        is NavOverlay.Feature -> FeatureRoute(name = overlay.name, onBack = onBack)
        NavOverlay.History -> HistoryRoute(onBack = onBack, onOpenReport = onPushReport)
        NavOverlay.Settings -> SettingsRoute(onBack = onBack)
        NavOverlay.Members -> MembersRoute(onBack = onBack)
    }
}

/**
 * 进程重建的序列化适配：把整个导航状态压成一个字符串列表。
 * 旧实现用 7 个独立 rememberSaveable，只能存「哪些页面开着」，存不下「以什么顺序开的」；
 * 这里栈序被完整保存，恢复后顶层页面与用户离开时一致。
 */
private const val FIELD_SEP = '\u0001'

private val NavStateSaver: Saver<NavState, Any> = listSaver(
    save = { state ->
        buildList {
            add(state.tab.name)
            add(state.record?.let { "r$FIELD_SEP${it.type}$FIELD_SEP${it.preset}" } ?: "")
            state.overlays.forEach { overlay ->
                add(
                    when (overlay) {
                        NavOverlay.Ai -> "ai"
                        NavOverlay.Scale -> "scale"
                        is NavOverlay.Report -> "report$FIELD_SEP${overlay.time}"
                        is NavOverlay.Feature -> "feature$FIELD_SEP${overlay.name}"
                        NavOverlay.History -> "history"
                        NavOverlay.Settings -> "settings"
                        NavOverlay.Members -> "members"
                    },
                )
            }
        }
    },
    restore = { saved ->
        NavState(
            tab = MainTab.values().firstOrNull { it.name == saved.firstOrNull() } ?: MainTab.HOME,
            record = saved.getOrNull(1)
                ?.takeIf { it.isNotEmpty() }
                ?.split(FIELD_SEP)
                ?.let { parts ->
                    NavRecord(
                        type = parts.getOrElse(1) { "" },
                        preset = parts.getOrElse(2) { "" },
                    )
                },
            overlays = saved.drop(2).mapNotNull { raw ->
                when {
                    raw == "ai" -> NavOverlay.Ai
                    raw == "scale" -> NavOverlay.Scale
                    raw == "history" -> NavOverlay.History
                    raw == "settings" -> NavOverlay.Settings
                    raw == "members" -> NavOverlay.Members
                    raw.startsWith("report$FIELD_SEP") ->
                        raw.substringAfter(FIELD_SEP).toLongOrNull()?.let { NavOverlay.Report(it) }
                    raw.startsWith("feature$FIELD_SEP") -> NavOverlay.Feature(raw.substringAfter(FIELD_SEP))
                    else -> null
                }
            },
        )
    },
)

@Composable
private fun SheetRow(title: String, enabled: Boolean, hint: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 顶栏副标题随页签变化（原型 pageTitles 口径）。 */
private fun subtitleFor(tab: MainTab): String = when (tab) {
    MainTab.HOME -> "关注身体，记录更好的自己"
    MainTab.TREND -> "用趋势理解身体变化"
    MainTab.DEVICE -> "管理你的健康设备"
    MainTab.MINE -> "你的健康档案"
}

@Composable
private fun PlaceholderTab(title: String, hint: String) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(16.dp)) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        QkCard(modifier = Modifier.padding(top = 16.dp)) { QkEmptyHint(hint) }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainShellPreview() {
    QkTheme { MainShell() }
}
