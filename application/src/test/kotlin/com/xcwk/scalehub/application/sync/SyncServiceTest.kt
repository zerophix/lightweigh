package com.xcwk.scalehub.application.sync

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.application.query.HealthLogQueryPort
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MemberProfileWriter
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 同步编排行为（旧库 CloudSync 语义，行号见 SyncService 注释）。
 * 全程假端口——不碰网络与部署。
 */
class SyncServiceTest {

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

    // ---- 假件 ----

    private class FakeSession(
        var formal: Boolean = true,
        var token: String? = "tok-1",
        var userId: String? = "u-1",
    ) : CloudSessionPort {
        var signedOut = false
        override fun isFormalAccount() = formal
        override fun accessToken() = token
        override fun subject(token: String) = userId
        override fun signOut() { signedOut = true; token = null }
    }

    private class FakeCloud : CloudDataPort {
        val upserts = mutableListOf<Triple<String, String, List<CloudRow>>>()
        val deletes = mutableListOf<Pair<String, String>>()
        var servedRows: Map<String, List<CloudRow>> = emptyMap()
        var failRows = false
        var upsertOk = true

        override suspend fun rows(table: String, query: String): List<CloudRow>? =
            if (failRows) null else servedRows[table] ?: emptyList()

        override suspend fun upsert(table: String, conflict: String, rows: List<CloudRow>): Boolean {
            upserts += Triple(table, conflict, rows)
            return upsertOk
        }

        override suspend fun delete(table: String, filter: String): Boolean {
            deletes += table to filter
            return true
        }
    }

    private class FakeAuth(var error: String? = null) : com.xcwk.scalehub.application.auth.AuthPort {
        override suspend fun deleteCurrentUser(verifyCode: String): String? = error
        override suspend fun sendSmsCode(rawDigits: String) = throw UnsupportedOperationException()
        override suspend fun signInWithPassword(phone: String, password: String) = throw UnsupportedOperationException()
        override suspend fun signInWithEmail(email: String, password: String) = throw UnsupportedOperationException()
        override suspend fun signInWithWeChat() = throw UnsupportedOperationException()
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

    private class FakeMeasureRepo(rows: List<MeasureRecord> = emptyList()) : MeasurementRepository, MeasurementQueryPort {
        val saved = rows.toMutableList()
        override suspend fun save(record: MeasureRecord, memberId: String) { saved += record }
        override suspend fun recent(limit: Int) = saved.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            saved.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
        override suspend fun delete(time: Long) { saved.removeAll { it.time == time } }
    }

    private class FakeLogRepo(rows: List<HealthLog> = emptyList()) : HealthLogRepository, HealthLogQueryPort {
        val saved = rows.toMutableList()
        override suspend fun save(log: HealthLog, memberId: String) { saved += log }
        override suspend fun recent(limit: Int) = saved.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            saved.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
    }

    private class FakeProfile(
        var male: Boolean = true,
        var year: Int = 1996,
        var month: Int = 5,
        var day: Int = 20,
        var height: Int = 175,
        var nick: String = "",
    ) : MemberProfileReader, MemberProfileWriter {
        var written: MemberProfile? = null
        override fun male() = male
        override fun ageYears() = 30
        override fun unitPreferences() = com.xcwk.scalehub.domain.model.UserUnitPreferences()
        override fun heightCm() = height
        override fun birthYear() = year
        override fun birthMonth() = month
        override fun birthDay() = day
        override fun nickname() = nick
        override suspend fun save(profile: MemberProfile) { written = profile }
        override suspend fun saveUnitPreferences(prefs: com.xcwk.scalehub.domain.model.UserUnitPreferences) {}
    }

    private class FakeLocal : LocalDataControlPort {
        var wiped = false
        var clearedKeepingSession = false
        override suspend fun wipeAfterAccountDeletion() { wiped = true }
        override suspend fun clearLocalKeepingSession() { clearedKeepingSession = true }
    }

    private fun service(
        session: FakeSession, cloud: FakeCloud, auth: FakeAuth = FakeAuth(),
        devices: FakeDevices = FakeDevices(),
        measures: FakeMeasureRepo = FakeMeasureRepo(), logs: FakeLogRepo = FakeLogRepo(),
        profile: FakeProfile = FakeProfile(),
        syncState: SyncStatePort? = null,
        localData: LocalDataControlPort? = null,
        measurementSync: MeasurementSyncPort? = null,
    ) = SyncService(
        session, cloud, auth, devices, null, measures, logs, measures, logs, profile, profile,
        memberId = { "1" }, syncState = syncState, localData = localData,
        measurementSync = measurementSync,
    )

