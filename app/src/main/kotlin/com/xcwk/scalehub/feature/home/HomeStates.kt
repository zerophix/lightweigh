package com.xcwk.scalehub.feature.home

import com.xcwk.scalehub.ui.components.QkHeaderStyle
import com.xcwk.scalehub.ui.components.QkScreenHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyph
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkMetric
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.components.QkProgress
import com.xcwk.scalehub.ui.components.QkSkeletonBlock
import com.xcwk.scalehub.ui.components.QkSkeletonLine
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkComponentSize
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkStroke
import java.util.Locale
import kotlin.math.roundToInt

/**
 * home-states — 首页八态（T-095 · 原型 11-home-states.html）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/11-home-states.html`
 * - view：v-home / v-empty / v-unconn / v-syncing / v-error / v-switching / v-brief / v-goaldone
 * - 验收：AC-H4「关键指标摘要」、AC-H5「设备摘要」；then 文案逐字取自原型，禁止改写
 *
 * ## 结构
 * 1. [HomeText]：全部逐字文案（单测钉死，改字即红）；
 * 2. [HomeStage]/[HomeUiState]/[HomeEvent] + [homeReduce]：薄状态机，无副作用、可单测；
 *    进哪个状态由调用方发事件，呈现层不自建状态；
 * 3. 每个 stage 一个无状态 Composable，版式/颜色只走 Qk* 组件与 token。
 *
 * ## 呈现式约束
 * 所有交互经 [onEvent]（以及成员弹层经 [onOpenMemberSheet]）回传，本文件不读
 * 设备/网络/存储，也不持有可变状态——真机数据由宿主注入。
 *
 * ## 与 MainShell 的关系
 * 本任务只交付可独立组合的八态视图；壳层装配（MainShell 状态分支）由后续接入切片完成，
 * 避免与并行改 `ui/shell` 的分支冲突。
 */

/** 原型 11-home-states.html 文案（逐字，勿改）。 */
object HomeText {
    const val Brand = "轻衡"

    // v-home 正常首页
    const val Greeting = "早上好，林悦。"
    const val Slogan = "轻一点，更懂自己。"
    const val CurrentWeightHint = "当前体重 · 10月21日 07:30"
    const val WeightValue = "62.4"
    const val WeightUnit = "kg"
    const val WeightDelta = "↓0.3"
    const val BodyFatLabel = "体脂率"
    const val BodyFatValue = "28.6"
    const val BmiLabel = "BMI"
    const val BmiValue = "22.1"

    // v-empty 空数据
    const val EmptyGreeting = "你好，开始记录吧"
    const val EmptySlogan = "完成第一次测量，就能看到身体概览。"
    const val EmptyTitle = "还没有测量数据"
    const val EmptyBodyLine1 = "连接体脂秤并完成第一次测量后，"
    const val EmptyBodyLine2 = "这里会显示你的体重与指标。"
    const val EmptyPrimary = "开始第一次测量"
    const val BackHome = "← 返回正常首页"

    // v-unconn 设备未连接
    const val UnconnectedBanner = "体脂秤未连接，暂无法测量。"
    const val Reconnect = "重新连接 ›"
    const val StaleHint = "上次更新 10月21日 07:30 · 非实时"
    const val DeviceName = "轻衡 S1 · 未连接"
    const val DeviceHint = "轻踩秤面唤醒后重连"
    const val Connect = "连接"
    const val MeasureDisabled = "开始测量（需先连接设备）"

    // v-syncing 云端同步中
    const val CurrentWeight = "当前体重"
    const val Syncing = "同步中…"
    const val PendingTag = "待同步"
    const val PendingHint = "本机记录，稍后上传"
    const val SyncDoneDemo = "（演示）点击返回，视为同步完成 →"

    // v-error 刷新失败
    const val ErrorTitle = "指标加载失败"
    const val ErrorBody = "网络开小差了，请检查连接。"
    const val Reload = "重新加载"

    // v-switching 成员切换刷新中
    const val SwitchDemo = "（演示）点击返回 →"

