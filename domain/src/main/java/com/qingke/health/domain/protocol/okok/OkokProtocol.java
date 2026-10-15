package com.qingke.health.domain.protocol.okok;

import com.qingke.health.domain.protocol.okok.Constant;
import com.qingke.health.domain.protocol.packet.BroadcastInfo;
import com.qingke.health.domain.protocol.packet.MeasureFrame;
import com.qingke.health.domain.protocol.packet.TimePacket;
import com.qingke.health.domain.protocol.packet.UserInfoPacket;
import com.qingke.health.domain.protocol.ScaleEvent;
import com.qingke.health.domain.protocol.ScaleProtocol;
import com.qingke.health.domain.protocol.ScanMatch;

import java.util.UUID;

/** OKOK 连接秤，编号 okok-1。同一套特征覆盖体重秤、四电极和八电极。 */
public final class OkokProtocol implements ScaleProtocol {
    public static final String ID = "okok-1";
    public static final OkokProtocol INSTANCE = new OkokProtocol();

    private final OkokAssembly live = new OkokAssembly();
    private final OkokAssembly history = new OkokAssembly();

    private OkokProtocol() {
    }

    @Override
    public void reset() {
        live.reset();
        history.reset();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ScanMatch match(byte[] scanRecord) {
        BroadcastInfo info = BroadcastInfo.parse(scanRecord);
        if (info == null || info.protocolVersion == 0x02) {
            return null;
        }
        return new ScanMatch(ID, info.summary());
    }

    @Override
    public ScaleEvent broadcast(byte[] scanRecord) {
        BroadcastInfo info = BroadcastInfo.parse(scanRecord);
        if (info == null) {
            return null;
        }
        if (info.weight.raw <= 0) {
            return ScaleEvent.weight(0, false, false, 0, 0, "广播");
        }
        return ScaleEvent.weight(
                info.weight.kilograms(),
                info.flags.weightLocked,
                info.flags.impedanceMeasuring,
                0,
                0,
                "广播 " + info.summary());
    }

    @Override
    public UUID[] subscribe() {
        return Constant.SUBSCRIBE;
    }

    @Override
    public UUID requiredCharacteristic() {
        return Constant.CHAR_PROCESS;
    }

    @Override
    public String missingRequiredMessage() {
        return "没有测量过程特征 0xFFF1";
    }

    @Override
    public UUID timeCharacteristic() {
        return Constant.CHAR_TIME;
    }

    @Override
    public byte[] timePacket() {
        return TimePacket.now();
    }

    @Override
    public UUID userCharacteristic() {
        return Constant.CHAR_USER;
    }

    @Override
    public byte[] userPacket(int slot, boolean male, int age, int heightCm, int lastWeightTenthsKg) {
        return UserInfoPacket.build(slot, male, age, heightCm, lastWeightTenthsKg);
    }

    @Override
    public UUID batteryCharacteristic() {
        return Constant.CHAR_BATTERY;
    }

    @Override
    public ScaleEvent parse(UUID characteristic, byte[] data) {
        if (characteristic == null) {
            return null;
        }
        if (Constant.CHAR_BATTERY.equals(characteristic)) {
            if (data == null || data.length == 0) {
                return null;
            }
            return ScaleEvent.battery(data[0] & 0xFF, characteristicName(characteristic));
        }
        if (Constant.CHAR_HISTORY.equals(characteristic)) {
            MeasureFrame past = history.offer(data);
            if (history.isPending()) {
                return null;
            }
            String label = characteristicName(characteristic);
            if (past != null && !past.detail.isEmpty()) {
                label = label + " " + past.detail;
            }
            return ScaleEvent.history(label);
        }
        MeasureFrame frame = live.offer(data);
        if (frame == null) {
            return null;
        }
        String label = characteristicName(characteristic);
        if (!frame.detail.isEmpty()) {
            label = label + " " + frame.detail;
        }
        if (frame.weight.raw <= 0) {
            return ScaleEvent.weight(0, false, false, false, 0, 0, frame.kind, label);
        }
        boolean onResult = Constant.CHAR_RESULT.equals(characteristic);
        boolean impedanceMeasuring = frame.flags.impedanceMeasuring && !onResult;
        boolean heartMeasuring = frame.kind == ScaleEvent.KIND_FAT_HR && frame.flags.heartMeasuring && !onResult;
        double impedance = frame.impedanceValid ? frame.impedanceOhm : 0;
        boolean locked = finalLock(onResult, frame, impedance);
        int heart = frame.hasHeartRate && frame.heartRate > 0 ? frame.heartRate : 0;
        return ScaleEvent.weight(frame.weight.kilograms(), locked, impedanceMeasuring, heartMeasuring,
                impedance, heart, frame.kind, label);
    }

    /**
     * 体重秤在过程特征上锁定就可以结束。体脂秤的过程特征只刷新体重和阻抗，
     * 阻抗锁定或结果特征 2A9C 才是最终帧。
     */
    private static boolean finalLock(boolean onResult, MeasureFrame frame, double impedance) {
        if (onResult) {
            return true;
        }
        if (frame.flags.impedanceMeasuring || frame.flags.heartMeasuring) {
            return false;
        }
        if (frame.kind == ScaleEvent.KIND_WEIGHT) {
            return frame.flags.weightLocked;
        }
        if (frame.kind == ScaleEvent.KIND_FAT || frame.kind == ScaleEvent.KIND_FAT_HR) {
            return frame.flags.weightLocked && impedance > 0;
        }
        return false;
    }

    @Override
    public String characteristicName(UUID characteristic) {
        return Constant.characteristicName(characteristic);
    }
}
