package com.xcwk.scalehub.domain.protocol;

/** 协议层交给页面的一次解析结果。体重已经换成公斤。没有的字段保持空。 */
public final class ScaleEvent {
    /** 还没从帧头认出是哪种秤。 */
    public static final int KIND_UNKNOWN = 0;
    /** C0，或广播属性标明的体重秤。锁定后只保存体重。 */
    public static final int KIND_WEIGHT = 1;
    /** 四电极、八电极或芯海云端版。没有阻抗就不能出体成分报告。 */
    public static final int KIND_FAT = 2;
    /** 带心率的四电极、多频或八电极。测心率时还不是最终结果。 */
    public static final int KIND_FAT_HR = 3;

    public final Double weightKg;
    public final boolean locked;
    public final boolean impedanceMeasuring;
    public final boolean heartMeasuring;
    public final double impedanceOhm;
    public final int heartRate;
    public final int kind;
    public final Integer batteryPercent;
    public final boolean history;
    public final String label;

    private ScaleEvent(Double weightKg, boolean locked, boolean impedanceMeasuring, boolean heartMeasuring,
                       double impedanceOhm, int heartRate, int kind, Integer batteryPercent, boolean history, String label) {
        this.weightKg = weightKg;
        this.locked = locked;
        this.impedanceMeasuring = impedanceMeasuring;
        this.heartMeasuring = heartMeasuring;
        this.impedanceOhm = impedanceOhm;
        this.heartRate = heartRate;
        this.kind = kind;
        this.batteryPercent = batteryPercent;
        this.history = history;
        this.label = label == null ? "" : label;
    }

    public static ScaleEvent weight(double weightKg, boolean locked, boolean impedanceMeasuring,
                                    double impedanceOhm, int heartRate, String label) {
        return weight(weightKg, locked, impedanceMeasuring, false, impedanceOhm, heartRate, KIND_UNKNOWN, label);
    }

    public static ScaleEvent weight(double weightKg, boolean locked, boolean impedanceMeasuring, boolean heartMeasuring,
                                    double impedanceOhm, int heartRate, int kind, String label) {
        return new ScaleEvent(weightKg, locked, impedanceMeasuring, heartMeasuring, impedanceOhm, heartRate, kind,
                null, false, label);
    }

    public static ScaleEvent battery(int percent, String label) {
        return new ScaleEvent(null, false, false, false, 0, 0, KIND_UNKNOWN, percent, false, label);
    }

    public static ScaleEvent history(String label) {
        return new ScaleEvent(null, false, false, false, 0, 0, KIND_UNKNOWN, null, true, label);
    }
}
