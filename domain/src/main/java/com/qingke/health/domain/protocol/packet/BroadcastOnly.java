package com.qingke.health.domain.protocol.packet;

import com.qingke.health.domain.protocol.HexUtil;

/**
 * 第 2 章单向广播秤。广告结构长度 0x10、类型 0xFF，数据域 15 字节且版本号为 0xC0。
 * 体重和电阻高字节在前。小数位用表 2.3，不用连接秤的表 3.4。
 */
public final class BroadcastOnly {

    public final MessageFlags flags;
    public final WeightValue weight;
    public final double resistanceOhm;

    private BroadcastOnly(MessageFlags flags, WeightValue weight, double resistanceOhm) {
        this.flags = flags;
        this.weight = weight;
        this.resistanceOhm = resistanceOhm;
    }

    public static BroadcastOnly parse(byte[] scanRecord) {
        byte[] field = findField(scanRecord);
        if (field == null) {
            return null;
        }
        MessageFlags flags = new MessageFlags(field[8] & 0xFF);
        int weightRaw = HexUtil.readUInt16Be(field, 2);
        int resistance = HexUtil.readUInt16Be(field, 4);
        return new BroadcastOnly(flags, new WeightValue(weightRaw, flags, true), resistance);
    }

    public String summary() {
        String kind = (flags.raw & 0x20) != 0 ? "体脂秤" : "体重秤";
        return weight.display()
                + (flags.weightLocked ? " 锁定" : " 未锁定")
                + "  广播" + kind;
    }

    private static byte[] findField(byte[] data) {
        if (data == null) {
            return null;
        }
        int index = 0;
        while (index < data.length) {
            int length = data[index] & 0xFF;
            if (length == 0 || index + 1 + length > data.length) {
                break;
            }
            int type = data[index + 1] & 0xFF;
            if (length == 0x10 && type == 0xFF && (data[index + 2] & 0xFF) == 0xC0) {
                return copy(data, index + 2, 15);
            }
            index += 1 + length;
        }
        if (data.length >= 15 && (data[0] & 0xFF) == 0xC0) {
            return copy(data, 0, 15);
        }
        if (data.length >= 16 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xC0) {
            return copy(data, 1, 15);
        }
        return null;
    }

    private static byte[] copy(byte[] data, int offset, int length) {
        byte[] field = new byte[length];
        System.arraycopy(data, offset, field, 0, length);
        return field;
    }
}