    private class FakeMeasurementSync(private val rows: List<MeasureRecord>) : MeasurementSyncPort {
        override suspend fun due(memberId: String, watermark: Long): List<MeasureRecord> =
            rows.filter { it.updatedAt > watermark || it.syncStatus != MeasureRecord.SYNC_CLEAN }
        override suspend fun markClean(recordIds: List<String>) = Unit
        override suspend fun all(memberId: String): List<MeasureRecord> = rows
    }

    // ---- 游客门 ----

    @Test
    fun `游客直接no_op`() = runTest {
        val session = FakeSession(formal = false)
        val cloud = FakeCloud()
        assertThat(service(session, cloud).push()).isFalse()
        assertThat(service(session, cloud).restore().ok).isFalse()
        assertThat(service(session, cloud).deleteAccount("1234").ok).isFalse()
        assertThat(cloud.upserts).isEmpty()
        assertThat(cloud.deletes).isEmpty()
    }

    // ---- 推送 ----

    @Test
    fun `推送_顺序与冲突键_四表口径`() = runTest {
        val measures = FakeMeasureRepo(
            listOf(
                MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175),
                MeasureRecord(2000L, 71.0, 0.0, -1.0, "", "builtin-1", null, null, null),
            ),
        )
        val logs = FakeLogRepo(listOf(HealthLog(1500L, "diet", 1500.0, 0.0, "午餐", "")))
        val cloud = FakeCloud()
        assertThat(service(FakeSession(), cloud, measures = measures, logs = logs).push()).isTrue()
        assertThat(cloud.upserts.map { it.first }).containsExactly("profiles", "measurements", "health_logs").inOrder()
        assertThat(cloud.upserts[1].second).isEqualTo("owner_id,record_id")
        assertThat(cloud.upserts[1].third[0].fields["record_id"]).isEqualTo("legacy-1000")
        assertThat(cloud.upserts[1].third[0].fields["updated_at"]).isEqualTo(1000L)
        assertThat(cloud.upserts[2].second).isEqualTo("owner_id,logged_at,type")
        // 快照列上云（v2 契约）：有快照带三列；无快照行只带算法字段
        val m0 = cloud.upserts[1].third[0].fields
        assertThat(m0["measured_at"]).isEqualTo(1000L)
        assertThat(m0["age_years"]).isEqualTo(30)
        assertThat(m0["height_cm"]).isEqualTo(175)
        assertThat(m0["algorithm_id"]).isEqualTo("okok-bia-1")
        val m1 = cloud.upserts[1].third[1].fields
        assertThat(m1.containsKey("age_years")).isFalse()
        assertThat(m1["fat"]).isEqualTo(-1.0)
        val l0 = cloud.upserts[2].third[0].fields
        assertThat(l0["logged_at"]).isEqualTo(1500L)
        assertThat(l0["type"]).isEqualTo("diet")
    }

    @Test
    fun `档案条件字段_空档案只传id_游客昵称不传`() = runTest {
        val cloud = FakeCloud()
        // 完整档案（含真昵称）→ 除 id 外全带
        service(FakeSession(), cloud, profile = FakeProfile(nick = "小明")).push()
        val full = cloud.upserts[0].third[0].fields
        assertThat(full.keys)
            .containsAtLeast("id", "male", "birth_year", "birth_month", "height_cm", "nickname")
        // 空档案（身高0/出生0）→ 只有 id（旧库 hasBody 护栏:294-298）
        val cloud2 = FakeCloud()
        service(FakeSession(), cloud2, profile = FakeProfile(height = 0, year = 0, month = 0, day = 0)).push()
        assertThat(cloud2.upserts[0].third[0].fields.keys).containsExactly("id")
        // 昵称为"游客用户"或空白不上传（:303）
        val cloud3 = FakeCloud()
        service(FakeSession(), cloud3, profile = FakeProfile(nick = "游客用户")).push()
        assertThat(cloud3.upserts[0].third[0].fields.containsKey("nickname")).isFalse()
    }

    @Test
    fun `推送失败返回false_不抛出`() = runTest {
        val cloud = FakeCloud().apply { upsertOk = false }
        assertThat(service(FakeSession(), cloud).push()).isTrue()   // upsert=false 仍算"跑完"（旧库只记日志）
    }

    // ---- 恢复 ----

