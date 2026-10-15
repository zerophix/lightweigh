package com.qingke.health.domain.protocol;

import com.qingke.health.domain.protocol.chipsea.ChipseaCloudProtocol;
import com.qingke.health.domain.protocol.okok.OkokBroadcastProtocol;
import com.qingke.health.domain.protocol.okok.OkokProtocol;

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
}
