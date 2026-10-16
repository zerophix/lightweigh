package com.xcwk.scalehub.application

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 数据变更信号总线 —— DEC-021 支柱 2「跨屏刷新由数据源信号订阅决定」的实体化，
 * 替代旧库 UiRefresh 全局 version 计数（谁刷新由订阅关系决定，可推理）。
 *
 * 口径：无订阅者时发射即丢弃（没有活页面就无需刷新）；tryEmit + 单格缓冲
 * 覆盖"订阅方正在处理上一条"的瞬时拥塞；生产方是写用例（save/delete 后 notify），
 * 消费方是查询型 ViewModel（init 里 collect → 自身 refresh）。
 */
class DataRefreshBus {
    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    fun notifyChanged() {
        _changed.tryEmit(Unit)
    }
}