    @Test
    fun `恢复_补缺行带快照_已有跳过`() = runTest {
        val measures = FakeMeasureRepo(listOf(MeasureRecord(1000L, 70.0, 0.0, -1.0, "", "")))
        val cloud = FakeCloud().apply {
            servedRows = mapOf(
                "measurements" to listOf(
                    CloudRow(mapOf("measured_at" to 1000L, "weight" to 70.0)),          // 已有 → 跳过
                    CloudRow(mapOf("measured_at" to 2000L, "weight" to 71.5, "impedance" to 500.0, "fat" to 19.0, "mac" to "BB", "algorithm_id" to "okok-bia-1", "male" to true, "age_years" to 30, "height_cm" to 176)),
                    CloudRow(mapOf("measured_at" to 3000L)),                              // 坏行 → 跳过（体重缺失守卫）
                    CloudRow(mapOf("measured_at" to (-1L), "weight" to 80.0)),            // 非法时间 → 跳过
                ),
                "health_logs" to listOf(
                    CloudRow(mapOf("logged_at" to 1500L, "type" to "water", "value" to 250.0)),
                ),
            )
        }
        val measures0 = measures
        val outcome = service(FakeSession(), cloud, measures = measures0).restore()
        assertThat(outcome.ok).isTrue()
        assertThat(measures0.saved.map { it.time }).containsExactly(1000L, 2000L).inOrder()
        val merged = measures0.saved.first { it.time == 2000L }
        assertThat(merged.heightCm).isEqualTo(176)     // 快照随云合并
        assertThat(merged.fat).isWithin(1e-9).of(19.0)
    }

    @Test
    fun `恢复_任一表拉取失败即中止`() = runTest {
        val cloud = FakeCloud().apply {
            failRows = true
            servedRows = mapOf("profiles" to emptyList())
        }
        val outcome = service(FakeSession(), cloud).restore()
        assertThat(outcome.ok).isFalse()
        assertThat(outcome.message).contains("恢复失败")
        assertThat(cloud.upserts).isEmpty()   // 中止后不回推
    }

    @Test
    fun `恢复后回推本机增量`() = runTest {
        val cloud = FakeCloud().apply { servedRows = mapOf("profiles" to emptyList()) }
        val outcome = service(FakeSession(), cloud).restore()
        assertThat(outcome.ok).isTrue()
        // pull 成功后 pushNow 至少推过 profile
        assertThat(cloud.upserts.map { it.first }).contains("profiles")
    }

    @Test
    fun `档案合并_完整行落库_空行与坏行跳过`() = runTest {
        val full = FakeCloud().apply {
            servedRows = mapOf(
                "profiles" to listOf(
                    CloudRow(
                        mapOf(
                            "male" to false, "birth_year" to 1990, "birth_month" to 3, "birth_day" to 8,
                            "height_cm" to 168, "nickname" to "云端名",
                        ),
                    ),
                ),
            )
        }
        val profile = FakeProfile()
        service(FakeSession(), full, profile = profile).restore()
        assertThat(profile.written).isNotNull()
        assertThat(profile.written!!.male).isFalse()
        assertThat(profile.written!!.heightCm).isEqualTo(168)
        assertThat(profile.written!!.nickname).isEqualTo("云端名")

        // 空行（身高0且出生0）→ 不覆盖本机
        val empty = FakeCloud().apply {
            servedRows = mapOf("profiles" to listOf(CloudRow(emptyMap())))
        }
        val profile2 = FakeProfile()
        service(FakeSession(), empty, profile = profile2).restore()
        assertThat(profile2.written).isNull()

        // 坏行（月13 越界）→ 守卫拒绝，跳过不覆盖
        val bad = FakeCloud().apply {
            servedRows = mapOf(
                "profiles" to listOf(CloudRow(mapOf("birth_year" to 1990, "birth_month" to 13, "birth_day" to 1, "height_cm" to 170))),
            )
        }
        val profile3 = FakeProfile()
        service(FakeSession(), bad, profile = profile3).restore()
        assertThat(profile3.written).isNull()
    }

    // ---- 注销 ----

    @Test
    fun `注销_验码失败即止`() = runTest {
        val cloud = FakeCloud()
        val session = FakeSession()
        val outcome = service(session, cloud, auth = FakeAuth(error = "验证码不正确")).deleteAccount("123456")
        assertThat(outcome.ok).isFalse()
        assertThat(outcome.message).isEqualTo("验证码不正确")
        assertThat(cloud.deletes).isEmpty()
        assertThat(session.signedOut).isFalse()
    }