    // v-brief AI 健康简报
    const val BriefTitle = "AI 健康简报"
    const val BriefCutoff = "分析截止：2026年10月21日 07:30 · 林悦"
    const val BriefHeadline1 = "近 30 天趋势稳健，"
    const val BriefHeadline2 = "继续保持你的节奏。"
    const val BriefChanges = "关键变化"
    const val BriefWeightDelta = "−1.6"
    const val BriefWeightLabel = "体重 kg"
    const val BriefFatDelta = "−1.5"
    const val BriefFatLabel = "体脂 %"
    const val BriefBody =
        "近 30 天体重下降 1.6 kg、体脂率下降 1.5 个百分点，趋势稳健。单次波动不代表脂肪变化。"
    const val AskAi = "问 AI 助手"

    // v-goaldone 目标达成
    const val GoalDoneTitle = "目标达成"
    const val GoalCongrats = "恭喜，达成体重目标！"
    const val GoalSummary = "从 65.2 kg 到 58.0 kg，减重 7.2 kg，用时 84 天。"
    const val GoalTargetValue = "58.0"
    const val GoalTargetLabel = "目标 kg"
    const val GoalCurrentValue = "57.9"
    const val GoalCurrentLabel = "当前 kg"
    const val GoalDaysValue = "84"
    const val GoalDaysLabel = "天数"
    const val GoalSetNew = "设定新目标"
    const val GoalKeep = "维持当前体重"
}

/** 八个 view 与 [HomeStage] 一一对应；[viewId] 即原型里的 id，便于反向核对。 */
enum class HomeStage(val viewId: String) {
    Home("v-home"),
    Empty("v-empty"),
    Unconnected("v-unconn"),
    Syncing("v-syncing"),
    Error("v-error"),
    Switching("v-switching"),
    Brief("v-brief"),
    GoalDone("v-goaldone"),
}

/** 进入某状态的事件：只表达「去哪/做了什么」，不含业务副作用。 */
sealed interface HomeEvent {
    data object Back : HomeEvent
    data object OpenEmpty : HomeEvent
    data object OpenUnconnected : HomeEvent
    data object OpenSyncing : HomeEvent
    data object OpenError : HomeEvent
    data object OpenBrief : HomeEvent
    data object OpenGoalDone : HomeEvent
    data class SwitchMember(val name: String) : HomeEvent
    data object StartFirstMeasure : HomeEvent
    data object ReconnectDevice : HomeEvent
    data object SyncCompleted : HomeEvent
    data object ReloadMetrics : HomeEvent
    data object SetNewGoal : HomeEvent
    data object KeepCurrentWeight : HomeEvent
    data object AskAi : HomeEvent
}

/**
 * 首页八态快照。字段只承载呈现所需数据；
 * 文案一律由 [HomeText] 与同名纯函数派生，不在状态里存展示字符串（避免两份真源）。
 */
data class HomeUiState(
    val stage: HomeStage = HomeStage.Home,
    val memberName: String = "林悦",
    val pendingSyncCount: Int = 3,
    val startWeightKg: Double = 65.2,
    val currentWeightKg: Double = 62.4,
    val goalWeightKg: Double = 58.0,
) {
    /** 原型默认的问候语（成员切换后按当前成员拼）。 */
    val greeting: String get() = homeGreeting(memberName)

    /** 「向 58.0 kg 靠近 · 已减 2.8 kg」 */
    val goalHint: String get() = homeGoalHint(startWeightKg, currentWeightKg, goalWeightKg)

    /** 进度条百分比（原型 fill 39%）。 */
    val goalProgressPercent: Int
        get() = homeGoalProgressPercent(startWeightKg, currentWeightKg, goalWeightKg)

    /** 「正在同步 3 条测量记录…」 */
    val syncingBanner: String get() = homeSyncingBanner(pendingSyncCount)

    /** 「已切换到「妈妈」，正在加载她的数据…」 */
    val switchingBanner: String get() = homeSwitchingBanner(memberName)

    /** 头像位文字：取成员名首字（原型「悦」「妈」）。 */
    val memberInitial: String get() = memberName.take(1)
}

