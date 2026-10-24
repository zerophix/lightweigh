package com.xcwk.scalehub.feature.measure

import androidx.compose.runtime.Composable

/**
 * measure — 首页「开始测量」入口（T-077 接入波第三批）。
 *
 * ## 为什么单列一份
 * [MeasureFlowScreen] 是呈现层，不知道自己在导航里的位置。入口文案、宿主页、
 * 返回去向三者写在这里；首页只引 [MEASURE_ENTRY_TITLE]，流程屏只在
 * [MeasureFlowEntry] 里出现一次。
 *
 * ## 边界（本轮最小装配）
 * - 状态由 [MeasureFlowRoute] 本地持有，阅读数据/超时/成员名仍是演示值；
 *   真实读数、保存、反馈待 ViewModel 注入后替换（本文件签名不变）。
 * - 退出即回首页（宿主清空 [MeasureHomeLayer]）。
 */

/** 首页体重卡上的入口文案（原型首页主按钮逐字）。 */
const val MEASURE_ENTRY_TITLE = "开始测量"

/** 首页可整屏覆盖的接入层；[Home] 即真实首页本身。 */
enum class MeasureHomeLayer {
    Home,
    MeasureFlow,
    SyncCenter,
}

/**
 * 层导航纯函数：`open` 非空即进入该层，`null` 表示返回首页。
 *
 * 覆盖层是「模态替换」语义（与既有分享/导出一致），不叠栈：
 * 同一时刻首页上只有一个接入层，返回键把它清回 [MeasureHomeLayer.Home]。
 */
fun measureHomeLayerReduce(open: MeasureHomeLayer?): MeasureHomeLayer =
    open ?: MeasureHomeLayer.Home

/** 首页「开始测量」→ 测量流程八态。退出回首页。 */
@Composable
fun MeasureFlowEntry(onExit: () -> Unit = {}) {
    MeasureFlowRoute(onExit = onExit)
}
