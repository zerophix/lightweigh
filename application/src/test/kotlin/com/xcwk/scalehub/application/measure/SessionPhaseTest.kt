package com.xcwk.scalehub.application.measure

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.protocol.ScaleEvent
import kotlin.random.Random
import org.junit.Test

/**
 * 相位转移矩阵（DEC-024 组 D/E）。
 *
 * 这些用例不碰蓝牙、不碰协程——[reduce] 是纯函数，输入相位与事件，输出相位。
 * 组 D 要求的「状态 × 事件矩阵」与「随机序列不变量」都在这里落地：
 *  - 矩阵：每个事件在每种相位上的行为（生效 / no-op）逐格断言；
 *  - 不变量：随机 1000 步后仍满足「终态不可复活」「待确认必在封存内」。
 */
class SessionPhaseTest {

    private fun linked(
        seenWeight: Boolean = false,
        steppingOff: Boolean = false,
        impedance: Double = 0.0,
        heart: Int = 0,
        kind: Int = ScaleEvent.KIND_UNKNOWN,
    ) = SessionPhase.Linked(
        MeasureProgress(
            seenWeight = seenWeight,
            steppingOff = steppingOff,
            pendingImpedance = impedance,
            heartPeak = heart,
            scaleKind = kind,
        ),
    )

    private val sealed = SessionPhase.Sealed(SealedResult(weight = 65.5, impedance = 500.0, heartPeak = 0))

    // ---- 就绪 / 复位 ----

    @Test
    fun `链路就绪只从连接中生效`() {
        assertThat(SessionPhase.Linking().reduce(SessionEvent.LinkEstablished)).isEqualTo(SessionPhase.Linked())
        assertThat(SessionPhase.Idle.reduce(SessionEvent.LinkEstablished)).isEqualTo(SessionPhase.Idle)
        assertThat(sealed.reduce(SessionEvent.LinkEstablished)).isEqualTo(sealed)
        assertThat(SessionPhase.Closed.reduce(SessionEvent.LinkEstablished)).isEqualTo(SessionPhase.Closed)
    }

    @Test
    fun `复位保留吞断连意图但清掉已选设备`() {
        val suppressed = SessionPhase.Linking(picked = true, suppressNextDisconnect = true)
        val reset = suppressed.reduce(SessionEvent.ResetForConnect)
        assertThat(reset).isEqualTo(SessionPhase.Linking(picked = false, suppressNextDisconnect = true))
    }

    @Test
    fun `终态不可复活`() {
        val closed = SessionPhase.Closed
        assertThat(closed.reduce(SessionEvent.ResetForConnect)).isEqualTo(closed)
        assertThat(closed.reduce(SessionEvent.Reseat)).isEqualTo(closed)
        assertThat(closed.reduce(SessionEvent.WeightSeen(65.0, 500.0, 0))).isEqualTo(closed)
        assertThat(closed.reduce(SessionEvent.LinkEstablished)).isEqualTo(closed)
    }

    // ---- 体重与下秤 ----

    @Test
    fun `迟到帧在连接中也能补齐就绪态`() {
        val after = SessionPhase.Linking().reduce(SessionEvent.WeightSeen(65.5, 500.0, 0))
        assertThat(after).isInstanceOf(SessionPhase.Linked::class.java)
        assertThat(after.seenWeight).isTrue()
        assertThat(after.linkReady).isTrue()
    }

    @Test
    fun `未见体重时的零帧不进入下秤`() {
        val after = linked(seenWeight = false).reduce(SessionEvent.ZeroSeen)
        assertThat(after.steppingOff).isFalse()
    }

    @Test
    fun `已见体重后的零帧进入下秤但保留体重`() {
        val after = linked(seenWeight = true).reduce(SessionEvent.ZeroSeen)
        assertThat(after.steppingOff).isTrue()
        assertThat(after.seenWeight).isTrue()
    }

    @Test
    fun `下秤确认清空体重与阻抗累加但保持就绪`() {
        val after = linked(seenWeight = true, impedance = 520.0).reduce(SessionEvent.StepOff)
        assertThat(after.seenWeight).isFalse()
        assertThat(after.steppingOff).isTrue()
        assertThat(after.pendingImpedance).isEqualTo(0.0)
        assertThat(after.linkReady).isTrue()
    }