/** 纯状态机：事件 → 新快照。不做 IO，不抛异常，越界分支收敛到「回正常首页」。 */
fun homeReduce(state: HomeUiState, event: HomeEvent): HomeUiState = when (event) {
    HomeEvent.Back -> state.copy(stage = HomeStage.Home)
    HomeEvent.OpenEmpty -> state.copy(stage = HomeStage.Empty)
    HomeEvent.OpenUnconnected -> state.copy(stage = HomeStage.Unconnected)
    HomeEvent.OpenSyncing -> state.copy(stage = HomeStage.Syncing)
    HomeEvent.OpenError -> state.copy(stage = HomeStage.Error)
    HomeEvent.OpenBrief -> state.copy(stage = HomeStage.Brief)
    HomeEvent.OpenGoalDone -> state.copy(stage = HomeStage.GoalDone)
    is HomeEvent.SwitchMember -> homeSwitchMember(state, event.name)
    HomeEvent.StartFirstMeasure -> state.copy(stage = HomeStage.Home)
    HomeEvent.ReconnectDevice -> state.copy(stage = HomeStage.Home)
    // 原型 v-syncing 的「点击返回，视为同步完成」：退出同步态且清空待同步条数。
    HomeEvent.SyncCompleted -> state.copy(stage = HomeStage.Home, pendingSyncCount = 0)
    HomeEvent.ReloadMetrics -> state.copy(stage = HomeStage.Home)
    HomeEvent.SetNewGoal -> state.copy(stage = HomeStage.Home)
    HomeEvent.KeepCurrentWeight -> state.copy(stage = HomeStage.Home)
    HomeEvent.AskAi -> state.copy(stage = HomeStage.Home)
}

/**
 * 成员切换：选中的就是当前成员时原型只弹 toast、停留在首页（不进入 switching）。
 * 选中他人则直接进入 switching——成员名在此刻提交，刷新完成由 [HomeEvent.Back] 收尾。
 */
private fun homeSwitchMember(state: HomeUiState, name: String): HomeUiState =
    if (name.isBlank() || name == state.memberName) {
        state.copy(stage = HomeStage.Home)
    } else {
        state.copy(stage = HomeStage.Switching, memberName = name)
    }

/** 「早上好，<成员>。」 */
fun homeGreeting(memberName: String): String = "早上好，$memberName。"

/** 「正在同步 N 条测量记录…」；负数按 0 处理，避免文案出现「-1 条」。 */
fun homeSyncingBanner(pendingCount: Int): String = "正在同步 ${pendingCount.coerceAtLeast(0)} 条测量记录…"

/** 「已切换到「<成员>」，正在加载她的数据…」 */
fun homeSwitchingBanner(memberName: String): String =
    "已切换到「$memberName」，正在加载她的数据…"

/** 「向 58.0 kg 靠近 · 已减 2.8 kg」；已减量按起点与当前值计算，不写死。 */
fun homeGoalHint(startKg: Double, currentKg: Double, goalKg: Double): String =
    "向 ${homeOneDecimal(goalKg)} kg 靠近 · 已减 ${homeOneDecimal(startKg - currentKg)} kg"

/** 「从 65.2 kg 到 58.0 kg，减重 7.2 kg，用时 84 天。」 */
fun homeGoalSummary(startKg: Double, goalKg: Double, days: Int): String =
    "从 ${homeOneDecimal(startKg)} kg 到 ${homeOneDecimal(goalKg)} kg，" +
        "减重 ${homeOneDecimal(startKg - goalKg)} kg，用时 ${days.coerceAtLeast(0)} 天。"

/**
 * 目标进度百分比（0..100）。起点与目标重合（或反向）时视为已完成，
 * 避免除零；当前值越过目标也夹到 100。
 */
fun homeGoalProgressPercent(startKg: Double, currentKg: Double, goalKg: Double): Int {
    val span = startKg - goalKg
    if (span <= 0.0) return 100
    val done = (startKg - currentKg) / span
    return (done * 100).roundToInt().coerceIn(0, 100)
}