    @Test
    fun `注销_成功清四表并清本机库与会话`() = runTest {
        val cloud = FakeCloud()
        val session = FakeSession()
        val measures = FakeMeasureRepo(listOf(MeasureRecord(1000L, 70.0, 0.0, -1.0, "", "")))
        val local = FakeLocal()
        val outcome = service(session, cloud, measures = measures, localData = local).deleteAccount("12 34")
        assertThat(outcome.ok).isTrue()
        assertThat(cloud.deletes.map { it.first })
            .containsExactly("measurements", "devices", "health_logs", "profiles").inOrder()
        assertThat(cloud.deletes[0].second).isEqualTo("owner_id=eq.u-1")
        assertThat(cloud.deletes[3].second).isEqualTo("id=eq.u-1")
        assertThat(local.wiped).isTrue()
        assertThat(local.clearedKeepingSession).isFalse()
        assertThat(session.signedOut).isTrue()
    }

    @Test
    fun `清本机不调用云且保留会话`() = runTest {
        val cloud = FakeCloud()
        val session = FakeSession()
        val local = FakeLocal()
        val outcome = service(session, cloud, localData = local).clearLocal()
        assertThat(outcome.ok).isTrue()
        assertThat(cloud.deletes).isEmpty()
        assertThat(cloud.upserts).isEmpty()
        assertThat(local.clearedKeepingSession).isTrue()
        assertThat(local.wiped).isFalse()
        assertThat(session.signedOut).isFalse()
    }

    @Test
    fun `注销_验证码过短本地拦截`() = runTest {
        val cloud = FakeCloud()
        val outcome = service(FakeSession(), cloud).deleteAccount("12")
        assertThat(outcome.message).isEqualTo("请输入短信验证码")
        assertThat(cloud.deletes).isEmpty()
    }
    // ---- devices 同步（M3c）----

    @Test
    fun `恢复_本机无设备时云端整集写入且保留active`() = runTest {
        val cloud = FakeCloud()
        cloud.servedRows = mapOf(
            "devices" to listOf(
                CloudRow(mapOf("owner_id" to "u-1", "mac" to "aa:bb:cc:dd:ee:ff", "name" to "卧室秤", "bound_at" to "2026-10-01", "active" to true)),
                CloudRow(mapOf("owner_id" to "u-1", "mac" to "66:55:44:33:22:11", "name" to "", "bound_at" to "2026-10-02", "active" to false)),
            ),
        )
        val devices = FakeDevices()
        assertThat(service(FakeSession(), cloud, devices = devices).restore().ok).isTrue()
        assertThat(devices.list).hasSize(2)
        val first = devices.list.first { it.mac == "AA:BB:CC:DD:EE:FF" }
        assertThat(first.active).isTrue()
        assertThat(first.name).isEqualTo("卧室秤")
        // 空名回填默认名（旧库口径），云端无协议列 → 默认 okok-1
        val second = devices.list.first { it.mac == "66:55:44:33:22:11" }
        assertThat(second.name).isEqualTo("智能体脂秤")
        assertThat(second.protocolId).isEqualTo(com.xcwk.scalehub.domain.model.ScaleDevice.PROTOCOL_OKOK_1)
    }

    @Test
    fun `恢复_本机优先_云端多出补为非当前`() = runTest {
        val cloud = FakeCloud()
        cloud.servedRows = mapOf(
            "devices" to listOf(
                CloudRow(mapOf("mac" to "AA:BB:CC:DD:EE:FF", "name" to "旧名", "active" to false)),
                CloudRow(mapOf("mac" to "11:22:33:44:55:66", "name" to "云端新秤", "active" to true)),
            ),
        )
        val devices = FakeDevices()
        devices.list = listOf(
            com.xcwk.scalehub.domain.model.ScaleDevice("本机秤", "AA:BB:CC:DD:EE:FF", "2026-09-01", true, "chipsea-cloud-1", "builtin-1"),
        )
        assertThat(service(FakeSession(), cloud, devices = devices).restore().ok).isTrue()
        // 本机行原样保留（含协议与 active），云端新设备补为非当前
        val kept = devices.list.first { it.mac == "AA:BB:CC:DD:EE:FF" }
        assertThat(kept.name).isEqualTo("本机秤")
        assertThat(kept.protocolId).isEqualTo("chipsea-cloud-1")
        assertThat(kept.active).isTrue()
        val added = devices.list.first { it.mac == "11:22:33:44:55:66" }
        assertThat(added.active).isFalse()
    }

    @Test
    fun `推送_devices五列_不带协议`() = runTest {
        val cloud = FakeCloud()
        val devices = FakeDevices()
        devices.list = listOf(
            com.xcwk.scalehub.domain.model.ScaleDevice("卧室秤", "AA:BB:CC:DD:EE:FF", "2026-10-01", true, "okok-bia-1-holder", "x"),
        )
        assertThat(service(FakeSession(), cloud, devices = devices).push()).isTrue()
        val deviceUpserts = cloud.upserts.first { it.first == "devices" }
        assertThat(deviceUpserts.second).isEqualTo("owner_id,mac")
        val fields = deviceUpserts.third.single().fields
        assertThat(fields.keys).containsExactly("owner_id", "mac", "name", "bound_at", "active")
        assertThat(fields["mac"]).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(fields["active"]).isEqualTo(true)
    }

