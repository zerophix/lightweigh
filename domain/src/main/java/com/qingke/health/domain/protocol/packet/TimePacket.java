package com.qingke.health.domain.protocol.packet;

/**
 * 当前时间，写入 0x2A08。UTC 时间戳，高字节在前。
 */
public final class TimePacket {

    private TimePacket() {
    }

    public static byte[] now() {
        long seconds = System.currentTimeMillis() / 1000L;
        byte[] data = new byte[4];
        data[0] = (byte) (seconds >> 24);
        data[1] = (byte) (seconds >> 16);
        data[2] = (byte) (seconds >> 8);
        data[3] = (byte) seconds;
        return data;
    }
}
