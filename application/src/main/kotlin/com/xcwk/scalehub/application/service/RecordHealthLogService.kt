package com.xcwk.scalehub.application.service

import com.xcwk.scalehub.application.command.RecordHealthLogCommand
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.model.HealthLogType
import com.xcwk.scalehub.domain.repository.HealthLogRepository

/**
 * application service — 记录健康记录用例。
 *
 * 语义与旧库 RecordScreen 保存分支一致（qk-health-android Pages.kt:531-543）：
 *  - 习惯类：主值归一为 1.0，label 为习惯名称（构造守卫强制非空）
 *  - 其余类型：主值必须为正（构造守卫），副值缺失即 0.0
 *  - weight 不在此路径（旧库写测量表，由测量用例承担）
 * 时间戳在此盖章（存储层不各自盖章，同 RecordMeasurementService）。
 */
class RecordHealthLogService(
    private val repository: HealthLogRepository,
) {

    suspend fun execute(cmd: RecordHealthLogCommand): HealthLog {
        // 旧库习惯分支恒存 1.0（Pages.kt:539 add("habit", 1.0, ...)）
        val value = if (HealthLogType.fromWire(cmd.type) == HealthLogType.HABIT) 1.0 else cmd.value
        val log = HealthLog(
            System.currentTimeMillis(), cmd.type, value,
            cmd.value2, cmd.label, cmd.note,
        )
        repository.save(log)
        return log
    }
}
