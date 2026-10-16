package com.xcwk.scalehub.infrastructure.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.xcwk.scalehub.application.ble.BleConnectListener
import com.xcwk.scalehub.application.ble.BleIndicateListener
import com.xcwk.scalehub.application.ble.BlePort
import com.xcwk.scalehub.application.ble.BleScanListener
import com.xcwk.scalehub.application.ble.BleWriteListener
import java.util.ArrayDeque
import java.util.UUID

/**
 * ble — BlePort 实现（旧库 BLEManager.java 591 行逐位移植，行号随注释）。
 * 扫描、连接、订阅和写入。不识别具体体脂秤，特征列表由协议层传入。
 * 权限契约：调用方入口门禁（BlePermission）保证；SecurityException 兜底随旧库。
 */
@SuppressLint("MissingPermission")
class AndroidBleLink(context: Context) : BlePort {

    private val appContext: Context = context.applicationContext
    private val adapter: BluetoothAdapter? = run {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val writes = ArrayDeque<PendingWrite>()
    private var subscribeList: MutableList<BluetoothGattCharacteristic> = mutableListOf()

    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var scanListener: BleScanListener? = null

    private var gatt: BluetoothGatt? = null
    private var connectListener: BleConnectListener? = null
    private var indicateListener: BleIndicateListener? = null
    private var connecting = false
    private var writeInFlight = false
    private var subscribeIndex = 0
    private var subscribeUuids: List<UUID> = emptyList()
    private var requiredUuid: UUID? = null
    private var requiredMissing: String = "没有测量所需特征"
    private var connectTimeout: Runnable? = null

    override fun isBluetoothEnabled(): Boolean = adapter != null && adapter.isEnabled

    override fun scan(listener: BleScanListener) {
        val bt = adapter
        if (bt == null || !bt.isEnabled) return
        stopScan()
        scanListener = listener
        scanner = bt.bluetoothLeScanner ?: return
        listener.onScanStarted()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val record = result.scanRecord
                var name = ""
                var mac = ""
                try {
                    name = device.name ?: ""
                    mac = device.address ?: ""
                } catch (ex: SecurityException) {
                    return
                }
                if (name.isEmpty() && record?.deviceName != null) name = record.deviceName ?: ""
                val foundMac = mac
                val copy = record?.bytes?.clone()
                val finalName = if (name.isEmpty()) "(无名设备)" else name
                val rssi = result.rssi
                mainHandler.post {
                    scanListener?.onDeviceFound(finalName, foundMac, rssi, copy)
                }
            }
        }
        try {
            scanner?.startScan(null, settings, scanCallback)
        } catch (ex: SecurityException) {
            scanCallback = null
            notifyScanFailed("没有蓝牙权限")
        } catch (ex: IllegalStateException) {
            scanCallback = null
            notifyScanFailed("蓝牙未打开")
        }
    }

    private fun notifyScanFailed(reason: String) {
        val listener = scanListener
        scanListener = null
        listener?.onScanFailed(reason)
        listener?.onScanStop()
    }

    override fun stopScan() {
        val current = scanCallback
        if (scanner != null && current != null) {
            try {
                scanner?.stopScan(current)
            } catch (ignored: Exception) {
            }
        }
        scanCallback = null
        scanListener?.let { listener ->
            scanListener = null
            listener.onScanStop()
        }
    }

    override fun connect(mac: String, listener: BleConnectListener) {
        connectListener = listener
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            postConnectFail("蓝牙未打开")
            return
        }
        if (!MAC_PATTERN.matches(mac)) {
            postConnectFail("设备地址无效")
            return
        }
        listener.onStartConnect()
        stopScan()
        closeGatt()
        connecting = true
        try {
            val device = bt.getRemoteDevice(mac)
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(appContext, false, gattCallback)
            }
        } catch (ex: SecurityException) {
            connecting = false
            postConnectFail("没有蓝牙权限")
            return
        } catch (ex: IllegalArgumentException) {
            connecting = false
            postConnectFail("无法连接这台秤")
            return
        } catch (ex: IllegalStateException) {
            connecting = false
            postConnectFail("无法连接这台秤")
            return
        }
        connectTimeout?.let { mainHandler.removeCallbacks(it) }
        val timeout = Runnable {
            if (!connecting) return@Runnable
            connecting = false
            closeGatt()
            postConnectFail("连接超时")
        }
        connectTimeout = timeout
        mainHandler.postDelayed(timeout, CONNECT_TIMEOUT_MS)
    }

    override fun isConnected(): Boolean = gatt != null && !connecting

    override fun isConnecting(): Boolean = connecting

    override fun disconnect() {
        connecting = false
        connectTimeout?.let { mainHandler.removeCallbacks(it) }
        writes.clear()
        writeInFlight = false
        subscribeList.clear()
        val current = gatt
        gatt = null
        if (current != null) {
            try {
                current.disconnect()
            } catch (ignored: SecurityException) {
            } catch (ignored: IllegalStateException) {
            }
            mainHandler.postDelayed(
                {
                    try {
                        current.close()
                    } catch (ignored: RuntimeException) {
                    }
                },
                400,
            )
        }
    }

    override fun subscribe(
        characteristics: List<UUID>,
        required: UUID?,
        missingMessage: String,
        listener: BleIndicateListener,
    ) {
        subscribeUuids = characteristics
        requiredUuid = required
        requiredMissing = missingMessage.ifEmpty { "没有测量所需特征" }
        indicateListener = listener
        mainHandler.post { startSubscribe() }
    }

    override fun write(characteristic: UUID?, data: ByteArray, listener: BleWriteListener?) {
        val copy = data.clone()
        mainHandler.post {
            val target = characteristic?.let { findCharacteristic(it) }
            if (gatt == null || target == null) {
                listener?.onWriteFailure("没有特征 $characteristic")
                return@post
            }
            writes.add(PendingWrite(target, copy, listener))
            pumpWrite()
        }
    }

    override fun read(characteristic: UUID) {
        mainHandler.post {
            val target = findCharacteristic(characteristic)
            if (gatt == null || target == null) {
                indicateListener?.onIndicateFailure("没有特征 $characteristic")
                return@post
            }
            try {
                gatt?.readCharacteristic(target)
            } catch (ex: SecurityException) {
                indicateListener?.onIndicateFailure("没有蓝牙权限")
            }
        }
    }

    private fun startSubscribe() {
        subscribeList = mutableListOf()
        subscribeIndex = 0
        if (gatt == null) {
            indicateListener?.onIndicateFailure("未连接")
            return
        }
        for (uuid in subscribeUuids) {
            findCharacteristic(uuid)?.let { subscribeList.add(it) }
        }
        val required = requiredUuid
        if (required != null && findCharacteristic(required) == null) {
            indicateListener?.onIndicateFailure(requiredMissing)
            return
        }
        subscribeNext()
    }

    @Suppress("DEPRECATION")
    private fun subscribeNext() {
        val currentGatt = gatt ?: return
        if (subscribeIndex >= subscribeList.size) {
            indicateListener?.onIndicateSuccess()
            return
        }
        val characteristic = subscribeList[subscribeIndex]
        try {
            if (!currentGatt.setCharacteristicNotification(characteristic, true)) {
                subscribeIndex++
                subscribeNext()
                return
            }
            val descriptor = characteristic.getDescriptor(CCCD)
            if (descriptor == null) {
                subscribeIndex++
                subscribeNext()
                return
            }
            val properties = characteristic.properties
            val value =
                if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                }
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeDescriptor(descriptor, value) ==
                    android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                descriptor.value = value
                currentGatt.writeDescriptor(descriptor)
            }
            if (!started) {
                subscribeIndex++
                subscribeNext()
            }
        } catch (ex: SecurityException) {
            indicateListener?.onIndicateFailure("没有蓝牙权限")
        }
    }

    @Suppress("DEPRECATION")
    private fun pumpWrite() {
        if (writeInFlight || writes.isEmpty() || gatt == null) return
        val request = writes.peek()
        writeInFlight = true
        val properties = request.characteristic.properties
        val writeType =
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
        val noResponse = writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        var ok: Boolean
        try {
            ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val code = gatt?.writeCharacteristic(request.characteristic, request.data, writeType)
                code == BluetoothGatt.GATT_SUCCESS
            } else {
                request.characteristic.writeType = writeType
                request.characteristic.value = request.data
                gatt?.writeCharacteristic(request.characteristic) ?: false
            }
        } catch (ex: SecurityException) {
            writeInFlight = false
            writes.poll()
            request.listener?.onWriteFailure("没有蓝牙权限")
            return
        }
        if (!ok || noResponse) {
            writeInFlight = false
            writes.poll()
            if (ok) {
                request.listener?.onWriteSuccess()
            } else {
                request.listener?.onWriteFailure("写入失败")
            }
            pumpWrite()
        }
    }

    private fun findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        val currentGatt = gatt ?: return null
        for (service in currentGatt.services) {
            service.getCharacteristic(uuid)?.let { return it }
        }
        return null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(bluetoothGatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt.discoverServices()
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mainHandler.post {
                    if (gatt !== bluetoothGatt) {
                        try {
                            bluetoothGatt.close()
                        } catch (ignored: RuntimeException) {
                        }
                        return@post
                    }
                    val wasConnecting = connecting
                    connecting = false
                    connectTimeout?.let { mainHandler.removeCallbacks(it) }
                    var mac = ""
                    try {
                        mac = bluetoothGatt.device.address
                    } catch (ignored: SecurityException) {
                    }
                    if (gatt === bluetoothGatt) {
                        closeGatt()
                    } else {
                        try {
                            bluetoothGatt.close()
                        } catch (ignored: RuntimeException) {
                        }
                    }
                    if (wasConnecting) {
                        postConnectFail("连接失败 $status")
                    } else {
                        connectListener?.onDisConnected(mac)
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(bluetoothGatt: BluetoothGatt, status: Int) {
            mainHandler.post {
                connecting = false
                connectTimeout?.let { mainHandler.removeCallbacks(it) }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    closeGatt()
                    postConnectFail("服务发现失败 $status")
                    return@post
                }
                connectListener?.onConnectSuccess(
                    bluetoothGatt.device.address,
                    dumpServices(bluetoothGatt),
                )
            }
        }

        @Suppress("DEPRECATION")
        override fun onDescriptorWrite(bluetoothGatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            mainHandler.post {
                subscribeIndex++
                subscribeNext()
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(bluetoothGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            dispatchNotify(characteristic.uuid, characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            dispatchNotify(characteristic.uuid, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                dispatchRead(characteristic.uuid, characteristic.value ?: ByteArray(0))
            }
        }

        override fun onCharacteristicRead(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                dispatchRead(characteristic.uuid, value)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            mainHandler.post {
                val request = writes.peek()
                if (!writeInFlight || request == null ||
                    request.characteristic.uuid != characteristic.uuid
                ) {
                    return@post
                }
                writeInFlight = false
                writes.poll()
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    request.listener?.onWriteSuccess()
                } else {
                    request.listener?.onWriteFailure("写入回调失败 $status")
                }
                pumpWrite()
            }
        }
    }

    private fun dispatchNotify(uuid: UUID, value: ByteArray) {
        val copy = value.clone()
        mainHandler.post {
            indicateListener?.onCharacteristicChanged(uuid, copy)
        }
    }

    private fun dispatchRead(uuid: UUID, value: ByteArray) {
        val copy = value.clone()
        mainHandler.post {
            indicateListener?.onCharacteristicRead(uuid, copy)
        }
    }

    private fun dumpServices(bluetoothGatt: BluetoothGatt): List<String> {
        val lines = mutableListOf<String>()
        for (service in bluetoothGatt.services) {
            lines.add("服务 ${service.uuid}")
            for (characteristic in service.characteristics) {
                lines.add(
                    "  特征 ${characteristic.uuid} props=0x" +
                        Integer.toHexString(characteristic.properties),
                )
            }
        }
        return lines
    }

    private fun postConnectFail(reason: String) {
        connectListener?.onConnectFail(reason)
    }

    private fun closeGatt() {
        writes.clear()
        writeInFlight = false
        subscribeList.clear()
        val current = gatt
        gatt = null
        if (current != null) {
            try {
                current.close()
            } catch (ignored: RuntimeException) {
            }
        }
    }

    private class PendingWrite(
        val characteristic: BluetoothGattCharacteristic,
        val data: ByteArray,
        val listener: BleWriteListener?,
    )

    companion object {
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val MAC_PATTERN = Regex("(?i)([0-9A-F]{2}:){5}[0-9A-F]{2}")
    }
}
