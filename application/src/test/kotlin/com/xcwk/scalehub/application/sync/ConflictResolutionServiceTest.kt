package com.xcwk.scalehub.application.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 冲突解决编排行为测试。
 */
class ConflictResolutionServiceTest {

    private class FakePort : ConflictResolutionPort {
        var calls = 0
            private set

        override fun resolve(
            localTs: Long,
            cloudTs: Long,
            localFields: Map<String, Any?>,
            cloudFields: Map<String, Any?>,
        ): ConflictResolutionResult {
            calls++
            return when {
                cloudTs > localTs -> ConflictResolutionResult(
                    cloudFields, ConflictResolutionStrategy.CLOUD_WINS, needsOverride = true
                )
                localTs > cloudTs -> ConflictResolutionResult(
                    localFields, ConflictResolutionStrategy.LOCAL_WINS
                )
                else -> ConflictResolutionResult(
                    cloudFields, ConflictResolutionStrategy.MANUAL, needsOverride = true
                )
            }
        }

        override fun needsManualResolution(localTs: Long, cloudTs: Long): Boolean = localTs == cloudTs

        override fun conflictKeys(since: Long): List<String> = emptyList()
    }

    @Test
    fun `resolve with cloud newer returns CLOUD_WINS`() = runTest {
        val service = ConflictResolutionService(FakePort())
        val localFields = mapOf("weight" to 65.0, "time" to 100L)
        val cloudFields = mapOf("weight" to 66.0, "time" to 100L)

        val result = service.resolve("measurements", 100L, 200L, localFields, cloudFields)

        assertThat(result.strategy).isEqualTo(ConflictResolutionStrategy.CLOUD_WINS)
        assertThat(result.needsOverride).isTrue()
        assertThat(result.mergedFields["weight"]).isEqualTo(66.0)
    }

    @Test
    fun `resolve with local newer returns LOCAL_WINS`() = runTest {
        val service = ConflictResolutionService(FakePort())
        val localFields = mapOf("weight" to 65.0, "time" to 100L)
        val cloudFields = mapOf("weight" to 66.0, "time" to 100L)

        val result = service.resolve("measurements", 200L, 100L, localFields, cloudFields)

        assertThat(result.strategy).isEqualTo(ConflictResolutionStrategy.LOCAL_WINS)
        assertThat(result.needsOverride).isFalse()
        assertThat(result.mergedFields["weight"]).isEqualTo(65.0)
    }

    @Test
    fun `resolve with same timestamp returns MANUAL`() = runTest {
        val service = ConflictResolutionService(FakePort())
        val localFields = mapOf("weight" to 65.0)
        val cloudFields = mapOf("weight" to 66.0)

        val result = service.resolve("measurements", 100L, 100L, localFields, cloudFields)

        assertThat(result.strategy).isEqualTo(ConflictResolutionStrategy.MANUAL)
        assertThat(result.needsOverride).isTrue()
    }

    @Test
    fun `needsManualResolution returns true for same timestamp`() = runTest {
        val service = ConflictResolutionService(FakePort())
        assertThat(service.needsManualResolution(100L, 100L)).isTrue()
    }

    @Test
    fun `needsManualResolution returns false for different timestamps`() = runTest {
        val service = ConflictResolutionService(FakePort())
        assertThat(service.needsManualResolution(100L, 200L)).isFalse()
    }
}
