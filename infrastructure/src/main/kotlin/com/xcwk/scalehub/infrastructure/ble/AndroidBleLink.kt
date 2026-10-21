package com.xcwk.scalehub.infrastructure.ble

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.xcwk.scalehub.application.ble.BleConnectListener
import com.xcwk.scalehub.application.debug.DebugLog
import com.xcwk.scalehub.application.ble.BleIndicateListener
import com.xcwk.scalehub.application.ble.BlePort
import com.xcwk.scalehub.application.ble.BleScanListener
import com.xcwk.scalehub.application.ble.BleWriteListener
import com.xcwk.scalehub.domain.protocol.ConnectionMode
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ble — BlePort 实现（上游 BodyFatScale BLEManager.kt 805 行移植）。
 * 扫描、连接、订阅和写入。不识别具体体脂秤，特征列表由协议层传入。
 * 权限契约：调用方入口门禁（BlePermission）保证；SecurityException 兜底随旧库。
 *
 * connectGatt 用前台 Activity 而非 applicationContext，在部分厂商 ROM 上成功率更高。
 * 这纯属平台细节，故由本类自行监听 [Application.ActivityLifecycleCallbacks] 跟踪，
 * 不经 BlePort 接口外泄——application 层完全不知道 Context 存在。
 *
 * 其余增强：连接后请求 HIGH 优先级 + 设置 1M/2M PHY、扫描 LOW_LATENCY、超时 45s、BLE_DEBUG 日志。
 */
@SuppressLint("MissingPermission")
class AndroidBleLink(context: Context) : BlePort {

    private val appContext: Context = context.applicationContext

    /**
     * 前台 Activity 弱引用。paused/destroyed 即清空，弱引用兜底——Application 持有
     * Activity 只可能发生在它确实存活的 resumed 期间，不会泄漏。
     */
    private var foregroundRef: WeakReference<Activity> = WeakReference(null)

    /** 连接时优先用前台 Activity；不可得时回退 Application Context。 */
    private val connectContext: Context
        get() = foregroundRef.get() ?: appContext

