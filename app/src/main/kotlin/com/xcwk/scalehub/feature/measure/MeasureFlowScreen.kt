package com.xcwk.scalehub.feature.measure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkActionChip
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkMetric
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkPill
import com.xcwk.scalehub.ui.components.QkPillTone
import com.xcwk.scalehub.ui.components.QkProgress
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * measure — 测量完整状态机的呈现层（T-077 · 原型 12 `12-measure-full.html`）。
 *
 * 状态与逐字文案见 `MeasureFlow.kt`（[MeasureFlowUiState] / [MeasureFlowIntent] /
 * [measureReduce] / [MeasureFlowText]）；本文件只负责把八个态画出来。
 *
 * 呈现式边界：读数、超时、成员名经状态注入；保存、反馈、退出走回调。
 * 颜色/间距/圆角一律取 Qk token，禁止裸 dp 与硬编码色值。
 */

// ---- 呈现 ----

/**
 * 有状态入口：本地持有 [MeasureFlowUiState]，推进走 [measureReduce]。
 *
 * 接入波落地时把状态提到 ViewModel 即可，本 Composable 的签名不变。
 */
@Composable
fun MeasureFlowRoute(
    state: MeasureFlowUiState = MeasureFlowUiState(),
    onExit: () -> Unit = {},
    onFeedback: () -> Unit = {},
) {
    var flow by remember { mutableStateOf(state) }
    MeasureFlowScreen(
        state = flow,
        onIntent = { flow = measureReduce(flow, it) },
        onExit = onExit,
        onFeedback = onFeedback,
    )
}

@Composable
fun MeasureFlowScreen(
    state: MeasureFlowUiState,
    onIntent: (MeasureFlowIntent) -> Unit,
    onExit: () -> Unit = {},
    onFeedback: () -> Unit = {},
) {
    when (state.step) {
        MeasureFlowStep.Check -> CheckView(state, onIntent)
        MeasureFlowStep.Prep -> PrepView(onIntent)
        MeasureFlowStep.Stable -> StableView(state, onIntent)
        MeasureFlowStep.Calc -> CalcView(state, onIntent)
        MeasureFlowStep.Left -> LeftView(onIntent)
        MeasureFlowStep.Invalid -> InvalidView(onIntent, onFeedback)
        MeasureFlowStep.SaveFail -> SaveFailView(state, onIntent)
        MeasureFlowStep.Success -> SuccessView(state, onIntent, onExit)
    }
}

/** 统一页框：返回钮 + 标题 + 可滚动内容。 */
@Composable
private fun FlowPage(
    title: String?,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
        ) {
            QkBackButton(onClick = onBack)
            if (title != null) QkPageTitle(title)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg), content = content)
        Spacer(modifier = Modifier.height(QkSpacing.Xl))
    }
}

