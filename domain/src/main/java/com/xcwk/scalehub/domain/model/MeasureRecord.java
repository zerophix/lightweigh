package com.xcwk.scalehub.domain.model;

import com.xcwk.scalehub.domain.algorithm.Algorithms;
import com.xcwk.scalehub.domain.algorithm.BodyComposition;
import com.xcwk.scalehub.domain.algorithm.BuiltinComposition;
import com.xcwk.scalehub.domain.types.Clock;
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
    /** 计算输入快照（旧数据/未记录为 null）。 */
    public final Boolean male;
    public final Integer ageYears;
    public final Integer heightCm;

    /**
     * 成员归属不在此聚合内（DEC-034 组 D / F-005）。
     *
     * 此前这里有个 memberId 字段、构造器默认空串，但生产路径从不给它有效值——
     * 写入被仓储 .copy(当前成员) 覆盖、读取压根不传。一个「允许为空串」的归属
     * 字段等于允许无成员的测量记录存在，接口也说不出「多成员隔离」这条核心约束。
     * 现改为：归属由仓储端口签名 save(record, memberId) 显式携带，
     * 聚合只描述「一次测量的事实」，不假装知道谁测的。
     */

    /** 兼容构造（无档案快照）——仅限测试与迁移场景。 */
    public MeasureRecord(long time, double weight, double impedance, double fat, String mac, String algorithm) {
        this(time, weight, impedance, fat, mac, algorithm, null, null, null);
    }

    public MeasureRecord(long time, double weight, double impedance, double fat, String mac, String algorithm,
                         Boolean male, Integer ageYears, Integer heightCm) {
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
        this.algorithm = algorithm == null || algorithm.isEmpty() ? ScaleDevice.ALGORITHM_BUILTIN_1 : algorithm;
        this.male = male;
        this.ageYears = ageYears;
        this.heightCm = heightCm;
    }

    /**
     * 新建一条测量记录（DEC-034 组 C / F-003 + F-006）。
     *
     * 把原本散在 RecordMeasurementService 里的两件事收进聚合：
     *  1. **体脂计算的编排**——无阻抗时取手动录入值、缺省 -1 哨兵；有阻抗时按
     *     algorithmId 找算法，编号未知则回落 builtin-1
     *  2. **时间戳盖章**——时间由 [Clock] 取，用例拿不到时钟也就绕不过盖章规则
     *
     * 计算输入（male/ageYears/heightCm）仍随记录持久化：历史体脂永远可解释，
     * 不受档案后续变更影响（R2-Q5）。
     *
     * @param manualFat 手动录入的体脂率，仅在无阻抗时采用；null 即「未计算」
     */
    public static MeasureRecord create(
            double weightKg, double impedanceOhms, Double manualFat,
            String mac, String algorithmId,
            boolean male, int ageYears, int heightCm,
            Clock clock) {
        final double fat;
        if (impedanceOhms <= 0) {
            // 手动录入可直录体脂（旧库 saveManualWeight extra 口径）；缺省 -1 哨兵
            fat = manualFat == null ? FAT_NOT_CALCULATED : manualFat.doubleValue();
        } else {
            // Algorithms.of 已对 null/空/builtin-1 回落 builtin；返回 null 即未知编号
            BodyComposition algorithm = Algorithms.of(algorithmId);
            if (algorithm == null) {
                algorithm = BuiltinComposition.INSTANCE;
            }
            fat = algorithm.fatPercent(male, ageYears, heightCm, weightKg, impedanceOhms);
        }
        return new MeasureRecord(
                clock.nowMillis(), weightKg, impedanceOhms, fat, mac, algorithmId,
                male, ageYears, heightCm
        );
    }
}