    private val foregroundTracker = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            foregroundRef = WeakReference(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (foregroundRef.get() === activity) foregroundRef = WeakReference(null)
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (foregroundRef.get() === activity) foregroundRef = WeakReference(null)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    init {
        // 本类是容器级单例，生命周期与进程相同，无需注销；未持 Activity 引用故无泄漏风险
        (appContext as? Application)?.registerActivityLifecycleCallbacks(foregroundTracker)
    }

    private val adapter: BluetoothAdapter? = run {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val writes = ArrayDeque<PendingWrite>()
    private var subscribeList: MutableList<BluetoothGattCharacteristic> = mutableListOf()

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var scanListener: BleScanListener? = null

    /**
     * 扫描日志洪泛控制：实测密集环境 ~90 条广播/秒，2 万行缓冲约 3.7 分钟被冲光，
     * 决定性的 session 行会被淹死。首 [SCAN_LOG_FIRST] 条全量、之后每 [SCAN_LOG_EVERY]
     * 条抽样，另每 [SCAN_SUMMARY_MS] 落一行计数汇总；判定逻辑（协议标记/绑定命中）
     * 在 MeasureSession 侧每条必查，不依赖本抽样。
     */
    private var scanAdCount = 0
    private var scanWindowCount = 0
    private var scanSummary: Runnable? = null

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

    /** 订阅链推进依赖 onDescriptorWrite 回调——部分秤固件静默不回调，必须超时兜底（真机死锁点）。 */
    private var subscribeTimeout: Runnable? = null
    private var pendingDescriptor: BluetoothGattDescriptor? = null

    /** 写队列推进依赖 onCharacteristicWrite 回调——2A08 时间写入静默会卡死用户信息下发（真机死锁点）。 */
    private var writeTimeout: Runnable? = null

    /** 连接令牌：每次 connect/disconnect 递增，过期回调据此自弃（上游 BLEManager.connectToken）。 */
    private var connectToken = 0

    /** 芯海等随机地址秤：用扫描保留的实例连接并按节奏重试（上游 scannedDeviceMode 全套）。 */
    private var scannedDeviceMode = false
    private var scannedDeviceAttempt = 0
    private var scannedDeviceForConnection: BluetoothDevice? = null
    private val scannedDevices = ConcurrentHashMap<String, BluetoothDevice>()

    /** 调试双写：logcat 保持 BLE_DEBUG tag 不变，同时进应用内悬浮调试面板的环形缓冲。 */
    private fun blog(message: String) {
        Log.d("BLE_DEBUG", message)
        DebugLog.add("ble", message)
    }

    /** 16/32 位标准 UUID 缩写为 0xXXXX，避免面板行过长；非常规 UUID 保持全形。 */
    private fun shortUuid(uuid: UUID): String {
        val text = uuid.toString()
        return if (text.startsWith("0000") && text.endsWith("-1000-8000-00805f9b34fb")) {
            "0x${text.substring(4, 8)}"
        } else {
            text
        }
    }

    override fun isBluetoothEnabled(): Boolean = adapter != null && adapter.isEnabled

    override fun scan(listener: BleScanListener) {
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            blog("scan() skipped: adapter=$bt enabled=${bt?.isEnabled}")
            return
        }
        stopScan()
        scanListener = listener
        scanner = bt.bluetoothLeScanner ?: return
        listener.onScanStarted()
        // 与参考项目 BLEManager.kt:86 对齐：LOW_LATENCY 连续扫（100% 占空比）。
        // 曾改为 BALANCED 求兼容，真机取证（DEC-038 追加）中秤的短促唤醒广播两份日志
        // 全部错失——抓到广播优先于省电，功耗/限流问题出现时再有据降档
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        blog("scan() started: mode=LOW_LATENCY")
        scanAdCount = 0
        scanWindowCount = 0
        scanSummary?.let { mainHandler.removeCallbacks(it) }
        val summary = object : Runnable {
            override fun run() {
                if (scanCallback == null) return
                blog("scan: +$scanWindowCount ads/5s (total=$scanAdCount)")
                scanWindowCount = 0
                mainHandler.postDelayed(this, SCAN_SUMMARY_MS)
            }
        }
        scanSummary = summary
        mainHandler.postDelayed(summary, SCAN_SUMMARY_MS)
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
                // 保留扫描到的设备实例——芯海等随机地址秤必须用它连接（上游 BLEManager.kt:107）
                if (foundMac.isNotEmpty()) {
                    scannedDevices[foundMac] = device
                }
                val copy = record?.bytes?.clone()
                val finalName = if (name.isEmpty()) "(无名设备)" else name
                val rssi = result.rssi
                // 洪泛控制：仅日志抽样，投递给监听器的判定路径每条都走（见字段注释）
                scanAdCount++
                scanWindowCount++
                if (scanAdCount <= SCAN_LOG_FIRST || scanAdCount % SCAN_LOG_EVERY == 0) {
                    blog(
                        "onScanResult: name=$finalName mac=$foundMac rssi=$rssi hex=${copy?.toHex() ?: "null"}",
                    )
                }
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
            scanSummary?.let { mainHandler.removeCallbacks(it) }
            scanSummary = null
            blog("scan() stopped: total=$scanAdCount ads")
        }
        scanCallback = null
        scanListener?.let { listener ->
            scanListener = null
            listener.onScanStop()
        }
    }

    /**
     * 打开设备。OKOK 等公有地址秤按地址直连；芯海等随机地址秤（SCANNED_DEVICE）
     * 必须走扫描保留的实例（上游 BLEManager.connect :159-185 移植）。
     */
    override fun connect(
        mac: String,
        listener: BleConnectListener,
        mode: ConnectionMode,
    ) {
        if (mode == ConnectionMode.SCANNED_DEVICE) {
            connectScannedDevice(mac, listener)
            return
        }
        connectToken += 1
        val token = connectToken
        scannedDeviceMode = false
        scannedDeviceForConnection = null
        connectListener = listener
        if (!readyToConnect(mac)) {
            return
        }
        listener.onStartConnect()
        stopScan()
        closeGatt()
        connecting = true
        armConnectTimeout(token)
        openLink(connectContext, mac)
    }