@Composable
private fun CenteredHero(title: String, caption: String? = null, icon: ImageVector? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            QkIconBubble(icon, QkGlyphTone.Green, QkSpacing.Xl * 2)
            Spacer(modifier = Modifier.height(QkSpacing.Lg))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        if (caption != null) {
            Text(
                caption,
                modifier = Modifier.padding(top = QkSpacing.Xs),
                style = MaterialTheme.typography.bodyMedium,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CheckView(state: MeasureFlowUiState, onIntent: (MeasureFlowIntent) -> Unit) {
    FlowPage(title = MeasureFlowText.CheckTitle, onBack = { onIntent(MeasureFlowIntent.Back) }) {
        CenteredHero(MeasureFlowText.CheckHero, MeasureFlowText.CheckMember)
        QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap), padding = QkSpacing.Sm) {
            QkListRow(
                title = MeasureFlowText.CheckDevice,
                subtitle = MeasureFlowText.CheckDeviceDetail,
                icon = QkIcons.Bluetooth,
                tone = QkGlyphTone.Green,
                trailing = { StatusPill(state.precheck.deviceReady) },
            )
            QkListRow(
                title = MeasureFlowText.CheckBluetooth,
                icon = QkIcons.Bluetooth,
                tone = QkGlyphTone.Green,
                trailing = { StatusPill(state.precheck.bluetoothReady) },
            )
            QkListRow(
                title = MeasureFlowText.CheckSurface,
                icon = QkIcons.About,
                tone = QkGlyphTone.Orange,
                trailing = { StatusPill(state.precheck.surfaceConfirmed) },
                showDivider = false,
                // 秤面需要用户自己看一眼再确认（原型「待确认」）
                onClick = { onIntent(MeasureFlowIntent.ConfirmSurface) },
            )
        }
        QkButton(
            text = MeasureFlowText.CheckConfirm,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            enabled = state.ready,
            onClick = { onIntent(MeasureFlowIntent.ConfirmPrecheck) },
        )
    }
}

/** 就绪 / 待确认 胶囊（原型 `.tag` / `.tag.warn`）。 */
@Composable
private fun StatusPill(ready: Boolean) {
    if (ready) {
        QkPill(MeasureFlowText.Ready, QkPillTone.Green)
    } else {
        QkPill(MeasureFlowText.Pending, QkPillTone.Amber)
    }
}

@Composable
private fun PrepView(onIntent: (MeasureFlowIntent) -> Unit) {
    FlowPage(title = MeasureFlowText.PrepTitle, onBack = { onIntent(MeasureFlowIntent.Back) }) {
        CenteredHero(MeasureFlowText.PrepHero, icon = QkIcons.Weight)
        QkCard(modifier = Modifier.padding(top = QkSpacing.Lg), padding = QkSpacing.Sm) {
            QkListRow(title = MeasureFlowText.PrepElectrodes, icon = QkIcons.Habit)
            QkListRow(title = MeasureFlowText.PrepLegs, icon = QkIcons.Habit)
            QkListRow(title = MeasureFlowText.PrepStill, icon = QkIcons.Habit, showDivider = false)
        }
        QkButton(
            text = MeasureFlowText.PrepReady,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            onClick = { onIntent(MeasureFlowIntent.ReadyToStand) },
        )
    }
}

@Composable
private fun StableView(state: MeasureFlowUiState, onIntent: (MeasureFlowIntent) -> Unit) {
    FlowPage(title = MeasureFlowText.MeasuringTitle, onBack = { onIntent(MeasureFlowIntent.Back) }) {
        WeightReadout(
            value = state.liveWeightText,
            caption = MeasureFlowText.StableWaiting,
            percent = 68,
            valueColor = QkColor.Muted,
            captionColor = QkColor.Muted,
        )
        CenteredHero(MeasureFlowText.StableKeepStill, MeasureFlowText.StableAutoNext)
        QkButton(
            text = MeasureFlowText.StableDone,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            onClick = { onIntent(MeasureFlowIntent.ReadingStable(62.4)) },
        )
        Text(
            MeasureFlowText.StableTimeoutHint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Sm)
                .clickable { onIntent(MeasureFlowIntent.Timeout) },
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
            textAlign = TextAlign.Center,
        )
    }
    if (state.timeoutDialogVisible) {
        AlertDialog(
            onDismissRequest = { onIntent(MeasureFlowIntent.DismissTimeout) },
            title = { Text(MeasureFlowText.TimeoutTitle) },
            text = { Text(MeasureFlowText.TimeoutBody) },
            confirmButton = {
                TextButton(onClick = { onIntent(MeasureFlowIntent.RestartAfterTimeout) }) {
                    Text(MeasureFlowText.RetryMeasure)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(MeasureFlowIntent.DismissTimeout) }) {
                    Text(MeasureFlowText.GotIt)
                }
            },
        )
    }
}

