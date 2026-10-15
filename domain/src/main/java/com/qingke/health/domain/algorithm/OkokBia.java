package com.qingke.health.domain.algorithm;

/**
 * OKOK 秤的体脂和体成分。编号 okok-bia-1。
 * 公式按公开说明独立实现，已保存的 builtin-1 记录不重算。
 */
public final class OkokBia implements BodyComposition {
    public static final String ID = "okok-bia-1";
    public static final OkokBia INSTANCE = new OkokBia();

    private OkokBia() {
    }

    public static boolean uses(String protocolId) {
        return protocolId == null || protocolId.isEmpty() || "okok".equals(protocolId)
                || "okok-1".equals(protocolId) || "okok-broadcast-1".equals(protocolId);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public double fatPercent(boolean male, int age, int heightCm, double weightKg, double impedanceOhm) {
        Double fat = bodyFat(male, age, heightCm, weightKg, impedanceOhm);
        return fat == null ? -1 : fat;
    }

    public static Analysis analyze(boolean male, int age, int heightCm, double weightKg, double impedanceOhm) {
        if (heightCm <= 0 || age <= 0 || age > 127 || weightKg <= 0 || impedanceOhm <= 0) {
            return null;
        }
        double height = heightCm;
        double fat = bodyFat(male, age, height, weightKg, impedanceOhm);
        if (fat < 0) {
            return null;
        }
        double fatMass = fat / 100.0 * weightKg;
        double muscleKg = skeletalLeanKg(male, height, weightKg, age, impedanceOhm);
        Double musclePercent = weightKg > 0 ? round1(muscleKg / weightKg * 100.0) : null;
        Double water = age > 17 ? waterPercent(male, height, weightKg, age, impedanceOhm) : null;
        Double waterMass = water == null ? null : water / 100.0 * weightKg;
        Double proteinPercent = null;
        if (waterMass != null) {
            double proteinMass = muscleKg - waterMass;
            proteinPercent = round1(proteinMass / weightKg * 100.0);
        }
        double bone = clamp(weightKg - fatMass - muscleKg, 1.0, 4.0);
        Double visceral = age > 17 ? visceralFat(male, height, weightKg, age, impedanceOhm) : null;
        double bmr = basalMetabolism(male, height, weightKg, age, impedanceOhm);
        return new Analysis(
                fat,
                musclePercent,
                round1(muscleKg),
                round1(bone),
                water,
                waterMass == null ? null : round1(waterMass),
                proteinPercent,
                visceral,
                bmr > 0 ? round1(bmr) : null,
                bodyAge(male, height, weightKg, age, impedanceOhm));
    }

    private static Double bodyFat(boolean male, int age, double heightCm, double weightKg, double impedanceOhm) {
        if (heightCm <= 0 || age <= 0 || weightKg <= 0 || impedanceOhm <= 0) {
            return null;
        }
        double raw = male
                ? (-0.3315 * heightCm) + (0.6216 * weightKg) + (0.0183 * age) + (0.0085 * impedanceOhm) + 22.554
                : (-0.3332 * heightCm) + (0.7509 * weightKg) + (0.0196 * age) + (0.0072 * impedanceOhm) + 22.7193;
        return round1(clamp(raw / weightKg * 100.0, 5.0, 45.0));
    }

    /**
     * 公式第 5 节的 SLM 原始值，单位公斤，用作肌肉重量。
     * 男：(0.2867×身高) + (0.3894×体重) − (0.0408×年龄) − (0.01235×阻抗) − 15.7665
     * 女：(0.3186×身高) + (0.1934×体重) − (0.0206×年龄) − (0.0132×阻抗) − 16.4556
     * 肌肉率是这个重量除以体重再乘 100。
     */
    public static double skeletalLeanKg(boolean male, double heightCm, double weightKg, int age, double impedanceOhm) {
        return male
                ? (0.2867 * heightCm) + (0.3894 * weightKg) - (0.0408 * age) - (0.01235 * impedanceOhm) - 15.7665
                : (0.3186 * heightCm) + (0.1934 * weightKg) - (0.0206 * age) - (0.0132 * impedanceOhm) - 16.4556;
    }

    private static Double waterPercent(boolean male, double heightCm, double weightKg, int age, double impedanceOhm) {
        double raw = male
                ? (0.0939 * heightCm) + (0.3758 * weightKg) - (0.0032 * age) - (0.006925 * impedanceOhm) + 0.097
                : (0.0877 * heightCm) + (0.2973 * weightKg) + (0.0128 * age) - (0.00603 * impedanceOhm) + 0.5175;
        return round1(clamp(raw / weightKg * 100.0, 20.0, 85.0));
    }

    private static Double visceralFat(boolean male, double heightCm, double weightKg, int age, double impedanceOhm) {
        double raw = male
                ? (-0.2675 * heightCm) + (0.42 * weightKg) + (0.1462 * age) + (0.0123 * impedanceOhm) + 13.9871
                : (-0.1651 * heightCm) + (0.2628 * weightKg) + (0.0649 * age) + (0.0024 * impedanceOhm) + 12.3445;
        double stepped = Math.round(raw * 2.0) / 2.0;
        return clamp(stepped, 1.0, 59.0);
    }

    private static double basalMetabolism(boolean male, double heightCm, double weightKg, int age, double impedanceOhm) {
        return male
                ? (7.5037 * heightCm) + (13.1523 * weightKg) - (4.3376 * age) - (0.3486 * impedanceOhm) - 311.7751
                : (7.5432 * heightCm) + (9.9474 * weightKg) - (3.4382 * age) - (0.309 * impedanceOhm) - 288.2821;
    }

    private static int bodyAge(boolean male, double heightCm, double weightKg, int age, double impedanceOhm) {
        double raw = male
                ? (-0.7471 * heightCm) + (0.9161 * weightKg) + (0.4184 * age) + (0.0517 * impedanceOhm) + 54.2267
                : (-1.1165 * heightCm) + (1.5784 * weightKg) + (0.4615 * age) + (0.0415 * impedanceOhm) + 83.2548;
        int value = (int) Math.round(raw);
        int low = Math.max(18, age - 10);
        int high = Math.min(80, age + 10);
        if (low > high) {
            return Math.max(18, Math.min(80, age));
        }
        return Math.max(low, Math.min(high, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public static final class Analysis {
        public final double fatPercent;
        public final Double musclePercent;
        public final Double muscleKg;
        public final Double boneKg;
        public final Double waterPercent;
        public final Double waterKg;
        public final Double proteinPercent;
        public final Double visceral;
        public final Double bmr;
        public final int bodyAge;

        Analysis(double fatPercent, Double musclePercent, Double muscleKg, Double boneKg,
                 Double waterPercent, Double waterKg, Double proteinPercent, Double visceral,
                 Double bmr, int bodyAge) {
            this.fatPercent = fatPercent;
            this.musclePercent = musclePercent;
            this.muscleKg = muscleKg;
            this.boneKg = boneKg;
            this.waterPercent = waterPercent;
            this.waterKg = waterKg;
            this.proteinPercent = proteinPercent;
            this.visceral = visceral;
            this.bmr = bmr;
            this.bodyAge = bodyAge;
        }
    }
}
