package com.xcwk.scalehub.feature

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.feature.device.QrScanEvent
import com.xcwk.scalehub.feature.device.QrScanState
import com.xcwk.scalehub.feature.device.qrScanMessage
import com.xcwk.scalehub.feature.device.qrScanNext
import com.xcwk.scalehub.feature.sync.HealthPlatformAvailability
import com.xcwk.scalehub.feature.sync.canWriteNow
import com.xcwk.scalehub.feature.sync.shouldShowHealthSync
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** T-059 / T-058：端口必须如实报告"不可用"，不能假装成功。 */
class HealthAndQrPortsTest {

    @Test
    fun `未接入时不展示健康同步入口`() {
        assertThat(shouldShowHealthSync(HealthPlatformAvailability.Unavailable)).isFalse()
        assertThat(shouldShowHealthSync(HealthPlatformAvailability.PermissionRequired)).isTrue()
        assertThat(shouldShowHealthSync(HealthPlatformAvailability.Ready)).isTrue()
    }

    @Test
    fun `只有已授权且用户同意才允许写入`() {
        assertThat(canWriteNow(HealthPlatformAvailability.Ready, true)).isTrue()
        assertThat(canWriteNow(HealthPlatformAvailability.Ready, false)).isFalse()
        assertThat(canWriteNow(HealthPlatformAvailability.PermissionRequired, true)).isFalse()
    }

    @Test
    fun `默认实现如实报告不可用`() = runBlocking {
        val port = com.xcwk.scalehub.feature.sync.UnavailableHealthPlatform()
        assertThat(port.availability()).isEqualTo(HealthPlatformAvailability.Unavailable)
        assertThat(port.write(emptyList()))
            .isEqualTo(com.xcwk.scalehub.feature.sync.HealthWriteResult.NotAvailable)
    }

    @Test
    fun `无相机时直接进入不可用态`() {
        val s = qrScanNext(QrScanState.Idle, QrScanEvent.Start, cameraAvailable = false)
        assertThat(s).isEqualTo(QrScanState.Unsupported)
    }

    @Test
    fun `有相机时按授权到扫码的顺序前进`() {
        var s = qrScanNext(QrScanState.Idle, QrScanEvent.Start, true)
        assertThat(s).isEqualTo(QrScanState.RequestingPermission)
        s = qrScanNext(s, QrScanEvent.PermissionGranted)
        assertThat(s).isEqualTo(QrScanState.Scanning)
        s = qrScanNext(s, QrScanEvent.Scanned("okok://AA:BB"))
        assertThat(s).isEqualTo(QrScanState.Result("okok://AA:BB"))
    }

    @Test
    fun `拒绝授权给出可执行的手动路径`() {
        var s = qrScanNext(QrScanState.Idle, QrScanEvent.Start, true)
        s = qrScanNext(s, QrScanEvent.PermissionDenied)
        assertThat(s).isInstanceOf(QrScanState.Failed::class.java)
        assertThat((s as QrScanState.Failed).reason).contains("手动输入")
    }

    @Test
    fun `取消回到初始态`() {
        assertThat(qrScanNext(QrScanState.Scanning, QrScanEvent.Cancel)).isEqualTo(QrScanState.Idle)
    }

    @Test
    fun `每个扫码状态都有可执行文案`() {
        assertThat(qrScanMessage(QrScanState.Unsupported)).contains("手动输入")
        assertThat(qrScanMessage(QrScanState.Scanning)).contains("二维码")
        assertThat(qrScanMessage(QrScanState.Failed("权限被拒绝，可改用手动输入")))
            .contains("手动输入")
        assertThat(qrScanMessage(QrScanState.Result("okok://AA"))).contains("okok://AA")
    }
}