    @Test
    fun `阻抗与心率只累加峰值`() {
        var phase: SessionPhase = linked()
        phase = phase.reduce(SessionEvent.WeightSeen(65.0, 480.0, 70))
        phase = phase.reduce(SessionEvent.WeightSeen(65.0, 520.0, 62))
        phase = phase.reduce(SessionEvent.WeightSeen(65.0, 490.0, 75))
        assertThat(phase.pendingImpedance).isEqualTo(520.0)
        assertThat(phase.heartPeak).isEqualTo(75)
    }

    @Test
    fun `秤类型只升不降`() {
        var phase: SessionPhase = linked(kind = ScaleEvent.KIND_WEIGHT)
        phase = phase.reduce(SessionEvent.KindObserved(ScaleEvent.KIND_FAT))
        assertThat(phase.scaleKind).isEqualTo(ScaleEvent.KIND_FAT)
        phase = phase.reduce(SessionEvent.KindObserved(ScaleEvent.KIND_WEIGHT))
        assertThat(phase.scaleKind).isEqualTo(ScaleEvent.KIND_FAT)
        phase = phase.reduce(SessionEvent.KindObserved(ScaleEvent.KIND_FAT_HR))
        assertThat(phase.scaleKind).isEqualTo(ScaleEvent.KIND_FAT_HR)
    }

    // ---- 封存 ----

    @Test
    fun `封存取阻抗峰值而非最后一帧`() {
        val phase = linked(seenWeight = true, impedance = 520.0, heart = 68)
        val after = phase.reduce(SessionEvent.Seal(65.5))
        val result = requireNotNull(after.sealedResult)
        assertThat(result.weight).isEqualTo(65.5)
        assertThat(result.impedance).isEqualTo(520.0)
        assertThat(result.heartPeak).isEqualTo(68)
        assertThat(result.awaitingConfirm).isFalse()
        assertThat(result.saved).isFalse()
    }

    @Test
    fun `封存只从就绪相位生效`() {
        assertThat(SessionPhase.Linking().reduce(SessionEvent.Seal(65.5))).isEqualTo(SessionPhase.Linking())
        assertThat(sealed.reduce(SessionEvent.Seal(70.0))).isEqualTo(sealed)
    }

    @Test
    fun `封存后体重帧不再改变结果`() {
        val after = sealed.reduce(SessionEvent.WeightSeen(70.0, 600.0, 90))
        assertThat(after).isEqualTo(sealed)
    }

    // ---- 确认与落库 ----

    @Test
    fun `未封存不可能进入待确认`() {
        assertThat(linked().reduce(SessionEvent.AwaitConfirm(true)).awaitingConfirm).isFalse()
        assertThat(SessionPhase.Linking().reduce(SessionEvent.AwaitConfirm(true)).awaitingConfirm).isFalse()
        assertThat(SessionPhase.Idle.reduce(SessionEvent.AwaitConfirm(true)).awaitingConfirm).isFalse()
    }

    @Test
    fun `待确认只可能出现在封存相位内`() {
        val after = sealed.reduce(SessionEvent.AwaitConfirm(weightOnly = true))
        assertThat(after).isInstanceOf(SessionPhase.Sealed::class.java)
        assertThat(after.awaitingConfirm).isTrue()
        assertThat(after.weightOnly).isTrue()
    }

    @Test
    fun `已落库后不能再进入待确认`() {
        val saved = sealed.reduce(SessionEvent.SaveCommitted)
        assertThat(saved.saved).isTrue()
        val again = saved.reduce(SessionEvent.AwaitConfirm(true))
        assertThat(again.awaitingConfirm).isFalse()
        assertThat(again).isEqualTo(saved)
    }

