package com.xcwk.scalehub.application.service

import com.xcwk.scalehub.application.command.RecordMeasurementCommand
import com.xcwk.scalehub.domain.algorithm.Algorithms
import com.xcwk.scalehub.domain.algorithm.BuiltinComposition
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader

/**
 * application service — 记录测量用例。
 *
 * 语义与旧库 MeasureActivity.finishSave 逐位一致（真机验证过的行为）：
 *  - 无阻抗（<=0）体脂记 -1（"未计算"哨兵，对应 data-schema 的 fat=-1）
 *  - 未知算法编号回落 builtin-1
 *  - 时间戳在此盖章（存储不再各自盖章）
 */
class RecordMeasurementService(
    private val repository: MeasurementRepository,
    private val profile: MemberProfileReader,
) {

    suspend fun execute(cmd: RecordMeasurementCommand): MeasureRecord {
        val fat = if (cmd.impedanceOhm <= 0) {
            // 手动录入可直录体脂（旧库 saveManualWeight extra 口径）；缺省 -1 哨兵，
            // 越界仍由 MeasureRecord 构造守卫 fail-fast
            cmd.manualFat ?: -1.0
        } else {
            val algorithm = Algorithms.of(cmd.algorithmId) ?: BuiltinComposition.INSTANCE
            algorithm.fatPercent(
                profile.male(), profile.ageYears(), profile.heightCm(),
                cmd.weightKg, cmd.impedanceOhm,
            )
        }
        // 计算输入随记录持久化（R2-Q5）：历史体脂永远可解释，不受档案后续变更影响
        val record = MeasureRecord(
            System.currentTimeMillis(), cmd.weightKg, cmd.impedanceOhm,
            fat, cmd.mac, cmd.algorithmId,
            profile.male(), profile.ageYears(), profile.heightCm(),
        )
        repository.save(record)
        return record
    }
}