/** 一位小数（Locale.ROOT，避免测试机语言影响小数点）。 */
private fun homeOneDecimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

/** 首页八态入口：按 [HomeUiState.stage] 分派到对应无状态视图。 */
@Composable
fun HomeStateScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onOpenMemberSheet: () -> Unit = {},
) {
    when (state.stage) {
        HomeStage.Home -> HomeNormalView(state, onEvent, onOpenMemberSheet)
        HomeStage.Empty -> HomeEmptyView(onEvent)
        HomeStage.Unconnected -> HomeUnconnectedView(state, onEvent)
        HomeStage.Syncing -> HomeSyncingView(state, onEvent)
        HomeStage.Error -> HomeErrorView(onEvent)
        HomeStage.Switching -> HomeSwitchingView(state, onEvent)
        HomeStage.Brief -> HomeBriefView(onEvent)
        HomeStage.GoalDone -> HomeGoalDoneView(state, onEvent)
    }
}

// ---- v-home 正常首页 ----

@Composable
private fun HomeNormalView(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onOpenMemberSheet: () -> Unit,
) {
    HomePage {
        HomeBrand(
            initial = state.memberInitial,
            onAvatarClick = onOpenMemberSheet,
        )
        Spacer(Modifier.size(QkSpacing.Sm))
        Text(
            text = state.greeting,
            style = MaterialTheme.typography.headlineSmall,
            color = QkColor.Fg,
        )
        Text(
            text = HomeText.Slogan,
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Sub,
            modifier = Modifier.padding(top = QkSpacing.Xs),
        )
        Spacer(Modifier.size(QkSpacing.Md))
        QkCard(onClick = { onEvent(HomeEvent.OpenBrief) }) {
            Text(
                text = HomeText.CurrentWeightHint,
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Muted,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = HomeText.WeightValue,
                    style = MaterialTheme.typography.displayLarge,
                    color = QkColor.Fg,
                )
                Text(
                    text = HomeText.WeightUnit,
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Sub,
                    modifier = Modifier.padding(start = QkSpacing.Xs, bottom = QkSpacing.Xs),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = HomeText.WeightDelta,
                    style = MaterialTheme.typography.labelMedium,
                    color = QkColor.Primary,
                    modifier = Modifier.padding(bottom = QkSpacing.Xs),
                )
            }
            Text(
                text = state.goalHint,
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Muted,
                modifier = Modifier.padding(top = QkSpacing.Sm),
            )
            QkProgress(
                percent = state.goalProgressPercent,
                modifier = Modifier.padding(top = QkSpacing.Sm),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.CardGap),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.CardGap),
        ) {
            QkMetric(
                label = HomeText.BodyFatLabel,
                value = HomeText.BodyFatValue,
                unit = "%",
                modifier = Modifier.weight(1f),
            )
            QkMetric(
                label = HomeText.BmiLabel,
                value = HomeText.BmiValue,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---- v-empty 空数据 ----

@Composable
private fun HomeEmptyView(onEvent: (HomeEvent) -> Unit) {
    HomePage {
        HomeBrand()
        Spacer(Modifier.size(QkSpacing.Md))
        Text(
            text = HomeText.EmptyGreeting,
            style = MaterialTheme.typography.headlineSmall,
            color = QkColor.Fg,
        )
        Text(
            text = HomeText.EmptySlogan,
            style = MaterialTheme.typography.bodyMedium,
            color = QkColor.Sub,
            modifier = Modifier.padding(top = QkSpacing.Xs),
        )
        Spacer(Modifier.size(QkSpacing.Lg))
        QkCard(padding = QkSpacing.Xl) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                QkIconBubble(
                    icon = QkIcons.Device,
                    size = QkSpacing.Xl * 2,
                    contentDescription = null,
                )
            }
            Text(
                text = HomeText.EmptyTitle,
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg),
            )
            Text(
                text = HomeText.EmptyBodyLine1 + "\n" + HomeText.EmptyBodyLine2,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            )
        }
        Spacer(Modifier.weight(1f))
        QkButton(
            text = HomeText.EmptyPrimary,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onEvent(HomeEvent.StartFirstMeasure) },
        )
        HomeGhostButton(HomeText.BackHome) { onEvent(HomeEvent.Back) }
    }
}

