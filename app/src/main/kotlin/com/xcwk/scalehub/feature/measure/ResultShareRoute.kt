package com.xcwk.scalehub.feature.measure

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * measure — [ResultShareFlow] 的接入装配点（T-078 接入波）。
 *
 * 首页测量结果区挂一个「分享 / 导出」入口（[com.xcwk.scalehub.ui.shell.WiringEntries.ResultShare]），
 * 三态（分享预览 / 导出配置 / 导出成功）由 [resultShareReduce] 推进。
 *
 * ## 返回口径
 * - 导出配置页的返回回到分享预览，而不是整段退出（避免「选完格式按返回」直接回首页）
 * - 分享预览页与导出成功页的返回交回宿主
 *
 * ## 仍是空回调的地方
 * - 分享渠道（微信/朋友圈/保存图片/复制链接）与生成导出文件：需要真实分享 SDK / 文件系统，
 *   本轮只把选择结果记进状态；`onExport` 不落文件
 * - 「重新发送到邮箱」同理，保持空回调
 */
@Composable
fun ResultShareRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf(ResultShareUiState()) }

    ResultShareFlow(
        state = state,
        onIntent = { state = resultShareReduce(state, it) },
        modifier = modifier,
        onBack = {
            if (state.step == ResultShareStep.Export) {
                state = state.copy(step = ResultShareStep.Share)
            } else {
                onBack()
            }
        },
        onShare = { channel -> state = resultShareReduce(state, ResultShareIntent.SelectChannel(channel)) },
        onExport = { },
        onBackToReport = onBack,
    )
}
