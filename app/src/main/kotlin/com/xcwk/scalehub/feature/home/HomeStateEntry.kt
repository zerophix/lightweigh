package com.xcwk.scalehub.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * home — 首页八态接入（T-095 接入波第三批）。
 *
 * ## 接入口径
 * [HomeStateScreen] 只认外部注入的 [HomeUiState]。首页按**能证实的信号**选态，
 * 信号不足时保持真实首页（[HomeStage.Home]），不伪造「未连接 / 同步中 / 目标达成」：
 *
 * | 信号 | 来源 | 态 |
 * |---|---|---|
 * | 加载失败 | 待 ViewModel 提供（MeasureUiState.error 是保存错误，不能复用） | [HomeStage.Error] |
 * | 无测量记录 | loaded 为真且 HomeGoalState.latestWeight == null | [HomeStage.Empty] |
 * | 其余 | — | [HomeStage.Home]（真实首页） |
 *
 * 首轮读完之前（loaded == false）一律保持真实首页：否则冷启动会先闪一屏空态。
 *
 * 设备连接、待同步条数、目标达成、简报这几路信号 MeasureUiState 尚未提供，
 * 待 ViewModel 补字段后由 [homeStageOf] 增参接入，调用点只改一处。
 */

/**
 * 真实信号 → 首页态。加载失败优先于空数据：失败时不知道有没有记录，
 * 报「加载失败」比报「还没有数据」更诚实。
 */
fun homeStageOf(hasMeasurements: Boolean, loadFailed: Boolean): HomeStage = when {
    loadFailed -> HomeStage.Error
    !hasMeasurements -> HomeStage.Empty
    else -> HomeStage.Home
}

/** 八态页是否替换真实首页：Home 态本身就是真实首页，替换会自我循环。 */
fun homeStatesReplacesHome(stage: HomeStage): Boolean = stage != HomeStage.Home

/**
 * 八态覆盖层：本地持有状态，推进走 [homeReduce]。
 *
 * - 「开始第一次测量」上交 [onStartMeasure]（宿主打开测量流程）；
 * - 其余回到 [HomeStage.Home] 的事件视为退出，上交 [onClose]；
 * - 「重新加载」本轮只回正常首页，真实重查由宿主的刷新信号负责。
 */
@Composable
fun HomeStatesRoute(
    initial: HomeUiState,
    onClose: () -> Unit,
    onStartMeasure: () -> Unit = onClose,
    onOpenMemberSheet: () -> Unit = {},
) {
    var state by remember(initial) { mutableStateOf(initial) }

    HomeStateScreen(
        state = state,
        onEvent = { event ->
            state = homeReduce(state, event)
            if (event == HomeEvent.StartFirstMeasure) {
                onStartMeasure()
            } else if (state.stage == HomeStage.Home) {
                onClose()
            }
        },
        onOpenMemberSheet = onOpenMemberSheet,
    )
}
