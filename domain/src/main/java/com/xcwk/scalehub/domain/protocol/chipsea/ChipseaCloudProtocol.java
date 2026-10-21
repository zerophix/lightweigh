package com.xcwk.scalehub.domain.protocol.chipsea;

import com.xcwk.scalehub.domain.protocol.ConnectionMode;
import com.xcwk.scalehub.domain.protocol.ScaleEvent;
import com.xcwk.scalehub.domain.protocol.ScaleProtocol;
import com.xcwk.scalehub.domain.protocol.ScanMatch;
import com.xcwk.scalehub.domain.protocol.HexUtil;

import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

/**
 * 芯海蓝牙秤云端版通讯协议 v0.3，编号 chipsea-cloud-1。
 * 广播版本号是 0x02。测量帧和 okok-1 不是同一套。
 */
public final class ChipseaCloudProtocol implements ScaleProtocol {
    public static final String ID = "chipsea-cloud-1";
    public static final ChipseaCloudProtocol INSTANCE = new ChipseaCloudProtocol();

    private static final int BROADCAST_VERSION = 0x02;
    private static final UUID PROCESS = uuid16(0xFFF1);
    private static final UUID UNIT = uuid16(0xFFF2);
    private static final UUID RESULT = uuid16(0x2A9C);
    private static final UUID HISTORY = uuid16(0xFA9C);
    private static final UUID BATTERY = uuid16(0x2A19);
    private static final UUID TIME = uuid16(0x2A08);

    private ChipseaCloudProtocol() {
    }

    @Override
    public String id() {
        return ID;
    }

    /**
     * 芯海秤是随机可解析地址——必须用扫描结果保留的 BluetoothDevice 实例连接。
     * 用地址字符串重建会被当成公有地址，芯海秤就连不上（上游 ChipseaCloudProtocol.kt:18）。
     */
    @Override
    public ConnectionMode connectionMode() {
        return ConnectionMode.SCANNED_DEVICE;
    }

    @Override
    public ScanMatch match(byte[] scanRecord) {
        BroadcastField field = readBroadcast(scanRecord);
        if (field == null) {
            return null;
        }
        return new ScanMatch(ID, String.format(java.util.Locale.US, "%.1f kg  云端版", field.kilograms));
    }

    @Override
    public ScaleEvent broadcast(byte[] scanRecord) {
        BroadcastField field = readBroadcast(scanRecord);
        if (field == null) {
            return null;
        }
        if (field.kilograms <= 0) {
            return ScaleEvent.weight(0, false, false, false, 0, 0, ScaleEvent.KIND_UNKNOWN, "广播");
        }
        return ScaleEvent.weight(field.kilograms, false, false, false, 0, 0, ScaleEvent.KIND_UNKNOWN,
                String.format(java.util.Locale.US, "广播 %.1f kg", field.kilograms));
    }

    /**
     * 称重时不订阅历史。文档写明 indicate FA9C 后秤开始吐历史，并停掉非锁定通道，
     * 页面就收不到实时体重。历史仍能在解析里识别，只是这次测量不打开它。
     */
    @Override
    public UUID[] subscribe() {
        return new UUID[]{PROCESS, RESULT, BATTERY};
    }

    @Override
    public UUID requiredCharacteristic() {
        return PROCESS;
    }

    @Override
    public String missingRequiredMessage() {
        return "没有非锁定数据特征 0xFFF1";
    }

    @Override
    public UUID timeCharacteristic() {
        return TIME;
    }

