package com.xcwk.scalehub.domain.protocol.packet;

import com.xcwk.scalehub.domain.protocol.HexUtil;

/**
 * 广播厂商自定义字段，20 字节，从 0xFF 0xF0 开始。
 */
public final class BroadcastInfo {

    public final int protocolVersion;
    public final MessageFlags flags;
    public final WeightValue weight;

    private BroadcastInfo(int protocolVersion, MessageFlags flags, WeightValue weight) {
        this.protocolVersion = protocolVersion;
        this.flags = flags;
        this.weight = weight;
    }

    public static BroadcastInfo parse(byte[] scanRecord) {
        int index = indexOfMarker(scanRecord);
        if (index < 0 || scanRecord.length < index + 20) {
            return null;
        }
        int version = scanRecord[index + 2] & 0xFF;
        MessageFlags flags = new MessageFlags(scanRecord[index + 3] & 0xFF);
        int weightRaw = HexUtil.readUInt16Le(scanRecord, index + 4);
        return new BroadcastInfo(version, flags, new WeightValue(weightRaw, flags));
    }

    public String summary() {
        String versionText = protocolVersion == 0x10 ? "设备不算体成分"
                : protocolVersion == 0x11 ? "设备计算部分体成分"
                : "协议0x" + Integer.toHexString(protocolVersion);
        return weight.display()
                + (flags.weightLocked ? " 锁定" : " 未锁定")
                + "  " + versionText;
    }

    private static int indexOfMarker(byte[] data) {
        if (data == null) {
            return -1;
        }
        for (int i = 0; i + 20 <= data.length; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xF0) {
                return i;
            }
        }
        return -1;
    }
}
