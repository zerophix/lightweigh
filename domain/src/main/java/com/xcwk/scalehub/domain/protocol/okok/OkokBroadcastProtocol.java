package com.xcwk.scalehub.domain.protocol.okok;

import com.xcwk.scalehub.domain.protocol.packet.BroadcastOnly;
import com.xcwk.scalehub.domain.protocol.ScaleEvent;
import com.xcwk.scalehub.domain.protocol.ScaleProtocol;
import com.xcwk.scalehub.domain.protocol.ScanMatch;

import java.util.UUID;

/** 单向广播秤，编号 okok-broadcast-1。没有连接特征。 */
public final class OkokBroadcastProtocol implements ScaleProtocol {
    public static final String ID = "okok-broadcast-1";
    public static final OkokBroadcastProtocol INSTANCE = new OkokBroadcastProtocol();

    private OkokBroadcastProtocol() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ScanMatch match(byte[] scanRecord) {
        BroadcastOnly info = BroadcastOnly.parse(scanRecord);
        if (info == null) {
            return null;
        }
        return new ScanMatch(ID, info.summary());
    }

    @Override
    public ScaleEvent broadcast(byte[] scanRecord) {
        BroadcastOnly info = BroadcastOnly.parse(scanRecord);
        if (info == null) {
            return null;
        }
        boolean fat = (info.flags.raw & 0x20) != 0;
        int kind = fat ? ScaleEvent.KIND_FAT : ScaleEvent.KIND_WEIGHT;
        if (info.weight.raw <= 0) {
            return ScaleEvent.weight(0, false, false, false, 0, 0, kind, "广播");
        }
        return ScaleEvent.weight(
                info.weight.kilograms(),
                info.flags.weightLocked,
                false,
                false,
                fat ? info.resistanceOhm : 0,
                0,
                kind,
                "广播 " + info.summary());
    }

    @Override
    public UUID[] subscribe() {
        return new UUID[0];
    }

    @Override
    public UUID requiredCharacteristic() {
        return null;
    }

    @Override
    public String missingRequiredMessage() {
        return "这台秤只通过广播发送体重";
    }

    @Override
    public UUID timeCharacteristic() {
        return null;
    }

    @Override
    public byte[] timePacket() {
        return new byte[0];
    }

    @Override
    public UUID userCharacteristic() {
        return null;
    }

    @Override
    public byte[] userPacket(int slot, boolean male, int age, int heightCm, int lastWeightTenthsKg) {
        return new byte[0];
    }

    @Override
    public UUID batteryCharacteristic() {
        return null;
    }

    @Override
    public ScaleEvent parse(UUID characteristic, byte[] data) {
        return null;
    }

    @Override
    public String characteristicName(UUID characteristic) {
        return "广播";
    }
}
