package com.xcwk.scalehub.application

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 数据变更的语义类型（DEC-034 任务组 A）。
 *
 * 原来所有写入都发同一个 `Unit` 信号，订阅方收到后无差别全量 `refresh()`——
 * 用户改个头像，趋势页也要重查测量数据。类型化后订阅方按需订阅。
 *
 * 分两类：
 *  - **本地写入**（[Measurements] / [HealthLogs] / [Profile]）：既要刷 UI，也要推云
 *  - **云端侧操作**（[AccountDataRestored] / [AccountDeleted]）：只刷 UI，
 *    绝不能再推一次云——刚拉完/刚清空的数据回推是空转，且 SyncTrigger 的点火源
 *    本就只挂在本地写入事件上
 */
sealed interface DataChange {

    /** 测量记录增删（趋势 / 历史 / 报告 / 首页体重都依赖）。 */
    data object Measurements : DataChange

    /** 健康记录增删（趋势 / 历史里的习惯与日志部分）。 */
    data object HealthLogs : DataChange

    /** 习惯定义增删改（习惯管理页，DEC-043-D）。 */
    data object Habits : DataChange

    /** 成员档案变更（我的页 / 报告的体成分计算输入）。 */
    data object Profile : DataChange

    /** 云端数据拉回本机完成（设置页「恢复账号」），本地数据被整体替换。 */
    data object AccountDataRestored : DataChange

    /** 账号注销完成，本地数据清空。 */
    data object AccountDeleted : DataChange

    /**
     * 当前成员被切换或删除——所有按成员隔离的数据（测量/健康记录/档案）整体换了一批。
     * 订阅方收到它必须全量重查，不能按类型挑着刷。
     */
    data object MemberScopeChanged : DataChange

    companion object {
        /** 需要推云的事件集合——SyncTrigger 的点火源只认这一组。 */
        val LOCAL_WRITES: Set<DataChange> = setOf(Measurements, HealthLogs, Profile, Habits)
    }
}

/**
 * 数据变更信号总线 —— DEC-021 支柱 2「跨屏刷新由数据源信号订阅决定」的实体化，
 * 替代旧库 UiRefresh 全局 version 计数（谁刷新由订阅关系决定，可推理）。
 *
 * 位置说明（DEC-034 任务组 A）：这是**应用层的编排关注点**，留在 application 是对的。
 * 它的实现只是 SharedFlow 包装、零平台能力，抽接口让 infrastructure 提供实现不会带来
 * 任何可替换性；而下沉 domain 则要给纯模型层引入协程依赖，且订阅方（表现层）将被迫
 * 依赖领域层的总线概念。故保持现状，只做事件类型化。
 *
 * 口径：无订阅者时发射即丢弃（没有活页面就无需刷新）；tryEmit + 单格缓冲
 * 覆盖"订阅方正在处理上一条"的瞬时拥塞；生产方是写用例（save/delete 后 notify），
 * 消费方是查询型 ViewModel（init 里 collect → 自身 refresh）。
 */
class DataRefreshBus {
    private val _changed = MutableSharedFlow<DataChange>(extraBufferCapacity = 4)
    val changed: SharedFlow<DataChange> = _changed.asSharedFlow()

    fun notifyChanged(change: DataChange) {
        _changed.tryEmit(change)
    }
}