// ---- v-unconn 设备未连接 ----

@Composable
private fun HomeUnconnectedView(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    HomePage {
        HomeBrand()
        Spacer(Modifier.size(QkSpacing.Md))
        QkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QkIconBubble(
                    icon = QkIcons.Bluetooth,
                    size = QkComponentSize.SkeletonAvatar,
                    contentDescription = null,
                )
                Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
                    Text(
                        text = HomeText.UnconnectedBanner,
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Fg,
                    )
                    Text(
                        text = HomeText.Reconnect,
                        style = MaterialTheme.typography.labelMedium,
                        color = QkColor.Primary,
                        modifier = Modifier
                            .padding(top = QkSpacing.Xs)
                            .clickable { onEvent(HomeEvent.ReconnectDevice) },
                    )
                }
            }
        }
        Spacer(Modifier.size(QkSpacing.CardGap))
        QkCard {
            Text(
                text = HomeText.StaleHint,
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Muted,
            )
            Row(
                modifier = Modifier.padding(top = QkSpacing.Xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = homeOneDecimal(state.currentWeightKg),
                    style = MaterialTheme.typography.displayLarge,
                    color = QkColor.Fg.copy(alpha = 0.6f),
                )
                Text(
                    text = HomeText.WeightUnit,
                    style = MaterialTheme.typography.titleSmall,
                    color = QkColor.Sub,
                    modifier = Modifier.padding(start = QkSpacing.Xs, bottom = QkSpacing.Xs),
                )
            }
        }
        Spacer(Modifier.size(QkSpacing.CardGap))
        QkCard(padding = QkSpacing.Lg) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QkGlyph(text = "秤", size = QkComponentSize.SkeletonAvatar)
                Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
                    Text(
                        text = HomeText.DeviceName,
                        style = MaterialTheme.typography.titleSmall,
                        color = QkColor.Fg,
                    )
                    Text(
                        text = HomeText.DeviceHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = QkColor.Muted,
                    )
                }
                Text(
                    text = HomeText.Connect,
                    style = MaterialTheme.typography.labelMedium,
                    color = QkColor.Primary,
                    modifier = Modifier.clickable { onEvent(HomeEvent.ReconnectDevice) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        QkButton(
            text = HomeText.MeasureDisabled,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Secondary,
            enabled = false,
            onClick = {},
        )
        HomeGhostButton(HomeText.BackHome) { onEvent(HomeEvent.Back) }
    }
}

// ---- v-syncing 云端同步中 ----

@Composable
private fun HomeSyncingView(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        HomeLoadingBanner(text = state.syncingBanner)
        HomePage(modifier = Modifier.weight(1f)) {
            HomeBrand()
            Spacer(Modifier.size(QkSpacing.Md))
            QkCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = HomeText.CurrentWeight,
                        style = MaterialTheme.typography.labelSmall,
                        color = QkColor.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = HomeText.Syncing,
                        style = MaterialTheme.typography.labelSmall,
                        color = QkColor.Primary,
                    )
                }
                Row(
                    modifier = Modifier.padding(top = QkSpacing.Xs),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = HomeText.WeightValue,
                        style = MaterialTheme.typography.displayLarge,
                        color = QkColor.Fg,
                    )
                    Text(
                        text = HomeText.WeightUnit,
                        style = MaterialTheme.typography.titleSmall,
                        color = QkColor.Sub,
                        modifier = Modifier.padding(start = QkSpacing.Xs, bottom = QkSpacing.Xs),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = QkSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QkPill(text = HomeText.PendingTag, tone = QkPillTone.Gray)
                    Text(
                        text = HomeText.PendingHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = QkColor.Muted,
                        modifier = Modifier.padding(start = QkSpacing.Sm),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = HomeText.SyncDoneDemo,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(HomeEvent.SyncCompleted) },
            )
        }
    }
}

