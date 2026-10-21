package com.xcwk.scalehub.application.service

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.command.RecordHealthLogCommand
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.domain.types.DomainException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 记录健康记录用例行为测试（DEC-022 组 A）。
 * 语义对照旧库 RecordScreen 保存分支（qk-health-android Pages.kt:531-543）。
 */
class RecordHealthLogServiceTest {

    private class FakeRepo : HealthLogRepository {
        val saved = CopyOnWriteArrayList<HealthLog>()
        override suspend fun save(log: HealthLog, memberId: String) {
            saved.removeAll { it.time == log.time && it.type == log.type }
            saved.add(log)
        }
        override suspend fun recent(limit: Int): List<HealthLog> =
            saved.sortedByDescending { it.time }.take(limit)


        override suspend fun between(fromInc: Long, toExc: Long) =
            saved.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
    }

    private val repo = FakeRepo()
    private val service = RecordHealthLogService(repo, memberId = { "1" })

    @Test
    fun `保存后发健康记录变更事件`() = runTest {
        val bus = com.xcwk.scalehub.application.DataRefreshBus()
        val svc = RecordHealthLogService(repo, bus, memberId = { "1" })
        val got = mutableListOf<com.xcwk.scalehub.application.DataChange>()
        val job = launch { bus.changed.collect { got += it } }
        runCurrent()
        svc.execute(RecordHealthLogCommand("water", 250.0))
        runCurrent()
        assertThat(got).containsExactly(com.xcwk.scalehub.application.DataChange.HealthLogs)
        job.cancel()
    }

    @Test
    fun `发的事件属于本地写入_点火交由订阅侧`() = runTest {
        // 点火不再由用例直接触发（DEC-034 组 A）：SyncOnLocalWrites 订阅后点火
        val bus = com.xcwk.scalehub.application.DataRefreshBus()
        val svc = RecordHealthLogService(repo, bus, memberId = { "1" })
        val got = mutableListOf<com.xcwk.scalehub.application.DataChange>()
        val job = launch { bus.changed.collect { got += it } }
        runCurrent()
        svc.execute(RecordHealthLogCommand("water", 250.0))
        runCurrent()
        assertThat(got.all { it in com.xcwk.scalehub.application.DataChange.LOCAL_WRITES }).isTrue()
        job.cancel()
    }

    @Test
    fun `保存被拒时不发事件`() = runTest {
        // 构造守卫抛错（习惯缺名称）→ save 未执行 → 不得发事件，否则订阅侧会白推一次云
        val bus = com.xcwk.scalehub.application.DataRefreshBus()
        val svc = RecordHealthLogService(repo, bus, memberId = { "1" })
        val got = mutableListOf<com.xcwk.scalehub.application.DataChange>()
        val job = launch { bus.changed.collect { got += it } }
        runCurrent()
        assertThrows(DomainException::class.java) {
            runBlocking { svc.execute(RecordHealthLogCommand("habit", 1.0, label = "")) }
        }
        runCurrent()
        assertThat(got).isEmpty()
        job.cancel()
    }

    @Test
    fun `正常记录盖当下时间戳并落库`() = runTest {
        val before = System.currentTimeMillis()
        val log = service.execute(RecordHealthLogCommand("water", 250.0))
        val after = System.currentTimeMillis()
        assertThat(log.time).isAtLeast(before)
        assertThat(log.time).isAtMost(after)
        assertThat(repo.saved).hasSize(1)
        assertThat(repo.saved[0].value).isEqualTo(250.0)
    }

    @Test
    fun `习惯记录主值归一为1`() = runTest {
        val log = service.execute(RecordHealthLogCommand("habit", 0.0, label = "早睡"))
        assertThat(log.value).isEqualTo(1.0)
        assertThat(log.label).isEqualTo("早睡")
        assertThat(repo.saved).hasSize(1)
    }

    @Test
    fun `习惯记录缺名称被拒`() {
        assertThrows(DomainException::class.java) {
            runBlocking { service.execute(RecordHealthLogCommand("habit", 1.0, label = "")) }
        }
        assertThat(repo.saved).isEmpty()
    }

    @Test
    fun `副值缺失默认0随库保存`() = runTest {
        val log = service.execute(RecordHealthLogCommand("exercise", 30.0, label = "跑步"))
        assertThat(log.value2).isEqualTo(0.0)
        Thread.sleep(5)   // (time,type) 唯一性：同毫秒同类型会按覆盖语义合并（同云表约束）
        val withExtra = service.execute(RecordHealthLogCommand("exercise", 45.0, value2 = 320.0, label = "骑行"))
        assertThat(withExtra.value2).isEqualTo(320.0)
        assertThat(repo.saved).hasSize(2)
    }

    @Test
    fun `weight类型被本用例拒绝`() {
        // weight 在旧库走测量路径；本断言钉住两个用例的分界
        assertThrows(DomainException::class.java) {
            runBlocking { service.execute(RecordHealthLogCommand("weight", 70.0)) }
        }
        assertThat(repo.saved).isEmpty()
    }

    @Test
    fun `同类型同刻重复写入按唯一性覆盖`() = runTest {
        val log = service.execute(RecordHealthLogCommand("water", 200.0))
        val dup = log.copy(value = 300.0)
        repo.save(dup, "1")
        assertThat(repo.saved).hasSize(1)
        assertThat(repo.saved[0].value).isEqualTo(300.0)
    }
}
