package com.xcwk.scalehub.application.measure

import com.xcwk.scalehub.application.ble.BleConnectListener
import com.xcwk.scalehub.application.debug.DebugLog
import com.xcwk.scalehub.application.ble.BleIndicateListener
import com.xcwk.scalehub.application.ble.BlePort
import com.xcwk.scalehub.application.ble.BleScanListener
import com.xcwk.scalehub.application.ble.BleWriteListener
import com.xcwk.scalehub.application.command.RecordMeasurementCommand
import com.xcwk.scalehub.application.service.RecordMeasurementService
import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.domain.protocol.ConnectionMode
import com.xcwk.scalehub.domain.protocol.Protocols
import com.xcwk.scalehub.domain.protocol.ScaleEvent
import com.xcwk.scalehub.domain.protocol.ScaleProtocol
import com.xcwk.scalehub.domain.repository.DeviceRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * application service — 测量会话编排（旧库 MeasureActivity 1157 行状态机移植，行号随注释）。
 *
 * 分层口径（偏差声明）：
 *  - 运行期蓝牙权限门是 Android 侧事务，由 trigger 层（测量页）守卫后再进会话；
 *    蓝牙开关与空设备列表守卫留在会话内（:152-162），BlePort 可伪造故可测。
 *  - 旧库 Handler 定时器（连接 30s 倒计时/重试 1500ms/断线提示 700ms/静默重连 800ms/
 *    归零 1000ms/贴士 4000ms）收敛为注入 scope 的协程延时，虚拟时间可测。
 *  - 用户槽位：经 memberSlot 注入（多成员接入后由 MemberRepository.activeSlot 提供）。
 *  - 落库走 RecordMeasurementService（记录用例已抽 application，旧库 finishSave :951-970）。
 *
 * 状态表达（DEC-024 组 E）：
 *  - 相位与进度收进 [SessionPhase]（sealed + 纯 [reduce]），不再用 13 个并列可变字段；
 *    字段的可组合性曾是「未封存却在等确认」这类非法状态的温床，且编译器无从拦截。
 *  - 仍保留的可变字段只剩三类：设备绑定（选中的秤与协议）、UI 计时（倒计时/贴士/
 *    上次体重缓存）、以及 6 个 Job 句柄。前两类是输入，Job 是资源，都不是状态机语义。
 *  - 定时器仍由本类命令式驱动：`reduce` 只管「应该处于哪个相位」，不管「哪个 Job 该活着」。
 *    这是有意的边界——把 Job 也塞进状态会让纯函数变成副作用清单，反而不可测。
 */
