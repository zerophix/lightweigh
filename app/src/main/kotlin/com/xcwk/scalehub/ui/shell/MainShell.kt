package com.xcwk.scalehub.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ExperimentalMaterial3Api
import com.xcwk.scalehub.feature.record.RECORD_TYPE_TITLES
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.feature.measure.MeasureScreen
import com.xcwk.scalehub.feature.mine.MineRoute
import com.xcwk.scalehub.feature.trend.TrendRoute
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkEmptyHint
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * M4 导航壳 —— 需求文档信息架构的底部四页签（首页/趋势/设备/我的）。
 *
 * 决策口径（DEC-021 执行记录）：
 *  - 页签切换是宿主级导航状态，由 shell 自持（对应导航库自身的状态域），
 *    业务状态仍归各 Feature 的 ViewModel——不违反"状态归 VM"纪律。
 *  - 暂用 Compose 状态 + 页签直挂，不引 navigation 库：类型安全路由的
 *    A3 假设要等详情栈（报告/历史/设置）出现才验证；TREND_ROUTE_NAME 等
 *    契约常量已就位，届时映射为类型安全 Route。
 *  - 图标取 material-icons-core 子集（DateRange 代趋势/List 代设备），
 *    蓝牙等 extended 图标随 M3 依赖引入。
 */
enum class MainTab(val label: String) {
    HOME("首页"),
    TREND("趋势"),
    DEVICE("设备"),
    MINE("我的"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell() {
    // 首启门（需求 3.1）：未进入前只见登录/游客门——VM 提升到壳层保单实例
    val authVm: com.xcwk.scalehub.feature.auth.AuthGateViewModel =
        viewModel(factory = com.xcwk.scalehub.feature.auth.AuthGateViewModel.Factory)
    val authState by authVm.state.collectAsState()
    if (!authState.entered) {
        com.xcwk.scalehub.feature.auth.AuthScreen(state = authState, onIntent = authVm::onIntent)
        return
    }
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var showQuick by rememberSaveable { mutableStateOf(false) }
    // AI 问答是宿主级覆盖页（与记录表单同层：打开即回到刚才页面）
    var showAi by rememberSaveable { mutableStateOf(false) }
    var showScale by rememberSaveable { mutableStateOf(false) }
    // 报告覆盖页（测量保存后查看报告入口，M4）：-1=未打开
    var reportTime by rememberSaveable { mutableStateOf(-1L) }
    var recordPreset by rememberSaveable { mutableStateOf("") }
    var featureName by rememberSaveable { mutableStateOf("") }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.xcwk.scalehub.App
    // 记录表单是宿主级覆盖页（任何页签打开，导航即关闭）——例外域同 Route 层声明
    var recordType by rememberSaveable { mutableStateOf("") }
    Scaffold(
        bottomBar = {
            NavigationBar {
                @Composable
                fun item(t: MainTab) = NavigationBarItem(
                    selected = tab == t && recordType.isEmpty() && !showAi && !showScale,
                    onClick = { recordType = ""; showQuick = false; showAi = false; showScale = false; tab = t },
                    icon = { Icon(iconFor(t), contentDescription = t.label) },
                    label = { Text(t.label) },
                )
                item(MainTab.HOME)
                item(MainTab.TREND)
                // 中央加号（需求：底部四页 + 中央加号打开快捷记录）
                NavigationBarItem(
                    selected = false,
                    onClick = { recordType = ""; showQuick = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "快捷记录") },
                    label = { Text("记录") },
                )
                item(MainTab.DEVICE)
                item(MainTab.MINE)
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                recordType.isNotEmpty() -> com.xcwk.scalehub.feature.record.RecordRoute(
                    type = recordType,
                    preset = recordPreset,
                    onDone = { recordType = ""; recordPreset = "" },
                )
                tab == MainTab.HOME -> MeasureScreen(
                    onOpenScale = { showScale = true },
                    onOpenReport = { time -> reportTime = time },
                    onOpenHistory = { tab = MainTab.TREND },
                    onAskAi = { q ->
                        appContext.container.aiPrefillText = q
                        showAi = true
                    },
                    onOpenRecord = { type, preset ->
                        recordType = type
                        recordPreset = preset
                    },
                    onOpenFeature = { name -> featureName = name },
                    onOpenMine = { tab = MainTab.MINE },
                )
                tab == MainTab.TREND -> TrendRoute(onAskAi = { showAi = true })
                tab == MainTab.DEVICE -> com.xcwk.scalehub.feature.device.DeviceRoute()
                else -> MineRoute()   // 设置页已接入；档案编辑已接入
            }
            // 宿主级覆盖页叠放而非替换：下层页面保持组合态，返回即回到刚才页面（需求口径）
            val overlayOpen = recordType.isEmpty()
            if (overlayOpen && showAi) {
                androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    com.xcwk.scalehub.feature.ai.AiRoute(onBack = { showAi = false })
                }
            }
            if (overlayOpen && !showAi && showScale) {
                androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    com.xcwk.scalehub.feature.measure.scale.ScaleRoute(
                        onBack = { showScale = false },
                        onOpenReport = { time ->
                            showScale = false
                            reportTime = time
                        },
                    )
                }
            }
            if (overlayOpen && featureName.isNotEmpty()) {
                androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    com.xcwk.scalehub.feature.featurepage.FeatureRoute(name = featureName, onBack = { featureName = "" })
                }
            }
            if (overlayOpen && !showAi && !showScale && reportTime >= 0L && featureName.isEmpty()) {
                androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    com.xcwk.scalehub.feature.report.ReportRoute(
                        time = reportTime,
                        onBack = { reportTime = -1L },
                        onAskAi = { showAi = true },
                    )
                }
            }
        }
    }

    // 快捷记录面板（需求：AI 记录 + 8 项健康记录 + 绑定入口；不可用项如实置灰）
    if (showQuick) {
        ModalBottomSheet(onDismissRequest = { showQuick = false }) {
            SheetRow("AI 记录", enabled = true, hint = "问减脂饮食与体测指标；游客需手机号登录") {
                showQuick = false
                showAi = true
            }
            RECORD_TYPE_TITLES.forEach { (type, title) ->
                SheetRow(title, enabled = true) {
                    showQuick = false
                    recordType = type
                }
            }
            SheetRow("绑定体脂秤", enabled = true, hint = "搜索并绑定附近的体脂秤") {
                showQuick = false
                showScale = false
                tab = MainTab.DEVICE
            }
            Spacer(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

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

private fun iconFor(tab: MainTab): ImageVector = when (tab) {
    MainTab.HOME -> Icons.Filled.Home
    MainTab.TREND -> Icons.Filled.DateRange
    MainTab.DEVICE -> Icons.Filled.List
    MainTab.MINE -> Icons.Filled.Person
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
