package com.qingke.health.domain.model;

/** 已绑定的秤。协议和算法编号在绑定时写下。 */
public final class ScaleDevice {
    public static final String PROTOCOL_OKOK_1 = "okok-1";
    public static final String ALGORITHM_BUILTIN_1 = "builtin-1";

    public final String name;
    public final String mac;
    public final String boundAt;
    public final boolean active;
    public final String protocolId;
    public final String algorithmId;

    public ScaleDevice(String name, String mac, String boundAt, boolean active) {
        this(name, mac, boundAt, active, PROTOCOL_OKOK_1, ALGORITHM_BUILTIN_1);
    }

    public ScaleDevice(String name, String mac, String boundAt, boolean active, String protocolId, String algorithmId) {
        this.name = name;
        this.mac = mac;
        this.boundAt = boundAt;
        this.active = active;
        this.protocolId = protocolId == null || protocolId.isEmpty() ? PROTOCOL_OKOK_1 : protocolId;
        this.algorithmId = algorithmId == null || algorithmId.isEmpty() ? ALGORITHM_BUILTIN_1 : algorithmId;
    }

    public ScaleDevice withActive(boolean on) {
        return new ScaleDevice(name, mac, boundAt, on, protocolId, algorithmId);
    }
}
