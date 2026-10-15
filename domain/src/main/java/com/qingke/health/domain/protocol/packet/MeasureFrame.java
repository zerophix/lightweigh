package com.qingke.health.domain.protocol.packet;

import com.qingke.health.domain.protocol.HexUtil;
import com.qingke.health.domain.protocol.ScaleEvent;

/**
 * 连接秤数据帧。过程、结果、历史使用同一套帧头。
 * C4/C5 的字节位置保持不变。C9/CA 由组包器收齐后再生成。
 */
public final class MeasureFrame {

    private static final int HEADER_WEIGHT = 0xC0;
    private static final int HEADER_FOUR = 0xC4;
    private static final int HEADER_FOUR_HR = 0xC5;
    private static final int HEADER_FOUR_MULTI = 0xC6;
    private static final int HEADER_EIGHT = 0xC8;
    private static final int HEADER_PROCESS = 0xCF;
    private static final int HEADER_PROCESS_ALT = 0xCC;

    public final MessageFlags flags;
    public final WeightValue weight;
    public final boolean impedanceValid;
    public final double impedanceOhm;
    public final boolean hasHeartRate;
    public final int heartRate;
    public final int kind;
    public final String detail;

    private MeasureFrame(MessageFlags flags, WeightValue weight, boolean impedanceValid, double impedanceOhm,
                         boolean hasHeartRate, int heartRate, int kind, String detail) {
        this.flags = flags;
        this.weight = weight;
        this.impedanceValid = impedanceValid;
        this.impedanceOhm = impedanceOhm;
        this.hasHeartRate = hasHeartRate;
        this.heartRate = heartRate;
        this.kind = kind;
        this.detail = detail == null ? "" : detail;
    }

    public static MeasureFrame parse(byte[] data) {
        if (data == null || data.length < 4) {
            return null;
        }
        int header = data[0] & 0xFF;
        if (header == HEADER_PROCESS || header == HEADER_PROCESS_ALT) {
            MessageFlags flags = new MessageFlags(data[1] & 0xFF);
            int weightRaw = HexUtil.readUInt16Le(data, 2);
            int impedanceRaw = data.length >= 6 ? HexUtil.readUInt16Le(data, 4) : 0;
            return create(flags, weightRaw, impedanceRaw == 0 ? 0 : impedanceRaw / 10.0, false, 0, ScaleEvent.KIND_UNKNOWN, "过程");
        }
        if (header == HEADER_WEIGHT) {
            if (data.length < 8) {
                return null;
            }
            MessageFlags flags = new MessageFlags(data[1] & 0xFF);
            return create(flags, HexUtil.readUInt16Le(data, 6), 0, false, 0, ScaleEvent.KIND_WEIGHT, "体重秤");
        }
        if (data.length < 20) {
            return null;
        }
        if (header == HEADER_FOUR || header == HEADER_FOUR_HR) {
            MessageFlags flags = new MessageFlags(data[1] & 0xFF);
            int weightRaw = HexUtil.readUInt16Le(data, 6);
            int impedanceRaw = HexUtil.readUInt16Le(data, 8);
            boolean hr = header == HEADER_FOUR_HR;
            int heartRate = hr ? (data[10] & 0xFF) : 0;
            return create(flags, weightRaw, ohms(impedanceRaw), hr, heartRate,
                    hr ? ScaleEvent.KIND_FAT_HR : ScaleEvent.KIND_FAT, hr ? "四电极心率" : "四电极");
        }
        if (header == HEADER_FOUR_MULTI) {
            MessageFlags flags = new MessageFlags(data[1] & 0xFF);
            int z1 = HexUtil.readUInt16Le(data, 8);
            int z2 = HexUtil.readUInt16Le(data, 10);
            int z3 = HexUtil.readUInt16Le(data, 12);
            int heart = data[14] & 0xFF;
            return create(flags, HexUtil.readUInt16Le(data, 6), firstOhm(z1, z2, z3), true, heart, ScaleEvent.KIND_FAT_HR,
                    "四电极多频 " + channelText(new String[]{"频1", "频2", "频3"}, new int[]{z1, z2, z3}));
        }
        if (header == HEADER_EIGHT) {
            MessageFlags flags = new MessageFlags(data[1] & 0xFF);
            int z34 = HexUtil.readUInt16Le(data, 8);
            int z12 = HexUtil.readUInt16Le(data, 10);
            int z13 = HexUtil.readUInt16Le(data, 12);
            int z14 = HexUtil.readUInt16Le(data, 14);
            int z23 = HexUtil.readUInt16Le(data, 16);
            int z24 = HexUtil.readUInt16Le(data, 18);
            return create(flags, HexUtil.readUInt16Le(data, 6), ohms(z34), false, 0, ScaleEvent.KIND_FAT,
                    "八电极 " + channelText(new String[]{"Z34", "Z12", "Z13", "Z14", "Z23", "Z24"},
                            new int[]{z34, z12, z13, z14, z23, z24}));
        }
        return null;
    }

