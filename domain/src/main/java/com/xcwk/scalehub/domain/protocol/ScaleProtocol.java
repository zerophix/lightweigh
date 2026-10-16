package com.xcwk.scalehub.domain.protocol;

import java.util.UUID;

/**
 * 一种体脂秤的报文。连接层只按这里给出的特征收发字节。
 * 编号一旦随设备保存，后续版本保持该编号的解析不变。
 */
public interface ScaleProtocol {
    String id();

    /** 认不出这帧广播时返回 null。 */
    ScanMatch match(byte[] scanRecord);

    /** 广播里的实时体重。认不出或没有体重时返回 null。 */
    ScaleEvent broadcast(byte[] scanRecord);

    UUID[] subscribe();

    /** 缺少这个特征就视为连接不可用。没有必选特征时返回 null。 */
    UUID requiredCharacteristic();

    String missingRequiredMessage();

    UUID timeCharacteristic();

    byte[] timePacket();

    UUID userCharacteristic();

    byte[] userPacket(int slot, boolean male, int age, int heightCm, int lastWeightTenthsKg);

    UUID batteryCharacteristic();

    /** 通知或读取的原始字节。与本协议无关时返回 null。 */
    ScaleEvent parse(UUID characteristic, byte[] data);

    String characteristicName(UUID characteristic);

    /** 开始一次新的连接或广播测量前清掉未收齐的分包。 */
    default void reset() {
    }
}