    @Test
    fun `确认消费后再次确认不再重复`() {
        val awaiting = sealed.reduce(SessionEvent.AwaitConfirm(true))
        val consumed = awaiting.reduce(SessionEvent.ConfirmConsumed)
        assertThat(consumed.awaitingConfirm).isFalse()
        assertThat(consumed.reduce(SessionEvent.ConfirmConsumed)).isEqualTo(consumed)
    }

    @Test
    fun `落库完成同时清掉待确认`() {
        val awaiting = sealed.reduce(SessionEvent.AwaitConfirm(true))
        val done = awaiting.reduce(SessionEvent.SaveCommitted)
        assertThat(done.saved).isTrue()
        assertThat(done.awaitingConfirm).isFalse()
    }

    // ---- 超时与结束 ----

    @Test
    fun `超时只在连接中封存空结果`() {
        val after = SessionPhase.Linking().reduce(SessionEvent.Timeout)
        val result = requireNotNull(after.sealedResult)
        assertThat(result.weight).isEqualTo(0.0)
        // 已就绪（已见体重）时不该被超时封存
        assertThat(linked(seenWeight = true).reduce(SessionEvent.Timeout)).isEqualTo(linked(seenWeight = true))
    }

    @Test
    fun `结束是终态且吞掉后续所有事件`() {
        val closed = linked(seenWeight = true).reduce(SessionEvent.Close)
        assertThat(closed).isEqualTo(SessionPhase.Closed)
        val events = listOf(
            SessionEvent.LinkEstablished,
            SessionEvent.ZeroSeen,
            SessionEvent.StepOff,
            SessionEvent.Reseat,
            SessionEvent.Seal(65.0),
            SessionEvent.Timeout,
            SessionEvent.AwaitConfirm(true),
            SessionEvent.ConfirmConsumed,
            SessionEvent.SaveCommitted,
            SessionEvent.MarkPicked,
            SessionEvent.ClearSuppress,
        )
        for (event in events) {
            assertThat(closed.reduce(event)).isEqualTo(SessionPhase.Closed)
        }
    }

    @Test
    fun `结束只从非终态生效`() {
        assertThat(SessionPhase.Closed.reduce(SessionEvent.Close)).isEqualTo(SessionPhase.Closed)
    }

    // ---- 随机序列不变量 ----

    @Test
    fun `随机事件序列后仍满足相位不变量`() {
        val random = Random(20261017)
        var phase: SessionPhase = SessionPhase.Idle
        var closedAt = -1
        repeat(2_000) { step ->
            val event = randomEvent(random)
            phase = phase.reduce(event)
            if (phase is SessionPhase.Closed && closedAt < 0) closedAt = step
            // 不变量 1：终态一旦进入就不再离开
            if (closedAt >= 0) assertThat(phase).isEqualTo(SessionPhase.Closed)
            // 不变量 2：待确认只可能出现在封存相位
            if (phase.awaitingConfirm) assertThat(phase).isInstanceOf(SessionPhase.Sealed::class.java)
            // 不变量 3：进度只可能出现在就绪相位
            if (phase.seenWeight || phase.steppingOff || phase.pendingImpedance > 0.0) {
                assertThat(phase).isInstanceOf(SessionPhase.Linked::class.java)
            }
            // 不变量 4：已落库必然意味着已封存
            if (phase.saved) assertThat(phase.sealed).isTrue()
        }
    }

    private fun randomEvent(random: Random): SessionEvent = when (random.nextInt(13)) {
        0 -> SessionEvent.LinkEstablished
        1 -> SessionEvent.KindObserved(random.nextInt(4))
        2, 3 -> SessionEvent.WeightSeen(random.nextInt(0, 150) / 10.0, random.nextInt(0, 700).toDouble(), random.nextInt(0, 120))
        4 -> SessionEvent.ZeroSeen
        5 -> SessionEvent.StepOff
        6 -> SessionEvent.Reseat
        7 -> SessionEvent.Seal(random.nextInt(0, 150) / 10.0)
        8 -> SessionEvent.Timeout
        9 -> SessionEvent.AwaitConfirm(random.nextBoolean())
        10 -> SessionEvent.ConfirmConsumed
        11 -> SessionEvent.SaveCommitted
        else -> SessionEvent.ResetForConnect
    }
}
