package com.xcwk.scalehub.feature.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyph
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkStroke

/**
 * data-conflict — 数据冲突处理六态（T-094 · 原型22 v-offline-switch / v-offline-done /
 * v-multi-device / v-multi-done / v-sync-conflict / v-sync-done）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/22-data-conflict.html`
 * - 验收：AC-N3「离线/多设备/同步冲突处理」，文案逐字取自原型，改一个字即验收不过
 *
 * ## 呈现式约束（硬约束）
 * 1. 只渲染传入的 [DataConflictUiState]：离线记录、设备名、冲突字段全部由调用方注入，
 *    本文件不持有 `remember`、不读 Room、不读网络；
 * 2. 状态推进是纯函数 [reduceDataConflict]：宿主拿到 intent 后自行 `copy` 回写，
 *    冲突解决结果记在 [ConflictResolution] 里（备注 / 合并读数），UI 只负责展示；
 * 3. 颜色、间距、圆角一律 Qk token，按钮 / 卡片 / 胶囊复用 ui/components 的 Qk* 组件，
 *    页面不出现裸色值与裸 dp。
 *
 * ## 与原型的一处偏移
 * 原型 6 屏没有「重试」按钮，但验收要求 `onRetry` 回调：本组件仅在
 * `state.offlineSyncFailed == true`（宿主注入的同步失败态）时把主按钮文案切成
 * [DataConflictText.RetrySync] 并派发 `onRetry`；正常态仍逐字使用原型「先同步再切换」。
 */

/** 原型 6 个 view 的逐字文案（勿改）。 */
object DataConflictText {
    // ---- 页标题（navbar .title）----
    const val TitleOfflineSwitch = "离线测量 · 切换成员"
    const val TitleOfflineDone = "已切换"
    const val TitleMultiDevice = "多设备新记录"
    const val TitleMultiDone = "合并完成"
    const val TitleSyncConflict = "同步冲突"
    const val TitleSyncDone = "冲突已解决"

    // ---- v-offline-switch ----
    fun offlineDetected(pendingCount: Int, targetName: String): String =
        "检测到 $pendingCount 条未同步的离线测量记录，\n当前要切换到「$targetName」。"

    fun pendingHeader(ownerName: String): String = "未同步记录（属于 $ownerName）"

    fun offlineOwnershipNote(ownerName: String, targetName: String): String =
        "离线记录属于「$ownerName」，\n切换到「$targetName」后这些记录仍归$ownerName，不会混入${targetName}的数据。"

    const val PendingSync = "待同步"
    const val SwitchAfterSync = "先同步再切换"
    const val SwitchDirect = "直接切换"

    /** 仅同步失败态出现（原型无此按钮，见文件头「与原型的一处偏移」）。 */
    const val RetrySync = "重试同步"

    // ---- v-offline-done ----
    fun viewingAs(targetName: String): String = "当前查看：$targetName"

    fun keptOwnershipSummary(ownerName: String, pendingCount: Int): String =
        "${ownerName}的 $pendingCount 条离线记录已保留原归属"

    const val OwnershipHeader = "归属确认"

    fun ownershipKept(ownerName: String, pendingCount: Int, targetName: String): String =
        "${ownerName}的 $pendingCount 条离线记录 → 仍归$ownerName，不混入$targetName"

    fun targetHistoryIntact(targetName: String): String = "${targetName}的历史记录不受影响"

    fun syncWhenOnline(ownerName: String): String = "联网后${ownerName}的记录将自动同步到云端"

    const val SwitchCheckNote = "每次切换都会检查未同步记录，确保归属正确。"

    // ---- v-multi-device ----
    const val MultiDeviceDetected = "检测到另一台设备也产生了新测量记录，\n需要合并到你的账号。"
    const val ConflictRecordsHeader = "冲突记录（同一时间点，两端不同）"
    const val MultiDeviceNote = "两条记录时间相近但读数不同，\n请选择保留哪一条。不会自动合并。"
    const val KeepLocalRecord = "保留本机记录"
    const val KeepRemoteRecord = "采用远端记录"
    const val KeepBothRecords = "两条都保留"

    const val TagLocal = "本地"
    const val TagRemote = "远端"
    const val TagCloud = "云端"
    const val StatusSynced = "已同步"
    const val StatusNotSynced = "未同步"

