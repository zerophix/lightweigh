package com.xcwk.scalehub.application.sync

import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus

/**
 * application service — 注销账号（设置页，需先取验证码）。
 *
 * 存在理由同 [RestoreAccountService]：把「调同步 + 成功后广播」这组编排从
 * SettingsViewModel 收回应用层，表现层不发数据变更信号（DEC-034 任务组 A）。
 *
 * 只发 [DataChange.AccountDeleted]：本地数据已被清空，无内容可推云。
 */
class DeleteAccountService(
    private val syncService: SyncService,
    private val refreshBus: DataRefreshBus,
) {
    suspend fun execute(code: String): SyncOutcome {
        val outcome = syncService.deleteAccount(code)
        if (outcome.ok) refreshBus.notifyChanged(DataChange.AccountDeleted)
        return outcome
    }
}
