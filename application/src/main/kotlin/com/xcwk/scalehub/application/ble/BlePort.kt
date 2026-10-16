package com.xcwk.scalehub.application.ble

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
}

interface BleWriteListener {
    fun onWriteSuccess()
    fun onWriteFailure(reason: String)
}

interface BlePort {
    fun isBluetoothEnabled(): Boolean
    fun scan(listener: BleScanListener)
    fun stopScan()
    fun connect(mac: String, listener: BleConnectListener)
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
