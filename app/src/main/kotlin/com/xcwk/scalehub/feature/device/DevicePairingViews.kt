package com.xcwk.scalehub.feature.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkDeviceHero
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/*
 * device — 配对流程三态呈现（T-104 · 原型 04-device.html）。
 *
 * 三个 view 与状态机一一对应（状态机在 DeviceUiState.kt 的 pairingReduce）：
 * - v-pairing       → PairingWaitingView
 * - v-pair-success  → PairingSuccessView
 * - v-pair-fail     → PairingFailView
 *
 * 拆出本文件是为了让 DeviceScreen.kt（列表 + 扫描发现）只留一处分支接入，
 * 与既有多 view 屏「状态机/文案 + 各 view 分文件」的口径一致。
 */

/**
 * 配对流程路由：三屏共用同一台秤名；真实连接由 BlePort 层负责，
 * 本层只按 [pairingReduce] 推进呈现态。
 */
@Composable
internal fun PairingFlow(state: PairingState, onIntent: (DeviceIntent) -> Unit) {
    when (state) {
        is PairingState.Pairing -> PairingWaitingView(state.deviceName, onIntent)
        is PairingState.Success -> PairingSuccessView(state.deviceName, onIntent)
        is PairingState.Fail -> PairingFailView(state.deviceName, onIntent)
    }
}

/** 压栈页导航条（原型 .navbar：返回圆钮 + 页标题）。 */
@Composable
private fun PairingNavBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkBackButton(onClick = onBack)
        Text(
            PAIRING_TITLE,
            modifier = Modifier.weight(1f).padding(start = QkSpacing.Sm),
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
        )
    }
}

/** v-pairing：蓝牙环 + 「正在连接 …」+ 底部「模拟失败/超时」「模拟成功」。 */
@Composable
private fun PairingWaitingView(deviceName: String, onIntent: (DeviceIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = QkSpacing.Page)) {
        PairingNavBar { onIntent(DeviceIntent.Pairing(PairingIntent.Exit)) }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            QkDeviceHero(status = pairingConnectingText(deviceName), sub = PAIRING_KEEP_NEAR)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = QkSpacing.Xl),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm),
        ) {
            QkButton(
                text = PAIRING_SIMULATE_FAIL,
                style = QkButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(DeviceIntent.Pairing(PairingIntent.Failed)) },
            )
            QkButton(
                text = PAIRING_SIMULATE_SUCCESS,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(DeviceIntent.Pairing(PairingIntent.Succeeded)) },
            )
        }
    }
}

/**
 * v-pair-success：居中成功状态页（原型无导航条）。
 *「开始测量」的测量入口由导航层接线，本层先退回设备页。
 */
@Composable
private fun PairingSuccessView(deviceName: String, onIntent: (DeviceIntent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = QkSpacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        QkIconBubble(QkIcons.Habit, QkGlyphTone.Green, size = QkSpacing.Xl + QkSpacing.Lg)
        Text(
            PAIRING_SUCCESS_TITLE,
            modifier = Modifier.padding(top = QkSpacing.Lg),
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
        )
        Text(
            pairingSuccessBody(deviceName),
            modifier = Modifier.padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
            textAlign = TextAlign.Center,
        )
        QkButton(
            text = PAIRING_START_MEASURE,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            onClick = { onIntent(DeviceIntent.Pairing(PairingIntent.Exit)) },
        )
        QkButton(
            text = PAIRING_BACK_TO_DEVICE,
            style = QkButtonStyle.Ghost,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            onClick = { onIntent(DeviceIntent.Pairing(PairingIntent.Exit)) },
        )
    }
}

/** v-pair-fail：导航条 + 失败状态页 +「重新搜索」「返回」。 */
@Composable
private fun PairingFailView(deviceName: String, onIntent: (DeviceIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = QkSpacing.Page)) {
        PairingNavBar { onIntent(DeviceIntent.Pairing(PairingIntent.Exit)) }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            QkIconBubble(QkIcons.Bluetooth, QkGlyphTone.Orange, size = QkSpacing.Xl + QkSpacing.Lg)
            Text(
                PAIRING_FAIL_TITLE,
                modifier = Modifier.padding(top = QkSpacing.Lg),
                style = MaterialTheme.typography.titleLarge,
                color = QkColor.Fg,
            )
            Text(
                pairingFailBody(deviceName),
                modifier = Modifier.padding(top = QkSpacing.Sm),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
        QkButton(
            text = PAIRING_RETRY,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onIntent(DeviceIntent.Pairing(PairingIntent.Retry)) },
        )
        QkButton(
            text = PAIRING_BACK,
            style = QkButtonStyle.Ghost,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md, bottom = QkSpacing.Xl),
            onClick = { onIntent(DeviceIntent.Pairing(PairingIntent.Exit)) },
        )
    }
}

