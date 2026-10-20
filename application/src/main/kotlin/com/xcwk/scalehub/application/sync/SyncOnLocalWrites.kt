package com.xcwk.scalehub.application.sync

import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 云推送点火器的订阅侧（DEC-034 任务组 A，替代 F-002「写用例注入 SyncTrigger」）。
 *
 * 此前三个写用例各自持有 [SyncTrigger] 并在落库后 fire()，导致：
 *  - 用例单测必须构造同步触发器（业务逻辑测试被基础设施牵着走）
 *  - 换同步策略（批量/定时/仅 Wi-Fi）要改每个用例
 *  - 同步失败、重试、幂等的考量渗进业务用例
 *
 * 现在用例只发 [DataChange]，点火与否由本类在事件流上决定，用例既不知道 UI 也
 * 不知道云。订阅侧单独成类是为了可测性——藏在容器里就只能靠集成测试兜底。
 *
 * 只认 [DataChange.LOCAL_WRITES]：云端侧操作（恢复/注销）绝不能回推一次，
 * 恢复是刚从云拉回、注销是本地已清空，两者推云都是空转。
 */
class SyncOnLocalWrites(
    private val bus: DataRefreshBus,
    private val scope: CoroutineScope,
    private val trigger: SyncTrigger,
) {
    /**
     * 开始订阅，返回该订阅的 [Job] 便于取消。
     *
     * 返回 Job 而不是 fire-and-forget：collect 是无限挂起的，不给句柄就没人能停它
     * （runTest 会因此等满超时；生产侧则意味着作用域取消后订阅仍挂在总线上）。
     * 容器里用不到——它的 applicationScope 与进程同寿。
     */
    fun start(): Job = scope.launch {
        bus.changed.collect { change ->
            if (change in DataChange.LOCAL_WRITES) trigger.fire()
        }
    }
}
