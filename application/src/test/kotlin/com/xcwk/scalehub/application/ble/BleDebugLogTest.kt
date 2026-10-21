package com.xcwk.scalehub.application.ble

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * 调试日志环形缓冲契约：2 万行上限、最旧淘汰、快照隔离、清空、并发安全。
 * 悬浮调试面板的一键复制与滚动展示全部建立在这几条语义上。
 */
class BleDebugLogTest {

    @Test
    fun `追加与快照_带时间戳和标签`() {
        BleDebugLog.clear()
        BleDebugLog.add("ble", "scan started")
        val snap = BleDebugLog.snapshot()
        assertThat(snap).hasSize(1)
        // 格式: HH:mm:ss.SSS [tag] message
        assertThat(snap[0]).matches("""\d{2}:\d{2}:\d{2}\.\d{3} \[ble] scan started""")
    }

    @Test
    fun `超出两万行_淘汰最旧保留最新`() {
        BleDebugLog.clear()
        for (i in 1..BleDebugLog.MAX_LINES + 5) BleDebugLog.add("t", "line-$i")
        val snap = BleDebugLog.snapshot()
        assertThat(snap).hasSize(BleDebugLog.MAX_LINES)
        // 最旧 5 行被淘汰：第一条应是 line-6，最后一条是最新
        assertThat(snap.first()).contains("line-6")
        assertThat(snap.last()).contains("line-${BleDebugLog.MAX_LINES + 5}")
    }

    @Test
    fun `边界_恰好两万行不淘汰`() {
        BleDebugLog.clear()
        for (i in 1..BleDebugLog.MAX_LINES) BleDebugLog.add("t", "line-$i")
        val snap = BleDebugLog.snapshot()
        assertThat(snap).hasSize(BleDebugLog.MAX_LINES)
        assertThat(snap.first()).contains("line-1")
    }

    @Test
    fun `快照是拷贝_后续追加不影响已取快照`() {
        BleDebugLog.clear()
        BleDebugLog.add("t", "before")
        val snap = BleDebugLog.snapshot()
        BleDebugLog.add("t", "after")
        assertThat(snap).hasSize(1)
        assertThat(BleDebugLog.snapshot()).hasSize(2)
    }

    @Test
    fun `清空_归零`() {
        BleDebugLog.clear()
        BleDebugLog.add("t", "x")
        BleDebugLog.clear()
        assertThat(BleDebugLog.size()).isEqualTo(0)
        assertThat(BleDebugLog.snapshot()).isEmpty()
    }

    @Test
    fun `并发写入_不丢锁且行数不超上限`() {
        BleDebugLog.clear()
        val threads = 4
        val perThread = 5_000
        val pool = Executors.newFixedThreadPool(threads)
        val done = CountDownLatch(threads)
        repeat(threads) { t ->
            pool.execute {
                repeat(perThread) { i -> BleDebugLog.add("t$t", "msg-$i") }
                done.countDown()
            }
        }
        assertThat(done.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue()
        pool.shutdown()
        val snap = BleDebugLog.snapshot()
        // 总写入 2 万恰好等于上限：一条不丢
        assertThat(snap).hasSize(threads * perThread)
        BleDebugLog.clear()
    }

    @Test
    fun `并发写入超上限_始终收敛在两万行`() {
        BleDebugLog.clear()
        val threads = 4
        val perThread = 8_000
        val pool = Executors.newFixedThreadPool(threads)
        val done = CountDownLatch(threads)
        repeat(threads) { t ->
            pool.execute {
                repeat(perThread) { i -> BleDebugLog.add("t", "m-$t-$i") }
                done.countDown()
            }
        }
        assertThat(done.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue()
        pool.shutdown()
        assertThat(BleDebugLog.size()).isEqualTo(BleDebugLog.MAX_LINES)
        BleDebugLog.clear()
    }
}
