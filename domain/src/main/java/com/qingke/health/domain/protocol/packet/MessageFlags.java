package com.qingke.health.domain.protocol.packet;

/**
 * 表 3.4 消息体属性。
 */
public final class MessageFlags {

    public static final int UNIT_KG = 0;
    public static final int UNIT_JIN = 1;
    public static final int UNIT_LB = 2;
    public static final int UNIT_ST_LB = 3;

    public final int raw;
    public final boolean heartMeasuring;
    public final boolean impedanceMeasuring;
    public final int unit;
    public final int decimalCode;
    public final boolean weightLocked;

    public MessageFlags(int raw) {
        this.raw = raw & 0xFF;
        heartMeasuring = (this.raw & 0x80) != 0;
        impedanceMeasuring = (this.raw & 0x40) != 0;
        unit = (this.raw >> 3) & 0x03;
        decimalCode = (this.raw >> 1) & 0x03;
        weightLocked = (this.raw & 0x01) != 0;
    }

    /** 表 2.3：00=1 位，01=0 位，10=2 位。只给纯广播秤。 */
    public int broadcastDecimalPlaces() {
        if (unit == UNIT_ST_LB) {
            return 0;
        }
        switch (decimalCode) {
            case 0b01:
                return 0;
            case 0b10:
                return 2;
            case 0b00:
            default:
                return 1;
        }
    }

    /** 表 3.4：00=2 位，01=0 位，10=1 位。LB 固定 1 位，ST:LB 按高低字节拆。 */
    public int decimalPlaces() {
        if (unit == UNIT_LB) {
            return 1;
        }
        if (unit == UNIT_ST_LB) {
            return 0;
        }
        switch (decimalCode) {
            case 0b01:
                return 0;
            case 0b10:
                return 1;
            case 0b00:
            default:
                return 2;
        }
    }

    public String unitLabel() {
        switch (unit) {
            case UNIT_JIN:
                return "斤";
            case UNIT_LB:
                return "lb";
            case UNIT_ST_LB:
                return "st:lb";
            case UNIT_KG:
            default:
                return "kg";
        }
    }
}
