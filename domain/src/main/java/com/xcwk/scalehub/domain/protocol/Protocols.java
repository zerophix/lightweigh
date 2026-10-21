package com.xcwk.scalehub.domain.protocol;

import com.xcwk.scalehub.domain.protocol.chipsea.ChipseaCloudProtocol;
import com.xcwk.scalehub.domain.protocol.okok.OkokBroadcastProtocol;
import com.xcwk.scalehub.domain.protocol.okok.OkokProtocol;

/** 已注册的体脂秤协议。 */
public final class Protocols {
    private Protocols() {
    }

    public static ScaleProtocol of(String id) {
        if (ChipseaCloudProtocol.ID.equals(id)) {
            return ChipseaCloudProtocol.INSTANCE;
        }
        if (OkokBroadcastProtocol.ID.equals(id)) {
            return OkokBroadcastProtocol.INSTANCE;
        }
        if (id == null || id.isEmpty() || OkokProtocol.ID.equals(id) || "okok".equals(id)) {
            return OkokProtocol.INSTANCE;
        }
        return null;
    }

    public static ScanMatch match(byte[] scanRecord) {
        ScanMatch cloud = ChipseaCloudProtocol.INSTANCE.match(scanRecord);
        if (cloud != null) {
            return cloud;
        }
        ScanMatch connected = OkokProtocol.INSTANCE.match(scanRecord);
        if (connected != null) {
            return connected;
        }
        return OkokBroadcastProtocol.INSTANCE.match(scanRecord);
    }

    /** 判断广播是否属于某已绑定设备（任一注册协议认领）。供 matchBound 兜底识别。 */
    public static boolean owns(byte[] scanRecord, String deviceAddress) {
        if (scanRecord == null || deviceAddress == null) {
            return false;
        }
        if (ChipseaCloudProtocol.INSTANCE.owns(scanRecord, deviceAddress)) {
            return true;
        }
        if (OkokProtocol.INSTANCE.owns(scanRecord, deviceAddress)) {
            return true;
        }
        if (OkokBroadcastProtocol.INSTANCE.owns(scanRecord, deviceAddress)) {
            return true;
        }
        return false;
    }
}
