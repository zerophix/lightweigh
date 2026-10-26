package com.xcwk.scalehub.feature.device

import com.xcwk.scalehub.ui.components.QkHeaderStyle
import com.xcwk.scalehub.ui.components.QkScreenHeader
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * device — 设备固件管理（T-079 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/18-device-setup.html` 的三个 view：
 * - `v-firmware-check` 固件检查结果：设备行（当前 v1.1.0 · 可更新）+ 新版本 v1.2.0 四条更新内容
 *   + 更新期间提示 + 「立即更新」「暂不更新」
 * - `v-firmware-update` 升级中：进度环（70%）+ 剩余时间 + 中断警示 + 「模拟更新完成」
 * - `v-firmware-latest` 设备就绪：固件已是最新版本 + 「开始测量」「返回」
 *
 * ## 验收（AC-D4 固件检测与更新提示）
 * - 有更新时展示可更新状态、新版本号与更新内容，可立即更新或暂不更新
 * - 升级中展示进度百分比与剩余时间，并始终保留中断警示
 * - 无更新 / 升级完成后进入设备就绪态，可开始首次测量
 *
 * ## 边界（呈现式组件）
 * 本文件**不发起蓝牙 / OTA / 网络请求**：固件版本检查结果由外部经
 * [FirmwareIntent.CheckResult] 注入，升级进度经 [FirmwareIntent.Progress] 注入；
 * 组件只在需要时通过 [FirmwareManager] 的回调请求外部做事：
 * - [FirmwareManager.onCheckFirmware] 进入流程时请求检查一次固件版本
 * - [FirmwareManager.onStartUpdate] 用户点「立即更新」，由外部启动 OTA
 * - [FirmwareManager.onDismiss] 关闭本流程（返回键 / 就绪页「返回」）
 * - [FirmwareManager.onStartMeasure] 就绪页「开始测量」，交给测量流程
 *
 * ## 不变量（JVM 单测钉死）
 * 1. 进度未达 100% 时状态机绝不进入 [FirmwareStep.Latest]，因此「成功态」与
 *    「可以开始测量」不会在升级中途出现；
 * 2. 升级中页始终渲染中断警示 [UPDATE_INTERRUPT_WARNING]，且返回键无效（禁止静默退出）；
 * 3. 完成态一经到达不会因迟到的低进度事件回退。
 */

// ---- 三态状态机 ----

/** 固件流程的三个呈现态（与原型三个 view 一一对应）。 */
enum class FirmwareStep { Check, Updating, Latest }

/**
 * 呈现式 UI 状态。版本号、进度、剩余时间全部由调用方注入（真实 OTA 在外部进行）。
 *
 * @param hasUpdate 检查结果：true = 有新版本可更新；false = 已是最新
 * @param progressPercent 升级进度，构造时即收敛到 0..100
 */
data class FirmwareUiState(
    val step: FirmwareStep = FirmwareStep.Check,
    val deviceName: String = DEFAULT_DEVICE_NAME,
    val currentVersion: String = DEFAULT_CURRENT_VERSION,
    val latestVersion: String = DEFAULT_LATEST_VERSION,
    val hasUpdate: Boolean = true,
    val progressPercent: Int = 0,
    val remainingSeconds: Int = DEFAULT_REMAINING_SECONDS,
) {
    init {
        require(progressPercent in 0..100) { "progressPercent 必须落在 0..100，收到 $progressPercent" }
    }
}

/** 流程内的呈现态转移。外部副作用（OTA、导航、测量）走回调，不进 reduce。 */
sealed interface FirmwareIntent {
    /** 固件检查结果回填（外部检查完成后调用）。 */
    data class CheckResult(
        val hasUpdate: Boolean,
        val currentVersion: String? = null,
        val latestVersion: String? = null,
    ) : FirmwareIntent

    /** 用户点「立即更新」。 */
    data object StartUpdate : FirmwareIntent

    /** 外部 OTA 进度回调，百分比越界会被收敛。 */
    data class Progress(val percent: Int) : FirmwareIntent

    /** 外部确认 OTA 结束；仅在进度已达 100% 时才落到完成态。 */
    data object CompleteUpdate : FirmwareIntent

    /** 用户点「暂不更新」（原型行为：进入设备就绪页，更新机会保留）。 */
    data object SkipUpdate : FirmwareIntent
}

/**
 * 纯呈现态转移 —— JVM 单测钉死的部分。
 *
 * 口径：
 * - [FirmwareIntent.CheckResult] 决定第二态：有更新停在 [FirmwareStep.Check]，无更新直接 [FirmwareStep.Latest]
 * - [FirmwareIntent.StartUpdate] 只对「可更新」生效，无更新时点不动（防御错误外部调用）
 * - [FirmwareIntent.Progress] 只在升级中接受；< 100 停留 [FirmwareStep.Updating]，
 *   达 100 才允许进入 [FirmwareStep.Latest]
 * - [FirmwareIntent.CompleteUpdate] 在 < 100 时**不转移**（防「没升完就报成功」）
 * - 完成态单调：迟到的低进度事件不会把 [FirmwareStep.Latest] 拉回升级中
 * - [FirmwareIntent.SkipUpdate] 进入 [FirmwareStep.Latest] 但保留 [FirmwareUiState.hasUpdate]，
 *   外部据此仍可再次提示更新
 */
fun firmwareReduce(state: FirmwareUiState, intent: FirmwareIntent): FirmwareUiState = when (intent) {
    is FirmwareIntent.CheckResult -> state.copy(
        step = if (intent.hasUpdate) FirmwareStep.Check else FirmwareStep.Latest,
        hasUpdate = intent.hasUpdate,
        currentVersion = intent.currentVersion ?: state.currentVersion,
        latestVersion = intent.latestVersion ?: state.latestVersion,
        progressPercent = 0,
        remainingSeconds = DEFAULT_REMAINING_SECONDS,
    )

    FirmwareIntent.StartUpdate ->
        if (state.step == FirmwareStep.Check && state.hasUpdate) {
            state.copy(step = FirmwareStep.Updating, progressPercent = 0)
        } else {
            state
        }

    is FirmwareIntent.Progress -> when {
        state.step == FirmwareStep.Check -> state
        state.step == FirmwareStep.Latest -> state
        intent.percent >= 100 -> state.copy(step = FirmwareStep.Latest, progressPercent = 100)
        else -> state.copy(progressPercent = intent.percent.coerceIn(0, 100))
    }

    FirmwareIntent.CompleteUpdate ->
        if (canCompleteUpdate(state)) state.copy(step = FirmwareStep.Latest) else state

    FirmwareIntent.SkipUpdate ->
        if (state.step == FirmwareStep.Updating) state else state.copy(step = FirmwareStep.Latest)
}

/**
 * 「模拟更新完成」是否可点：升级中且进度已达 100%。
 *
 * 原型该按钮是演示用的模拟触发器；生产口径下未满 100% 不得展示成功态，
 * 故按此函数置灰而不是删除（文案保持原型原文）。
 */
fun canCompleteUpdate(state: FirmwareUiState): Boolean =
    state.step == FirmwareStep.Updating && state.progressPercent >= 100

/** 进度环中央的百分比文案，越界收敛（如 120% → 100%）。 */
fun firmwareProgressLabel(percent: Int): String = "${percent.coerceIn(0, 100)}%"

/** 升级中副标题：原型「请勿断开蓝牙或关闭秤 / 预计剩余 45 秒」。 */
fun firmwareUpdatingSubtitle(remainingSeconds: Int): String =
    "请勿断开蓝牙或关闭秤\n预计剩余 ${remainingSeconds.coerceAtLeast(0)} 秒"

/** 设备行副标题：原型「当前固件 v1.1.0」。 */
fun firmwareCurrentLabel(version: String): String = "当前固件 $version"

/** 新版本标签：原型「新版本 v1.2.0」。 */
fun firmwareLatestLabel(version: String): String = "新版本 $version"

// ---- 静态文案（逐字取自原型，JVM 单测校验） ----

const val DEFAULT_DEVICE_NAME = "轻衡 S1"
const val DEFAULT_CURRENT_VERSION = "v1.1.0"
const val DEFAULT_LATEST_VERSION = "v1.2.0"
const val DEFAULT_REMAINING_SECONDS = 45

const val TITLE_FIRMWARE_CHECK = "固件更新"
const val TITLE_FIRMWARE_UPDATING = "固件更新中"
const val TITLE_FIRMWARE_LATEST = "设备信息"

// v-firmware-check
const val UPDATE_AVAILABLE_TAG = "可更新"
const val CHECK_NOTE = "更新过程中请勿断开蓝牙或关闭秤，\n预计耗时 2–3 分钟。"
const val ACTION_UPDATE_NOW = "立即更新"
const val ACTION_SKIP_UPDATE = "暂不更新"

/** 新版本更新内容四条（原型 v-firmware-check 卡片，顺序与原文一致）。 */
val FIRMWARE_UPDATE_NOTES = listOf(
    "优化蓝牙连接稳定性",
    "修复特定机型测量偶发失败",
    "提升体脂率估算精度",
    "新增低电量提醒",
)

// v-firmware-update
const val UPDATING_PROGRESS_HINT = "正在更新"
const val UPDATING_HEADLINE = "正在更新固件…"
const val UPDATE_INTERRUPT_WARNING = "更新中断可能导致设备无法使用，请保持耐心。"
const val ACTION_SIMULATE_COMPLETE = "模拟更新完成"

// v-firmware-latest
const val LATEST_READY_TITLE = "设备已就绪"
const val LATEST_READY_SUBTITLE = "固件已是最新版本\n可以开始第一次测量了。"
const val ACTION_START_MEASURE = "开始测量"
const val ACTION_BACK = "返回"

// ---- 原型实测尺寸（一律由 Qk token 组合，页面不出现裸 dp 字面量） ----

/** 进度环外径（原型 150px 环）。 */
private val RingSize: Dp = QkSpacing.Xl * 6

/** 进度环描边宽度（原型 8px）。 */
private val RingStroke: Dp = QkSpacing.Sm

/** 设备行圆形头像（原型 .av 48px）。 */
private val FirmwareAvatarSize: Dp = QkSpacing.Xl * 2

/** 就绪页圆形图标（原型 .statepage .sic 88px）。 */
private val ReadyIconSize: Dp = QkSpacing.Xl * 3 + QkSpacing.Lg

/** 警示图标（原型 .banner .bic 18px）。 */
private val BannerIconSize: Dp = QkSpacing.Lg + QkSpacing.Xs / 2

// ---- 三态路由 ----

/**
 * 固件管理流程入口：按 [FirmwareUiState.step] 呈现对应 view。
 *
 * 进入时经 [onCheckFirmware] 请求一次版本检查（检查结果仍由外部经
 * [FirmwareIntent.CheckResult] 回填，组件不自己发请求）。
 */
@Composable
fun FirmwareManager(
    state: FirmwareUiState,
    onIntent: (FirmwareIntent) -> Unit,
    modifier: Modifier = Modifier,
    onCheckFirmware: () -> Unit = {},
    onStartUpdate: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onStartMeasure: () -> Unit = {},
) {
    LaunchedEffect(Unit) { onCheckFirmware() }

    when (state.step) {
        FirmwareStep.Check -> FirmwareCheckView(
            state = state,
            onIntent = onIntent,
            onDismiss = onDismiss,
            onStartUpdate = onStartUpdate,
            modifier = modifier,
        )

        FirmwareStep.Updating -> FirmwareUpdatingView(
            state = state,
            onIntent = onIntent,
            modifier = modifier,
        )

        FirmwareStep.Latest -> FirmwareLatestView(
            onDismiss = onDismiss,
            onStartMeasure = onStartMeasure,
            modifier = modifier,
        )
    }
}

// ---- v-firmware-check ----

@Composable
private fun FirmwareCheckView(
    state: FirmwareUiState,
    onIntent: (FirmwareIntent) -> Unit,
    onDismiss: () -> Unit,
    onStartUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page),
    ) {
        FirmwareNavBar(TITLE_FIRMWARE_CHECK, onDismiss)
        FirmwareDeviceRow(state)
        Text(
            firmwareLatestLabel(state.latestVersion),
            modifier = Modifier.padding(top = QkSpacing.Sm, start = QkSpacing.Xs),
            style = MaterialTheme.typography.labelMedium,
            color = QkColor.Sub,
        )
        QkCard(modifier = Modifier.padding(top = QkSpacing.Sm), padding = QkSpacing.Lg) {
            FIRMWARE_UPDATE_NOTES.forEach { note ->
                Text(
                    "· $note",
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FirmwareWarnBanner(
            text = CHECK_NOTE,
            modifier = Modifier.padding(top = QkSpacing.Md),
        )
        Spacer(modifier = Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            QkButton(ACTION_UPDATE_NOW, modifier = Modifier.fillMaxWidth()) {
                onStartUpdate()
                onIntent(FirmwareIntent.StartUpdate)
            }
            QkButton(
                ACTION_SKIP_UPDATE,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Secondary,
            ) {
                onIntent(FirmwareIntent.SkipUpdate)
            }
        }
        Spacer(modifier = Modifier.height(QkSpacing.Lg))
    }
}

/** 设备行（原型 .card.soft）：白色圆底图标 + 型号/当前固件 + 右侧状态胶囊。 */
@Composable
private fun FirmwareDeviceRow(state: FirmwareUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = QkSpacing.Md)
            .clip(RoundedCornerShape(QkRadius.Card))
            .background(QkColor.Mint)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md + QkSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(FirmwareAvatarSize)
                .clip(CircleShape)
                .background(QkColor.Card),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(QkIcons.Device, tint = QkColor.Primary, size = QkSpacing.Xl)
        }
        Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
            Text(state.deviceName, style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
            Text(
                firmwareCurrentLabel(state.currentVersion),
                modifier = Modifier.padding(top = QkSpacing.Xs / 2),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
            )
        }
        QkPill(UPDATE_AVAILABLE_TAG)
    }
}

// ---- v-firmware-update ----

@Composable
private fun FirmwareUpdatingView(
    state: FirmwareUiState,
    onIntent: (FirmwareIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page),
    ) {
        // 升级中返回键无效：中断会损坏设备，禁止静默退出（原型该返回键同样无跳转）
        FirmwareNavBar(TITLE_FIRMWARE_UPDATING, onBack = {})
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FirmwareProgressRing(percent = state.progressPercent)
            Text(
                UPDATING_HEADLINE,
                modifier = Modifier.padding(top = QkSpacing.Lg),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
            )
            Text(
                firmwareUpdatingSubtitle(state.remainingSeconds),
                modifier = Modifier.padding(top = QkSpacing.Sm),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
                textAlign = TextAlign.Center,
            )
        }
        FirmwareWarnBanner(
            text = UPDATE_INTERRUPT_WARNING,
            modifier = Modifier.padding(top = QkSpacing.Lg),
        )
        Spacer(modifier = Modifier.weight(1f))
        QkButton(
            ACTION_SIMULATE_COMPLETE,
            modifier = Modifier.fillMaxWidth(),
            enabled = canCompleteUpdate(state),
        ) {
            onIntent(FirmwareIntent.CompleteUpdate)
        }
        Spacer(modifier = Modifier.height(QkSpacing.Lg))
    }
}

/** 进度环（原型 150px 环 + 中央 32px 百分比 + 「正在更新」提示）。 */
@Composable
private fun FirmwareProgressRing(percent: Int) {
    val clamped = percent.coerceIn(0, 100)
    Box(modifier = Modifier.size(RingSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = QkColor.ScoreTrack,
                radius = radius,
                center = center,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = QkColor.Primary,
                startAngle = -90f,
                sweepAngle = 360f * (clamped / 100f),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                firmwareProgressLabel(clamped),
                style = MaterialTheme.typography.displayLarge,
                color = QkColor.Fg,
            )
            Text(
                UPDATING_PROGRESS_HINT,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Muted,
            )
        }
    }
}

// ---- v-firmware-latest ----

@Composable
private fun FirmwareLatestView(
    onDismiss: () -> Unit,
    onStartMeasure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QkColor.Bg)
            .padding(horizontal = QkSpacing.Page),
    ) {
        FirmwareNavBar(TITLE_FIRMWARE_LATEST, onDismiss)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(ReadyIconSize)
                    .clip(CircleShape)
                    .background(QkColor.Mint),
                contentAlignment = Alignment.Center,
            ) {
                QkIcon(QkIcons.Habit, tint = QkColor.Primary, size = QkSpacing.Xl * 2)
            }
            Text(
                LATEST_READY_TITLE,
                modifier = Modifier.padding(top = QkSpacing.Lg),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
            )
            Text(
                LATEST_READY_SUBTITLE,
                modifier = Modifier.padding(top = QkSpacing.Md),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
                textAlign = TextAlign.Center,
            )
            QkButton(
                ACTION_START_MEASURE,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = QkSpacing.Xl),
            ) {
                onStartMeasure()
            }
            QkButton(
                ACTION_BACK,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = QkSpacing.Md),
                style = QkButtonStyle.Ghost,
            ) {
                onDismiss()
            }
        }
    }
}

// ---- 共享原语 ----

/** 压栈页导航条：返回圆钮 + 标题（原型 .navbar）。 */
@Composable
private fun FirmwareNavBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QkScreenHeader(title, onBack, modifier = modifier, style = QkHeaderStyle.Title)
}

/** 警示横幅（原型 .banner.warn：琥珀底圆角条 + 线性警示图标 + 小字）。 */
@Composable
private fun FirmwareWarnBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(QkColor.AccentSoft)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(WarningIcon, tint = QkColor.AmberInk, size = BannerIconSize)
        Text(
            text,
            modifier = Modifier.weight(1f).padding(start = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.AmberInk,
        )
    }
}

/** 警示图标：QkIcons 未收录，仅在警示横幅内使用（不扩散到其他页面）。 */
private val WarningIcon: ImageVector = Icons.Outlined.WarningAmber
