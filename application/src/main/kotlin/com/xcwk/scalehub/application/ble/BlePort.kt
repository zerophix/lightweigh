package com.xcwk.scalehub.application.ble

import com.xcwk.scalehub.domain.protocol.ConnectionMode
import java.util.UUID

/**
 * 蓝牙链路端口（M3b，DEC-022）—— 旧库 IBle*Listener 四接口 + BLEManager 门面切面。
 * 扫描/连接/订阅/写读的语义结果经此进入 application；Android 蓝牙 API 由实现持有
 * （M3c AndroidBleLink = BLEManager 移植）。回调线程由实现保证（旧库统一主线程 post）。
 */
interface BleScanListener {
    fun onScanStarted()
    fun onScanStop()

    /** 扫描因为权限或蓝牙状态没有开始。 */
    fun onScanFailed(reason: String) {}

    /** scanRecord 是原始广播。是否体脂秤由协议层判断。 */
    fun onDeviceFound(name: String, mac: String, rssi: Int, scanRecord: ByteArray?)
}

interface BleConnectListener {
    fun onStartConnect()
    fun onConnectFail(reason: String)
    fun onConnectSuccess(mac: String, services: List<String>)
    fun onDisConnected(mac: String)
}

interface BleIndicateListener {
    fun onIndicateSuccess()
    fun onIndicateFailure(reason: String)
    fun onCharacteristicChanged(characteristic: UUID, data: ByteArray)
    fun onCharacteristicRead(characteristic: UUID, data: ByteArray)

    /** 读失败≠订阅失败（DEC-038 组A）：可选特征（电量）缺失/读失败不得被当成链路故障弹断开。 */
    fun onReadFailure(reason: String) {}
}

interface BleWriteListener {
    fun onWriteSuccess()
    fun onWriteFailure(reason: String)
}

interface BlePort {
    fun isBluetoothEnabled(): Boolean
    fun scan(listener: BleScanListener)
    fun stopScan()

    /**
     * 打开设备（上游 BLEManager.connect(context, mac, listener, mode) 移植）。
     * SCANNED_DEVICE 用于芯海等随机地址秤：实现层必须用扫描结果保留的
     * BluetoothDevice 实例连接，否则地址类型判错导致永远连不上。
     */
    fun connect(
        mac: String,
        listener: BleConnectListener,
        mode: ConnectionMode = ConnectionMode.PUBLIC_ADDRESS,
    )

    fun isConnected(): Boolean
    fun isConnecting(): Boolean
    fun disconnect()

    /** 缺少 required 特征即以 missingMessage 报订阅失败（旧库 BLEManager.subscribe :244-250）。 */
    fun subscribe(
        characteristics: List<UUID>,
        required: UUID?,
        missingMessage: String,
        listener: BleIndicateListener,
    )

    fun write(characteristic: UUID?, data: ByteArray, listener: BleWriteListener?)
    fun read(characteristic: UUID)
}
