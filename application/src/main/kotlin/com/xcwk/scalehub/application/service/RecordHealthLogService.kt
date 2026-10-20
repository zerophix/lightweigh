package com.xcwk.scalehub.application.service

import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.command.RecordHealthLogCommand
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.domain.types.Clock
import com.xcwk.scalehub.domain.types.SystemClock

/**
 * application service — 记录健康记录用例。
 *
 * 语义与旧库 RecordScreen 保存分支一致（qk-health-android Pages.kt:531-543）：
 *  - 习惯类：主值归一为 1.0，label 为习惯名称（构造守卫强制非空）
 *  - 其余类型：主值必须为正（构造守卫），副值缺失即 0.0
 *  - weight 不在此路径（旧库写测量表，由测量用例承担）
 *
 * 归一化与时间盖章都收在 [HealthLog.create]（DEC-034 组 C）；本用例只落库 + 广播。
 * 发 [DataChange.HealthLogs]，刷 UI 与推云均由容器的事件订阅承担（组 A）。
 */
class RecordHealthLogService(
    private val repository: HealthLogRepository,
    private val refreshBus: DataRefreshBus? = null,
    private val clock: Clock = SystemClock,
    /** 当前成员归属（DEC-034 组 D / F-005）：云表唯一键含 owner，归属不可省略。 */
    private val memberId: () -> String,
) {

    suspend fun execute(cmd: RecordHealthLogCommand): HealthLog {
        val log = HealthLog.create(
            cmd.type, cmd.value, cmd.value2, cmd.label, cmd.note, clock,
        )
        repository.save(log, memberId())
        refreshBus?.notifyChanged(DataChange.HealthLogs)
        return log
    }
}