    fun deviceStatus(deviceLabel: String, synced: Boolean): String =
        "$deviceLabel · ${if (synced) StatusSynced else StatusNotSynced}"

    // ---- v-multi-done ----
    const val MergedTitle = "记录已合并"
    const val MergedSubtitle = "根据你的选择更新了本地与云端"
    const val MergeResultHeader = "合并结果"
    const val MergeResultLocalSynced = "本机记录已同步到云端"
    const val MergeResultRemoteHandled = "远端记录已按你的选择处理"
    const val MergeResultTrendUpdated = "趋势图已更新，无重复数据"
    const val MergeUndoNote = "合并操作可撤销，7 天内可在「数据管理」恢复。"

    // ---- v-sync-conflict ----
    const val SyncConflictDetected = "同一条记录在本地和云端都被修改过，\n请选择保留哪个版本。"

    fun conflictDetailHeader(measuredAt: String): String = "冲突详情 · $measuredAt 测量记录"

    const val LocalVersionTitle = "本地版本"
    const val CloudVersionTitle = "云端版本"
    const val SyncConflictNote = "测量数值两端一致，仅备注不同。\n选择保留的版本将覆盖另一端。"
    const val KeepLocalVersion = "保留本地版本"
    const val KeepCloudVersion = "采用云端版本"

    // ---- v-sync-done ----
    const val ResolvedTitle = "冲突已解决"
    const val ResolvedSubtitle = "保留的版本已覆盖另一端"
    const val ResolveResultHeader = "解决结果"
    const val ResolveResultNote = "备注已统一为你选择的版本"

    fun resolveResultWeight(weight: String): String = "测量数值保持不变（$weight）"

    const val ResolveResultSaved = "冲突解决记录已保存，可追溯"
    const val ResolvePreferenceNote = "下次遇到同类冲突会记住你的偏好，但每次仍会确认。"

    // ---- 通用 ----
    const val BackToScenarios = "返回场景列表"

    /** 原型页脚（flowbar），与 GuideScreen.PrototypeFooter 同口径保留原文。 */
    const val PrototypeFooter = "轻衡 · 数据冲突处理交互原型 · 7 屏（补 page-list-TODO §13 缺口）"
}

/** 六态：与原型 6 个 view 一一对应。 */
enum class DataConflictStep {
    OfflineSwitch,
    OfflineDone,
    MultiDevice,
    MultiDone,
    SyncConflict,
    SyncDone,
}

/** 离线切换方式：先同步再切换 / 直接切换（完成页据此回写归属说明）。 */
enum class OfflineSwitchMode { SyncThenSwitch, DirectSwitch }

/** 冲突处理选择：保留本地 / 保留远端 / 两条都保留。 */
enum class DataConflictChoice { KeepLocal, KeepRemote, Both }

/**
 * 冲突解决结果：纯数据回写载体。
 *
 * - 多设备：`mergedWeights` 是写入趋势的读数序列（保留本机 / 采用远端 / 两条都保留）；
 * - 同步冲突：`resolvedNote` 是统一后的备注（保留哪端就写回哪端）。
 */
data class ConflictResolution(
    val choice: DataConflictChoice,
    val resolvedNote: String? = null,
    val mergedWeights: List<String> = emptyList(),
)

/** 离线待同步记录（原型行文案「10/21 07:30 · 62.4 kg」）。 */
data class OfflinePendingRecord(
    val measuredAt: String,
    val weight: String,
    val state: String = DataConflictText.PendingSync,
) {
    val summary: String get() = "$measuredAt · $weight"
}

/** 多设备冲突记录（两端各一条，设备名与同步状态外部注入）。 */
data class DeviceConflictRecord(
    val measuredAt: String,
    val weight: String,
    val deviceLabel: String,
    val synced: Boolean,
) {
    val summary: String get() = "$measuredAt · $weight"
    val statusLine: String get() = DataConflictText.deviceStatus(deviceLabel, synced)
}

/** 同一条记录的一个版本（同步冲突页的本地 / 云端两端）。 */
data class SyncVersion(
    val label: String,
    val note: String,
    val modifiedAt: String,
) {
    val noteLine: String get() = "备注：$note · 修改于 $modifiedAt"
}

