package com.xcwk.scalehub.domain.model;

import com.xcwk.scalehub.domain.algorithm.OkokBia;

/**
 * 已绑定的秤。协议和算法编号在绑定时写下。
 */
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

    /**
     * 协议→算法的解析规则：协议自带体脂算法时用之，否则用调用方给的回退值。
     *
     * 这条规则原本散落三处（RoomDeviceRepository 绑定时取默认、MeasureSession 测量时按协议
     * 覆盖、测试假仓储复制一份），是同一份业务知识的三份拷贝——改一处漏两处即出 bug。
     * 收敛到这里后，infrastructure 不再需要认识 OkokBia（DEC-034 任务组 B / F-004）。
     *
     * @param fallback 协议不自带算法时的取值：绑定场景传 [ALGORITHM_BUILTIN_1]，
     *                 测量场景传设备记录里已存的 algorithmId
     */
    public static String algorithmFor(String protocolId, String fallback) {
        return OkokBia.uses(protocolId) ? OkokBia.ID : fallback;
    }

    public ScaleDevice withActive(boolean on) {
        return new ScaleDevice(name, mac, boundAt, on, protocolId, algorithmId);
    }
}
