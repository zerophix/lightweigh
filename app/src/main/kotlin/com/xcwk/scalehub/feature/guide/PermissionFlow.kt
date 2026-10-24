package com.xcwk.scalehub.feature.guide

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkComponentSize
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * guide — 启动边界后三屏：权限说明 / 授权完成 / 拒绝引导（T-075 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/02-onboarding-permission.html`：
 * - `v-permissions` 权限说明：蓝牙 / 附近设备 / 通知三项用途 + 「继续（触发系统授权）」
 * - `v-granted` 授权完成：就绪态 + 更新提示弹窗（v0.2.0）
 * - `v-denied` 拒绝引导：两步开启方法 + 「去设置开启」/「暂不连接」
 *
 * 闪屏与条款页在 `SplashScreen.kt`（同包，同一启动边界的前两屏）。
 *
 * ## 验收（AC-V5 权限用时申请：先说明再系统弹窗）
 * - 三项权限各自说明用途后才出现系统弹窗：`继续` 只上报意图
 *   （[PermissionFlowIntent.PermissionRequested]），真正弹系统授权框由宿主经
 *   [PermissionFlow] 的 onPermissionRequested 触发，本文件不引用任何权限 API
 * - 授权结果分支由纯函数 [permissionFlowReduce] 决定，JVM 单测覆盖
 * - 拒绝页给出两步开启方法与「去设置开启」（跳设置经回调 onOpenSettings）
 *
 * ## 边界（呈现式组件）
 * 不建 ViewModel、不读写存储、不调系统 API（含 ConnectivityManager / Settings）；
 * 原型里模拟系统弹窗的 `dlg-sys` 不在此渲染——系统弹窗由平台绘制。
 */

// ---- 静态文案（逐字取自原型 v-permissions / v-granted / v-denied，JVM 单测校验） ----

// v-permissions
const val PERMISSION_HEADLINE = "需要几项权限，把体脂秤连起来"
const val PERMISSION_SUBLINE = "只用于连接设备与提醒测量。"
const val PERMISSION_BLUETOOTH_TITLE = "蓝牙"
const val PERMISSION_BLUETOOTH_DESC = "用于搜索并连接体脂秤"
const val PERMISSION_NEARBY_TITLE = "附近设备"
const val PERMISSION_NEARBY_DESC = "Android 12+ 发现体脂秤所需"
const val PERMISSION_NOTIFICATION_TITLE = "通知"
const val PERMISSION_NOTIFICATION_DESC = "可选，用于测量提醒"
const val PERMISSION_CONTINUE = "继续（触发系统授权）"

// v-granted
const val GRANTED_TITLE = "权限已就绪"
const val GRANTED_BODY = "现在可以连接体脂秤了。"
const val GRANTED_UPDATE_ENTRY = "看更新提示弹窗"

/**
 * 完成页主按钮：原型此处是演示用的「重新走一遍」，正式流程应由宿主接设备连接，
 * 故按钮文案取产品语义，流程不闭环的演示动作不搬进产品代码。
 */
const val GRANTED_CONNECT_DEVICE = "连接体脂秤"

// v-granted 更新弹窗
const val UPDATE_DIALOG_TITLE = "发现新版本 v0.2.0"
const val UPDATE_DIALOG_BODY = "新增身体评分、优化蓝牙稳定性。"
const val UPDATE_LATER = "稍后"
const val UPDATE_NOW = "立即更新"

// v-denied
const val DENIED_HEADLINE = "需要蓝牙权限才能连接体脂秤"
const val DENIED_SUBLINE = "你拒绝了蓝牙权限，暂时无法连接设备。"
const val DENIED_METHOD_TITLE = "开启方法"
const val DENIED_STEP_OPEN = "点击下方「去设置开启」"
const val DENIED_STEP_PATH = "设置 › 应用 › 轻衡 › 权限 › 蓝牙"
const val DENIED_OPEN_SETTINGS = "去设置开启"
const val DENIED_SKIP = "暂不连接"

// ---- 权限项与状态机（纯函数，JVM 单测钉死） ----

/** 启动阶段申请的三项权限。 */
enum class PermissionKind { Bluetooth, NearbyDevices, Notifications }

/**
 * 权限说明行。
 *
 * [required] 为 false 的（通知）不参与阻断判断：拒绝它不影响设备连接。
 */
data class PermissionItem(
    val kind: PermissionKind,
    val title: String,
    val desc: String,
    val required: Boolean,
)

/** 权限说明页三行（原型 v-permissions 顺序：蓝牙 → 附近设备 → 通知）。 */
val PERMISSION_ITEMS = listOf(
    PermissionItem(PermissionKind.Bluetooth, PERMISSION_BLUETOOTH_TITLE, PERMISSION_BLUETOOTH_DESC, required = true),
    PermissionItem(PermissionKind.NearbyDevices, PERMISSION_NEARBY_TITLE, PERMISSION_NEARBY_DESC, required = true),
    PermissionItem(PermissionKind.Notifications, PERMISSION_NOTIFICATION_TITLE, PERMISSION_NOTIFICATION_DESC, required = false),
)

/** 系统授权结果（由宿主读回后回传，本组件不查询权限状态）。 */
enum class PermissionResult { Granted, Denied }

/** 权限流程三态（与原型 v-permissions / v-granted / v-denied 对应）。 */
enum class PermissionFlowStep { Explain, Granted, Denied }

/**
 * 权限流程呈现态。
 *
 * [granted] / [denied] 是已回传的结果集合，[step] 由 [blockingPermissionStep]
 * 从这两集合推导——不额外存一份「当前第几屏」，避免双份状态不一致。
 */
data class PermissionFlowUiState(
    val step: PermissionFlowStep = PermissionFlowStep.Explain,
    val granted: Set<PermissionKind> = emptySet(),
    val denied: Set<PermissionKind> = emptySet(),
    val updateDialogVisible: Boolean = false,
)

/** 权限流程意图：申请、结果回传、跳设置、跳过连接、更新提示弹窗。 */
sealed interface PermissionFlowIntent {
    /** 用户点「继续（触发系统授权）」：只上报，真正弹窗由宿主执行。 */
    data class PermissionRequested(val kind: PermissionKind) : PermissionFlowIntent

    /** 系统授权回到前台后的结果回传。 */
    data class Resulted(val kind: PermissionKind, val result: PermissionResult) : PermissionFlowIntent

    /** 「去设置开启」：跳系统设置归宿主，本组件不切屏（等结果回传）。 */
    data object OpenSettings : PermissionFlowIntent

    /** 「暂不连接」：跳过设备连接回到主流程。 */
    data object SkipConnection : PermissionFlowIntent

    data object ShowUpdateDialog : PermissionFlowIntent
    data object DismissUpdateDialog : PermissionFlowIntent
}

/**
 * 纯状态转移 —— 授权结果分支的判定口径：
 * - 蓝牙被拒 → 拒绝引导页（连接设备的前置权限，AC 指定的阻断点）
 * - 蓝牙已允许 → 授权完成页（附近设备/通知的拒绝不阻断）
 * - 其余情况留在权限说明页，等蓝牙结果回来
 * - 更新提示弹窗只改 [PermissionFlowUiState.updateDialogVisible]，与 [step] 正交
 */
fun permissionFlowReduce(
    state: PermissionFlowUiState,
    intent: PermissionFlowIntent,
): PermissionFlowUiState = when (intent) {
    is PermissionFlowIntent.PermissionRequested -> state.copy(
        denied = state.denied - intent.kind,
    )

    is PermissionFlowIntent.Resulted -> {
        val granted = if (intent.result == PermissionResult.Granted) state.granted + intent.kind else state.granted - intent.kind
        val denied = if (intent.result == PermissionResult.Denied) state.denied + intent.kind else state.denied - intent.kind
        state.copy(
            step = blockingPermissionStep(granted = granted, denied = denied),
            granted = granted,
            denied = denied,
        )
    }

    PermissionFlowIntent.OpenSettings -> state
    PermissionFlowIntent.SkipConnection -> state
    PermissionFlowIntent.ShowUpdateDialog -> state.copy(updateDialogVisible = true)
    PermissionFlowIntent.DismissUpdateDialog -> state.copy(updateDialogVisible = false)
}

/**
 * 由已回传的授权结果推导当前该呈现哪一屏。
 *
 * 蓝牙是唯一阻断项：它被拒就进拒绝引导，被允许就进完成页；两者都没有时留在说明页。
 * 这个函数单独暴露，便于宿主在冷启动恢复权限态时复用同一口径。
 */
fun blockingPermissionStep(
    granted: Set<PermissionKind>,
    denied: Set<PermissionKind>,
): PermissionFlowStep = when {
    PermissionKind.Bluetooth in denied -> PermissionFlowStep.Denied
    PermissionKind.Bluetooth in granted -> PermissionFlowStep.Granted
    else -> PermissionFlowStep.Explain
}

// ---- 呈现层 ----

/**
 * 权限三态路由。
 *
 * 外部交接点（副作用都在宿主侧，本组件只上报）：
 * - [onPermissionRequested] 点「继续」：宿主据此按 kind 发起系统授权请求
 * - [onOpenSettings] 拒绝页「去设置开启」：宿主打开应用权限设置页
 * - [onSkipConnection] 拒绝页「暂不连接」：宿主跳过设备连接继续主流程
 * - [onUpdateNow] 更新弹窗「立即更新」：宿主走应用商店/下载流程
 * - [onConnectDevice] 授权完成页的主按钮：进入设备连接
 */
@Composable
fun PermissionFlow(
    state: PermissionFlowUiState,
    onIntent: (PermissionFlowIntent) -> Unit,
    modifier: Modifier = Modifier,
    onPermissionRequested: (PermissionKind) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onSkipConnection: () -> Unit = {},
    onUpdateNow: () -> Unit = {},
    onConnectDevice: () -> Unit = {},
) {
    when (state.step) {
        PermissionFlowStep.Explain -> PermissionExplainScreen(
            onContinue = {
                // 先说明再弹系统窗：说明页的「继续」是唯一的申请入口（AC-V5）。
                onIntent(PermissionFlowIntent.PermissionRequested(PermissionKind.Bluetooth))
                onPermissionRequested(PermissionKind.Bluetooth)
            },
            modifier = modifier,
        )

        PermissionFlowStep.Granted -> PermissionGrantedScreen(
            updateDialogVisible = state.updateDialogVisible,
            onShowUpdateDialog = { onIntent(PermissionFlowIntent.ShowUpdateDialog) },
            onDismissUpdateDialog = { onIntent(PermissionFlowIntent.DismissUpdateDialog) },
            onUpdateNow = {
                onIntent(PermissionFlowIntent.DismissUpdateDialog)
                onUpdateNow()
            },
            onConnectDevice = onConnectDevice,
            modifier = modifier,
        )

        PermissionFlowStep.Denied -> PermissionDeniedScreen(
            onOpenSettings = {
                onIntent(PermissionFlowIntent.OpenSettings)
                onOpenSettings()
            },
            onSkipConnection = {
                onIntent(PermissionFlowIntent.SkipConnection)
                onSkipConnection()
            },
            modifier = modifier,
        )
    }
}

/** v-permissions 权限说明页：三项用途说明在前，系统弹窗在后。 */
@Composable
fun PermissionExplainScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(Modifier.height(QkSpacing.Xl))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(QkComponentSize.SkeletonBlock)
                .clip(RoundedCornerShape(QkRadius.Card))
                .background(QkColor.Mint),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(QkIcons.Bluetooth, tint = QkColor.Primary, size = QkSpacing.Xl)
        }
        Text(
            text = PERMISSION_HEADLINE,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            style = MaterialTheme.typography.headlineSmall,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Text(
            text = PERMISSION_SUBLINE,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(QkSpacing.Xl))
        QkCard {
            PERMISSION_ITEMS.forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(QkSpacing.Sm))
                PermissionRow(item)
            }
        }

        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = PERMISSION_CONTINUE,
            modifier = Modifier.fillMaxWidth(),
            onClick = onContinue,
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/** 一条权限说明（图标泡 + 名称 + 用途，原型 .row）。 */
@Composable
private fun PermissionRow(item: PermissionItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkIconBubble(
            icon = when (item.kind) {
                PermissionKind.Bluetooth -> QkIcons.Bluetooth
                PermissionKind.NearbyDevices -> QkIcons.Scan
                PermissionKind.Notifications -> QkIcons.Bell
            },
            tone = QkGlyphTone.Green,
        )
        Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
            Text(
                text = item.desc,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
            )
        }
    }
}

/** v-granted 授权完成页（含更新提示弹窗）。 */
@Composable
fun PermissionGrantedScreen(
    updateDialogVisible: Boolean,
    onShowUpdateDialog: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    modifier: Modifier = Modifier,
    onUpdateNow: () -> Unit = {},
    onConnectDevice: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(QkComponentSize.SkeletonBlock)
                .clip(CircleShape)
                .background(QkColor.Mint),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(QkIcons.Habit, tint = QkColor.Primary, size = QkSpacing.Xl)
        }
        Text(
            text = GRANTED_TITLE,
            modifier = Modifier.padding(top = QkSpacing.Xl),
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
        )
        Text(
            text = GRANTED_BODY,
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        QkButton(
            text = GRANTED_CONNECT_DEVICE,
            modifier = Modifier.fillMaxWidth(),
            onClick = onConnectDevice,
        )
        Spacer(Modifier.height(QkSpacing.Md))
        QkButton(
            text = GRANTED_UPDATE_ENTRY,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Ghost,
            onClick = onShowUpdateDialog,
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }

    if (updateDialogVisible) {
        AlertDialog(
            onDismissRequest = onDismissUpdateDialog,
            title = { Text(UPDATE_DIALOG_TITLE) },
            text = { Text(UPDATE_DIALOG_BODY) },
            confirmButton = {
                TextButton(onClick = onUpdateNow) {
                    Text(UPDATE_NOW, color = QkColor.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUpdateDialog) {
                    Text(UPDATE_LATER, color = QkColor.Sub)
                }
            },
        )
    }
}

/** v-denied 拒绝引导页：两步开启方法 + 去设置 / 暂不连接。 */
@Composable
fun PermissionDeniedScreen(
    onOpenSettings: () -> Unit,
    onSkipConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Spacer(Modifier.height(QkSpacing.Lg))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(QkComponentSize.SkeletonBlock)
                .clip(RoundedCornerShape(QkRadius.Card))
                .background(QkColor.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(QkIcons.Bluetooth, tint = QkColor.Accent, size = QkSpacing.Xl)
        }
        Text(
            text = DENIED_HEADLINE,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Text(
            text = DENIED_SUBLINE,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(QkSpacing.Xl))
        QkCard {
            Text(
                text = DENIED_METHOD_TITLE,
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
            Spacer(Modifier.height(QkSpacing.Sm))
            DeniedStep(index = 1, text = DENIED_STEP_OPEN)
            Spacer(Modifier.height(QkSpacing.Sm))
            DeniedStep(index = 2, text = DENIED_STEP_PATH)
        }

        Spacer(Modifier.height(QkSpacing.Xl))
        QkButton(
            text = DENIED_OPEN_SETTINGS,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenSettings,
        )
        Spacer(Modifier.height(QkSpacing.Md))
        QkButton(
            text = DENIED_SKIP,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Ghost,
            onClick = onSkipConnection,
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/** 开启方法的一步：圆形序号 + 步骤文案（原型 .limit）。 */
@Composable
private fun DeniedStep(index: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
    ) {
        Box(
            modifier = Modifier
                .size(QkSpacing.Xl)
                .clip(CircleShape)
                .background(QkColor.MintSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Primary,
            )
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Fg,
        )
    }
}
