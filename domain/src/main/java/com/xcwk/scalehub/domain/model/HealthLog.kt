package com.xcwk.scalehub.domain.model

import com.xcwk.scalehub.domain.types.Clock
import com.xcwk.scalehub.domain.types.DomainException

/**
 * 健康记录聚合（饮食/喝水/运动/体围/血糖/血压/习惯）。
 *
 * 构造期强制不变量（DEC-022 组 A，规则出处见 HealthLogType 注释）：
 *  - time > 0                     [契约: cloud health_logs.logged_at bigint]
 *  - type 必须是已知记录类型；"weight" 不是健康记录（旧库写入测量表）
 *  - 非习惯类 value > 0           [旧库「请输入有效数字」行为移植]
 *  - 习惯类 label 非空            [习惯名称即记录主体]
 *  - value2 >= 0                  [产品收紧: 副值(体脂率/消耗热量/臀围/舒张压)缺失存 0，
 *                                   负数无业务含义；weight 的 -1 体脂哨兵不在此路径]
 *
 * 读写同路径守卫：Room 读映射经本构造器，脏行 fail-fast（同 MeasureRecord 语义）。
 *
 * 成员归属不在此聚合内（DEC-034 组 D / F-005，同 MeasureRecord）：归属由仓储端口
 * 签名 save(log, memberId) 显式携带，聚合只描述「一条记录的事实」。
 */
data class HealthLog(
    val time: Long,
    val type: String,
    val value: Double,
    val value2: Double,
    val label: String,
    val note: String,
    /** 冲突与水位时刻，epoch ms UTC。默认等于采样时刻（DEC-058 / DEC-S13）。 */
    val updatedAt: Long = time,
    /** 软删墓碑；null = 存活。 */
    val deletedAt: Long? = null,
) {
    init {
        if (time <= 0) {
            throw DomainException("健康记录时间戳非法: $time")
        }
        val kind = HealthLogType.fromWire(type)
            ?: throw DomainException("未知健康记录类型: $type（weight 属测量路径，不入健康记录）")
        if (kind.requiresPositiveValue && value <= 0) {
            throw DomainException("${kind.wire} 记录主值必须为正数: $value")
        }
        if (kind.requiresLabel && label.isBlank()) {
            throw DomainException("习惯记录必须提供名称")
        }
        if (value2 < 0) {
            throw DomainException("副值不可为负: $value2")
        }
    }

    companion object {
        /**
         * 新建一条健康记录（DEC-034 组 C / F-003 + F-006）。
         *
         * 收进聚合的两件事：
         *  1. **习惯类主值归一为 1.0**（旧库 RecordScreen 保存分支 Pages.kt:539
         *     `add("habit", 1.0, ...)`——习惯记录的主值恒为 1，名称才是记录主体）
         *  2. **时间戳盖章**——时间由 [Clock] 取，用例拿不到时钟也就绕不过盖章规则
         */
        fun create(
            type: String,
            value: Double,
            value2: Double,
            label: String,
            note: String,
            clock: Clock,
        ): HealthLog {
            val normalized = if (HealthLogType.fromWire(type) == HealthLogType.HABIT) 1.0 else value
            return HealthLog(clock.nowMillis(), type, normalized, value2, label, note)
        }
    }
}
