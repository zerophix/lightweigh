package com.xcwk.scalehub.application.sync

import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus

/**
 * application service — 恢复账号数据（设置页「恢复账号」）。
 *
 * 存在理由（DEC-034 任务组 A）：此前这一步由 SettingsViewModel 直接编排
 * （调 SyncService.restore → 成功即 refreshBus.notifyChanged()），
 * 表现层在发数据变更信号，属于层次越位。收进用例后，ViewModel 只调 execute、
 * 只关心返回给用户的 message。
 *
 * 只发 [DataChange.AccountDataRestored]：云端拉回的数据不需要再推一次云。
 */
class RestoreAccountService(
    private val syncService: SyncService,
    private val refreshBus: DataRefreshBus,
) {
    suspend fun execute(): SyncOutcome {
        val outcome = syncService.restore()
        if (outcome.ok) refreshBus.notifyChanged(DataChange.AccountDataRestored)
        return outcome
    }
}
