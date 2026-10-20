package com.xcwk.scalehub.application.sync

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 云推送点火器订阅侧行为（DEC-034 任务组 A）。
 *
 * 钉住四条口径，任何一条破了都会让「写在本机即上云」这个 P0 决策悄悄失效，
 * 或反过来让不该推的场景空推一遍：
 *  1. 本地写入事件必须点火
 *  2. 云端侧事件（恢复/注销）与成员切换不得回推
 *  3. 订阅建立前的历史事件不补发
 *  4. 取消订阅后不再点火
 */
class SyncOnLocalWritesTest {

    @Test
    fun `本地写入事件触发点火`() = runTest {
        val bus = DataRefreshBus()
        var fired = 0
        val job = SyncOnLocalWrites(bus, this, SyncTrigger(this) { fired += 1 }).start()
        runCurrent()

        DataChange.LOCAL_WRITES.forEach { bus.notifyChanged(it) }
        runCurrent()

        assertThat(fired).isEqualTo(DataChange.LOCAL_WRITES.size)
        job.cancel()
    }

    @Test
    fun `云端侧与成员切换事件不回推`() = runTest {
        val bus = DataRefreshBus()
        var fired = 0
        val job = SyncOnLocalWrites(bus, this, SyncTrigger(this) { fired += 1 }).start()
        runCurrent()

        bus.notifyChanged(DataChange.AccountDataRestored)
        bus.notifyChanged(DataChange.AccountDeleted)
        bus.notifyChanged(DataChange.MemberScopeChanged)
        runCurrent()

        assertThat(fired).isEqualTo(0)
        job.cancel()
    }

    @Test
    fun `订阅前发出的事件不补发`() = runTest {
        // 总线口径是无订阅者时发射即丢弃：容器启动订阅之前的历史事件不该被重放，
        // 否则 App 冷启动会凭空推一次云
        val bus = DataRefreshBus()
        bus.notifyChanged(DataChange.Measurements)
        var fired = 0
        val job = SyncOnLocalWrites(bus, this, SyncTrigger(this) { fired += 1 }).start()
        runCurrent()

        assertThat(fired).isEqualTo(0)
        job.cancel()
    }

    @Test
    fun `取消订阅后不再点火`() = runTest {
        val bus = DataRefreshBus()
        var fired = 0
        val job = SyncOnLocalWrites(bus, this, SyncTrigger(this) { fired += 1 }).start()
        runCurrent()
        job.cancel()
        runCurrent()

        bus.notifyChanged(DataChange.Measurements)
        runCurrent()

        assertThat(fired).isEqualTo(0)
    }
}