// ---- v-error 刷新失败 ----

@Composable
private fun HomeErrorView(onEvent: (HomeEvent) -> Unit) {
    HomePage {
        HomeBrand()
        Spacer(Modifier.size(QkSpacing.Md))
        QkCard {
            QkSkeletonLine(widthPercent = 40f, height = QkComponentSize.SkeletonLineTitle)
            QkSkeletonLine(
                widthPercent = 60f,
                height = QkComponentSize.SkeletonLineBigNumber,
                modifier = Modifier.padding(top = QkSpacing.Md),
            )
        }
        Spacer(Modifier.size(QkSpacing.CardGap))
        QkCard(padding = QkSpacing.Xl) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(QkSpacing.Xl * 2)
                        .clip(CircleShape)
                        .background(QkColor.DangerSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = QkColor.Danger,
                        modifier = Modifier.size(QkSpacing.Xl),
                    )
                }
            }
            Text(
                text = HomeText.ErrorTitle,
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg),
            )
            Text(
                text = HomeText.ErrorBody,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xs),
            )
            QkButton(
                text = HomeText.Reload,
                style = QkButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg),
                onClick = { onEvent(HomeEvent.ReloadMetrics) },
            )
        }
        Spacer(Modifier.weight(1f))
        HomeGhostButton(HomeText.BackHome) { onEvent(HomeEvent.Back) }
    }
}

// ---- v-switching 成员切换刷新中 ----

@Composable
private fun HomeSwitchingView(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        HomeLoadingBanner(text = state.switchingBanner)
        HomePage(modifier = Modifier.weight(1f)) {
            HomeBrand(initial = state.memberInitial)
            Spacer(Modifier.size(QkSpacing.Md))
            QkCard {
                QkSkeletonLine(widthPercent = 35f)
                QkSkeletonLine(
                    widthPercent = 55f,
                    height = QkComponentSize.SkeletonLineBigNumber,
                    modifier = Modifier.padding(top = QkSpacing.Md),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.CardGap),
                horizontalArrangement = Arrangement.spacedBy(QkSpacing.CardGap),
            ) {
                QkSkeletonBlock(
                    height = QkComponentSize.SkeletonAvatar * 2,
                    modifier = Modifier.weight(1f),
                )
                QkSkeletonBlock(
                    height = QkComponentSize.SkeletonAvatar * 2,
                    modifier = Modifier.weight(1f),
                )
            }
            QkSkeletonBlock(
                height = QkComponentSize.SkeletonAvatar,
                modifier = Modifier.padding(top = QkSpacing.CardGap),
            )
            Text(
                text = HomeText.SwitchDemo,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = QkSpacing.Md)
                    .clickable { onEvent(HomeEvent.Back) },
            )
        }
    }
}

// ---- v-brief AI 健康简报 ----

@Composable
private fun HomeBriefView(onEvent: (HomeEvent) -> Unit) {
    HomePage {
        HomeNavBar(title = HomeText.BriefTitle) { onEvent(HomeEvent.Back) }
        Spacer(Modifier.size(QkSpacing.Md))
        Text(
            text = HomeText.BriefCutoff,
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
        )
        Spacer(Modifier.size(QkSpacing.CardGap))
        QkCard {
            Text(
                text = HomeText.BriefHeadline1 + "\n" + HomeText.BriefHeadline2,
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
            )
        }
        Spacer(Modifier.size(QkSpacing.CardGap))
        QkCard {
            Text(
                text = HomeText.BriefChanges,
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
            ) {
                HomeBriefStat(
                    value = HomeText.BriefWeightDelta,
                    label = HomeText.BriefWeightLabel,
                    modifier = Modifier.weight(1f),
                )
                HomeBriefStat(
                    value = HomeText.BriefFatDelta,
                    label = HomeText.BriefFatLabel,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = HomeText.BriefBody,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
                modifier = Modifier.padding(top = QkSpacing.Lg),
            )
        }
        Spacer(Modifier.weight(1f))
        QkButton(
            text = HomeText.AskAi,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onEvent(HomeEvent.AskAi) },
        )
    }
}

