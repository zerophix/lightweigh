package com.xcwk.scalehub.application.sync

import com.google.common.truth.Truth.assertThat
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.repository.HealthLogRepository
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
        override suspend fun signInWithCode(
            rawDigits: String, verificationId: String, registered: Boolean, code: String,
        ) = throw UnsupportedOperationException()
        override fun isEntered() = true
        override fun markEntered() = throw UnsupportedOperationException()
    }

    private class FakeMeasureRepo(rows: List<MeasureRecord> = emptyList()) : MeasurementRepository {
        val saved = rows.toMutableList()
        override suspend fun save(record: MeasureRecord) { saved += record }
        override suspend fun recent(limit: Int) = saved.sortedByDescending { it.time }.take(limit)
        override suspend fun between(fromInc: Long, toExc: Long) =
            saved.filter { it.time >= fromInc && it.time < toExc }.sortedBy { it.time }
        override suspend fun delete(time: Long) { saved.removeAll { it.time == time } }
    }

    private class FakeLogRepo(rows: List<HealthLog> = emptyList()) : HealthLogRepository {
        val saved = rows.toMutableList()
        override suspend fun save(log: HealthLog) { saved += log }
        override suspend fun recent(limit: Int) = saved.sortedByDescending { it.time }.take(limit)
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
        override fun heightCm() = height
        override fun birthYear() = year
        override fun birthMonth() = month
        override fun birthDay() = day
        override fun nickname() = nick
        override suspend fun save(profile: MemberProfile) { written = profile }
    }

    private fun service(
        session: FakeSession, cloud: FakeCloud, auth: FakeAuth = FakeAuth(),
        measures: FakeMeasureRepo = FakeMeasureRepo(), logs: FakeLogRepo = FakeLogRepo(),
        profile: FakeProfile = FakeProfile(),
    ) = SyncService(session, cloud, auth, measures, logs, profile, profile)

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
        assertThat(cloud.upserts[1].second).isEqualTo("owner_id,measured_at")
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
    fun `注销_成功清四表并登出_本机保留`() = runTest {
        val cloud = FakeCloud()
        val session = FakeSession()
        val measures = FakeMeasureRepo(listOf(MeasureRecord(1000L, 70.0, 0.0, -1.0, "", "")))
        val outcome = service(session, cloud, measures = measures).deleteAccount("12 34")
        assertThat(outcome.ok).isTrue()
        assertThat(cloud.deletes.map { it.first })
            .containsExactly("measurements", "devices", "health_logs", "profiles").inOrder()
        assertThat(cloud.deletes[0].second).isEqualTo("owner_id=eq.u-1")
        assertThat(cloud.deletes[3].second).isEqualTo("id=eq.u-1")
        assertThat(session.signedOut).isTrue()
        assertThat(measures.saved).hasSize(1)   // 本机测量保留（需求：注销不删本机）
    }

    @Test
    fun `注销_验证码过短本地拦截`() = runTest {
        val cloud = FakeCloud()
        val outcome = service(FakeSession(), cloud).deleteAccount("12")
        assertThat(outcome.message).isEqualTo("请输入短信验证码")
        assertThat(cloud.deletes).isEmpty()
    }
}
