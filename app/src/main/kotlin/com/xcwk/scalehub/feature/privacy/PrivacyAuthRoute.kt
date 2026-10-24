package com.xcwk.scalehub.feature.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.xcwk.scalehub.ui.theme.QkTheme

/**
 * privacy — 隐私授权流程的接线层（宿主页 = 设置 → 隐私设置）。
 *
 * ## 为什么单列一份
 * [PrivacyAuthFlow] 是呈现式组件：它不落库、不知道自己在导航里的位置。
 * 本文件只做三件事：持有五态状态、把意图交给 [privacyAuthReduce]、
 * 把「同意 / 撤回」按宿主回调转出。导航状态留在本文件，不改 shell。
 *
 * ## 边界
 * 本仓库没有授权落库端口，故 [onConsentGranted] / [onConsentWithdrawn] 默认空实现：
 * 同意与撤回只在本页进程内生效，杀进程重进会回到授权页。这是当前数据层的真实能力，
 * 不假装已持久化（同 SDK 清单授权的既有口径）。
 */

/** 隐私设置页上的入口文案（纯数据，JVM 单测校验）。 */
object PrivacyAuthEntry {
    /** 稳定标识（进程重建 / 埋点用）。 */
    const val ID: String = "settings.privacyAuth"

    /** 入口行标题。 */
    const val TITLE: String = "健康数据授权"

    /** 入口行副标题。 */
    const val HINT: String = "同意、撤回与四类删除规则"

    /** 目标屏（与 [PrivacyAuthFlow] 一一对应）。 */
    const val TARGET: String = "PrivacyAuthFlow"
}

/**
 * 隐私授权流程装配点。
 *
 * @param onBack 返回隐私设置页
 * @param onConsentGranted 同意授权（[includeCloudSync] = 是否含云端同步）
 * @param onConsentWithdrawn 确认撤回全部授权
 */
@Composable
fun PrivacyAuthRoute(
    onBack: () -> Unit = {},
    onConsentGranted: (includeCloudSync: Boolean) -> Unit = {},
    onConsentWithdrawn: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 只序列化「授权页/已授权页」等粗粒度状态即可，本页是短生命周期流程
    var state by rememberSaveable(stateSaver = PrivacyAuthStateSaver) {
        mutableStateOf(PrivacyAuthUiState())
    }
    PrivacyAuthFlow(
        state = state,
        onIntent = { state = privacyAuthReduce(state, it) },
        modifier = modifier,
        onBack = onBack,
        onConsentGranted = onConsentGranted,
        onConsentWithdrawn = onConsentWithdrawn,
    )
}

/** 进程重建的序列化适配：五态 + 云端同步勾选 + 弹层可见性。 */
private val PrivacyAuthStateSaver = androidx.compose.runtime.saveable.listSaver<PrivacyAuthUiState, Any>(
    save = { listOf(it.step.name, it.cloudSyncSelected, it.withdrawSheetVisible) },
    restore = { saved ->
        PrivacyAuthUiState(
            step = PrivacyAuthStep.values().firstOrNull { it.name == saved[0] } ?: PrivacyAuthStep.Auth,
            cloudSyncSelected = saved[1] as Boolean,
            withdrawSheetVisible = saved[2] as Boolean,
        )
    },
)

@Preview(showBackground = true)
@Composable
private fun PrivacyAuthRoutePreview() {
    QkTheme { PrivacyAuthRoute() }
}