    @Override
    public byte[] timePacket() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        return new byte[]{
                (byte) year,
                (byte) (year >> 8),
                (byte) (calendar.get(Calendar.MONTH) + 1),
                (byte) calendar.get(Calendar.DAY_OF_MONTH),
                (byte) calendar.get(Calendar.HOUR_OF_DAY),
                (byte) calendar.get(Calendar.MINUTE),
                (byte) calendar.get(Calendar.SECOND)
        };
    }

    @Override
    public UUID userCharacteristic() {
        return UNIT;
    }

    /** 云端版的 FFF2 是单位切换。应用固定公斤，所以写 01 00。 */
    @Override
    public byte[] userPacket(int slot, boolean male, int age, int heightCm, int lastWeightTenthsKg) {
        return new byte[]{0x01, 0x00};
    }

    @Override
    public UUID batteryCharacteristic() {
        return BATTERY;
    }

    @Override
    public ScaleEvent parse(UUID characteristic, byte[] data) {
        if (characteristic == null || data == null) {
            return null;
        }
        if (BATTERY.equals(characteristic)) {
            if (data.length == 0) {
                return null;
            }
            return ScaleEvent.battery(data[0] & 0xFF, "电量");
        }
        if (HISTORY.equals(characteristic)) {
            return ScaleEvent.history("测量历史");
        }
        Reading reading = reading(data);
        if (reading == null) {
            return null;
        }
        boolean locked = RESULT.equals(characteristic) && reading.kilograms > 0;
        boolean measuringFat = reading.kind == ScaleEvent.KIND_FAT && !locked && reading.impedanceOhm > 0;
        String label = (locked ? "锁定 " : "非锁定 ") + reading.detail;
        return ScaleEvent.weight(reading.kilograms, locked, measuringFat, false, reading.impedanceOhm, 0,
                reading.kind, label);
    }

    @Override
    public String characteristicName(UUID characteristic) {
        if (PROCESS.equals(characteristic)) {
            return "非锁定数据";
        }
        if (RESULT.equals(characteristic)) {
            return "锁定数据";
        }
        if (HISTORY.equals(characteristic)) {
            return "测量历史";
        }
        if (BATTERY.equals(characteristic)) {
            return "电量";
        }
        if (TIME.equals(characteristic)) {
            return "时间";
        }
        if (UNIT.equals(characteristic)) {
            return "单位";
        }
        return characteristic == null ? "" : characteristic.toString();
    }

    private static BroadcastField readBroadcast(byte[] scanRecord) {
        int index = marker(scanRecord);
        if (index < 0) {
            return null;
        }
        int version = scanRecord[index + 2] & 0xFF;
        // forceChipsea: 设备名称包含 "Chipsea"，跳过版本 0x02 检查
        if (!isChipseaName(scanRecord) && version != BROADCAST_VERSION) {
            return null;
        }
        int property = scanRecord[index + 3] & 0xFF;
        int weightRaw = HexUtil.readUInt16Le(scanRecord, index + 4);
        return new BroadcastField(chipseaKilograms(weightRaw, property));
    }

    /** 广播里的 MAC 与当前绑定地址一致时，也算这台秤。地址有正序和反序两种。 */
    public boolean owns(byte[] scanRecord, String mac) {
        int index = marker(scanRecord);
        if (index < 0 || scanRecord.length < index + 20 || mac == null) {
            return false;
        }
        String forward = macText(scanRecord, index + 14, false);
        String reverse = macText(scanRecord, index + 14, true);
        return mac.equalsIgnoreCase(forward) || mac.equalsIgnoreCase(reverse);
    }

    /**
     * 云端版自己的 20 字节测量帧，不走 OKOK 的 C0/C4 帧头。
     * 第 0–1 字节是 flags：0x0002 体重秤，0x0306 单电阻体脂秤，0x2306 双电阻体脂秤。
     * 电阻 1 在第 9–10 字节，体重在第 11–12 字节，电阻 2 在第 13–14 字节，都是低字节在前。
     * 第 15 字节是表一：bit4–3 单位，bit2–1 小数（00=2 位，01=0 位，10=1 位）。bit0 不是锁定。
     * 锁定只看特征是不是 2A9C。
     */
    private static Reading reading(byte[] data) {
        if (data == null || data.length < 13) {
            return null;
        }
        int flags = HexUtil.readUInt16Le(data, 0);
        int first = HexUtil.readUInt16Le(data, 9);
        int weightRaw = HexUtil.readUInt16Le(data, 11);
        int second = data.length >= 15 ? HexUtil.readUInt16Le(data, 13) : 0;
        int property = data.length > 15 ? data[15] & 0xFF : 0;
        boolean fat = (flags & 0xFF00) != 0 || (flags & 0x0004) != 0 || first != 0 || second != 0;
        int kind = fat ? ScaleEvent.KIND_FAT : ScaleEvent.KIND_WEIGHT;
        if (weightRaw == 0) {
            return new Reading(0, 0, kind, fat ? "体脂秤 0.0 kg" : "体重秤 0.0 kg");
        }
        double kilograms = chipseaKilograms(weightRaw, property);
        double impedance = first != 0 ? first / 10.0 : (second != 0 ? second / 10.0 : 0);
        String detail = (fat ? "体脂秤 " : "体重秤 ") + String.format(java.util.Locale.US, "%.1f kg", kilograms);
        if (first != 0) {
            detail += String.format(java.util.Locale.US, " 电阻1 %.1f", first / 10.0);
        }
        if (second != 0) {
            detail += String.format(java.util.Locale.US, " 电阻2 %.1f", second / 10.0);
        }
        return new Reading(kilograms, impedance, kind, detail);
    }

    /** 表一。00=2 位小数，01=0 位，10=1 位。ST:LB 高字节是英石整数，低字节是磅的 10 倍。 */
    private static double chipseaKilograms(int weightRaw, int property) {
        int unit = (property >> 3) & 0x03;
        if (unit == 3) {
            double stones = (weightRaw >> 8) & 0xFF;
            double pounds = (weightRaw & 0xFF) / 10.0;
            return Math.round((stones * 14 + pounds) * 0.45359237 * 10.0) / 10.0;
        }
        int decimals = (property >> 1) & 0x03;
        int places = decimals == 0x01 ? 0 : decimals == 0x02 ? 1 : 2;
        double divisor = 1;
        for (int i = 0; i < places; i++) {
            divisor *= 10;
        }
        double kilograms = weightRaw / divisor;
        if (unit == 1) {
            kilograms = kilograms / 2.0;
        } else if (unit == 2) {
            kilograms = kilograms * 0.45359237;
        }
        return Math.round(kilograms * 10.0) / 10.0;
    }

    /**
     * 只看厂商数据开头。厂商标识 0xFFF0 小端是 F0 FF，有的秤直接发 FF F0，下一字节必须是 0x02。
     * 服务列表里的 FFF0 也是 F0 FF，后面常跟着下一组广告的长度 0x02，那不是芯海版本。
     * 开头已是 OKOK 的 0x10 或 0x11 时，整段都不要认成芯海。
     *
     * 例如外壳，但广播版本 0x10 的 Chipsea_BLE 设备名称包含 "Chipsea"，
     * 应当跳过 OKOK 排除逻辑，仍然识别为芯海云端版 (chipsea-cloud-1)。
     */
    private static int marker(byte[] data) {
        if (data == null) {
            return -1;
        }
        boolean forceChipsea = isChipseaName(data);
        int cursor = 0;
        boolean sawStructure = false;
        while (cursor + 1 < data.length) {
            int length = data[cursor] & 0xFF;
            if (length == 0 || cursor + 1 + length > data.length) {
                break;
            }
            sawStructure = true;
            int type = data[cursor + 1] & 0xFF;
            int start = cursor + 2;
            int end = cursor + 1 + length;
            if (type == 0xFF) {
                if (!forceChipsea &&
                    (vendorVersion(data, start, end, 0x10) || vendorVersion(data, start, end, 0x11))) {
                    return -1;
                }
                int found = chipseaAt(data, start, end);
                if (found >= 0) {
                    return found;
                }
                if (forceChipsea) {
                    int forced = chipseaAtTagOnly(data, start, end);
                    if (forced >= 0) {
                        return forced;
                    }
                }
            }
            cursor += 1 + length;
        }
        if (sawStructure) {
            return -1;
        }
        if (!forceChipsea &&
            (vendorVersion(data, 0, data.length, 0x10) || vendorVersion(data, 0, data.length, 0x11))) {
            return -1;
        }
        int found = chipseaAt(data, 0, data.length);
        if (found >= 0) {
            return found;
        }
        if (forceChipsea) {
            return chipseaAtTagOnly(data, 0, data.length);
        }
        return -1;
    }

    /**
     * 从扫描记录中提取设备名称 (AD type 0x09 / 0x08)，
     * 如果名称包含 "Chipsea" (不区分大小写) 则返回 true。
     * 用于识别广播版本为 OKOK (0x10) 但 GATT 使用 Chipsea 协议的混合设备。
     */
    private static boolean isChipseaName(byte[] data) {
        if (data == null) {
            return false;
        }
        int cursor = 0;
        while (cursor + 1 < data.length) {
            int length = data[cursor] & 0xFF;
            if (length == 0 || cursor + 1 + length > data.length) {
                break;
            }
            int type = data[cursor + 1] & 0xFF;
            if (type == 0x09 || type == 0x08) {
                // AD type 0x09 = Complete Local Name, 0x08 = Short Local Name
                String name = new String(data, cursor + 2, length - 1, java.nio.charset.StandardCharsets.UTF_8);
                if (name.toLowerCase(Locale.ROOT).contains("chipsea")) {
                    return true;
                }
            }
            cursor += 1 + length;
        }
        return false;
    }

    private static int chipseaAt(byte[] data, int start, int end) {
        int at = tagIndex(data, start, end);
        if (at >= 0 && end - at >= 6 && (data[at + 2] & 0xFF) == BROADCAST_VERSION) {
            return at;
        }
        return -1;
    }

    /**
     * 仅查找厂商标签 (FF F0 或 F0 FF), 不检查版本字节。
     * 用于 forceChipsea 模式: 名称含 "Chipsea" 的设备跳过版本检查。
     */
    private static int chipseaAtTagOnly(byte[] data, int start, int end) {
        int at = tagIndex(data, start, end);
        if (at >= 0 && end - start >= 6 && end - at >= 6) {
            return at;
        }
        return -1;
    }

    private static boolean vendorVersion(byte[] data, int start, int end, int version) {
        int at = tagIndex(data, start, end);
        return at >= 0 && at + 2 < end && (data[at + 2] & 0xFF) == version;
    }

    /** 厂商字段开头就是标识，允许前面多一个字节。不在整段里搜索，避免扫到地址或服务号。 */
    private static int tagIndex(byte[] data, int start, int end) {
        if (isTag(data, start)) {
            return start;
        }
        if (start + 1 < end && isTag(data, start + 1)) {
            return start + 1;
        }
        return -1;
    }

    private static boolean isTag(byte[] data, int index) {
        if (data == null || index < 0 || index + 1 >= data.length) {
            return false;
        }
        int first = data[index] & 0xFF;
        int second = data[index + 1] & 0xFF;
        return (first == 0xFF && second == 0xF0) || (first == 0xF0 && second == 0xFF);
    }

    private static String macText(byte[] data, int offset, boolean reverse) {
        StringBuilder builder = new StringBuilder(17);
        for (int i = 0; i < 6; i++) {
            if (i > 0) {
                builder.append(':');
            }
            int index = reverse ? offset + 5 - i : offset + i;
            builder.append(String.format(java.util.Locale.US, "%02X", data[index] & 0xFF));
        }
        return builder.toString();
    }

    private static UUID uuid16(int shortId) {
        return UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", shortId & 0xFFFF));
    }

    private static final class BroadcastField {
        final double kilograms;

        BroadcastField(double kilograms) {
            this.kilograms = kilograms;
        }
    }

    private static final class Reading {
        final double kilograms;
        final double impedanceOhm;
        final int kind;
        final String detail;

        Reading(double kilograms, double impedanceOhm, int kind, String detail) {
            this.kilograms = kilograms;
            this.impedanceOhm = impedanceOhm;
            this.kind = kind;
            this.detail = detail;
        }
    }
}
