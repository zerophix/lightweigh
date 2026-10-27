package com.xcwk.scalehub.application.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 隐私授权服务行为测试。
 */
class PrivacyAuthServiceTest {

    private class FakeStatePort : PrivacyAuthStatePort {
        var agreed = false
        var version = ""
        var time = 0L

        override fun isAgreed(): Boolean = agreed
        override fun saveAgreed() { agreed = true; version = "v2.1"; time = System.currentTimeMillis() }
        override fun revokeAgreed() { agreed = false; version = ""; time = 0L }
        override fun agreedVersion(): String? = version.ifEmpty { null }
        override fun agreedTime(): Long = time
    }

    @Test
    fun `isAgreed returns false initially`() {
        val port = FakeStatePort()
        val service = PrivacyAuthService(port)

        assertThat(service.isAgreed()).isFalse()
    }

    @Test
    fun `saveAgreed sets agreed`() {
        val port = FakeStatePort()
        val service = PrivacyAuthService(port)

        service.saveAgreed()

        assertThat(service.isAgreed()).isTrue()
        assertThat(port.version).isEqualTo("v2.1")
    }

    @Test
    fun `revoke clears agreed`() {
        val port = FakeStatePort().apply {
            agreed = true
            version = "v2.0"
            time = 1000L
        }
        val service = PrivacyAuthService(port)

        val result = service.revoke()

        assertThat(result).isTrue()
        assertThat(port.agreed).isFalse()
        assertThat(port.version).isEmpty()
    }

    @Test
    fun `needsReauth returns true when no version recorded`() {
        val port = FakeStatePort()
        val service = PrivacyAuthService(port)

        assertThat(service.needsReauth()).isTrue()
    }

    @Test
    fun `needsReauth returns true when version differs`() {
        val port = FakeStatePort().apply { version = "v1.0" }
        val service = PrivacyAuthService(port)

        assertThat(service.needsReauth()).isTrue()
    }

    @Test
    fun `needsReauth returns false when version matches`() {
        val port = FakeStatePort().apply {
            agreed = true
            version = PrivacyAuthService.CURRENT_VERSION
        }
        val service = PrivacyAuthService(port)

        assertThat(service.needsReauth()).isFalse()
    }

    @Test
    fun `summary returns correct info`() {
        val port = FakeStatePort().apply {
            agreed = true
            version = "v2.1"
            time = 1000L
        }
        val service = PrivacyAuthService(port)

        val summary = service.summary()

        assertThat(summary.agreed).isTrue()
        assertThat(summary.version).isEqualTo("v2.1")
        assertThat(summary.agreedTime).isEqualTo(1000L)
    }
}