    /**
     * 芯海秤。扫描结果里的设备才带着随机地址类型；用地址字符串重建会当成公有地址，
     * 芯海秤就一直连不上（上游 BLEManager.connectScannedDevice :191-216 移植）。
     */
    private fun connectScannedDevice(mac: String, listener: BleConnectListener) {
        connectToken += 1
        val token = connectToken
        scannedDeviceAttempt = 0
        scannedDeviceMode = false
        connectListener = listener
        if (!readyToConnect(mac)) {
            return
        }
        val scanned = scannedDevice(mac)
        if (scanned == null) {
            postConnectFail("没有扫到这台芯海秤")
            return
        }
        scannedDeviceMode = true
        scannedDeviceForConnection = scanned
        listener.onStartConnect()
        stopScan()
        closeGatt()
        connecting = true
        armConnectTimeout(token)
        scheduleScannedDeviceOpen(token)
        blog("connect(scannedDevice): mac=$mac attempt=0")
    }

    private fun readyToConnect(mac: String): Boolean {
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            postConnectFail("蓝牙未打开")
            return false
        }
        if (!MAC_PATTERN.matches(mac)) {
            postConnectFail("设备地址无效")
            return false
        }
        return true
    }

    private fun armConnectTimeout(token: Int) {
        connectTimeout?.let { mainHandler.removeCallbacks(it) }
        val timeout = Runnable {
            // 令牌不符说明已有更新的连接动作，本次超时作废
            if (token != connectToken || !connecting) return@Runnable
            connecting = false
            scannedDeviceMode = false
            closeGatt()
            postConnectFail("连接超时")
        }
        connectTimeout = timeout
        mainHandler.postDelayed(timeout, CONNECT_TIMEOUT_MS)
    }

    /** 随机地址秤停扫后需稍等再开链路：首 150ms，重试 400ms（上游 :246-261）。 */
    private fun scheduleScannedDeviceOpen(token: Int) {
        val wait = if (scannedDeviceAttempt == 0) SCAN_DEVICE_FIRST_GAP_MS else SCAN_DEVICE_RETRY_GAP_MS
        mainHandler.postDelayed({
            if (token != connectToken || !connecting || !scannedDeviceMode) return@postDelayed
            val device = scannedDeviceForConnection
            if (device == null) {
                connecting = false
                scannedDeviceMode = false
                postConnectFail("没有扫到这台芯海秤")
                return@postDelayed
            }
            openScannedDevice(connectContext, device)
        }, wait)
    }

    private fun scannedDevice(mac: String): BluetoothDevice? {
        scannedDevices[mac]?.let { return it }
        for ((address, device) in scannedDevices) {
            if (address.equals(mac, ignoreCase = true)) return device
        }
        return null
    }

    /**
     * connectGatt 公共收尾。上游 openLink / openScannedDevice 各写了一份（版本分支、
     * 三种异常的清理语义完全一致），此处收口；差异只有 device 来源与是否复位
     * scannedDeviceMode，分别经 [resolve] 与 [scanned] 表达。
     */
    private fun withGattOpen(
        caller: Context,
        tag: String,
        scanned: Boolean,
        resolve: () -> BluetoothDevice,
    ) {
        try {
            val device = resolve()
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(caller, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(caller, false, gattCallback)
            }
            blog("$tag: ${device.address} context=${caller::class.simpleName}")
        } catch (ex: SecurityException) {
            failOpen(scanned, "没有蓝牙权限")
        } catch (ex: IllegalArgumentException) {
            failOpen(scanned, "无法连接这台秤")
        } catch (ex: IllegalStateException) {
            failOpen(scanned, "无法连接这台秤")
        }
    }

    /** 打开失败的统一清理：随机地址模式需额外复位重试状态。 */
    private fun failOpen(scanned: Boolean, reason: String) {
        connecting = false
        if (scanned) scannedDeviceMode = false
        postConnectFail(reason)
    }

    /** 随机地址秤：直接用扫描保留的设备实例开链路。 */
    private fun openScannedDevice(caller: Context, device: BluetoothDevice) =
        withGattOpen(caller, "openScannedDevice", scanned = true) { device }

    /** 公有地址秤：按 MAC 字符串重建设备后开链路（getRemoteDevice 的异常一并由公共收口处理）。 */
    private fun openLink(caller: Context, mac: String) {
        val bt = adapter
        if (bt == null) {
            connecting = false
            postConnectFail("蓝牙未打开")
            return
        }
        withGattOpen(caller, "openLink", scanned = false) { bt.getRemoteDevice(mac) }
    }

    override fun isConnected(): Boolean = gatt != null && !connecting

    override fun isConnecting(): Boolean = connecting

    override fun disconnect() {
        // 令牌递增让所有在途的超时/重试/延迟 open 回调作废（上游 BLEManager.disconnect :335-337）
        connectToken += 1
        scannedDeviceMode = false
        scannedDeviceForConnection = null
        connecting = false
        connectTimeout?.let { mainHandler.removeCallbacks(it) }
        cancelSubscribeTimeout()
        cancelWriteTimeout()
        pendingDescriptor = null
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
        blog("TX queued: uuid=${characteristic?.let { shortUuid(it) }} hex=${copy.toHex()}")
        mainHandler.post {
            val target = characteristic?.let { findCharacteristic(it) }
            if (gatt == null || target == null) {
                blog("TX fail: no characteristic ${characteristic?.let { shortUuid(it) }}")
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
                // DEC-038 组A：读失败走独立通道，不再复用订阅失败语义（否则无电量特征的秤必弹假断开）
                indicateListener?.onReadFailure("没有特征 $characteristic")
                return@post
            }
            try {
                gatt?.readCharacteristic(target)
            } catch (ex: SecurityException) {
                indicateListener?.onReadFailure("没有蓝牙权限")
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
            cancelSubscribeTimeout()
            pendingDescriptor = null
            blog("subscribe: done (${subscribeList.size} characteristics)")
            indicateListener?.onIndicateSuccess()
            return
        }
        val characteristic = subscribeList[subscribeIndex]
        blog("subscribe[${subscribeIndex}]: cccd write uuid=${shortUuid(characteristic.uuid)}")
        try {
            if (!currentGatt.setCharacteristicNotification(characteristic, true)) {
                blog("subscribe[${subscribeIndex}]: skip setNotification=false")
                subscribeIndex++
                subscribeNext()
                return
            }
            val descriptor = characteristic.getDescriptor(CCCD)
            if (descriptor == null) {
                blog("subscribe[${subscribeIndex}]: skip no CCCD")
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
            pendingDescriptor = descriptor
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeDescriptor(descriptor, value) ==
                    android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                descriptor.value = value
                currentGatt.writeDescriptor(descriptor)
            }
            if (!started) {
                pendingDescriptor = null
                blog("subscribe[${subscribeIndex}]: skip writeDescriptor=false")
                subscribeIndex++
                subscribeNext()
                return
            }
            // DEC-038 追加：固件静默不回调 onDescriptorWrite 时不许卡死订阅链——
            // 超时跳过当前特征继续推进，迟到回调靠 pendingDescriptor 身份校验忽略
            armSubscribeTimeout()
        } catch (ex: SecurityException) {
            blog("subscribe[${subscribeIndex}]: fail SecurityException")
            indicateListener?.onIndicateFailure("没有蓝牙权限")
        }
    }

    private fun armSubscribeTimeout() {
        cancelSubscribeTimeout()
        val timeout = Runnable {
            subscribeTimeout = null
            if (gatt == null) return@Runnable
            blog("subscribe[${subscribeIndex}]: descriptor timeout (firmware silent), skip ahead")
            pendingDescriptor = null
            subscribeIndex++
            subscribeNext()
        }
        subscribeTimeout = timeout
        mainHandler.postDelayed(timeout, DESCRIPTOR_TIMEOUT_MS)
    }

    private fun cancelSubscribeTimeout() {
        subscribeTimeout?.let { mainHandler.removeCallbacks(it) }
        subscribeTimeout = null
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
        blog("TX write: uuid=${shortUuid(request.characteristic.uuid)} len=${request.data.size} noResponse=$noResponse hex=${request.data.toHex()}")
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
            blog("TX fail: SecurityException uuid=${shortUuid(request.characteristic.uuid)}")
            request.listener?.onWriteFailure("没有蓝牙权限")
            return
        }
        if (!ok || noResponse) {
            writeInFlight = false
            writes.poll()
            if (ok) {
                blog("TX done(noResponse): uuid=${shortUuid(request.characteristic.uuid)}")
                request.listener?.onWriteSuccess()
            } else {
                blog("TX fail: writeCharacteristic returned false uuid=${shortUuid(request.characteristic.uuid)}")
                request.listener?.onWriteFailure("写入失败")
            }
            pumpWrite()
        } else {
            // DEC-038 追加：需回写确认的写入（如 2A08 时间）固件可能静默不回调，
            // 超时按失败推进队列——否则用户信息永远发不出、测量数据永远不来
            armWriteTimeout()
        }
    }

    private fun armWriteTimeout() {
        cancelWriteTimeout()
        val timeout = Runnable {
            writeTimeout = null
            if (!writeInFlight) return@Runnable
            val request = writes.poll()
            blog("TX timeout: uuid=${request?.characteristic?.uuid?.let { shortUuid(it) } ?: "?"} (firmware silent), treat as failure")
            writeInFlight = false
            request?.listener?.onWriteFailure("写入超时")
            pumpWrite()
        }
        writeTimeout = timeout
        mainHandler.postDelayed(timeout, WRITE_TIMEOUT_MS)
    }

    private fun cancelWriteTimeout() {
        writeTimeout?.let { mainHandler.removeCallbacks(it) }
        writeTimeout = null
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
                blog("onConnectionStateChange: CONNECTED status=$status scannedDevice=$scannedDeviceMode")
                // status 非 0 说明链路建立即异常（上游 BLEManager.kt:552 移植）
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    mainHandler.post { failLink(bluetoothGatt, "连接失败 $status") }
                    return
                }
                // 请求高优先级连接（降低延迟、提升稳定性）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val prioOk = bluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    blog("requestConnectionPriority(HIGH)=$prioOk")
                }
                // 尝试设置 1M/2M PHY（更高吞吐、更稳定）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // PHY_LE_1M_MASK=1, PHY_LE_2M_MASK=2, PHY_LE_CODED_MASK=4
                    val phyOk = bluetoothGatt.setPreferredPhy(
                        1 or 2,  // PHY_LE_1M_MASK | PHY_LE_2M_MASK
                        0, 0
                    )
                    blog("setPreferredPhy(1M|2M)=$phyOk")
                }
                if (scannedDeviceMode) {
                    // 随机地址秤：发现服务可能撞上刚建立的链路，须回到主线程并捕获权限异常（上游 :556-569）
                    mainHandler.post {
                        if (gatt !== bluetoothGatt || !connecting) return@post
                        val started = try {
                            bluetoothGatt.discoverServices()
                        } catch (ex: SecurityException) {
                            false
                        }
                        if (!started) failLink(bluetoothGatt, "服务发现失败")
                    }
                } else {
                    bluetoothGatt.discoverServices()
                }
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                blog("onConnectionStateChange: DISCONNECTED status=$status scannedDevice=$scannedDeviceMode")
                mainHandler.post {
                    if (gatt !== bluetoothGatt) {
                        try {
                            bluetoothGatt.close()
                        } catch (ignored: RuntimeException) {
                        }
                        return@post
                    }
                    val wasConnecting = connecting
                    // 随机地址秤连接中断：未达重试上限则按节奏再开一次同一实例（上游 :585-591）
                    if (scannedDeviceMode && wasConnecting &&
                        scannedDeviceAttempt < SCAN_DEVICE_ATTEMPTS && gatt === bluetoothGatt
                    ) {
                        scannedDeviceAttempt += 1
                        blog("scannedDevice retry attempt=$scannedDeviceAttempt/$SCAN_DEVICE_ATTEMPTS")
                        closeGatt()
                        connecting = true
                        scheduleScannedDeviceOpen(connectToken)
                        return@post
                    }
                    connecting = false
                    scannedDeviceMode = false
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
                val services = dumpServices(bluetoothGatt)
                blog("services discovered: count=${bluetoothGatt.services.size}")
                services.forEach { blog("services: $it") }
                connectListener?.onConnectSuccess(
                    bluetoothGatt.device.address,
                    services,
                )
            }
        }

        @Suppress("DEPRECATION")
        override fun onDescriptorWrite(bluetoothGatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            blog("subscribe ack: uuid=${shortUuid(descriptor.characteristic.uuid)} status=$status")
            mainHandler.post {
                // DEC-038 追加：超时跳过后迟到的回调、非本链路回调一律忽略（身份校验）
                if (descriptor !== pendingDescriptor) {
                    blog("subscribe ack: stale/foreign descriptor ignored uuid=${shortUuid(descriptor.characteristic.uuid)}")
                    return@post
                }
                cancelSubscribeTimeout()
                pendingDescriptor = null
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    blog("subscribe ack failed status=$status, continue chain")
                }
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
            } else {
                blog("RX read fail: uuid=${shortUuid(characteristic.uuid)} status=$status")
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
            } else {
                blog("RX read fail: uuid=${shortUuid(characteristic.uuid)} status=$status")
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
                cancelWriteTimeout()
                writeInFlight = false
                writes.poll()
                blog("TX ack: uuid=${shortUuid(characteristic.uuid)} status=$status")
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
        blog("RX notify: uuid=${shortUuid(uuid)} len=${copy.size} hex=${copy.toHex()}")
        mainHandler.post {
            indicateListener?.onCharacteristicChanged(uuid, copy)
        }
    }

    private fun dispatchRead(uuid: UUID, value: ByteArray) {
        val copy = value.clone()
        blog("RX read: uuid=${shortUuid(uuid)} len=${copy.size} hex=${copy.toHex()}")
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
        blog("postConnectFail: $reason")
        connectListener?.onConnectFail(reason)
    }

    /** 连接中途失败的统一收尾：非本链路的迟到 gatt 直接关掉，本链路则清理后上报（上游 :748-763）。 */
    private fun failLink(bluetoothGatt: BluetoothGatt, reason: String) {
        if (gatt !== bluetoothGatt) {
            try {
                bluetoothGatt.close()
            } catch (ignored: RuntimeException) {
            }
            return
        }
        connecting = false
        scannedDeviceMode = false
        connectTimeout?.let { mainHandler.removeCallbacks(it) }
        closeGatt()
        postConnectFail(reason)
    }

    private fun closeGatt() {
        cancelSubscribeTimeout()
        cancelWriteTimeout()
        pendingDescriptor = null
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
        private const val CONNECT_TIMEOUT_MS = 45_000L
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val MAC_PATTERN = Regex("(?i)([0-9A-F]{2}:){5}[0-9A-F]{2}")

        /** 随机地址秤停扫后首开/重开的等待节奏与重试上限（上游 BLEManager.kt:793-795）。 */
        private const val SCAN_DEVICE_FIRST_GAP_MS = 150L
        private const val SCAN_DEVICE_RETRY_GAP_MS = 400L
        private const val SCAN_DEVICE_ATTEMPTS = 3

        /**
         * DEC-038 追加：CCCD 写入与需回写写入的固件静默兜底。
         * 部分秤固件不回调 onDescriptorWrite / onCharacteristicWrite，链路会静默卡死——
         * 超时按「跳过当前项 / 按失败推进」处理，真机实测「唤醒后停在等待态」的第一嫌疑。
         */
        private const val DESCRIPTOR_TIMEOUT_MS = 3_000L
        private const val WRITE_TIMEOUT_MS = 3_000L

        /** 扫描日志抽样与汇总节奏（洪泛控制，见 scanAdCount 字段注释）。 */
        private const val SCAN_LOG_FIRST = 20
        private const val SCAN_LOG_EVERY = 50
        private const val SCAN_SUMMARY_MS = 5_000L
    }
}

/** 广播原始字节转十六进制（蓝牙排障日志用；协议归属由 Protocols.match 判定）。 */
internal fun ByteArray?.toHex(): String =
    this?.joinToString(separator = "") { "%02X".format(it) } ?: "null"
