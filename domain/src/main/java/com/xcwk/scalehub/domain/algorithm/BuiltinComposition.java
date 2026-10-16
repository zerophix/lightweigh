package com.xcwk.scalehub.domain.algorithm;

/** 当前内置公式，编号 builtin-1。已保存的测量继续用这个编号，不在升级后重算。 */
public final class BuiltinComposition implements BodyComposition {
    public static final String ID = "builtin-1";
    public static final BuiltinComposition INSTANCE = new BuiltinComposition();

    private BuiltinComposition() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public double fatPercent(boolean male, int age, int heightCm, double weightKg, double impedanceOhm) {
        double heightM = heightCm / 100.0;
        if (heightM <= 0 || age <= 0 || impedanceOhm <= 0 || weightKg <= 0) {
            return -1;
        }
        double bmi = weightKg / (heightM * heightM);
        double fat = 1.2 * bmi + 0.23 * age - 10.8 * (male ? 1 : 0) - 5.4;
        fat += (impedanceOhm - 500) * 0.005;
        fat = Math.max(3, Math.min(60, fat));
        return Math.round(fat * 10.0) / 10.0;
    }
}
