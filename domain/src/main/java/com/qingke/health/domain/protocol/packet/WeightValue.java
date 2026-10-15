package com.qingke.health.domain.protocol.packet;

import java.util.Locale;

/** 体重原始值按表 3.4 的小数位还原。低字节在前。 */
public final class WeightValue {

    public final int raw;
    public final double value;
    public final int decimalPlaces;
    public final String unitLabel;

    public WeightValue(int raw, MessageFlags flags) {
        this(raw, flags, false);
    }

    public WeightValue(int raw, MessageFlags flags, boolean broadcastScale) {
        this.raw = raw;
        decimalPlaces = broadcastScale ? flags.broadcastDecimalPlaces() : flags.decimalPlaces();
        unitLabel = flags.unitLabel();
        double divisor = 1;
        for (int i = 0; i < decimalPlaces; i++) {
            divisor *= 10;
        }
        value = raw / divisor;
    }

    /** 斤按 2 斤=1 公斤，磅按 0.45359237。ST:LB 高字节是英石整数，低字节是磅的 10 倍。 */
    public double kilograms() {
        double weight = value;
        if ("斤".equals(unitLabel)) {
            weight = weight / 2.0;
        } else if ("st:lb".equals(unitLabel)) {
            int stones = (raw >> 8) & 0xFF;
            double pounds = (raw & 0xFF) / 10.0;
            weight = (stones * 14.0 + pounds) * 0.45359237;
        } else if ("lb".equals(unitLabel)) {
            weight = weight * 0.45359237;
        }
        return Math.round(weight * 10.0) / 10.0;
    }

    public String display() {
        if ("st:lb".equals(unitLabel)) {
            int stones = (raw >> 8) & 0xFF;
            double pounds = (raw & 0xFF) / 10.0;
            return String.format(Locale.US, "%d:%.1f st:lb", stones, pounds);
        }
        if (decimalPlaces <= 0) {
            return String.format(Locale.US, "%.0f %s", value, unitLabel);
        }
        if (decimalPlaces == 1) {
            return String.format(Locale.US, "%.1f %s", value, unitLabel);
        }
        return String.format(Locale.US, "%.2f %s", value, unitLabel);
    }
}
