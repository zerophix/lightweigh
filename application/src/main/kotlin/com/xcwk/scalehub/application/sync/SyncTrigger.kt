package com.xcwk.scalehub.application.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 云同步触发器（P0 断链修复：写入用例触发 push 的点火器）。
 *
 * 背景：SyncService.push() 早已实装并注入容器，但生产代码零调用——
 * 只靠设置页「恢复账号」（restore）下拉，新测量/记录/档案变更永不上云，
 * 换机迁移拉到的是旧快照。修复把 push 挂到三个写入用例的成功出口
 * （测量/健康记录/档案），写在本机即上云，恢复只作兜底。
 *
 * 口径：
 *  - fire-and-forget：onFire 走网络（多表 upsert），绝不能阻塞本地写入，
 *    调用方（写用例）不等它；异常在此吞掉，写用例与 UI 无感
 *  - 游客天然 no-op：生产侧 onFire 即 SyncService.push，首行挡非正式账号
 *  - 串行有界：push 内部 Mutex 串行；连续触发按序执行而非折叠
 *    （SyncService 已声明的合并语义）
 */
class SyncTrigger(
    private val scope: CoroutineScope,
    private val onFire: suspend () -> Unit,
) {
    /** 点火一次后台推送；同步结果不影响调用方。 */
    fun fire() {
        scope.launch { runCatching { onFire() } }
    }
}
