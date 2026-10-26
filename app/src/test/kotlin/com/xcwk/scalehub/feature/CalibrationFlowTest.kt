package com.xcwk.scalehub.feature

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.feature.device.CalibrationEvent
import com.xcwk.scalehub.feature.device.CalibrationStep
import com.xcwk.scalehub.feature.device.calibrationHint
import com.xcwk.scalehub.feature.device.calibrationNext
import org.junit.Test

/** T-062：引导只能顺序前进；跳步与非法事件不改变状态。 */
class CalibrationFlowTest {

    @Test
    fun `完整顺序走完`() {
        var s = CalibrationStep.Idle
        s = calibrationNext(s, CalibrationEvent.Start)
        s = calibrationNext(s, CalibrationEvent.FloorConfirmed)
        s = calibrationNext(s, CalibrationEvent.SteppedOn)
        s = calibrationNext(s, CalibrationEvent.SteppedOff)
        assertThat(s).isEqualTo(CalibrationStep.Done)
    }

    @Test
    fun `跳步被忽略`() {
        assertThat(calibrationNext(CalibrationStep.Idle, CalibrationEvent.SteppedOn))
            .isEqualTo(CalibrationStep.Idle)
        assertThat(calibrationNext(CalibrationStep.PlaceOnFloor, CalibrationEvent.SteppedOff))
            .isEqualTo(CalibrationStep.PlaceOnFloor)
    }

    @Test
    fun `取消回到未开始`() {
        assertThat(calibrationNext(CalibrationStep.StepOn, CalibrationEvent.Cancel))
            .isEqualTo(CalibrationStep.Idle)
    }

    @Test
    fun `完成后取消无效重新开始有效`() {
        assertThat(calibrationNext(CalibrationStep.Done, CalibrationEvent.Cancel))
            .isEqualTo(CalibrationStep.Done)
        assertThat(calibrationNext(CalibrationStep.Done, CalibrationEvent.Restart))
            .isEqualTo(CalibrationStep.PlaceOnFloor)
    }

    @Test
    fun `每一步都有文案`() {
        CalibrationStep.entries.forEach { assertThat(calibrationHint(it)).isNotEmpty() }
    }
}
