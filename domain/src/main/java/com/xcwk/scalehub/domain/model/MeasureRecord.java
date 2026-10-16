package com.xcwk.scalehub.domain.model;

import com.xcwk.scalehub.domain.types.DomainException;

/**
 * 测量聚合根 —— 一条已保存的测量。
 *
 * 不变量（构造时强制，非法状态进不来）：
 *  - time 必须是有效时间戳
 *  - weight ∈ [1, 400] kg（秤量程与人体合理范围）
 *  - impedance ≥ 0（0 表示本次未测出阻抗）
 *  - fat == -1（"未计算"哨兵，对应 data-schema 契约）或 ∈ [0, 100]
 * 算法编号空着时按 builtin-1 定格，历史记录不在此重算。
 */
public final class MeasureRecord {
    public static final double FAT_NOT_CALCULATED = -1.0;

    public final long time;
    public final double weight;
    public final double impedance;
    public final double fat;
    public final String mac;
    public final String algorithm;

    public MeasureRecord(long time, double weight, double impedance, double fat, String mac, String algorithm) {
        if (time <= 0) {
            throw new DomainException("测量时间戳非法: " + time);
        }
        if (weight < 1.0 || weight > 400.0) {
            throw new DomainException("体重 " + weight + " kg 超出量程 1..400");
        }
        if (impedance < 0) {
            throw new DomainException("阻抗 " + impedance + " 非法（0 表示未测出）");
        }
        if (fat != FAT_NOT_CALCULATED && (fat < 0.0 || fat > 100.0)) {
            throw new DomainException("体脂率 " + fat + " 非法（-1 表示未计算，否则 0..100）");
        }
        this.time = time;
        this.weight = weight;
        this.impedance = impedance;
        this.fat = fat;
        this.mac = mac == null ? "" : mac;
        this.algorithm = algorithm == null || algorithm.isEmpty() ? ScaleDevice.ALGORITHM_BUILTIN_1 : algorithm;
    }
}