@Composable
private fun CalcView(state: MeasureFlowUiState, onIntent: (MeasureFlowIntent) -> Unit) {
    FlowPage(title = MeasureFlowText.MeasuringTitle, onBack = { onIntent(MeasureFlowIntent.Back) }) {
        WeightReadout(
            value = state.weightText,
            caption = MeasureFlowText.CalcLocked,
            percent = 90,
            valueColor = QkColor.Fg,
            captionColor = QkColor.Primary,
        )
        CenteredHero(MeasureFlowText.CalcTitle)
        Text(
            MeasureFlowText.CalcBranchLabel,
            modifier = Modifier.padding(top = QkSpacing.Lg, bottom = QkSpacing.Sm),
            style = MaterialTheme.typography.labelMedium,
            color = QkColor.Muted,
        )
        // 原型把四条异常分支放在本态做可点演示；真实链路由蓝牙/算法事件触发
        Row(horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
            QkActionChip(MeasureFlowText.BranchBluetoothLost) {
                onIntent(MeasureFlowIntent.BluetoothLost)
            }
            QkActionChip(MeasureFlowText.BranchLeftEarly) {
                onIntent(MeasureFlowIntent.DeviceLeft)
            }
        }
        Row(modifier = Modifier.padding(top = QkSpacing.Sm), horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
            QkActionChip(MeasureFlowText.BranchInvalid) {
                onIntent(MeasureFlowIntent.DataInvalid)
            }
            QkActionChip(MeasureFlowText.BranchSaveFail) {
                onIntent(MeasureFlowIntent.SaveFailed)
            }
        }
        QkButton(
            text = MeasureFlowText.CalcDone,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            onClick = { onIntent(MeasureFlowIntent.CalcFinished) },
        )
        Text(
            MeasureFlowText.CalcCancelHint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QkSpacing.Sm)
                .clickable { onIntent(MeasureFlowIntent.RequestCancel) },
            style = MaterialTheme.typography.labelSmall,
            color = QkColor.Muted,
            textAlign = TextAlign.Center,
        )
    }
    if (state.cancelDialogVisible) {
        AlertDialog(
            onDismissRequest = { onIntent(MeasureFlowIntent.DismissCancel) },
            title = { Text(MeasureFlowText.CancelDialogTitle) },
            text = { Text(MeasureFlowText.CancelDialogBody) },
            confirmButton = {
                TextButton(onClick = { onIntent(MeasureFlowIntent.ConfirmCancel) }) {
                    Text(MeasureFlowText.CancelDialogConfirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(MeasureFlowIntent.DismissCancel) }) {
                    Text(MeasureFlowText.CancelDialogKeep)
                }
            },
        )
    }
    if (state.bluetoothDialogVisible) {
        AlertDialog(
            onDismissRequest = { onIntent(MeasureFlowIntent.CancelAfterBluetoothLost) },
            title = { Text(MeasureFlowText.BtDialogTitle) },
            text = { Text(MeasureFlowText.BtDialogBody) },
            confirmButton = {
                TextButton(onClick = { onIntent(MeasureFlowIntent.ReconnectBluetooth) }) {
                    Text(MeasureFlowText.BtDialogReconnect)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(MeasureFlowIntent.CancelAfterBluetoothLost) }) {
                    Text(MeasureFlowText.BtDialogCancel)
                }
            },
        )
    }
}

/** 稳定/计算共用的读数区：读数 + 状态说明 + 进度条。 */
@Composable
private fun WeightReadout(
    value: String,
    caption: String,
    percent: Int,
    valueColor: Color,
    captionColor: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.displayLarge, color = valueColor)
        Text(
            caption,
            modifier = Modifier.padding(top = QkSpacing.Xs),
            style = MaterialTheme.typography.labelMedium,
            color = captionColor,
        )
        QkProgress(percent = percent, modifier = Modifier.padding(top = QkSpacing.Lg))
    }
}

/** 状态页（提前离开 / 无效数据）：图标泡 + 标题 + 说明 + 主次按钮。 */
@Composable
private fun StatePage(
    title: String,
    icon: ImageVector,
    tone: QkGlyphTone,
    headline: String,
    body: String,
    onBack: () -> Unit,
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
) {
    FlowPage(title = title, onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QkIconBubble(icon, tone, QkSpacing.Xl * 2)
            Text(
                headline,
                modifier = Modifier.padding(top = QkSpacing.Lg),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
                textAlign = TextAlign.Center,
            )
            Text(
                body,
                modifier = Modifier.padding(top = QkSpacing.Sm),
                style = MaterialTheme.typography.bodyMedium,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
            QkButton(
                text = primaryText,
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
                onClick = onPrimary,
            )
            QkButton(
                text = secondaryText,
                style = QkButtonStyle.Ghost,
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
                onClick = onSecondary,
            )
        }
    }
}

@Composable
private fun LeftView(onIntent: (MeasureFlowIntent) -> Unit) {
    StatePage(
        title = MeasureFlowText.LeftTitle,
        icon = QkIcons.Walk,
        tone = QkGlyphTone.Orange,
        headline = MeasureFlowText.LeftHeadline,
        body = MeasureFlowText.LeftBody,
        onBack = { onIntent(MeasureFlowIntent.RetryMeasure) },
        primaryText = MeasureFlowText.RetryMeasure,
        onPrimary = { onIntent(MeasureFlowIntent.RetryMeasure) },
        secondaryText = MeasureFlowText.Back,
        onSecondary = { onIntent(MeasureFlowIntent.Back) },
    )
}

@Composable
private fun InvalidView(onIntent: (MeasureFlowIntent) -> Unit, onFeedback: () -> Unit) {
    StatePage(
        title = MeasureFlowText.InvalidTitle,
        icon = QkIcons.Bmi,
        tone = QkGlyphTone.Gray,
        headline = MeasureFlowText.InvalidHeadline,
        body = MeasureFlowText.InvalidBody,
        onBack = { onIntent(MeasureFlowIntent.RetryMeasure) },
        primaryText = MeasureFlowText.RetryMeasure,
        onPrimary = { onIntent(MeasureFlowIntent.RetryMeasure) },
        secondaryText = MeasureFlowText.Feedback,
        onSecondary = {
            onIntent(MeasureFlowIntent.Feedback)
            onFeedback()
        },
    )
}

