package com.xcwk.scalehub.application.measure

import com.xcwk.scalehub.application.ble.BleConnectListener
import com.xcwk.scalehub.application.ble.BleIndicateListener
import com.xcwk.scalehub.application.ble.BlePort
import com.xcwk.scalehub.application.ble.BleScanListener
import com.xcwk.scalehub.application.ble.BleWriteListener
import com.xcwk.scalehub.application.command.RecordMeasurementCommand
import com.xcwk.scalehub.application.service.RecordMeasurementService
import com.xcwk.scalehub.domain.algorithm.OkokBia
import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.domain.protocol.Protocols
import com.xcwk.scalehub.domain.protocol.ScaleEvent
import com.xcwk.scalehub.domain.protocol.ScaleProtocol
import com.xcwk.scalehub.domain.protocol.chipsea.ChipseaCloudProtocol
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
 *  - 用户槽位：v2 单成员档案，slot 恒 1（旧库 MemberStore.activeSlot）。
 *  - 落库走 RecordMeasurementService（记录用例已抽 application，旧库 finishSave :951-970）。
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
) {

    private val _state = MutableStateFlow(MeasureState())
    val state: StateFlow<MeasureState> = _state.asStateFlow()

    private var closed = false
    private var protocol: ScaleProtocol? = null
    private var algorithmId: String = ScaleDevice.ALGORITHM_BUILTIN_1
    private var mac: String? = null
    private var picked = false
    private var linkReady = false
    private var sealed = false
    private var saved = false
    private var seenWeight = false
    private var steppingOff = false
    private var awaitingConfirm = false
    private var heldWeightOnly = false
    private var suppressDisconnect = false
    private var scaleKind = ScaleEvent.KIND_UNKNOWN
    private var secondsLeft = COUNTDOWN_SECONDS
    private var heldHeart = 0
    private var heldWeight = 0.0
    private var heldImpedance = 0.0
    private var pendingImpedance = 0.0
    private var tipIndex = 0
    /** 上次体重缓存（0.1kg 原始值；-1=无记录）。启动/重连/落库后刷新（旧库同步 SP 读的可测性替代）。 */
    private var lastWeightRaw = -1

    private var countdownJob: Job? = null
    private var retryJob: Job? = null
    private var disconnectPromptJob: Job? = null
    private var quietRetryJob: Job? = null
    private var zeroJob: Job? = null
    private var tipsJob: Job? = null

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
            _state.update { it.copy(link = "未连接") }
            return
        }
        picked = false
        mac = null
        refreshLastWeight()
        beginConnectWait()
        watchForScale()
    }

    /** 重试连接（旧库 btnRetry/retryConnect :115,880-894）。 */
    fun retry() {
        retryConnect()
    }

    /** 重新测量（旧库 btnDone 未保存分支 :101-107 → reconnect :246-261）。 */
    fun reconnect() {
        cancelSettle()
        saved = false
        seenWeight = false
        pendingImpedance = 0.0
        sealed = false
        linkReady = false
        awaitingConfirm = false
        suppressDisconnect = true
        cancelCountdown()
        cancelRetry()
        refreshLastWeight()
        start()
    }

    /** 确认保存（旧库 btnReport awaitingConfirm 分支 :108-114 与保存当前数据 :1006）。 */
    fun confirmSave() {
        if (awaitingConfirm) {
            scope.launch { finishSave(heldWeightOnly) }
        }
    }

    /** 再次提示退出（旧库 leave :215-231，返回行为由 trigger 层接管）。 */
    fun stop() {
        closed = true
        cancelSettle()
        cancelCountdown()
        cancelRetry()
        cancelZero()
        cancelTips()
        ble.stopScan()
        ble.disconnect()
    }

    // ---- 扫描与识别（:304-394）----

    private fun watchForScale() {
        ble.scan(object : BleScanListener {
            override fun onScanStarted() {
                _state.update { it.copy(notice = "开始寻找已绑定的秤") }
            }

            override fun onScanStop() = Unit

            override fun onScanFailed(reason: String) {
                _state.update { it.copy(notice = reason) }
            }

            override fun onDeviceFound(name: String, deviceMac: String, rssi: Int, scanRecord: ByteArray?) {
                if (closed || ble.isConnected()) return
                val device = matchBound(deviceMac, scanRecord) ?: return
                if (!picked) {
                    val chosen = preferAdvertisedProtocol(device, scanRecord) ?: return
                    picked = true
                    useDevice(chosen)
                    _state.update { it.copy(notice = "识别到 ${chosen.name}") }
                    val proto = protocol ?: return
                    if (proto.requiredCharacteristic() == null) {
                        deliverBroadcast(scanRecord)
                        return
                    }
                    ble.connect(chosen.mac, connectListener)
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
                if (ChipseaCloudProtocol.INSTANCE.owns(scanRecord, device.mac)) return device
            }
        }
        return null
    }

    /** 广播认出协议后就固定用这一套解析，测量过程中不再改成另一种秤（:367-380）。 */
    private fun preferAdvertisedProtocol(device: ScaleDevice, scanRecord: ByteArray?): ScaleDevice? {
        val record = scanRecord ?: return device
        val recognized = Protocols.match(record) ?: return device
        if (recognized.protocolId == device.protocolId) return device
        if (recognized.protocolId != OkokBroadcastProtocolId &&
            recognized.protocolId != ChipseaCloudProtocol.ID
        ) {
            return device
        }
        devices.bind(device.name, device.mac, recognized.protocolId)
        return devices.device(device.mac) ?: device
    }

    private fun useDevice(device: ScaleDevice) {
        mac = device.mac
        val proto = Protocols.of(device.protocolId) ?: Protocols.of(ScaleDevice.PROTOCOL_OKOK_1)
        protocol = proto
        algorithmId = if (proto != null && OkokBia.uses(proto.id())) OkokBia.ID else device.algorithmId
        proto?.reset()
    }

    private fun deliverBroadcast(scanRecord: ByteArray?) {
        val proto = protocol ?: return
        if (saved || scanRecord == null) return
        val event = proto.broadcast(scanRecord)
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
            if (!linkReady) {
                _state.update { it.copy(link = "连接中", notice = "正在连接设备，请踩亮体脂秤屏幕…") }
            }
        }

        override fun onConnectFail(reason: String) {
            suppressDisconnect = false
            _state.update { it.copy(notice = reason) }
            if (closed || sealed) return
            if (linkReady && (seenWeight || steppingOff)) {
                holdStepOff()
                return
            }
            if (linkReady) {
                scheduleDisconnectPrompt()
                return
            }
            _state.update { it.copy(link = "连接中", notice = "正在连接体脂秤…") }
            scheduleRetry()
        }

        override fun onConnectSuccess(mac: String, services: List<String>) {
            _state.update { it.copy(link = "连接中") }
            val proto = protocol ?: return
            ble.subscribe(proto.subscribe().toList(), proto.requiredCharacteristic(), proto.missingRequiredMessage(), indicateListener)
        }

        override fun onDisConnected(mac: String) {
            if (suppressDisconnect) {
                suppressDisconnect = false
                return
            }
            if (closed) return
            if (sealed) return
            if (linkReady && (seenWeight || steppingOff)) {
                holdStepOff()
                return
            }
            if (!linkReady) {
                _state.update { it.copy(link = "连接中") }
                scheduleRetry()
                return
            }
            scheduleDisconnectPrompt()
        }
    }

    private val indicateListener = object : BleIndicateListener {
        override fun onIndicateSuccess() {
            if (closed) return
            _state.update { it.copy(notice = "") }
            if (linkReady) {
                _state.update { it.copy(link = "已连接") }
                writeTimeThenUser()
                return
            }
            beginStand()
            writeTimeThenUser()
        }

        override fun onIndicateFailure(reason: String) {
            _state.update { it.copy(notice = reason) }
            if (linkReady && (seenWeight || steppingOff)) {
                holdStepOff()
                return
            }
            if (linkReady) {
                scheduleDisconnectPrompt()
                return
            }
            _state.update { it.copy(link = "连接中") }
            if (!sealed && secondsLeft > 1) scheduleRetry()
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
        ble.write(timeChar, proto.timePacket(), object : BleWriteListener {
            override fun onWriteSuccess() = sendUser()
            override fun onWriteFailure(reason: String) = sendUser()
        })
    }

    private fun sendUser() {
        val proto = protocol ?: return
        val data = proto.userPacket(1, profile.male(), profile.ageYears(), profile.heightCm(), lastWeightRaw.coerceAtLeast(0))
        val userChar = proto.userCharacteristic() ?: return
        ble.write(userChar, data, object : BleWriteListener {
            override fun onWriteSuccess() {
                proto.batteryCharacteristic()?.let { ble.read(it) }
            }

            override fun onWriteFailure(reason: String) = Unit
        })
    }

    private fun handleIncoming(characteristic: UUID, data: ByteArray) {
        val proto = protocol ?: return
        val event = proto.parse(characteristic, data) ?: return
        if (event.history) return
        if (event.batteryPercent != null) {
            _state.update { it.copy(notice = "电量 ${event.batteryPercent}%") }
            return
        }
        if (sealed) return
        if (event.weightKg == null) return
        present(event)
    }

    // ---- 呈现与封存（:628-777）----

    private fun present(event: ScaleEvent) {
        if (closed || sealed || event.weightKg == null) return
        noteKind(event.kind)
        val kilograms = event.weightKg
        if (kilograms <= 0) {
            if (seenWeight) {
                steppingOff = true
                cancelDisconnectPrompt()
                cancelRetry()
            }
            showZero()
            return
        }
        cancelZero()
        cancelCountdown()
        steppingOff = false
        cancelDisconnectPrompt()
        cancelRetry()
        seenWeight = true
        if (event.impedanceOhm > pendingImpedance) pendingImpedance = event.impedanceOhm
        if (event.heartRate > heldHeart) heldHeart = event.heartRate
        val number = String.format(java.util.Locale.US, "%.1f", kilograms)
        if (!finishesNow(event)) {
            _state.update { it.copy(link = "已连接") }
            showLive(liveHint(event), number)
            return
        }
        cancelDisconnectPrompt()
        sealed = true
        cancelZero()
        heldWeight = kilograms
        heldImpedance = pendingImpedance
        if (kilograms < MIN_WEIGHT_KG) {
            outcome(MeasureOutcomeKind.TOO_LIGHT, "体重过轻", "低于 10 公斤，这次不写入。请重新上秤。")
            return
        }
        val weightScale = scaleKind == ScaleEvent.KIND_WEIGHT
        val noBody = !weightScale && heldImpedance <= 0
        val last = lastWeightSync()
        val far = last > 0 && Math.abs(kilograms - last) >= LARGE_DELTA_KG
        if (weightScale) {
            if (far) {
                awaitingConfirm = true
                heldWeightOnly = true
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
            awaitingConfirm = true
            heldWeightOnly = true
            outcome(
                MeasureOutcomeKind.NO_COMPOSITION, "没有测出身体成分",
                "赤脚站上秤，双脚尽量覆盖所有电极片。确保脚底干净，避免沾有脏污或过于潮湿",
                weight = number,
            )
            return
        }
        awaitingConfirm = true
        heldWeightOnly = noBody
        confirmOutcome(number, fat = if (noBody) "--" else "待计算")
    }

    private fun confirmOutcome(number: String, fat: String) {
        val last = lastWeightSync()
        val extra = String.format(
            java.util.Locale.US, "和最近一次相差 %.1f 公斤。确认后才写入。", Math.abs(heldWeight - last),
        )
        outcome(
            MeasureOutcomeKind.CONFIRM_WEIGHT, "请确认这次体重", extra,
            weight = number, fat = fat, confirmable = true,
        )
    }

    /** 体重秤锁定即结束。体脂秤要阻抗锁定才出报告；测心率时还不结束（旧库 :738-754）。 */
    private fun finishesNow(event: ScaleEvent): Boolean {
        if (!event.locked || event.impedanceMeasuring || event.heartMeasuring) return false
        if (scaleKind == ScaleEvent.KIND_WEIGHT) return true
        if (pendingImpedance > 0) return true
        val label = event.label ?: ""
        if (label.contains("广播") && scaleKind != ScaleEvent.KIND_WEIGHT) return false
        return scaleKind == ScaleEvent.KIND_FAT || scaleKind == ScaleEvent.KIND_FAT_HR
    }

    private fun noteKind(kind: Int) {
        when {
            kind == ScaleEvent.KIND_FAT_HR -> scaleKind = kind
            kind == ScaleEvent.KIND_FAT && scaleKind != ScaleEvent.KIND_FAT_HR -> scaleKind = kind
            kind == ScaleEvent.KIND_WEIGHT && scaleKind == ScaleEvent.KIND_UNKNOWN -> scaleKind = kind
        }
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
        awaitingConfirm = false
        val currentMac = mac
        if (currentMac == null || !devices.contains(currentMac)) {
            outcome(MeasureOutcomeKind.NOT_BOUND, "这不是已绑定的秤", "地址不一致，这次不写入。")
            return
        }
        val impedance = if (weightOnly) 0.0 else heldImpedance
        val record = recordService.execute(
            RecordMeasurementCommand(heldWeight, impedance, currentMac, algorithmId),
        )
        saved = true
        refreshLastWeight()
        ble.stopScan()
        val extra = when {
            profile.heightCm() <= 0 || profile.birthYear() <= 0 ->
                "体重已保存在本机。到「我的档案」填写身高和出生年后，报告才能算出 BMI 和体脂"
            impedance <= 0 -> "未检测到体脂阻抗，这次只保存了体重。请赤脚踩在电极上再测一次"
            else -> String.format(java.util.Locale.US, "阻抗 %.1f Ω，记录已写入本机", impedance)
        } + if (heldHeart > 0) "    心率 $heldHeart" else ""
        _state.update {
            it.copy(
                phase = MeasurePhase.DONE,
                outcome = MeasureOutcome(
                    kind = MeasureOutcomeKind.SAVED,
                    title = "已写入本机",
                    extra = extra,
                    weight = String.format(java.util.Locale.US, "%.1f", heldWeight),
                    fat = if (record.fat < 0) "--"
                    else String.format(java.util.Locale.US, "%.1f%%", record.fat),
                    impedanceOhm = impedance,
                    heartRate = heldHeart,
                ),
            )
        }
    }

    // ---- 计时与链路守卫（:628-654, 797-894, 908-949）----

    private fun showZero() {
        if (sealed || !seenWeight || zeroJob != null) return
        zeroJob = scope.launch {
            delay(ZERO_DELAY_MS)
            zeroJob = null
            if (sealed || closed || !seenWeight) return@launch
            seenWeight = false
            steppingOff = true
            pendingImpedance = 0.0
            cancelDisconnectPrompt()
            _state.update { it.copy(weightText = "0.0", link = "已连接", notice = "请重新上秤") }
            showWaitKeepLink("请光脚站上秤", TIPS[tipIndex])
        }
    }

    /** 踩上再拿起时秤常会闪断。回到请光脚站上秤，保持已连接，后台把链路接回来（:831-848）。 */
    private fun holdStepOff() {
        if (closed || sealed) return
        cancelDisconnectPrompt()
        steppingOff = true
        cancelZero()
        seenWeight = false
        pendingImpedance = 0.0
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
        if (closed || sealed || !linkReady || !steppingOff) return
        val currentMac = mac ?: return
        ble.connect(currentMac, connectListener)
    }

    /** 体脂秤常在锁定帧之后立刻断开。先让结果帧落地，没有结果再提示重新连接（:813-828）。 */
    private fun scheduleDisconnectPrompt() {
        cancelDisconnectPrompt()
        disconnectPromptJob = scope.launch {
            delay(DISCONNECT_PROMPT_DELAY_MS)
            if (closed || sealed) return@launch
            if (!linkReady || steppingOff || seenWeight) {
                if (linkReady) _state.update { it.copy(link = "已连接") }
                return@launch
            }
            showReconnectPrompt()
        }
    }

    private fun showReconnectPrompt() {
        cancelTips()
        cancelRetry()
        cancelSettle()
        _state.update {
            it.copy(
                link = "已断开", title = "连接已断开", desc = "请重新连接体脂秤",
                showRetry = true, showCountdown = false,
            )
        }
    }

    private fun scheduleRetry() {
        cancelRetry()
        if (closed || sealed || linkReady || secondsLeft <= 1) return
        retryJob = scope.launch {
            delay(RETRY_DELAY_MS)
            retryConnect()
        }
    }

    private fun retryConnect() {
        retryJob = null
        if (closed || sealed || linkReady) return
        if (ble.isConnecting()) return
        val currentMac = mac
        val proto = protocol
        if (currentMac == null || proto == null || proto.requiredCharacteristic() == null) {
            if (!picked) watchForScale()
            return
        }
        ble.connect(currentMac, connectListener)
    }

    private fun beginConnectWait() {
        linkReady = false
        scaleKind = ScaleEvent.KIND_UNKNOWN
        seenWeight = false
        steppingOff = false
        pendingImpedance = 0.0
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
        if (linkReady) return
        linkReady = true
        cancelCountdown()
        cancelRetry()
        _state.update { it.copy(link = "已连接") }
        if (!seenWeight && !sealed && !awaitingConfirm) {
            showLive(null, "0.0")
            startTips()
        }
    }

    private fun beginStand() {
        sealed = false
        awaitingConfirm = false
        seenWeight = false
        pendingImpedance = 0.0
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
        if (sealed || awaitingConfirm || seenWeight || linkReady) return
        _state.update { it.copy(countdown = secondsLeft) }
    }

    private fun onCountdownEnd() {
        countdownJob = null
        if (closed || awaitingConfirm || seenWeight || sealed || linkReady) return
        sealed = true
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
                showRetry = false, outcome = null,
            )
        }
        if (!desc.isNullOrEmpty()) _state.update { it.copy(desc = desc) }
        val stand = title == "请光脚站上秤"
        _state.update { it.copy(showCountdown = stand && !linkReady) }
    }

    private fun showWaitKeepLink(title: String, desc: String?) {
        _state.update {
            it.copy(phase = MeasurePhase.WAIT, title = title, showRetry = false, outcome = null)
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

    private fun cancelSettle() { zeroJob?.cancel(); zeroJob = null }
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
