package com.xcwk.scalehub.application.service

import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.command.RecordMeasurementCommand
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.types.Clock
import com.xcwk.scalehub.domain.types.SystemClock

/**
 * application service — 记录测量用例。
 *
 * 语义与旧库 MeasureActivity.finishSave 逐位一致（真机验证过的行为）：
 *  - 无阻抗（<=0）体脂记 -1（"未计算"哨兵，对应 data-schema 的 fat=-1）
 *  - 未知算法编号回落 builtin-1
 *  - 时间戳由聚合根工厂盖章（存储不再各自盖章）
 *
 * 体脂计算编排与时间盖章都收在 [MeasureRecord.create] 里（DEC-034 组 C）——
 * 本用例只负责「取档案 + 落库 + 广播」，新增创建路径（导入历史、云端恢复）
 * 复用同一入口即不会漏规则。
 *
 * 落库后只发一条 [DataChange.Measurements]，不直接触发云推送——刷新与同步都由
 * 容器挂在事件流上的订阅负责（DEC-034 任务组 A）。
 */
class RecordMeasurementService(
    private val repository: MeasurementRepository,
    private val profile: MemberProfileReader,
    private val refreshBus: DataRefreshBus? = null,
    private val clock: Clock = SystemClock,
    /** 当前成员归属——落库必须回答「这条记录是谁的」（DEC-034 组 D / F-005）。 */
    private val memberId: () -> String,
) {

    suspend fun execute(cmd: RecordMeasurementCommand): MeasureRecord {
        val record = MeasureRecord.create(
            cmd.weightKg, cmd.impedanceOhm, cmd.manualFat, cmd.mac, cmd.algorithmId,
            profile.male(), profile.ageYears(), profile.heightCm(), clock,
        )
        repository.save(record, memberId())
        refreshBus?.notifyChanged(DataChange.Measurements)
        return record
    }
}