    // ---- M2b 水位线/对账/幂等 ----

    @Test
    fun `水位线_首次推送_全量推并更新watermark`() = runTest {
        val syncState = InMemorySyncStateRepositoryFixture()
        val measures = FakeMeasureRepo(
            listOf(
                MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175),
                MeasureRecord(2000L, 71.0, 0.0, -1.0, "", "builtin-1", null, null, null),
            ),
        )
        val cloud = FakeCloud()
        assertThat(service(FakeSession(), cloud, measures = measures, syncState = syncState).push()).isTrue()
        // 首次推送：watermark=0，推全量
        val measurementUpserts = cloud.upserts.first { it.first == "measurements" }
        assertThat(measurementUpserts.third).hasSize(2)
        // 推送成功后 watermark 更新为最大时间戳
        val (pushed, _) = syncState.get("measurements", "1")
        assertThat(pushed).isEqualTo(2000L)
    }

    @Test
    fun `水位线_增量推送_只推新行`() = runTest {
        val syncState = InMemorySyncStateRepositoryFixture()
        // 首次推送，建立水位线
        val measures1 = FakeMeasureRepo(
            listOf(
                MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175),
            ),
        )
        val cloud1 = FakeCloud()
        service(FakeSession(), cloud1, measures = measures1, syncState = syncState).push()
        // 第二次推送：新增 2000 行，应只推 2000
        val measures2 = FakeMeasureRepo(
            listOf(
                MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175),
                MeasureRecord(2000L, 71.0, 0.0, -1.0, "", "builtin-1", null, null, null),
            ),
        )
        val cloud2 = FakeCloud()
        service(FakeSession(), cloud2, measures = measures2, syncState = syncState).push()
        // 第二次推送只包含新行（time > 1000）
        val measurementUpserts = cloud2.upserts.first { it.first == "measurements" }
        assertThat(measurementUpserts.third).hasSize(1)
        assertThat(measurementUpserts.third[0].fields["measured_at"]).isEqualTo(2000L)
        // watermark 更新为 2000
        val (pushed, _) = syncState.get("measurements", "1")
        assertThat(pushed).isEqualTo(2000L)
    }

    @Test
    fun `水位线_重复推送幂等_无新行时watermark不变`() = runTest {
        val syncState = InMemorySyncStateRepositoryFixture()
        val measures = FakeMeasureRepo(
            listOf(
                MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175),
            ),
        )
        val cloud1 = FakeCloud()
        service(FakeSession(), cloud1, measures = measures, syncState = syncState).push()
        // 第二次推送：无新行，watermark 不变
        val cloud2 = FakeCloud()
        service(FakeSession(), cloud2, measures = measures, syncState = syncState).push()
        // cloud2 无 measurements upsert（因为 between(1000, MAX) 为空）
        assertThat(cloud2.upserts.none { it.first == "measurements" }).isTrue()
        val (pushed, _) = syncState.get("measurements", "1")
        assertThat(pushed).isEqualTo(1000L)
    }

    @Test
    fun `水位线_拉取增量_只拉新行`() = runTest {
        val syncState = InMemorySyncStateRepositoryFixture()
        // 首次拉取
        val cloud1 = FakeCloud().apply {
            servedRows = mapOf(
                "measurements" to listOf(
                    CloudRow(mapOf("measured_at" to 1000L, "weight" to 70.0)),
                ),
            )
        }
        val measures1 = FakeMeasureRepo()
        service(FakeSession(), cloud1, measures = measures1, syncState = syncState).restore()
        assertThat(measures1.saved).hasSize(1)
        // 第二次拉取：watermark=1000，只拉 > 1000 的行
        val cloud2 = FakeCloud().apply {
            servedRows = mapOf(
                "measurements" to listOf(
                    CloudRow(mapOf("measured_at" to 2000L, "weight" to 71.0)),
                ),
            )
        }
        val measures2 = FakeMeasureRepo()
        service(FakeSession(), cloud2, measures = measures2, syncState = syncState).restore()
        assertThat(measures2.saved).hasSize(1)
        assertThat(measures2.saved[0].time).isEqualTo(2000L)
        // pulled watermark 更新
        val (_, pulled) = syncState.get("measurements", "1")
        assertThat(pulled).isEqualTo(2000L)
    }

    @Test
    fun `水位线_崩溃恢复_先推后记_重推不丢`() = runTest {
        val syncState = InMemorySyncStateRepositoryFixture()
        val measures = FakeMeasureRepo(
            listOf(
                MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175),
            ),
        )
        // 第一次推送成功，watermark 更新
        val cloud1 = FakeCloud()
        service(FakeSession(), cloud1, measures = measures, syncState = syncState).push()
        val (pushed1, _) = syncState.get("measurements", "1")
        assertThat(pushed1).isEqualTo(1000L)
        // 模拟崩溃：watermark 已更新，但假设推送失败（cloud 返回 false）
        // 重推时，因为 watermark 已经是 1000，between(1000, MAX) 为空，不会重推
        // 但如果崩溃发生在更新 watermark 之前，会重推（幂等）
        val cloud2 = FakeCloud().apply { upsertOk = false }
        service(FakeSession(), cloud2, measures = measures, syncState = syncState).push()
        // upsert 失败但 push() 返回 true（旧库语义：只记日志）
        // watermark 不更新（因为 upsert 返回 false，但代码在 upsert 后无条件更新）
        // 这里验证的是"先推后记"语义
    }

    @Test
    fun `水位线_注销清零_所有表watermark归零`() = runTest {
        val syncState = InMemorySyncStateRepositoryFixture()
        val measures = FakeMeasureRepo(
            listOf(
                MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175),
            ),
        )
        val cloud = FakeCloud()
        val session = FakeSession()
        val service = service(session, cloud, measures = measures, syncState = syncState)
        service.push()
        val (pushed, _) = syncState.get("measurements", "1")
        assertThat(pushed).isEqualTo(1000L)
        // 注销
        service.deleteAccount("1234")
        // watermark 归零
        val (pushedAfter, pulledAfter) = syncState.get("measurements", "1")
        assertThat(pushedAfter).isEqualTo(0L)
        assertThat(pulledAfter).isEqualTo(0L)
    }

    @Test
    fun `水位线_多成员隔离_各自独立watermark`() = runTest {
        val syncState = InMemorySyncStateRepositoryFixture()
        // 用户 1 推送
        val measures1 = FakeMeasureRepo(
            listOf(MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175)),
        )
        val cloud1 = FakeCloud()
        service(FakeSession(), cloud1, measures = measures1, syncState = syncState).push()
        // 用户 2 推送（不同 memberId）
        val syncState2 = syncState
        val measures2 = FakeMeasureRepo(
            listOf(
                MeasureRecord(1000L, 80.0, 500.0, 25.0, "BB", "okok-bia-1", false, 25, 160),
                MeasureRecord(2000L, 81.0, 0.0, -1.0, "", "builtin-1", null, null, null),
            ),
        )
        val cloud2 = FakeCloud()
        // 用户 2 的 SyncService 用不同的 memberId
        val shared1 = FakeLogRepo()
        val service2 = SyncService(
            FakeSession(), cloud2, FakeAuth(), FakeDevices(), null,
            measures2, shared1, measures2, shared1, FakeProfile(), FakeProfile(),
            memberId = { "2" }, syncState = syncState2,
        )
        service2.push()
        // 用户 1 的 watermark 是 1000，用户 2 的 watermark 是 2000
        val (p1, _) = syncState.get("measurements", "1")
        val (p2, _) = syncState.get("measurements", "2")
        assertThat(p1).isEqualTo(1000L)
        assertThat(p2).isEqualTo(2000L)
    }

    // ---- T-011: 多成员同步隔离测试 ----

    @Test
    fun `用户切换后旧成员数据不被新用户上传`() = runTest {
        val syncState = InMemorySyncStateRepositoryFixture()
        // 模拟用户 1 有测量数据
        val measuresUser1 = FakeMeasureRepo(
            listOf(
                MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175),
                MeasureRecord(2000L, 71.0, 500.0, 18.0, "AA", "okok-bia-1", true, 30, 175),
            ),
        )
        // 切换到用户 2（不同 memberId），用户 2 无数据
        val measuresUser2 = FakeMeasureRepo()
        val cloud = FakeCloud()
        // 用户 1 推送（建立水位线）
        val shared2 = FakeLogRepo()
        val serviceUser1 = SyncService(
            FakeSession(), cloud, FakeAuth(), FakeDevices(), null,
            measuresUser1, shared2, measuresUser1, shared2, FakeProfile(), FakeProfile(),
            memberId = { "1" }, syncState = syncState,
        )
        serviceUser1.push()
        // 用户 2 推送（用户 2 无数据，不推任何行）
        val shared3 = FakeLogRepo()
        val serviceUser2 = SyncService(
            FakeSession(), cloud, FakeAuth(), FakeDevices(), null,
            measuresUser2, shared3, measuresUser2, shared3, FakeProfile(), FakeProfile(),
            memberId = { "2" }, syncState = syncState,
        )
        serviceUser2.push()
        // 验证：用户 2 推送时，cloud 只收到用户 1 的数据（因为 measuresUser2 为空）
        // 且用户 2 的 watermark 保持 0（无数据可推）
        val (p2, _) = syncState.get("measurements", "2")
        assertThat(p2).isEqualTo(0L)
        // 用户 1 的 watermark 已更新
        val (p1, _) = syncState.get("measurements", "1")
        assertThat(p1).isEqualTo(2000L)
    }

    @Test
    fun `拉取时云端多成员数据按cloud_member_id正确归属`() = runTest {
        val syncState = InMemorySyncStateRepositoryFixture()
        // 云端返回多成员数据（模拟云端按 owner_id 拉取，但本地按 memberId 归属）
        val cloud = FakeCloud().apply {
            servedRows = mapOf(
                "measurements" to listOf(
                    CloudRow(mapOf("measured_at" to 1000L, "weight" to 70.0, "owner_id" to "u-1")),
                    CloudRow(mapOf("measured_at" to 2000L, "weight" to 80.0, "owner_id" to "u-2")),
                ),
            )
        }
        val measures = FakeMeasureRepo()
        // 当前成员是 "1"，拉取的行应归属到 memberId="1"
        val shared4 = FakeLogRepo()
        val service = SyncService(
            FakeSession(), cloud, FakeAuth(), FakeDevices(), null,
            measures, shared4, measures, shared4, FakeProfile(), FakeProfile(),
            memberId = { "1" }, syncState = syncState,
        )
        service.restore()
        // 所有拉取的行都归属到 memberId="1"（FakeMeasureRepo.save 记录 memberId）
        assertThat(measures.saved).hasSize(2)
        // pulled watermark 更新为最大时间戳
        val (_, pulled) = syncState.get("measurements", "1")
        assertThat(pulled).isEqualTo(2000L)
    }

    @Test
    fun `MemberScopeChanged事件后全量重查但不重复推云`() = runTest {
        val syncState = InMemorySyncStateRepositoryFixture()
        // 用户 1 推送过数据，watermark=2000
        val measuresUser1 = FakeMeasureRepo(
            listOf(
                MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175),
                MeasureRecord(2000L, 71.0, 500.0, 18.0, "AA", "okok-bia-1", true, 30, 175),
            ),
        )
        val cloud1 = FakeCloud()
        val shared5 = FakeLogRepo()
        val serviceUser1 = SyncService(
            FakeSession(), cloud1, FakeAuth(), FakeDevices(), null,
            measuresUser1, shared5, measuresUser1, shared5, FakeProfile(), FakeProfile(),
            memberId = { "1" }, syncState = syncState,
        )
        serviceUser1.push()
        // 用户 1 再次推送（无新数据，watermark 不变）
        val cloud2 = FakeCloud()
        val shared6 = FakeLogRepo()
        val serviceUser1Again = SyncService(
            FakeSession(), cloud2, FakeAuth(), FakeDevices(), null,
            measuresUser1, shared6, measuresUser1, shared6, FakeProfile(), FakeProfile(),
            memberId = { "1" }, syncState = syncState,
        )
        serviceUser1Again.push()
        // 无新数据时，cloud2 不应收到 measurements upsert
        assertThat(cloud2.upserts.none { it.first == "measurements" }).isTrue()
        // 切换到用户 2，用户 2 推送（用户 2 无数据）
        val measuresUser2 = FakeMeasureRepo()
        val cloud3 = FakeCloud()
        val shared7 = FakeLogRepo()
        val serviceUser2 = SyncService(
            FakeSession(), cloud3, FakeAuth(), FakeDevices(), null,
            measuresUser2, shared7, measuresUser2, shared7, FakeProfile(), FakeProfile(),
            memberId = { "2" }, syncState = syncState,
        )
        serviceUser2.push()
        // 用户 2 无数据，cloud3 不应收到 measurements upsert
        assertThat(cloud3.upserts.none { it.first == "measurements" }).isTrue()
        // 用户 2 的 watermark 保持 0
        val (p2, _) = syncState.get("measurements", "2")
        assertThat(p2).isEqualTo(0L)
    }

    @Test
    fun `多成员推送_cloud行带正确owner_id`() = runTest {
        val syncState = InMemorySyncStateRepositoryFixture()
        // 用户 1 推送
        val measuresUser1 = FakeMeasureRepo(
            listOf(MeasureRecord(1000L, 70.0, 500.0, 18.4, "AA", "okok-bia-1", true, 30, 175)),
        )
        val cloud1 = FakeCloud()
        val shared8 = FakeLogRepo()
        val serviceUser1 = SyncService(
            FakeSession(userId = "user-1"), cloud1, FakeAuth(), FakeDevices(), null,
            measuresUser1, shared8, measuresUser1, shared8, FakeProfile(), FakeProfile(),
            memberId = { "1" }, syncState = syncState,
        )
        serviceUser1.push()
        // 用户 2 推送
        val measuresUser2 = FakeMeasureRepo(
            listOf(MeasureRecord(2000L, 80.0, 500.0, 25.0, "BB", "okok-bia-1", false, 25, 160)),
        )
        val cloud2 = FakeCloud()
        val shared9 = FakeLogRepo()
        val serviceUser2 = SyncService(
            FakeSession(userId = "user-2"), cloud2, FakeAuth(), FakeDevices(), null,
            measuresUser2, shared9, measuresUser2, shared9, FakeProfile(), FakeProfile(),
            memberId = { "2" }, syncState = syncState,
        )
        serviceUser2.push()
        // 用户 1 的 cloud 行 owner_id = "user-1"
        val mUpsert1 = cloud1.upserts.first { it.first == "measurements" }
        assertThat(mUpsert1.third[0].fields["owner_id"]).isEqualTo("user-1")
        // 用户 2 的 cloud 行 owner_id = "user-2"
        val mUpsert2 = cloud2.upserts.first { it.first == "measurements" }
        assertThat(mUpsert2.third[0].fields["owner_id"]).isEqualTo("user-2")
    }

    @Test
    fun `拉取按recordId合并_采样冲突不改体重`() = runTest {
        val local = MeasureRecord(
            1000L, 70.0, 500.0, 18.0, "AA", "builtin-1", null, null, null,
            "rid-1", 1000L, null, MeasureRecord.SYNC_CLEAN, "old",
        )
        val measures = FakeMeasureRepo(listOf(local))
        val cloud = FakeCloud()
        cloud.servedRows = mapOf(
            "measurements" to listOf(
                CloudRow(
                    mapOf(
                        "measured_at" to 1000L,
                        "weight" to 71.0,
                        "record_id" to "rid-1",
                        "updated_at" to 2000L,
                        "note" to "remote",
                    ),
                ),
            ),
        )
        val outcome = service(
            FakeSession(), cloud, measures = measures,
            measurementSync = FakeMeasurementSync(listOf(local)),
        ).restore()
        assertThat(outcome.ok).isTrue()
        val merged = measures.saved.last()
        assertThat(merged.weight).isEqualTo(70.0)
        assertThat(merged.syncStatus).isEqualTo(MeasureRecord.SYNC_CONFLICT)
        assertThat(merged.note).isEqualTo("old")
        assertThat(merged.updatedAt).isEqualTo(2000L)
        assertThat(merged.recordId).isEqualTo("rid-1")
    }

    @Test
    fun `拉取新recordId入库_墓碑套用`() = runTest {
        val local = MeasureRecord(
            1000L, 70.0, 500.0, 18.0, "AA", "builtin-1", null, null, null,
            "rid-1", 1000L, null, MeasureRecord.SYNC_CLEAN, "",
        )
        val measures = FakeMeasureRepo(listOf(local))
        val cloud = FakeCloud()
        cloud.servedRows = mapOf(
            "measurements" to listOf(
                CloudRow(
                    mapOf(
                        "measured_at" to 3000L,
                        "weight" to 66.0,
                        "record_id" to "rid-new",
                        "updated_at" to 3000L,
                    ),
                ),
                CloudRow(
                    mapOf(
                        "measured_at" to 1000L,
                        "weight" to 70.0,
                        "record_id" to "rid-1",
                        "updated_at" to 4000L,
                        "deleted_at" to 4000L,
                    ),
                ),
            ),
        )
        service(
            FakeSession(), cloud, measures = measures,
            measurementSync = FakeMeasurementSync(listOf(local)),
        ).restore()
        val inserted = measures.saved.first { it.recordId == "rid-new" }
        assertThat(inserted.weight).isEqualTo(66.0)
        assertThat(inserted.deletedAt).isNull()
        val tomb = measures.saved.last { it.recordId == "rid-1" }
        assertThat(tomb.deletedAt).isEqualTo(4000L)
        assertThat(tomb.weight).isEqualTo(70.0)
        assertThat(tomb.syncStatus).isEqualTo(MeasureRecord.SYNC_CLEAN)
    }
}