/**
 * 冲突处理页输入状态：不可变，只携带呈现所需信息。
 *
 * 默认值即原型 22 的演示数据；真实数据由宿主覆盖（记录、设备名、成员名全部注入）。
 */
data class DataConflictUiState(
    val step: DataConflictStep = DataConflictStep.OfflineSwitch,
    val ownerName: String = "林悦",
    val targetName: String = "妈妈",
    val offlineRecords: List<OfflinePendingRecord> = DEFAULT_OFFLINE_RECORDS,
    val localRecord: DeviceConflictRecord = DEFAULT_LOCAL_RECORD,
    val remoteRecord: DeviceConflictRecord = DEFAULT_REMOTE_RECORD,
    val conflictMeasuredAt: String = "10/21 07:30",
    val measureWeight: String = "62.4 kg",
    val localVersion: SyncVersion = DEFAULT_LOCAL_VERSION,
    val cloudVersion: SyncVersion = DEFAULT_CLOUD_VERSION,
    val offlineSwitchMode: OfflineSwitchMode? = null,
    val resolution: ConflictResolution? = null,
    /** 宿主注入：上次「先同步再切换」的同步是否失败；仅此态主按钮切为「重试同步」。 */
    val offlineSyncFailed: Boolean = false,
) {
    /** 待同步条数：由注入记录推导，不写死「2 条」。 */
    val pendingCount: Int get() = offlineRecords.size

    /** 完成页头像字：取目标成员名首字（原型「妈」）。 */
    val targetInitial: String get() = targetName.take(1)

    /** 解决后实际回写的备注（未解决为 null）。 */
    val resolvedNote: String? get() = resolution?.resolvedNote
}

/** 原型默认演示数据（仅作为默认值，宿主可整体覆盖）。 */
val DEFAULT_OFFLINE_RECORDS: List<OfflinePendingRecord> = listOf(
    OfflinePendingRecord(measuredAt = "10/21 07:30", weight = "62.4 kg"),
    OfflinePendingRecord(measuredAt = "10/20 07:28", weight = "62.7 kg"),
)

val DEFAULT_LOCAL_RECORD: DeviceConflictRecord = DeviceConflictRecord(
    measuredAt = "10/21 07:30",
    weight = "62.4 kg",
    deviceLabel = "本机",
    synced = true,
)

val DEFAULT_REMOTE_RECORD: DeviceConflictRecord = DeviceConflictRecord(
    measuredAt = "10/21 07:32",
    weight = "62.5 kg",
    deviceLabel = "iPhone 15 Pro",
    synced = false,
)

val DEFAULT_LOCAL_VERSION: SyncVersion = SyncVersion(
    label = DataConflictText.LocalVersionTitle,
    note = "晨起空腹",
    modifiedAt = "10:30",
)

val DEFAULT_CLOUD_VERSION: SyncVersion = SyncVersion(
    label = DataConflictText.CloudVersionTitle,
    note = "早上测量",
    modifiedAt = "10:28",
)

/** 页面意图：宿主翻译 UI 点击后交给 [reduceDataConflict]。 */
sealed interface DataConflictIntent {
    /** 「先同步再切换」：离线记录先入同步队列，成功后才切成员。 */
    data object SwitchAfterSync : DataConflictIntent

    /** 「直接切换」：保持离线，记录保留原归属。 */
    data object SwitchDirect : DataConflictIntent

    /** 「保留本机记录」/「保留本地版本」。 */
    data object KeepLocal : DataConflictIntent

    /** 「采用远端记录」/「采用云端版本」。 */
    data object KeepRemote : DataConflictIntent

    /** 「两条都保留」（仅多设备场景）。 */
    data object MergeBoth : DataConflictIntent

    /** 同步失败后重试（仅 `offlineSyncFailed` 态有意义）。 */
    data object Retry : DataConflictIntent

    /** 返回场景列表：出栈由宿主处理，状态机不自作主张改步。 */
    data object Back : DataConflictIntent
}

/**
 * 纯函数状态机：状态 × 意图 → 新状态。
 *
 * 规则：
 * - 只允许在当前步骤上推进，非法意图原样返回（防止跨屏跳跃）；
 * - 冲突解决结果写进 [DataConflictUiState.resolution]，备注回写取被保留那一端；
 * - 返回新实例，不改入参。
 */
