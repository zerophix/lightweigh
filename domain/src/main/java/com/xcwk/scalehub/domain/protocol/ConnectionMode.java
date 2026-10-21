package com.xcwk.scalehub.domain.protocol;

/**
 * 协议发现的设备应由通用 BLE 层如何打开（上游 BodyFatScale ConnectionMode.kt 移植）。
 * 芯海秤用随机可解析地址，必须保留扫描到的设备实例；OKOK 秤是公有地址，可按字符串重建。
 */
public enum ConnectionMode {
    /** 扫描到的地址可直接交给 BluetoothAdapter.getRemoteDevice。 */
    PUBLIC_ADDRESS,

    /** 用扫描结果里保留的 BluetoothDevice 实例连接（随机地址类型，字符串重建会连不上）。 */
    SCANNED_DEVICE
}