@Composable
private fun SaveFailView(state: MeasureFlowUiState, onIntent: (MeasureFlowIntent) -> Unit) {
    FlowPage(title = MeasureFlowText.SaveFailTitle, onBack = { onIntent(MeasureFlowIntent.Later) }) {
        QkCard(modifier = Modifier.padding(top = QkSpacing.CardGap)) {
            Text(
                MeasureFlowText.SaveFailDoneHint,
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Muted,
            )
            Row(
                modifier = Modifier.padding(top = QkSpacing.Xs),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
            ) {
                Text(state.result.weightText, style = MaterialTheme.typography.displaySmall, color = QkColor.Fg)
                Text(
                    MeasureFlowText.WeightUnit,
                    modifier = Modifier.padding(bottom = QkSpacing.Xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Muted,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${MeasureFlowText.FatInline}${state.result.fatText}${MeasureFlowText.PercentUnit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Primary,
                )
            }
            Row(modifier = Modifier.padding(top = QkSpacing.Sm)) {
                QkPill(MeasureFlowText.Unsaved, QkPillTone.Amber)
            }
        }
        Row(
            modifier = Modifier.padding(top = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
        ) {
            QkIcon(QkIcons.About, tint = QkColor.AmberInk)
            Text(
                MeasureFlowText.SaveFailBody,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Sub,
            )
        }
        QkButton(
            text = MeasureFlowText.RetrySave,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            onClick = { onIntent(MeasureFlowIntent.RetrySave) },
        )
        QkButton(
            text = MeasureFlowText.Later,
            style = QkButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            onClick = { onIntent(MeasureFlowIntent.Later) },
        )
    }
}

@Composable
private fun SuccessView(
    state: MeasureFlowUiState,
    onIntent: (MeasureFlowIntent) -> Unit,
    onExit: () -> Unit,
) {
    // 原型 v-success 没有导航标题，只有顶部的「测量完成 · 已保存」横幅
    FlowPage(title = null, onBack = onExit) {
        Row(
            modifier = Modifier.padding(top = QkSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Xs),
        ) {
            QkIcon(QkIcons.Habit, tint = QkColor.Primary, size = QkSpacing.Lg)
            Text(
                MeasureFlowText.SuccessBanner,
                style = MaterialTheme.typography.labelSmall,
                color = QkColor.Primary,
            )
        }
        QkCard(modifier = Modifier.padding(top = QkSpacing.Sm)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
                Text(state.result.weightText, style = MaterialTheme.typography.displayLarge, color = QkColor.Fg)
                Text(
                    MeasureFlowText.SuccessWeightUnit,
                    modifier = Modifier.padding(bottom = QkSpacing.Xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Muted,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    state.result.diffText,
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Primary,
                )
            }
            Text(
                MeasureFlowText.SuccessSummary,
                modifier = Modifier.padding(top = QkSpacing.Md),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.CardGap),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
        ) {
            QkMetric(
                label = MeasureFlowText.BodyFatLabel,
                value = state.result.fatText,
                unit = MeasureFlowText.PercentUnit,
                modifier = Modifier.weight(1f),
            )
            QkMetric(
                label = MeasureFlowText.MuscleLabel,
                value = state.result.muscleText,
                unit = MeasureFlowText.WeightUnit,
                modifier = Modifier.weight(1f),
            )
        }
        QkButton(
            text = MeasureFlowText.SuccessDone,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            onClick = {
                onIntent(MeasureFlowIntent.Finish)
                onExit()
            },
        )
        QkButton(
            text = MeasureFlowText.DuplicateMeasure,
            style = QkButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            onClick = { onIntent(MeasureFlowIntent.RequestDuplicateMeasure) },
        )
    }
    if (state.duplicateDialogVisible) {
        AlertDialog(
            onDismissRequest = { onIntent(MeasureFlowIntent.GiveUpDuplicate) },
            title = { Text(MeasureFlowText.DupTitle) },
            text = { Text(MeasureFlowText.DupBody) },
            confirmButton = {
                TextButton(onClick = { onIntent(MeasureFlowIntent.ReplaceLastRecord) }) {
                    Text(MeasureFlowText.DupReplace)
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { onIntent(MeasureFlowIntent.KeepAsNewRecord) }) {
                        Text(MeasureFlowText.DupKeep)
                    }
                    TextButton(onClick = { onIntent(MeasureFlowIntent.GiveUpDuplicate) }) {
                        Text(MeasureFlowText.DupGiveUp)
                    }
                }
            },
        )
    }
}