fun reduceDataConflict(
    state: DataConflictUiState,
    intent: DataConflictIntent,
): DataConflictUiState = when (intent) {
    DataConflictIntent.SwitchAfterSync -> when (state.step) {
        DataConflictStep.OfflineSwitch -> state.copy(
            step = DataConflictStep.OfflineDone,
            offlineSwitchMode = OfflineSwitchMode.SyncThenSwitch,
            offlineSyncFailed = false,
        )

        else -> state
    }

    DataConflictIntent.SwitchDirect -> when (state.step) {
        DataConflictStep.OfflineSwitch -> state.copy(
            step = DataConflictStep.OfflineDone,
            offlineSwitchMode = OfflineSwitchMode.DirectSwitch,
        )

        else -> state
    }

    DataConflictIntent.KeepLocal -> when (state.step) {
        DataConflictStep.MultiDevice -> state.copy(
            step = DataConflictStep.MultiDone,
            resolution = ConflictResolution(
                choice = DataConflictChoice.KeepLocal,
                mergedWeights = listOf(state.localRecord.weight),
            ),
        )

        DataConflictStep.SyncConflict -> state.copy(
            step = DataConflictStep.SyncDone,
            resolution = ConflictResolution(
                choice = DataConflictChoice.KeepLocal,
                resolvedNote = state.localVersion.note,
            ),
        )

        else -> state
    }

    DataConflictIntent.KeepRemote -> when (state.step) {
        DataConflictStep.MultiDevice -> state.copy(
            step = DataConflictStep.MultiDone,
            resolution = ConflictResolution(
                choice = DataConflictChoice.KeepRemote,
                mergedWeights = listOf(state.remoteRecord.weight),
            ),
        )

        DataConflictStep.SyncConflict -> state.copy(
            step = DataConflictStep.SyncDone,
            resolution = ConflictResolution(
                choice = DataConflictChoice.KeepRemote,
                resolvedNote = state.cloudVersion.note,
            ),
        )

        else -> state
    }

    DataConflictIntent.MergeBoth -> when (state.step) {
        DataConflictStep.MultiDevice -> state.copy(
            step = DataConflictStep.MultiDone,
            resolution = ConflictResolution(
                choice = DataConflictChoice.Both,
                mergedWeights = listOf(state.localRecord.weight, state.remoteRecord.weight),
            ),
        )

        else -> state
    }

    DataConflictIntent.Retry -> when {
        state.step != DataConflictStep.OfflineSwitch -> state
        !state.offlineSyncFailed -> state
        else -> state.copy(offlineSyncFailed = false)
    }

    DataConflictIntent.Back -> state
}

/**
 * 数据冲突处理页（原型 22 六屏）。
 *
 * 纯呈现：状态与推进全部来自 [state] 与注入回调，组件不持有任何可变状态。
 * 回调里带 `state` 参数，宿主可直接 `reduceDataConflict(state, intent)` 回写。
 */
@Composable
fun DataConflictScreen(
    state: DataConflictUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSwitchOffline: (DataConflictUiState) -> Unit = {},
    onSwitchOnline: (DataConflictUiState) -> Unit = {},
    onKeepLocal: (DataConflictUiState) -> Unit = {},
    onKeepRemote: (DataConflictUiState) -> Unit = {},
    onMerge: (DataConflictUiState) -> Unit = {},
    onRetry: (DataConflictUiState) -> Unit = {},
) {
    when (state.step) {
        DataConflictStep.OfflineSwitch -> OfflineSwitchScreen(
            state = state,
            modifier = modifier,
            onBack = onBack,
            onSwitchOffline = onSwitchOffline,
            onSwitchOnline = onSwitchOnline,
            onRetry = onRetry,
        )

        DataConflictStep.OfflineDone -> OfflineDoneScreen(
            state = state,
            modifier = modifier,
            onBack = onBack,
        )

        DataConflictStep.MultiDevice -> MultiDeviceScreen(
            state = state,
            modifier = modifier,
            onBack = onBack,
            onKeepLocal = onKeepLocal,
            onKeepRemote = onKeepRemote,
            onMerge = onMerge,
        )

        DataConflictStep.MultiDone -> MultiDoneScreen(
            state = state,
            modifier = modifier,
            onBack = onBack,
        )

        DataConflictStep.SyncConflict -> SyncConflictScreen(
            state = state,
            modifier = modifier,
            onBack = onBack,
            onKeepLocal = onKeepLocal,
            onKeepRemote = onKeepRemote,
        )

        DataConflictStep.SyncDone -> SyncDoneScreen(
            state = state,
            modifier = modifier,
            onBack = onBack,
        )
    }
}