@Composable
private fun HomeBriefStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(QkColor.Mint)
            .padding(QkSpacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
            modifier = Modifier.padding(top = QkSpacing.Xs),
        )
    }
}

// ---- v-goaldone 目标达成 ----

@Composable
private fun HomeGoalDoneView(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    HomePage {
        HomeNavBar(title = HomeText.GoalDoneTitle) { onEvent(HomeEvent.Back) }
        Spacer(Modifier.size(QkSpacing.Lg))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            QkIconBubble(
                icon = QkIcons.Habit,
                size = QkSpacing.Xl * 2.5f,
                contentDescription = null,
            )
        }
        Text(
            text = HomeText.GoalCongrats,
            style = MaterialTheme.typography.headlineSmall,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg),
        )
        Text(
            text = homeGoalSummary(state.startWeightKg, state.goalWeightKg, 84),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xs),
        )
        Spacer(Modifier.size(QkSpacing.Lg))
        QkCard(padding = QkSpacing.Xl) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                HomeGoalStat(HomeText.GoalTargetValue, HomeText.GoalTargetLabel, true)
                HomeGoalStat(HomeText.GoalCurrentValue, HomeText.GoalCurrentLabel, false)
                HomeGoalStat(HomeText.GoalDaysValue, HomeText.GoalDaysLabel, true)
            }
        }
        Spacer(Modifier.weight(1f))
        QkButton(
            text = HomeText.GoalSetNew,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onEvent(HomeEvent.SetNewGoal) },
        )
        QkButton(
            text = HomeText.GoalKeep,
            style = QkButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            onClick = { onEvent(HomeEvent.KeepCurrentWeight) },
        )
    }
}

@Composable
private fun HomeGoalStat(value: String, label: String, highlight: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = if (highlight) QkColor.Primary else QkColor.Fg,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
            modifier = Modifier.padding(top = QkSpacing.Xs),
        )
    }
}

// ---- 共用版式 ----

/** 页面骨架：统一左右边距 + 纵向留白，内容自上而下排布（底部按钮前用 weight 撑开）。 */
@Composable
private fun HomePage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Md),
        content = content,
    )
}

/** 品牌行：叶片图标 + 字标 + 可选成员头像（原型 .brand）。 */
@Composable
private fun HomeBrand(initial: String? = null, onAvatarClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 字标已在顶栏（QkTopBar），这里不再重复「叶片 + 轻衡」，
        // 否则首页会出现两行同样的品牌名（2026-10-24 实机截图确认）。
        if (initial != null) {
            Spacer(Modifier.weight(1f))
            val avatarModifier = if (onAvatarClick != null) {
                Modifier.clickable(onClick = onAvatarClick)
            } else {
                Modifier
            }
            QkGlyph(text = initial, size = QkSpacing.Xl, modifier = avatarModifier)
        }
    }
}

/** 顶部加载条（原型 v-syncing / v-switching 的薄荷底横条）：转圈 + 一行说明。 */
@Composable
private fun HomeLoadingBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(QkColor.MintSoft)
            .padding(horizontal = QkSpacing.Page, vertical = QkSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = QkColor.Primary,
            strokeWidth = QkStroke.Selected,
            modifier = Modifier.size(QkSpacing.Lg),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Primary,
            modifier = Modifier.padding(start = QkSpacing.Sm),
        )
    }
}

/** 二级页导航条：返回圆钮 + 标题（原型 .navbar）。 */
@Composable
private fun HomeNavBar(title: String, onBack: () -> Unit) {
    QkScreenHeader(title, onBack, style = QkHeaderStyle.Title)
}

/** 居中的幽灵按钮（原型 .btn.ghost + align-self:center）。 */
@Composable
private fun HomeGhostButton(text: String, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        QkButton(
            text = text,
            style = QkButtonStyle.Ghost,
            modifier = Modifier.padding(top = QkSpacing.Sm),
            onClick = onClick,
        )
    }
}
