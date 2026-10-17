package com.xcwk.scalehub.domain.model;

import com.xcwk.scalehub.domain.types.DomainException;

/**
 * 测量聚合根 —— 一条已保存的测量。
 *
 * 三层语义分离（对抗评审 R2-Q5）：
 *  1) 历史测量事实：time / weight / impedance / mac —— 设备或用户产生的原始事实
 *  2) 计算输入快照：male / ageYears / heightCm / algorithm —— 体脂计算那一刻的档案与算法，
 *     随记录持久化，使历史结果永远可解释（档案之后如何变更不影响已存记录）
 *  3) 当前成员档案：由 MemberProfileReader 提供，不属于本聚合
 *
 * 不变量（构造期强制）及来源：
 *  - time > 0                    [来源: data-schema measured_at bigint 约定]
 *  - weight ∈ [1, 400] kg        [来源: 产品合理量程决策；注意厂商协议原始过滤为 2..2000kg
 *                                 (proto-okok NOTE-20260908-003)，本守卫是产品级收紧而非厂商契约]
 *  - impedance ∈ [0, 5000] Ω     [来源: 物理非负 + 协议合理上限(5000Ω)；
 *                                 0 是"未测出"哨兵而非有效读数，算法仅在 >0 时被调用]
 *  - fat == -1 或 ∈ [0, 100]     [来源: data-schema 契约 fat NOT NULL DEFAULT -1("未计算")]
 */
public final class MeasureRecord {
    public static final double FAT_NOT_CALCULATED = -1.0;
    public static final double MAX_IMPEDANCE_OHMS = 5000.0;

    public final long time;
    public final double weight;
    public final double impedance;
    public final double fat;
    public final String mac;
    public final String algorithm;
    /** 归属成员（多成员维；仓储落库时以当前成员盖章，聚合默认空串）。 */
    public final String memberId;
    /** 计算输入快照（旧数据/未记录为 null）。 */
    public final Boolean male;
    public final Integer ageYears;
    public final Integer heightCm;

    /** 兼容构造（无档案快照）——仅限测试与迁移场景。 */
    public MeasureRecord(long time, double weight, double impedance, double fat, String mac, String algorithm) {
        this(time, weight, impedance, fat, mac, algorithm, null, null, null, "");
    }

    public MeasureRecord(long time, double weight, double impedance, double fat, String mac, String algorithm,
                         Boolean male, Integer ageYears, Integer heightCm) {
        this(time, weight, impedance, fat, mac, algorithm, male, ageYears, heightCm, "");
    }

    public MeasureRecord(long time, double weight, double impedance, double fat, String mac, String algorithm,
                         Boolean male, Integer ageYears, Integer heightCm, String memberId) {
        if (time <= 0) {
            throw new DomainException("测量时间戳非法: " + time);
        }
        if (weight < 1.0 || weight > 400.0) {
            throw new DomainException("体重 " + weight + " kg 超出产品量程 1..400");
        }
        if (impedance < 0.0 || impedance > MAX_IMPEDANCE_OHMS) {
            throw new DomainException("阻抗 " + impedance + " Ω 非法（0=未测出，上限 " + (int) MAX_IMPEDANCE_OHMS + "）");
        }
        if (fat != FAT_NOT_CALCULATED && (fat < 0.0 || fat > 100.0)) {
            throw new DomainException("体脂率 " + fat + " 非法（-1 表示未计算，否则 0..100）");
        }
        this.time = time;
        this.weight = weight;
        this.impedance = impedance;
        this.fat = fat;
        this.mac = mac == null ? "" : mac;
        this.memberId = memberId == null ? "" : memberId;
        this.algorithm = algorithm == null || algorithm.isEmpty() ? ScaleDevice.ALGORITHM_BUILTIN_1 : algorithm;
        this.male = male;
        this.ageYears = ageYears;
        this.heightCm = heightCm;
    }
}