// ---- v-offline-switch ----

@Composable
private fun OfflineSwitchScreen(
    state: DataConflictUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSwitchOffline: (DataConflictUiState) -> Unit,
    onSwitchOnline: (DataConflictUiState) -> Unit,
    onRetry: (DataConflictUiState) -> Unit,
) {
    DataConflictScaffold(
        title = DataConflictText.TitleOfflineSwitch,
        modifier = modifier,
        onBack = onBack,
        actions = {
            QkButton(
                text = if (state.offlineSyncFailed) {
                    DataConflictText.RetrySync
                } else {
                    DataConflictText.SwitchAfterSync
                },
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Primary,
                onClick = {
                    if (state.offlineSyncFailed) onRetry(state) else onSwitchOnline(state)
                },
            )
            QkButton(
                text = DataConflictText.SwitchDirect,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Secondary,
                onClick = { onSwitchOffline(state) },
            )
        },
    ) {
        DataConflictBanner(
            icon = Icons.Outlined.WifiOff,
            text = DataConflictText.offlineDetected(state.pendingCount, state.targetName),
            tone = DataConflictBannerTone.Warn,
        )
        QkCard(padding = QkSpacing.Lg) {
            Text(
                DataConflictText.pendingHeader(state.ownerName),
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
                modifier = Modifier.padding(bottom = QkSpacing.Sm),
            )
            state.offlineRecords.forEachIndexed { index, record ->
                DataConflictRow(
                    title = record.summary,
                    subtitle = record.state,
                    subtitleColor = QkColor.AmberInk,
                    showDivider = index != state.offlineRecords.lastIndex,
                )
            }
        }
        DataConflictBanner(
            icon = QkIcons.About,
            text = DataConflictText.offlineOwnershipNote(state.ownerName, state.targetName),
        )
    }
}

// ---- v-offline-done ----

@Composable
private fun OfflineDoneScreen(
    state: DataConflictUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    DataConflictScaffold(
        title = DataConflictText.TitleOfflineDone,
        modifier = modifier,
        onBack = onBack,
        actions = {
            QkButton(
                text = DataConflictText.BackToScenarios,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Primary,
                onClick = onBack,
            )
        },
    ) {
        QkCard(padding = QkSpacing.Lg) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QkGlyph(text = state.targetInitial, tone = QkGlyphTone.Green)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        DataConflictText.viewingAs(state.targetName),
                        style = MaterialTheme.typography.titleSmall,
                        color = QkColor.Fg,
                    )
                    Text(
                        DataConflictText.keptOwnershipSummary(state.ownerName, state.pendingCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Muted,
                        modifier = Modifier.padding(top = QkSpacing.Xs),
                    )
                }
            }
        }
        QkCard(padding = QkSpacing.Lg) {
            Text(
                DataConflictText.OwnershipHeader,
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
                modifier = Modifier.padding(bottom = QkSpacing.Sm),
            )
            Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
                DataConflictCheckItem(
                    DataConflictText.ownershipKept(
                        state.ownerName,
                        state.pendingCount,
                        state.targetName,
                    ),
                )
                DataConflictCheckItem(DataConflictText.targetHistoryIntact(state.targetName))
                DataConflictCheckItem(DataConflictText.syncWhenOnline(state.ownerName))
            }
        }
        DataConflictBanner(icon = QkIcons.About, text = DataConflictText.SwitchCheckNote)
    }
}

// ---- v-multi-device ----

