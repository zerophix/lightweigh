package com.xcwk.scalehub.application.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 云推送触发器行为测试（P0 断链修复）。
 * 口径：fire-and-forget（不等结果）、异常自吞、连续触发按序执行。
 */
class SyncTriggerTest {

    @Test
    fun `点火一次执行一次推送`() = runTest {
        val fired = mutableListOf<Unit>()
        val trigger = SyncTrigger(this) { fired += Unit }
        trigger.fire()
        runCurrent()
        assertThat(fired).hasSize(1)
    }

    @Test
    fun `未点火不执行`() = runTest {
        val fired = mutableListOf<Unit>()
        SyncTrigger(this) { fired += Unit }
        runCurrent()
        assertThat(fired).isEmpty()
    }

    @Test
    fun `连续点火按序执行`() = runTest {
        val log = mutableListOf<Int>()
        val trigger = SyncTrigger(this) { log += 1 }
        trigger.fire()
        trigger.fire()
        runCurrent()
        assertThat(log).containsExactly(1, 1).inOrder()
    }

    @Test
    fun `推送异常被吞_不传染fire调用方`() = runTest {
        // 无 runCatching 时 runCurrent() 会把异常抛进测试作用域 → 本用例失败
        val trigger = SyncTrigger(this) { throw IllegalStateException("network down") }
        trigger.fire()
        runCurrent()
    }
}
