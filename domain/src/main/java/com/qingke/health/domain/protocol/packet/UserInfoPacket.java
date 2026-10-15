package com.qingke.health.domain.protocol.packet;

/**
 * 用户信息，写入 0xFFF2。
 */
public final class UserInfoPacket {

    private static final byte HEADER = (byte) 0xA0;

    private UserInfoPacket() {
    }

    /** lastWeightRaw 为上次体重，精度 0.1kg，低字节在前。没有记录时传 0。 */
    public static byte[] build(int userId, boolean male, int age, int heightCm, int lastWeightRaw) {
        byte[] data = new byte[9];
        data[0] = HEADER;
        data[1] = (byte) userId;
        data[2] = (byte) (userId >> 8);
        data[3] = (byte) (userId >> 16);
        data[4] = (byte) (userId >> 24);
        int ageBits = Math.max(0, Math.min(age, 127));
        data[5] = (byte) ((male ? 0x80 : 0x00) | ageBits);
        data[6] = (byte) (heightCm & 0xFF);
        data[7] = (byte) lastWeightRaw;
        data[8] = (byte) (lastWeightRaw >> 8);
        return data;
    }
}