@Composable
private fun MultiDeviceScreen(
    state: DataConflictUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onKeepLocal: (DataConflictUiState) -> Unit,
    onKeepRemote: (DataConflictUiState) -> Unit,
    onMerge: (DataConflictUiState) -> Unit,
) {
    DataConflictScaffold(
        title = DataConflictText.TitleMultiDevice,
        modifier = modifier,
        onBack = onBack,
        actions = {
            QkButton(
                text = DataConflictText.KeepLocalRecord,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Primary,
                onClick = { onKeepLocal(state) },
            )
            QkButton(
                text = DataConflictText.KeepRemoteRecord,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Secondary,
                onClick = { onKeepRemote(state) },
            )
            QkButton(
                text = DataConflictText.KeepBothRecords,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Ghost,
                onClick = { onMerge(state) },
            )
        },
    ) {
        DataConflictBanner(
            icon = QkIcons.Device,
            text = DataConflictText.MultiDeviceDetected,
            tone = DataConflictBannerTone.Warn,
        )
        QkCard(padding = QkSpacing.Lg) {
            Text(
                DataConflictText.ConflictRecordsHeader,
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
                modifier = Modifier.padding(bottom = QkSpacing.Sm),
            )
            DataConflictRow(
                title = state.localRecord.summary,
                subtitle = state.localRecord.statusLine,
                trailing = {
                    QkPill(DataConflictText.TagLocal, QkPillTone.Green)
                },
            )
            DataConflictRow(
                title = state.remoteRecord.summary,
                subtitle = state.remoteRecord.statusLine,
                trailing = {
                    QkPill(DataConflictText.TagRemote, QkPillTone.Amber)
                },
                showDivider = false,
            )
        }
        DataConflictBanner(icon = QkIcons.About, text = DataConflictText.MultiDeviceNote)
    }
}

// ---- v-multi-done ----

@Composable
private fun MultiDoneScreen(
    state: DataConflictUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    DataConflictScaffold(
        title = DataConflictText.TitleMultiDone,
        modifier = modifier,
        onBack = onBack,
        actions = {
            QkButton(
                text = DataConflictText.BackToScenarios,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Primary,
                onClick = onBack,
            )
        },
    ) {
        QkCard(padding = QkSpacing.Lg) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QkIcon(QkIcons.Habit, tint = QkColor.Primary, size = QkSpacing.Xl)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        DataConflictText.MergedTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = QkColor.Fg,
                    )
                    Text(
                        DataConflictText.MergedSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Muted,
                        modifier = Modifier.padding(top = QkSpacing.Xs),
                    )
                }
            }
        }
        DataConflictResultCard(
            header = DataConflictText.MergeResultHeader,
            items = listOf(
                DataConflictText.MergeResultLocalSynced,
                DataConflictText.MergeResultRemoteHandled,
                DataConflictText.MergeResultTrendUpdated,
            ),
        )
        DataConflictBanner(icon = QkIcons.About, text = DataConflictText.MergeUndoNote)
    }
}

// ---- v-sync-conflict ----

@Composable
private fun SyncConflictScreen(
    state: DataConflictUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onKeepLocal: (DataConflictUiState) -> Unit,
    onKeepRemote: (DataConflictUiState) -> Unit,
) {
    DataConflictScaffold(
        title = DataConflictText.TitleSyncConflict,
        modifier = modifier,
        onBack = onBack,
        actions = {
            QkButton(
                text = DataConflictText.KeepLocalVersion,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Primary,
                onClick = { onKeepLocal(state) },
            )
            QkButton(
                text = DataConflictText.KeepCloudVersion,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Secondary,
                onClick = { onKeepRemote(state) },
            )
        },
    ) {
        DataConflictBanner(
            icon = QkIcons.Restore,
            text = DataConflictText.SyncConflictDetected,
            tone = DataConflictBannerTone.Warn,
        )
        QkCard(padding = QkSpacing.Lg) {
            Text(
                DataConflictText.conflictDetailHeader(state.conflictMeasuredAt),
                style = MaterialTheme.typography.titleSmall,
                color = QkColor.Fg,
                modifier = Modifier.padding(bottom = QkSpacing.Sm),
            )
            DataConflictRow(
                title = state.localVersion.label,
                subtitle = state.localVersion.noteLine,
                trailing = {
                    QkPill(DataConflictText.TagLocal, QkPillTone.Green)
                },
            )
            DataConflictRow(
                title = state.cloudVersion.label,
                subtitle = state.cloudVersion.noteLine,
                trailing = {
                    QkPill(DataConflictText.TagCloud, QkPillTone.Amber)
                },
                showDivider = false,
            )
        }
        DataConflictBanner(icon = QkIcons.About, text = DataConflictText.SyncConflictNote)
    }
}