class MeasureSession(
    private val ble: BlePort,
    private val devices: DeviceRepository,
    private val recordService: RecordMeasurementService,
    private val profile: MemberProfileReader,
    /** 上次体重（0.1kg 原始值），没有记录传 0（旧库 store.lastWeight :282）。 */
    private val lastWeightTenths: suspend () -> Int,
    private val scope: CoroutineScope,
    private val skipWeightOnlyPrompt: () -> Boolean = { false },
    /** 下发给秤的用户编号（多成员；旧库 MemberStore.activeSlot）。 */
    private val memberSlot: () -> Int = { 1 },
) {

    /** 选中的秤与协议（一次测量内不变）。 */
    private data class DeviceBinding(
        val mac: String,
        val protocol: ScaleProtocol,
        val algorithmId: String,
    )

    private val _state = MutableStateFlow(MeasureState())
    val state: StateFlow<MeasureState> = _state.asStateFlow()

    /** 单一事实源：相位与进度。写入只经 [reduce]。 */
    private var phase: SessionPhase = SessionPhase.Idle

    private var binding: DeviceBinding? = null
    private var secondsLeft = COUNTDOWN_SECONDS
    private var tipIndex = 0
    /** 上次体重缓存（0.1kg 原始值；-1=无记录）。启动/重连/落库后刷新（旧库同步 SP 读的可测性替代）。 */
    private var lastWeightRaw = -1

    private var countdownJob: Job? = null
    private var retryJob: Job? = null
    private var disconnectPromptJob: Job? = null
    private var quietRetryJob: Job? = null
    private var zeroJob: Job? = null
    private var tipsJob: Job? = null

    private val closed: Boolean get() = phase is SessionPhase.Closed
    private val mac: String? get() = binding?.mac
    private val protocol: ScaleProtocol? get() = binding?.protocol

    // ---- 对外入口（trigger 层调用）----

    /** 开始一次测量（旧库 startSession :136-167，权限门上提到 trigger 层）。 */
    fun start() {
        if (closed) return
        if (!ble.isBluetoothEnabled()) {
            showWait("请打开蓝牙", "打开手机蓝牙后才能连接体脂秤")
            _state.update { it.copy(notice = "手机蓝牙未打开") }
            return
        }
        if (devices.devices().isEmpty()) {
            showWait("无法连接", "请先绑定体脂秤")
            _state.update { it.copy(link = "未连接", needBind = true) }
            return
        }
        binding = null
        // DEC-038 组D：先取到上次体重再开扫——首包用户信息与 3kg 差值守卫都不允许读到 -1
        scope.launch {
            lastWeightRaw = lastWeightTenths()
            if (closed) return@launch
            beginConnectWait()
            watchForScale()
        }
    }

    /** 重试连接（旧库 btnRetry/retryConnect :115,880-894）。 */
    fun retry() {
        retryConnect()
    }

    /** 重新测量（旧库 btnDone 未保存分支 :101-107 → reconnect :246-261）。 */
    fun reconnect() {
        cancelZero()
        phase = SessionPhase.Linking(picked = false, suppressNextDisconnect = true)
        cancelCountdown()
        cancelRetry()
        start()
    }

    /**
     * 确认保存（旧库 btnReport awaitingConfirm 分支 :108-114 与保存当前数据 :1006）。
     *
     * closed 守卫：stop() 只取消定时器、不清确认态，会话结束后迟到的确认仍会走到
     * finishSave 写库。这类非法转移编译器不会拦，靠本守卫与「stop 之后迟到的确认
     * 不再写库」测试钉住。
     */
    fun confirmSave() {
        if (closed) return
        if (phase.awaitingConfirm) {
            val weightOnly = phase.weightOnly
            scope.launch { finishSave(weightOnly) }
        }
    }

    /** 再次提示退出（旧库 leave :215-231，返回行为由 trigger 层接管）。 */
    fun stop() {
        phase = phase.reduce(SessionEvent.Close)
        cancelCountdown()
        cancelRetry()
        cancelZero()
        cancelTips()
        ble.stopScan()
        ble.disconnect()
    }

    // ---- 扫描与识别（:304-394）----

    /** 扫描门守卫的去重日志标记——onDeviceFound 每秒可来上百条，各只报一次（真机定位用）。 */
    private var scanGateLogged = false
    private var scanUnboundLogged = false

    private fun watchForScale() {
        scanGateLogged = false
        scanUnboundLogged = false
        ble.scan(object : BleScanListener {
            override fun onScanStarted() {
                _state.update { it.copy(notice = "开始寻找已绑定的秤") }
            }

            override fun onScanStop() = Unit

            override fun onScanFailed(reason: String) {
                _state.update { it.copy(notice = reason) }
            }

            override fun onDeviceFound(name: String, deviceMac: String, rssi: Int, scanRecord: ByteArray?) {
                if (closed) return
                if (ble.isConnected()) {
                    // 守卫1（静默路径）：链路已连着时扫描结果全部作废——不打日志会表现为
                    // 「扫描流水不断却永远不 connect」，与秤无广播完全同相，必须留证据
                    if (!scanGateLogged) {
                        scanGateLogged = true
                        DebugLog.add("session", "scan gate: found $deviceMac but link already connected, all found ignored")
                    }
                    return
                }
                val device = matchBound(deviceMac, scanRecord)
                if (device == null) {
                    // 守卫2（静默路径）：秤广播被协议识别却无绑定设备拥有它 = 绑定缺失/换秤未绑；
                    // 协议无标记则属正常（手机耳机广告）。只在「识别到秤但绑不上」时报一次
                    val recognized = if (scanRecord != null) Protocols.match(scanRecord) else null
                    if (recognized != null && !scanUnboundLogged) {
                        scanUnboundLogged = true
                        DebugLog.add(
                            "session",
                            "scan gate: scale ad recognized (${recognized.protocolId}) but not bound mac=$deviceMac — 请先绑定该秤",
                        )
                    }
                    return
                }
                if (!phase.picked) {
                    val chosen = preferAdvertisedProtocol(device, scanRecord) ?: return
                    phase = phase.reduce(SessionEvent.MarkPicked)
                    useDevice(chosen)
                    _state.update { it.copy(notice = "识别到 ${chosen.name}") }
                    val proto = protocol ?: return
                    if (proto.requiredCharacteristic() == null) {
                        deliverBroadcast(scanRecord)
                        return
                    }
                    ble.connect(chosen.mac, connectListener, proto.connectionMode())
                    return
                }
                val proto = protocol
                if (proto != null && proto.requiredCharacteristic() == null &&
                    mac != null && mac.equals(deviceMac, ignoreCase = true)
                ) {
                    deliverBroadcast(scanRecord)
                }
            }
        })
    }

    private fun matchBound(deviceMac: String, scanRecord: ByteArray?): ScaleDevice? {
        for (device in devices.devices()) {
            if (device.mac.equals(deviceMac, ignoreCase = true)) return device
        }
        if (scanRecord != null) {
            for (device in devices.devices()) {
                if (Protocols.owns(scanRecord, device.mac)) return device
            }
        }
        return null
    }

    /** 广播认出协议后就固定用这一套解析，测量过程中不再改成另一种秤（:367-380）。 */
    private fun preferAdvertisedProtocol(device: ScaleDevice, scanRecord: ByteArray?): ScaleDevice? {
        val record = scanRecord ?: return device
        val recognized = Protocols.match(record) ?: return device
        if (recognized.protocolId == device.protocolId) return device
        // 关键：识别到的协议若是纯广播（无 requiredCharacteristic），保留原 GATT 协议
        // 对应参考项目 MeasureActivity.preferAdvertisedProtocol :355-367
        val recognizedProtocol = Protocols.of(recognized.protocolId)
        if (recognizedProtocol == null || recognizedProtocol.requiredCharacteristic() == null) {
            return device
        }
        devices.bind(device.name, device.mac, recognized.protocolId)
        DebugLog.add("session", "rebind: ${device.name} ${device.mac} ${device.protocolId} -> ${recognized.protocolId}")
        return devices.device(device.mac) ?: device
    }

    private fun useDevice(device: ScaleDevice) {
        val proto = Protocols.of(device.protocolId) ?: Protocols.of(ScaleDevice.PROTOCOL_OKOK_1)
        proto?.reset()
        if (proto == null) return
        // 协议自带算法时用之，否则沿用设备记录里的编号（规则收在 ScaleDevice，DEC-034 组 B）
        val algorithm = ScaleDevice.algorithmFor(proto.id(), device.algorithmId)
        binding = DeviceBinding(device.mac, proto, algorithm)
        DebugLog.add("session", "device picked: ${device.name} mac=${device.mac} protocol=${proto.id()} algorithm=$algorithm")
    }

    private fun deliverBroadcast(scanRecord: ByteArray?) {
        val proto = protocol ?: return
        if (phase.saved || scanRecord == null) return
        val event = proto.broadcast(scanRecord)
        DebugLog.add("session", "broadcast: weight=${event?.weightKg}")
        markLinked()
        if (event?.weightKg == null) return
        if (event.weightKg <= 0) {
            showZero()
            return
        }
        present(event)
    }

    // ---- 连接与订阅（:483-603）----

    private val connectListener = object : BleConnectListener {
        override fun onStartConnect() {
            protocol?.reset()
            DebugLog.add("session", "connect: onStartConnect")
            if (!phase.linkReady) {
                _state.update { it.copy(link = "连接中", notice = "正在连接设备，请踩亮体脂秤屏幕…") }
            }
        }

        override fun onConnectFail(reason: String) {
            DebugLog.add("session", "connect fail: $reason (sealed=${phase.sealed} settling=${phase.settling} linkReady=${phase.linkReady})")
            phase = phase.reduce(SessionEvent.ClearSuppress)
            _state.update { it.copy(notice = reason) }
            if (closed || phase.sealed) return
            if (phase.settling) {
                holdStepOff()
                return
            }
            if (phase.linkReady) {
                scheduleDisconnectPrompt()
                return
            }
            _state.update { it.copy(link = "连接中", notice = "正在连接体脂秤…") }
            scheduleRetry()
        }

        override fun onConnectSuccess(mac: String, services: List<String>) {
            DebugLog.add("session", "connect success: mac=$mac services=${services.size}")
            _state.update { it.copy(link = "连接中") }
            val proto = protocol ?: return
            ble.subscribe(proto.subscribe().toList(), proto.requiredCharacteristic(), proto.missingRequiredMessage(), indicateListener)
        }

        override fun onDisConnected(mac: String) {
            DebugLog.add("session", "link lost: mac=$mac suppress=${phase.suppressNextDisconnect} sealed=${phase.sealed} settling=${phase.settling}")
            if (phase.suppressNextDisconnect) {
                phase = phase.reduce(SessionEvent.ClearSuppress)
                return
            }
            if (closed) return
            if (phase.sealed) return
            if (phase.settling) {
                holdStepOff()
                return
            }
            if (!phase.linkReady) {
                _state.update { it.copy(link = "连接中") }
                scheduleRetry()
                return
            }
            scheduleDisconnectPrompt()
        }
    }

    private val indicateListener = object : BleIndicateListener {
        override fun onIndicateSuccess() {
            DebugLog.add("session", "subscribe: success (linkReady=${phase.linkReady})")
            if (closed) return
            _state.update { it.copy(notice = "") }
            if (phase.linkReady) {
                _state.update { it.copy(link = "已连接") }
                writeTimeThenUser()
                return
            }
            beginStand()
            writeTimeThenUser()
        }

        override fun onIndicateFailure(reason: String) {
            DebugLog.add("session", "subscribe fail: $reason (connected=${ble.isConnected()})")
            _state.update { it.copy(notice = reason) }
            if (phase.settling) {
                holdStepOff()
                return
            }
            if (phase.linkReady) {
                // DEC-038 组A兜底：链路其实活着就不弹断开（误报路径降级为提示）
                if (ble.isConnected()) {
                    _state.update { it.copy(link = "已连接") }
                    return
                }
                scheduleDisconnectPrompt()
                return
            }
            _state.update { it.copy(link = "连接中") }
            if (!phase.sealed && secondsLeft > 1) scheduleRetry()
        }

        /** DEC-038 组A：可选特征读失败只提示，不算链路故障。 */
        override fun onReadFailure(reason: String) {
            DebugLog.add("session", "read fail: $reason")
            _state.update { it.copy(notice = reason) }
        }

        override fun onCharacteristicChanged(characteristic: UUID, data: ByteArray) {
            handleIncoming(characteristic, data)
        }

        override fun onCharacteristicRead(characteristic: UUID, data: ByteArray) {
            handleIncoming(characteristic, data)
        }
    }

    /** 连接成功后：同步时间 → 下发用户 → 读电量（旧库 writeTimeThenUser/sendUser :263-302）。 */
    private fun writeTimeThenUser() {
        val proto = protocol ?: return
        val timeChar = proto.timeCharacteristic() ?: return
        DebugLog.add("session", "TX session: writeTime")
        ble.write(timeChar, proto.timePacket(), object : BleWriteListener {
            override fun onWriteSuccess() {
                DebugLog.add("session", "TX session: writeTime ok -> sendUser")
                sendUser()
            }

            override fun onWriteFailure(reason: String) {
                DebugLog.add("session", "TX session: writeTime fail: $reason -> sendUser anyway")
                sendUser()
            }
        })
    }

    private fun sendUser() {
        val proto = protocol ?: return
        val data = proto.userPacket(memberSlot(), profile.male(), profile.ageYears(), profile.heightCm(), lastWeightRaw.coerceAtLeast(0))
        val userChar = proto.userCharacteristic() ?: return
        DebugLog.add("session", "TX session: sendUser len=${data.size}")
        ble.write(userChar, data, object : BleWriteListener {
            override fun onWriteSuccess() {
                DebugLog.add("session", "TX session: sendUser ok -> readBattery")
                proto.batteryCharacteristic()?.let { ble.read(it) }
            }

            override fun onWriteFailure(reason: String) {
                DebugLog.add("session", "TX session: sendUser fail: $reason")
            }
        })
    }

    private fun handleIncoming(characteristic: UUID, data: ByteArray) {
        val proto = protocol ?: return
        val event = proto.parse(characteristic, data)
        if (event == null) {
            DebugLog.add("session", "parse: uuid=$characteristic -> null (dropped)")
            return
        }
        if (event.history) {
            DebugLog.add("session", "parse: history frame ignored")
            return
        }
        if (event.batteryPercent != null) {
            DebugLog.add("session", "parse: battery=${event.batteryPercent}%")
            _state.update { it.copy(notice = "电量 ${event.batteryPercent}%") }
            return
        }
        if (phase.sealed) return
        if (event.weightKg == null) return
        DebugLog.add("session", "frame: weight=${event.weightKg} impedance=${event.impedanceOhm} heart=${event.heartRate} kind=${event.kind}")
        present(event)
    }

    // ---- 呈现与封存（:628-777）----

    private fun present(event: ScaleEvent) {
        if (closed || phase.sealed || event.weightKg == null) return
        // 订阅刚建立就可能来帧：帧到了说明链路其实已通，补齐就绪态（旧实现靠后续回调补齐）
        if (!phase.linkReady) markLinked()
        phase = phase.reduce(SessionEvent.KindObserved(event.kind))
        val kilograms = event.weightKg
        if (kilograms <= 0) {
            DebugLog.add("session", "zero seen: weight=$kilograms")
            phase = phase.reduce(SessionEvent.ZeroSeen)
            if (phase.steppingOff) {
                cancelDisconnectPrompt()
                cancelRetry()
            }
            showZero()
            return
        }
        cancelZero()
        cancelCountdown()
        phase = phase.reduce(SessionEvent.WeightSeen(kilograms, event.impedanceOhm, event.heartRate))
        cancelDisconnectPrompt()
        cancelRetry()
        val number = String.format(java.util.Locale.US, "%.1f", kilograms)
        if (!finishesNow(event)) {
            _state.update { it.copy(link = "已连接") }
            showLive(liveHint(event), number)
            return
        }
        cancelDisconnectPrompt()
        phase = phase.reduce(SessionEvent.Seal(kilograms))
        DebugLog.add("session", "sealed: weight=$kilograms impedance=${event.impedanceOhm}")
        cancelZero()
        if (kilograms < MIN_WEIGHT_KG) {
            outcome(MeasureOutcomeKind.TOO_LIGHT, "体重过轻", "低于 10 公斤，这次不写入。请重新上秤。")
            return
        }
        val heldImpedance = phase.sealedResult?.impedance ?: 0.0
        val weightScale = phase.scaleKind == ScaleEvent.KIND_WEIGHT
        val noBody = !weightScale && heldImpedance <= 0
        val last = lastWeightSync()
        val far = last > 0 && Math.abs(kilograms - last) >= LARGE_DELTA_KG
        if (weightScale) {
            if (far) {
                phase = phase.reduce(SessionEvent.AwaitConfirm(weightOnly = true))
                confirmOutcome(number, fat = "--")
                return
            }
            scope.launch { finishSave(true) }
            return
        }
        if (!noBody && !far) {
            scope.launch { finishSave(false) }
            return
        }
        if (noBody && !far && skipWeightOnlyPrompt()) {
            scope.launch { finishSave(true) }
            return
        }
        if (noBody && !far) {
            phase = phase.reduce(SessionEvent.AwaitConfirm(weightOnly = true))
            outcome(
                MeasureOutcomeKind.NO_COMPOSITION, "没有测出身体成分",
                "赤脚站上秤，双脚尽量覆盖所有电极片。确保脚底干净，避免沾有脏污或过于潮湿",
                weight = number,
            )
            return
        }
        phase = phase.reduce(SessionEvent.AwaitConfirm(weightOnly = noBody))
        confirmOutcome(number, fat = if (noBody) "--" else "待计算")
    }

    private fun confirmOutcome(number: String, fat: String) {
        val last = lastWeightSync()
        val held = phase.sealedResult?.weight ?: 0.0
        val extra = String.format(
            java.util.Locale.US, "和最近一次相差 %.1f 公斤。确认后才写入。", Math.abs(held - last),
        )
        outcome(
            MeasureOutcomeKind.CONFIRM_WEIGHT, "请确认这次体重", extra,
            weight = number, fat = fat, confirmable = true,
        )
    }

    /** 体重秤锁定即结束。体脂秤要阻抗锁定才出报告；测心率时还不结束（旧库 :738-754）。 */
    private fun finishesNow(event: ScaleEvent): Boolean {
        if (!event.locked || event.impedanceMeasuring || event.heartMeasuring) return false
        if (phase.scaleKind == ScaleEvent.KIND_WEIGHT) return true
        if (phase.pendingImpedance > 0) return true
        val label = event.label ?: ""
        if (label.contains("广播") && phase.scaleKind != ScaleEvent.KIND_WEIGHT) return false
        return phase.scaleKind == ScaleEvent.KIND_FAT || phase.scaleKind == ScaleEvent.KIND_FAT_HR
    }

    private fun liveHint(event: ScaleEvent): String = when {
        event.heartMeasuring -> "正在测心率"
        event.impedanceMeasuring -> "正在测体脂"
        event.label.contains("八电极") -> "请双手握住手柄，保持站稳"
        else -> TIPS[tipIndex]
    }

    private fun refreshLastWeight() {
        scope.launch { lastWeightRaw = lastWeightTenths() }
    }

    private fun lastWeightSync(): Double =
        if (lastWeightRaw > 0) lastWeightRaw / 10.0 else -1.0

    /** 落库（旧库 finishSave :951-970）。 */
    private suspend fun finishSave(weightOnly: Boolean) {
        // 先消费确认态：否则落库过程中重复点确认会再起一次落库
        phase = phase.reduce(SessionEvent.ConfirmConsumed)
        val sealed = phase.sealedResult ?: return
        // DEC-038 组C：已落库的结果不得二次写（确认按钮连点 = 双协程排队）
        if (sealed.saved) {
            DebugLog.add("session", "save skipped: already saved weight=${sealed.weight}")
            return
        }
        val currentBinding = binding
        val currentMac = currentBinding?.mac
        if (currentBinding == null || currentMac == null || !devices.contains(currentMac)) {
            DebugLog.add("session", "save rejected: not bound binding=$currentBinding mac=$currentMac")
            outcome(MeasureOutcomeKind.NOT_BOUND, "这不是已绑定的秤", "地址不一致，这次不写入。")
            return
        }
        val impedance = if (weightOnly) 0.0 else sealed.impedance
        val record = recordService.execute(
            RecordMeasurementCommand(sealed.weight, impedance, currentMac, currentBinding.algorithmId),
        )
        phase = phase.reduce(SessionEvent.SaveCommitted)
        DebugLog.add("session", "saved: weight=${sealed.weight} impedance=$impedance weightOnly=$weightOnly")
        refreshLastWeight()
        ble.stopScan()
        val extra = when {
            profile.heightCm() <= 0 || profile.birthYear() <= 0 ->
                "体重已保存在本机。到「我的档案」填写身高和出生年后，报告才能算出 BMI 和体脂"
            impedance <= 0 -> "未检测到体脂阻抗，这次只保存了体重。请赤脚踩在电极上再测一次"
            else -> String.format(java.util.Locale.US, "阻抗 %.1f Ω，记录已写入本机", impedance)
        } + if (sealed.heartPeak > 0) "    心率 ${sealed.heartPeak}" else ""
        _state.update {
            it.copy(
                phase = MeasurePhase.DONE,
                outcome = MeasureOutcome(
                    kind = MeasureOutcomeKind.SAVED,
                    title = "已写入本机",
                    extra = extra,
                    weight = String.format(java.util.Locale.US, "%.1f", sealed.weight),
                    fat = if (record.fat < 0) "--"
                    else String.format(java.util.Locale.US, "%.1f%%", record.fat),
                    impedanceOhm = impedance,
                    heartRate = sealed.heartPeak,
                    recordTime = record.time,
                ),
            )
        }
    }

    // ---- 计时与链路守卫（:628-654, 797-894, 908-949）----

    private fun showZero() {
        if (phase.sealed || !phase.seenWeight || zeroJob != null) return
        zeroJob = scope.launch {
            delay(ZERO_DELAY_MS)
            zeroJob = null
            if (closed || phase.sealed || !phase.seenWeight) return@launch
            phase = phase.reduce(SessionEvent.StepOff)
            cancelDisconnectPrompt()
            _state.update { it.copy(weightText = "0.0", link = "已连接", notice = "请重新上秤") }
            showWaitKeepLink("请光脚站上秤", TIPS[tipIndex])
        }
    }

    /** 踩上再拿起时秤常会闪断。回到请光脚站上秤，保持已连接，后台把链路接回来（:831-848）。 */
    private fun holdStepOff() {
        if (closed || phase.sealed) return
        cancelDisconnectPrompt()
        phase = phase.reduce(SessionEvent.StepOff)
        cancelZero()
        showWaitKeepLink("请光脚站上秤", TIPS[tipIndex])
        _state.update { it.copy(link = "已连接", notice = "请重新上秤") }
        if (!ble.isConnected() && !ble.isConnecting()) {
            cancelQuietRetry()
            quietRetryJob = scope.launch {
                delay(QUIET_RETRY_DELAY_MS)
                quietReconnect()
            }
        }
    }

    private fun quietReconnect() {
        quietRetryJob = null
        if (closed || phase.sealed || !phase.linkReady || !phase.steppingOff) return
        val currentMac = mac ?: return
        ble.connect(currentMac, connectListener, protocol?.connectionMode() ?: ConnectionMode.PUBLIC_ADDRESS)
    }

    /** 体脂秤常在锁定帧之后立刻断开。先让结果帧落地，没有结果再提示重新连接（:813-828）。 */
    private fun scheduleDisconnectPrompt() {
        cancelDisconnectPrompt()
        disconnectPromptJob = scope.launch {
            delay(DISCONNECT_PROMPT_DELAY_MS)
            if (closed || phase.sealed) return@launch
            if (!phase.linkReady || phase.steppingOff || phase.seenWeight) {
                if (phase.linkReady) _state.update { it.copy(link = "已连接") }
                return@launch
            }
            showReconnectPrompt()
        }
    }

    private fun showReconnectPrompt() {
        cancelTips()
        cancelRetry()
        cancelZero()
        // DEC-038 组B：链路已死，相位降级——否则 retryConnect 被 linkReady 守卫拦成死按钮
        phase = phase.reduce(SessionEvent.Downgrade)
        _state.update {
            it.copy(
                link = "已断开", title = "连接已断开", desc = "请重新连接体脂秤",
                showRetry = true, showCountdown = false,
            )
        }
    }

    private fun scheduleRetry() {
        cancelRetry()
        if (closed || phase.sealed || phase.linkReady || secondsLeft <= 1) return
        retryJob = scope.launch {
            delay(RETRY_DELAY_MS)
            retryConnect()
        }
    }

    private fun retryConnect() {
        retryJob = null
        if (closed || phase.sealed || phase.linkReady) return
        if (ble.isConnecting()) return
        val currentMac = mac
        val proto = protocol
        if (currentMac == null || proto == null || proto.requiredCharacteristic() == null) {
            if (!phase.picked) watchForScale()
            return
        }
        ble.connect(currentMac, connectListener, proto.connectionMode())
    }

    private fun beginConnectWait() {
        phase = phase.reduce(SessionEvent.ResetForConnect)
        cancelZero()
        tipIndex = 0
        _state.update {
            it.copy(
                weightText = "0.0", countdown = COUNTDOWN_SECONDS, showCountdown = true,
                notice = "使用当前测量用户「${profile.nickname().ifBlank { "当前用户" }}」",
            )
        }
        showWait("请光脚站上秤", TIPS[0])
        _state.update { it.copy(link = "连接中") }
        startTips()
        startCountdown()
    }

    private fun markLinked() {
        if (phase.linkReady) return
        phase = phase.reduce(SessionEvent.LinkEstablished)
        cancelCountdown()
        cancelRetry()
        _state.update { it.copy(link = "已连接") }
        if (!phase.seenWeight && !phase.sealed && !phase.awaitingConfirm) {
            showLive(null, "0.0")
            startTips()
        }
    }

    private fun beginStand() {
        phase = phase.reduce(SessionEvent.Reseat)
        cancelZero()
        markLinked()
        showLive(null, "0.0")
        startTips()
    }

    private fun startCountdown() {
        cancelCountdown()
        secondsLeft = COUNTDOWN_SECONDS
        publishCountdown()
        countdownJob = scope.launch {
            while (true) {
                delay(1000)
                if (closed) return@launch
                secondsLeft -= 1
                publishCountdown()
                if (secondsLeft <= 0) {
                    onCountdownEnd()
                    return@launch
                }
            }
        }
    }

    private fun publishCountdown() {
        if (phase.sealed || phase.awaitingConfirm || phase.seenWeight || phase.linkReady) return
        _state.update { it.copy(countdown = secondsLeft) }
    }

    private fun onCountdownEnd() {
        countdownJob = null
        if (closed || phase.awaitingConfirm || phase.seenWeight || phase.sealed || phase.linkReady) return
        phase = phase.reduce(SessionEvent.Timeout)
        cancelTips()
        cancelRetry()
        ble.stopScan()
        ble.disconnect()
        outcome(
            MeasureOutcomeKind.NO_DATA, "没有获取到测量数据",
            "请检查设备电量是否充足，或者尝试重启设备。",
        )
    }

    // ---- 贴士与状态发布（:1086-1123, 412-448）----

    private fun startTips() {
        cancelTips()
        tipIndex = 0
        publishTip()
        tipsJob = scope.launch {
            while (true) {
                delay(TIP_DELAY_MS)
                if (closed) return@launch
                tipIndex = (tipIndex + 1) % TIPS.size
                publishTip()
            }
        }
    }

    private fun publishTip() {
        val tip = TIPS[tipIndex]
        val current = _state.value
        val inWait = current.phase == MeasurePhase.WAIT
        val inLive = current.phase == MeasurePhase.LIVE && tipCanReplace(current.desc)
        if (inWait || inLive) _state.update { it.copy(desc = tip) }
    }

    /** 正在测体脂/心率/握手柄的提示不被轮换贴士顶掉（旧库 :1100-1106）。 */
    private fun tipCanReplace(text: String): Boolean =
        !text.contains("手柄") && !text.contains("测体脂") && !text.contains("测心率")

    private fun showWait(title: String, desc: String?) {
        _state.update {
            it.copy(
                phase = MeasurePhase.WAIT, title = title, weightText = it.weightText,
                showRetry = false, outcome = null, needBind = false,
            )
        }
        if (!desc.isNullOrEmpty()) _state.update { it.copy(desc = desc) }
        val stand = title == "请光脚站上秤"
        _state.update { it.copy(showCountdown = stand && !phase.linkReady) }
    }

    private fun showWaitKeepLink(title: String, desc: String?) {
        _state.update {
            it.copy(phase = MeasurePhase.WAIT, title = title, showRetry = false, outcome = null, needBind = false)
        }
        if (!desc.isNullOrEmpty()) _state.update { it.copy(desc = desc) }
        _state.update { it.copy(showCountdown = false) }
    }

    private fun showLive(desc: String?, weight: String) {
        _state.update {
            it.copy(
                phase = MeasurePhase.LIVE, title = "测量中，请勿离开", weightText = weight,
                showRetry = false, showCountdown = false, outcome = null,
            )
        }
        if (!desc.isNullOrEmpty()) _state.update { it.copy(desc = desc) }
    }

    private fun outcome(kind: MeasureOutcomeKind, title: String, extra: String, weight: String = "", fat: String = "--", confirmable: Boolean = false) {
        cancelTips()
        val heldWeight = phase.sealedResult?.weight ?: 0.0
        _state.update {
            it.copy(
                phase = MeasurePhase.DONE, showCountdown = false, showRetry = false,
                outcome = MeasureOutcome(
                    kind = kind, title = title, extra = extra,
                    weight = weight.ifEmpty { String.format(java.util.Locale.US, "%.1f", heldWeight) },
                    fat = fat, confirmable = confirmable,
                ),
            )
        }
    }

    // ---- 定时器清理 ----

    private fun cancelCountdown() { countdownJob?.cancel(); countdownJob = null }
    private fun cancelRetry() {
        retryJob?.cancel(); retryJob = null
        cancelDisconnectPrompt()
        cancelQuietRetry()
    }

    private fun cancelDisconnectPrompt() { disconnectPromptJob?.cancel(); disconnectPromptJob = null }
    private fun cancelQuietRetry() { quietRetryJob?.cancel(); quietRetryJob = null }
    private fun cancelZero() { zeroJob?.cancel(); zeroJob = null }
    private fun cancelTips() { tipsJob?.cancel(); tipsJob = null }

    companion object {
        const val COUNTDOWN_SECONDS = 30
        const val MIN_WEIGHT_KG = 10.0
        const val LARGE_DELTA_KG = 3.0
        const val RETRY_DELAY_MS = 1500L
        const val DISCONNECT_PROMPT_DELAY_MS = 700L
        const val QUIET_RETRY_DELAY_MS = 800L
        const val ZERO_DELAY_MS = 1000L
        const val TIP_DELAY_MS = 4000L
        const val OkokBroadcastProtocolId = "okok-broadcast-1"
        val TIPS = listOf(
            "双脚尽量覆盖所有电极片",
            "请等待出报告后再下秤",
            "保持秤面与脚底清洁",
            "在数值稳定前，不要离开秤哦~",
        )
    }
}
