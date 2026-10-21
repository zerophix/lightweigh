package com.xcwk.scalehub.application.sync

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MemberProfileWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * T-012: 全链路端到端测试（本地写 → DataChange → SyncOnLocalWrites → SyncTrigger → SyncService → CloudDataPort）。
 *
 * 覆盖场景：
 * 1. 正常链路：本地写入 → 事件 → 订阅点火 → SyncService.push → CloudDataPort.upsert
 * 2. 事件类型过滤：非 LOCAL_WRITES 事件不触发推送
 * 3. 游客门：非正式账号 no-op
 * 4. 失败重试：upsert 失败后 watermark 不更新，下次可重推
 * 5. 幂等：重复推送无新行时不再 upsert
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FullChainSyncTest {

    // ---- 假件 ----

    private class FakeSession(
        var formal: Boolean = true,
        var token: String? = "tok-1",
        var userId: String? = "u-1",
    ) : CloudSessionPort {
        override fun isFormalAccount() = formal
        override fun accessToken() = token
        override fun subject(token: String) = userId
        override fun signOut() { token = null }
    }

    private class FakeCloud : CloudDataPort {
        val upserts = mutableListOf<Triple<String, String, List<CloudRow>>>()
        var failRows = false
        var upsertOk = true

        override suspend fun rows(table: String, query: String): List<CloudRow>? =
            if (failRows) null else emptyList()

        override suspend fun upsert(table: String, conflict: String, rows: List<CloudRow>): Boolean {
            if (!upsertOk) return false
            upserts += Triple(table, conflict, rows)
            return true
        }

        override suspend fun delete(table: String, filter: String): Boolean = true
    }

    private class FakeAuth : com.xcwk.scalehub.application.auth.AuthPort {
        override suspend fun deleteCurrentUser(verifyCode: String): String? = null
        override suspend fun sendSmsCode(rawDigits: String) = throw UnsupportedOperationException()
        override suspend fun signInWithCode(
            rawDigits: String, verificationId: String, registered: Boolean, code: String,
        ) = throw UnsupportedOperationException()
        override fun isEntered() = true
        override fun markEntered() = throw UnsupportedOperationException()
        override fun isFormalAccount() = true
        override fun phone() = "13800138000"
        override fun signOut() = throw UnsupportedOperationException()
        override suspend fun sendDeleteCode() = throw UnsupportedOperationException()
    }

    private class FakeDevices : com.xcwk.scalehub.domain.repository.DeviceRepository {
        var list: List<com.xcwk.scalehub.domain.model.ScaleDevice> = emptyList()
        override fun devices() = list
        override fun contains(mac: String) = list.any { it.mac.equals(mac, true) }
        override fun device(mac: String) = list.firstOrNull { it.mac.equals(mac, true) }
        override fun bind(name: String, mac: String, protocolId: String) = null
        override fun activate(mac: String) = Unit
        override fun unbind(mac: String) { list = list.filterNot { it.mac.equals(mac, true) } }
        override fun restoreAll(devices: List<com.xcwk.scalehub.domain.model.ScaleDevice>) { list = devices }
    }

    private class FakeMeasureRepo(rows: List<MeasureRecord> = emptyList()) : MeasurementRepository {
        val saved = rows.toMutableList()
        override suspend fun save(record: MeasureRecord, memberId: String) { saved += record }
        override suspend fun recent(limit: Int) = saved.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            saved.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
        override suspend fun delete(time: Long) { saved.removeAll { it.time == time } }
    }

    private class FakeLogRepo(rows: List<HealthLog> = emptyList()) : HealthLogRepository {
        val saved = rows.toMutableList()
        override suspend fun save(log: HealthLog, memberId: String) { saved += log }
        override suspend fun recent(limit: Int) = saved.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            saved.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
    }

    private class FakeProfile(
        var height: Int = 175,
        var year: Int = 1996,
    ) : MemberProfileReader, MemberProfileWriter {
        override fun male() = true
        override fun ageYears() = 30
        override fun heightCm() = height
        override fun birthYear() = year
        override fun birthMonth() = 5
        override fun birthDay() = 20
        override fun nickname() = ""
        override suspend fun save(profile: MemberProfile) { }
    }

    /**
     * 搭建完整链路：DataRefreshBus → SyncOnLocalWrites → SyncTrigger → SyncService → CloudDataPort
     * @return Chain(bus, cloud, syncState, job) 便于测试中发布事件与断言
     */
    private class Chain(
        val bus: DataRefreshBus,
        val cloud: FakeCloud,
        val syncState: SyncStatePort,
        val job: Job,
    )

    private fun buildChain(
        scope: TestScope,
        session: FakeSession = FakeSession(),
        cloud: FakeCloud = FakeCloud(),
        measures: FakeMeasureRepo = FakeMeasureRepo(),
        logs: FakeLogRepo = FakeLogRepo(),
        syncState: SyncStatePort = InMemorySyncStateRepositoryFixture(),
    ): Chain {
        val bus = DataRefreshBus()
        val syncService = SyncService(
            session, cloud, FakeAuth(), FakeDevices(), null,
            measures, logs, FakeProfile(), FakeProfile(),
            memberId = { "1" }, syncState = syncState,
        )
        val trigger = SyncTrigger(scope) { syncService.push() }
        val subscriber = SyncOnLocalWrites(bus, scope, trigger)
        val job = subscriber.start()
        return Chain(bus, cloud, syncState, job)
    }

    // ---- 场景 1: 正常链路 ----

    @Test
    fun `全链路_本地写入触发同步_数据上云`() = runTest {
        val measures = FakeMeasureRepo()
        val chain = buildChain(this, measures = measures)
        advanceUntilIdle()   // 等待订阅者就绪


        // 等待订阅者就绪（SharedFlow 无订阅者时发射即丢弃）
        advanceUntilIdle()

        // 模拟本地写入：先落库，再发事件
        val record = MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175)
        measures.save(record, "1")
        chain.bus.notifyChanged(DataChange.Measurements)

        advanceUntilIdle()
        chain.job.cancel()

        // 断言：数据已推到"云端"
        assertThat(chain.cloud.upserts).isNotEmpty()
        val measurementUpsert = chain.cloud.upserts.first { it.first == "measurements" }
        assertThat(measurementUpsert.third).hasSize(1)
        assertThat(measurementUpsert.third[0].fields["measured_at"]).isEqualTo(1000L)
        assertThat(measurementUpsert.third[0].fields["owner_id"]).isEqualTo("u-1")

        // 断言：watermark 已更新
        val (pushed, _) = chain.syncState.get("measurements", "1")
        assertThat(pushed).isEqualTo(1000L)
    }

    @Test
    fun `全链路_健康记录写入触发同步`() = runTest {
        val logs = FakeLogRepo()
        val chain = buildChain(this, logs = logs)
        advanceUntilIdle()   // 等待订阅者就绪


        // 模拟本地写入健康日志
        val log = HealthLog(1500L, "diet", 1500.0, 0.0, "午餐", "")
        logs.save(log, "1")
        chain.bus.notifyChanged(DataChange.HealthLogs)

        advanceUntilIdle()
        chain.job.cancel()

        // 断言：健康日志已推到云端
        val logUpsert = chain.cloud.upserts.first { it.first == "health_logs" }
        assertThat(logUpsert.third).hasSize(1)
        assertThat(logUpsert.third[0].fields["logged_at"]).isEqualTo(1500L)
        assertThat(logUpsert.third[0].fields["type"]).isEqualTo("diet")
    }

    @Test
    fun `全链路_档案变更触发同步`() = runTest {
        val chain = buildChain(this)
        advanceUntilIdle()   // 等待订阅者就绪


        // 模拟档案变更
        chain.bus.notifyChanged(DataChange.Profile)

        advanceUntilIdle()
        chain.job.cancel()

        // 断言：profile 已推到云端
        val profileUpsert = chain.cloud.upserts.first { it.first == "profiles" }
        assertThat(profileUpsert.third).isNotEmpty()
    }

    // ---- 场景 2: 事件类型过滤 ----

    @Test
    fun `全链路_非LOCAL_WRITES事件不触发推送`() = runTest {
        val measures = FakeMeasureRepo()
        val chain = buildChain(this, measures = measures)
        advanceUntilIdle()   // 等待订阅者就绪


        // 云端侧操作事件：不应触发推送
        chain.bus.notifyChanged(DataChange.AccountDataRestored)
        advanceUntilIdle()

        // 断言：无 measurements upsert
        assertThat(chain.cloud.upserts.none { it.first == "measurements" }).isTrue()

        // AccountDeleted 也不应触发
        chain.bus.notifyChanged(DataChange.AccountDeleted)
        advanceUntilIdle()
        chain.job.cancel()
        assertThat(chain.cloud.upserts.none { it.first == "measurements" }).isTrue()
    }

    // ---- 场景 3: 游客门 ----

    @Test
    fun `全链路_游客写入不触发推送`() = runTest {
        val session = FakeSession(formal = false)
        val measures = FakeMeasureRepo()
        val chain = buildChain(this, session = session, measures = measures)
        advanceUntilIdle()   // 等待订阅者就绪


        // 游客写入
        val record = MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175)
        measures.save(record, "1")
        chain.bus.notifyChanged(DataChange.Measurements)

        advanceUntilIdle()
        chain.job.cancel()

        // 断言：无任何 upsert
        assertThat(chain.cloud.upserts).isEmpty()
    }

    // ---- 场景 4: 失败重试 ----

    @Test
    fun `全链路_upsert失败_watermark不更新_下次可重推`() = runTest {
        val measures = FakeMeasureRepo()
        val cloud = FakeCloud().apply { upsertOk = false }
        val syncState = InMemorySyncStateRepositoryFixture()
        val chain = buildChain(this, cloud = cloud, measures = measures, syncState = syncState)
        advanceUntilIdle()   // 等待订阅者就绪


        // 写入 + 事件
        val record = MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175)
        measures.save(record, "1")
        chain.bus.notifyChanged(DataChange.Measurements)

        advanceUntilIdle()

        // 断言：watermark 未更新（因为 upsert 失败）
        val (pushed, _) = syncState.get("measurements", "1")
        assertThat(pushed).isEqualTo(0L)

        // 修复网络，再次触发
        cloud.upsertOk = true
        chain.bus.notifyChanged(DataChange.Measurements)
        advanceUntilIdle()
        chain.job.cancel()

        // 断言：数据重推成功，watermark 更新
        val measurementUpsert = cloud.upserts.first { it.first == "measurements" }
        assertThat(measurementUpsert.third).hasSize(1)
        val (pushedAfter, _) = syncState.get("measurements", "1")
        assertThat(pushedAfter).isEqualTo(1000L)
    }

    // ---- 场景 5: 幂等 ----

    @Test
    fun `全链路_重复触发幂等_无新行时不再upsert`() = runTest {
        val measures = FakeMeasureRepo()
        val chain = buildChain(this, measures = measures)
        advanceUntilIdle()   // 等待订阅者就绪


        // 第一次写入 + 事件
        val record = MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175)
        measures.save(record, "1")
        chain.bus.notifyChanged(DataChange.Measurements)
        advanceUntilIdle()

        // 记录第一次 upsert 数量
        val upsertCountBefore = chain.cloud.upserts.size

        // 再次触发（无新数据）
        chain.bus.notifyChanged(DataChange.Measurements)
        advanceUntilIdle()
        chain.job.cancel()

        // 断言：无新的 measurements upsert
        val measurementUpsertsAfter = chain.cloud.upserts.count { it.first == "measurements" }
        assertThat(measurementUpsertsAfter).isEqualTo(1)   // 只有一次
    }

    // ---- 场景 6: 增量推送 ----

    @Test
    fun `全链路_增量推送_只推新行`() = runTest {
        val measures = FakeMeasureRepo()
        val chain = buildChain(this, measures = measures)
        advanceUntilIdle()   // 等待订阅者就绪


        // 第一次写入
        val r1 = MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175)
        measures.save(r1, "1")
        chain.bus.notifyChanged(DataChange.Measurements)
        advanceUntilIdle()

        // 第二次写入（新行）
        val r2 = MeasureRecord(2000L, 71.0, 500.0, 18.0, "AA", "okok-bia-1", true, 30, 175)
        measures.save(r2, "1")
        chain.bus.notifyChanged(DataChange.Measurements)
        advanceUntilIdle()
        chain.job.cancel()

        // 断言：第二次 upsert 只包含新行
        val measurementUpserts = chain.cloud.upserts.filter { it.first == "measurements" }
        assertThat(measurementUpserts).hasSize(2)
        assertThat(measurementUpserts[1].third).hasSize(1)
        assertThat(measurementUpserts[1].third[0].fields["measured_at"]).isEqualTo(2000L)
    }

    // ---- 场景 7: 多事件合并 ----

    @Test
    fun `全链路_多事件快速触发_Mutex串行合并`() = runTest {
        val measures = FakeMeasureRepo()
        val chain = buildChain(this, measures = measures)
        advanceUntilIdle()   // 等待订阅者就绪


        // 快速连续写入 + 事件
        val r1 = MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175)
        val r2 = MeasureRecord(2000L, 71.0, 500.0, 18.0, "AA", "okok-bia-1", true, 30, 175)
        val r3 = MeasureRecord(3000L, 72.0, 500.0, 17.5, "AA", "okok-bia-1", true, 30, 175)
        measures.save(r1, "1")
        chain.bus.notifyChanged(DataChange.Measurements)
        measures.save(r2, "1")
        chain.bus.notifyChanged(DataChange.Measurements)
        measures.save(r3, "1")
        chain.bus.notifyChanged(DataChange.Measurements)

        advanceUntilIdle()
        chain.job.cancel()

        // 断言：最终 watermark 是 3000（所有行已推）
        val (pushed, _) = chain.syncState.get("measurements", "1")
        assertThat(pushed).isEqualTo(3000L)
    }
}