// ---- v-sync-done ----

@Composable
private fun SyncDoneScreen(
    state: DataConflictUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    DataConflictScaffold(
        title = DataConflictText.TitleSyncDone,
        modifier = modifier,
        onBack = onBack,
        actions = {
            QkButton(
                text = DataConflictText.BackToScenarios,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Primary,
                onClick = onBack,
            )
        },
    ) {
        QkCard(padding = QkSpacing.Lg) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QkIcon(QkIcons.Habit, tint = QkColor.Primary, size = QkSpacing.Xl)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        DataConflictText.ResolvedTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = QkColor.Fg,
                    )
                    Text(
                        DataConflictText.ResolvedSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = QkColor.Muted,
                        modifier = Modifier.padding(top = QkSpacing.Xs),
                    )
                }
            }
        }
        DataConflictResultCard(
            header = DataConflictText.ResolveResultHeader,
            items = listOf(
                DataConflictText.ResolveResultNote,
                DataConflictText.resolveResultWeight(state.measureWeight),
                DataConflictText.ResolveResultSaved,
            ),
        )
        DataConflictBanner(icon = QkIcons.About, text = DataConflictText.ResolvePreferenceNote)
    }
}

// ---- 共用版式 ----

/** 页面骨架：返回 + 标题（原型 .navbar）→ 内容区 → 底部按钮区。 */
@Composable
private fun DataConflictScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = onBack)
            QkPageTitle(
                title = title,
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.CardGap),
        ) {
            content()
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            actions()
        }
    }
}

/** 横幅色调：Info = 薄荷底（原型 .banner），Warn = 琥珀底（原型 .banner.warn）。 */
private enum class DataConflictBannerTone { Info, Warn }

/** 提示横幅（原型 .banner：圆角条 + 左侧线性图标 + 小字）。 */
@Composable
private fun DataConflictBanner(
    icon: ImageVector,
    text: String,
    tone: DataConflictBannerTone = DataConflictBannerTone.Info,
    modifier: Modifier = Modifier,
) {
    val background = when (tone) {
        DataConflictBannerTone.Info -> QkColor.Mint
        DataConflictBannerTone.Warn -> QkColor.AccentSoft
    }
    val foreground = when (tone) {
        DataConflictBannerTone.Info -> QkColor.Primary
        DataConflictBannerTone.Warn -> QkColor.Accent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QkRadius.Button))
            .background(background)
            .padding(horizontal = QkSpacing.Lg, vertical = QkSpacing.Md),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(icon, tint = foreground, size = QkSpacing.Lg)
        Text(
            text = text,
            modifier = Modifier.padding(start = QkSpacing.Sm).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
        )
    }
}

/** 记录行（原型 .row：主文案 + 次级文案 + 可选右侧标签 + 行分隔线）。 */
@Composable
private fun DataConflictRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    subtitleColor: Color = QkColor.Muted,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = QkColor.Fg)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    modifier = Modifier.padding(top = QkSpacing.Xs),
                )
            }
            trailing?.invoke()
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QkStroke.Thin)
                    .background(QkColor.LineSoft),
            )
        }
    }
}

/** 勾选项（原型结果卡里的「✔ + 说明」行）。 */
@Composable
private fun DataConflictCheckItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
        verticalAlignment = Alignment.Top,
    ) {
        QkIcon(QkIcons.Habit, tint = QkColor.Primary, size = QkSpacing.Lg)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
        )
    }
}

/** 结果卡（原型 .card.lg：小标题 + 勾选项列表）。 */
@Composable
private fun DataConflictResultCard(header: String, items: List<String>) {
    QkCard(padding = QkSpacing.Lg) {
        Text(
            header,
            style = MaterialTheme.typography.titleSmall,
            color = QkColor.Fg,
            modifier = Modifier.padding(bottom = QkSpacing.Sm),
        )
        Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
            items.forEach { DataConflictCheckItem(it) }
        }
    }
}
