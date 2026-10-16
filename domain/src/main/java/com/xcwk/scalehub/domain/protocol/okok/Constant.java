package com.xcwk.scalehub.domain.protocol.okok;

import java.util.UUID;

/**
 * OKOK 智能秤协议常量，只给 okok-1 使用。连接层不引用这里。
 */
public final class Constant {

    /** 测量过程数据，Notify，必选。 */
    public static final UUID CHAR_PROCESS = uuid16(0xFFF1);
    /** 用户信息，Write。 */
    public static final UUID CHAR_USER = uuid16(0xFFF2);
    /** 测量结果，Indicate。 */
    public static final UUID CHAR_RESULT = uuid16(0x2A9C);
    /** 测量历史，Indicate。 */
    public static final UUID CHAR_HISTORY = uuid16(0xFA9C);
    public static final UUID CHAR_BATTERY = uuid16(0x2A19);
    /** 每次连接后都要写入当前时间。 */
    public static final UUID CHAR_TIME = uuid16(0x2A08);

    /** 称重时不订阅历史。打开 FA9C 后秤会转去吐历史，阻抗和实时体重都停。 */
    public static final UUID[] SUBSCRIBE = new UUID[]{
            CHAR_PROCESS,
            CHAR_RESULT,
            CHAR_BATTERY
    };

    private Constant() {
    }

    private static UUID uuid16(int shortId) {
        return UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", shortId & 0xFFFF));
    }

    public static String characteristicName(UUID uuid) {
        if (CHAR_PROCESS.equals(uuid)) {
            return "测量过程";
        }
        if (CHAR_RESULT.equals(uuid)) {
            return "测量结果";
        }
        if (CHAR_HISTORY.equals(uuid)) {
            return "测量历史";
        }
        if (CHAR_BATTERY.equals(uuid)) {
            return "电量";
        }
        if (CHAR_TIME.equals(uuid)) {
            return "时间";
        }
        if (CHAR_USER.equals(uuid)) {
            return "用户信息";
        }
        return uuid.toString();
    }
}