    public static MeasureFrame eightHeart(byte[] first, byte[] second) {
        MessageFlags flags = new MessageFlags(first[2] & 0xFF);
        int z34 = HexUtil.readUInt16Le(first, 9);
        int z12 = HexUtil.readUInt16Le(first, 11);
        int z13 = HexUtil.readUInt16Le(first, 13);
        int z14 = HexUtil.readUInt16Le(first, 15);
        int z23 = HexUtil.readUInt16Le(first, 17);
        int z24 = HexUtil.readUInt16Le(second, 2);
        int heart = second[4] & 0xFF;
        return create(flags, HexUtil.readUInt16Le(first, 7), ohms(z34), true, heart, ScaleEvent.KIND_FAT_HR,
                "八电极心率 " + channelText(new String[]{"Z34", "Z12", "Z13", "Z14", "Z23", "Z24"},
                        new int[]{z34, z12, z13, z14, z23, z24}));
    }

    public static MeasureFrame eightMulti(byte[] first, byte[] second, byte[] third) {
        MessageFlags flags = new MessageFlags(first[2] & 0xFF);
        int f1 = HexUtil.readUInt16Le(first, 9);
        int f1z12 = HexUtil.readUInt16Le(first, 11);
        int f1z13 = HexUtil.readUInt16Le(first, 13);
        int f1z14 = HexUtil.readUInt16Le(first, 15);
        int f1z23 = HexUtil.readUInt16Le(first, 17);
        int f1z24 = HexUtil.readUInt16Le(second, 2);
        int f2 = HexUtil.readUInt16Le(second, 4);
        int f3 = HexUtil.readUInt16Le(second, 16);
        int heart = third[10] & 0xFF;
        String channels = channelText(new String[]{"频1", "频2", "频3"}, new int[]{f1, f2, f3});
        String segments = channelText(new String[]{"Z12", "Z13", "Z14", "Z23", "Z24"},
                new int[]{f1z12, f1z13, f1z14, f1z23, f1z24});
        String detail = "八电极多频 " + channels + (segments.isEmpty() ? "" : " " + segments);
        return create(flags, HexUtil.readUInt16Le(first, 7), firstOhm(f1, f2, f3), true, heart, ScaleEvent.KIND_FAT_HR, detail);
    }

    private static MeasureFrame create(MessageFlags flags, int weightRaw, double impedanceOhm,
                                      boolean hasHeartRate, int heartRate, int kind, String detail) {
        boolean valid = impedanceOhm > 0;
        return new MeasureFrame(flags, new WeightValue(weightRaw, flags), valid, valid ? impedanceOhm : 0,
                hasHeartRate, heartRate, kind, detail);
    }

    private static double ohms(int raw) {
        return raw == 0 ? 0 : raw / 10.0;
    }

    private static double firstOhm(int... raws) {
        for (int raw : raws) {
            if (raw != 0) {
                return raw / 10.0;
            }
        }
        return 0;
    }

    private static String channelText(String[] names, int[] raws) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (raws[i] == 0) {
                continue;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(names[i]).append('=').append(String.format(java.util.Locale.US, "%.1f", raws[i] / 10.0));
        }
        return text.toString();
    }
}
