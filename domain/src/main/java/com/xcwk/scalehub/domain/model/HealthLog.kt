package com.xcwk.scalehub.domain.model

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
 */
data class HealthLog(
    val time: Long,
    val type: String,
    val value: Double,
    val value2: Double,
    val label: String,
    val note: String,
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
}
