package com.xcwk.scalehub.domain.model;

import com.xcwk.scalehub.domain.algorithm.Algorithms;
import com.xcwk.scalehub.domain.algorithm.BodyComposition;
import com.xcwk.scalehub.domain.algorithm.BuiltinComposition;
import com.xcwk.scalehub.domain.algorithm.OkokBia;
import com.xcwk.scalehub.domain.service.BodyCompositionService;
import com.xcwk.scalehub.domain.service.BodyShapeClassificationService;
import com.xcwk.scalehub.domain.service.HealthAdviceService;
import com.xcwk.scalehub.domain.service.HealthRiskAssessmentService;
import com.xcwk.scalehub.domain.types.Clock;
import com.xcwk.scalehub.domain.types.DomainException;

import java.util.List;

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
 *
 * 业务行为（充血模型）：
 *  - calculateBodyComposition(): 计算完整体成分（体脂/肌肉/骨量/水分/内脏/代谢/身体年龄）
 *  - assessHealthRisks(): 评估 7 大健康风险（肥胖/脂肪肝/高血压/糖尿病/骨质疏松/高血脂/痛风）
 *  - classifyBodyShape(): 3x3 体型分类（消瘦型/低脂肪型/运动员型/苗条肌肉型/苗条型/健康型/隐形肥胖型/偏胖型/肥胖型）
 *  - getOverallAdvice(): 获取综合健康建议
 *  - getMetricAdvice(): 获取单项指标建议
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

    /**
     * 判断是否有阻抗数据（可进行体成分计算）
     */
    public boolean hasImpedance() {
        return impedance > 0;
    }

    /**
     * 判断是否已计算体脂率
     */
    public boolean hasFatPercent() {
        return fat != FAT_NOT_CALCULATED && fat >= 0;
    }

    /**
     * 判断是否有完整的计算输入快照（可进行体成分计算）
     */
    public boolean hasCalculationInput() {
        return male != null && ageYears != null && heightCm != null && heightCm > 0;
    }

    /**
     * 计算完整体成分分析
     *
     * @param bodyCompService 体成分计算域服务
     * @return 体成分分析结果，若无阻抗或无计算输入则返回 null
     */
    public BodyCompositionService.OkokAnalysis calculateBodyComposition(BodyCompositionService bodyCompService) {
        if (!hasImpedance() || !hasCalculationInput() || !hasFatPercent()) {
            return null;
        }

        // 根据算法选择计算路径
        if (OkokBia.ID.equals(algorithm) && ageYears != null) {
            return bodyCompService.analyzeOkok(
                male, ageYears, heightCm, weight, impedance
            );
        }

        // 通用估算路径
        BodyCompositionService.GeneralEstimation est = bodyCompService.estimateGeneral(
            male, ageYears, heightCm, weight, fat, impedance
        );
        BodyCompositionService.SkeletalMuscle skeletal = bodyCompService.calculateSkeletalMuscle(male, est.getLeanWeight(), weight);

        // 构建 OkokAnalysis 兼容对象
        return new BodyCompositionService.OkokAnalysis(
            fat,
            est.getMuscleRate(),
            est.getMuscleWeight(),
            est.getBoneWeight(),
            est.getWaterRate(),
            est.getWaterWeight(),
            est.getProteinPercent(),
            est.getVisceralFat(),
            est.getBmr(),
            est.getBodyAge() != null ? est.getBodyAge() : 0
        );
    }

    /**
     * 评估健康风险（7 大风险：肥胖症、脂肪肝、高血压、糖尿病、骨质疏松、高血脂、痛风）
     *
     * @param riskService 健康风险评估域服务
     * @param bodyCompService 体成分计算域服务（用于获取内脏脂肪、骨量等）
     * @return 风险评估列表，若无 BMI 则返回空列表
     */
    public List<HealthRiskAssessmentService.RiskResult> assessHealthRisks(
            HealthRiskAssessmentService riskService,
            BodyCompositionService bodyCompService) {
        // 计算 BMI
        Double bmi = bodyCompService.bmi(weight, heightCm != null ? heightCm : 0);
        if (bmi == null) {
            return List.of();
        }

        // 计算体成分（获取内脏脂肪、骨量等）
        BodyCompositionService.OkokAnalysis analysis = calculateBodyComposition(bodyCompService);
        Double visceralFat = analysis != null ? analysis.getVisceralFat() : null;
        Double boneWeight = analysis != null ? analysis.getBoneKg() : null;

        BodyCompositionService.FatStandard fatStd = bodyCompService.fatStandard(male != null && male);
        Double fatPercent = hasFatPercent() ? fat : null;

        return riskService.assessAll(
            hasImpedance(),
            bmi,
            fatPercent,
            fatStd,
            visceralFat,
            boneWeight,
            weight,
            ageYears
        );
    }

    /**
     * 3x3 体型分类
     *
     * @param shapeService 体型分类域服务
     * @param bodyCompService 体成分计算域服务（用于获取体脂标准区间）
     * @return 体型分类结果，若无 BMI 或体脂率则返回 null
     */
    public BodyShapeClassificationService.ShapeResult classifyBodyShape(
            BodyShapeClassificationService shapeService,
            BodyCompositionService bodyCompService) {
        Double bmi = bodyCompService.bmi(weight, heightCm != null ? heightCm : 0);
        BodyCompositionService.FatStandard fatStd = bodyCompService.fatStandard(male != null && male);
        Double fatPercent = hasFatPercent() ? fat : null;

        return shapeService.classify(fatPercent, bmi, fatStd);
    }

    /**
     * 获取综合健康建议
     *
     * @param adviceService 健康建议域服务
     * @param bodyCompService 体成分计算域服务（用于获取体脂标准区间）
     * @return 建议文案
     */
    public String getOverallAdvice(HealthAdviceService adviceService, BodyCompositionService bodyCompService) {
        Double bmi = bodyCompService.bmi(weight, heightCm != null ? heightCm : 0);
        Integer bmiLevel = bmi != null ? bodyCompService.bmiLevel(bmi) : null;
        BodyCompositionService.FatStandard fatStd = bodyCompService.fatStandard(male != null && male);
        Double fatPercent = hasFatPercent() ? fat : null;

        return adviceService.overallAdvice(bmiLevel, fatPercent, fatStd);
    }

    /**
     * 获取单项指标建议
     *
     * @param adviceService 健康建议域服务
     * @param metricName 指标名称
     * @param tone 声调（1=标准 2=注意 3=超标 0=未录入）
     * @return 建议文案
     */
    public String getMetricAdvice(HealthAdviceService adviceService, String metricName, int tone) {
        return adviceService.metricAdvice(metricName, tone);
    }

    /**
     * 批量生成指标建议分类（优项/关注项）
     *
     * @param adviceService 健康建议域服务
     * @param metrics 指标输入列表
     * @return 分类后的建议
     */
    public HealthAdviceService.ClassifiedAdvice classifyAdvice(
            HealthAdviceService adviceService,
            List<HealthAdviceService.MetricInput> metrics) {
        return adviceService.classifyAdvice(metrics);
    }
}