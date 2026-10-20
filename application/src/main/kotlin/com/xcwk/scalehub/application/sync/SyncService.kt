package com.xcwk.scalehub.application.sync

import com.xcwk.scalehub.application.auth.AuthPort
import com.xcwk.scalehub.domain.model.HealthLog
import com.xcwk.scalehub.domain.model.MeasureRecord
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.model.MacFormat
import com.xcwk.scalehub.domain.model.ScaleDevice
import com.xcwk.scalehub.domain.repository.DeviceRepository
import com.xcwk.scalehub.domain.repository.GoalRepository
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MemberProfileWriter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncOutcome(val ok: Boolean, val message: String)

/**
 * application service — 云同步编排（旧库 CloudSync.kt 语义移植，行号随注释）。
 *
 * 口径（需求文档 + 旧库 docstring）：
 *  - 游客只写本机：非正式用户直接 no-op（:31,169）
 *  - 登录后先合并云端进当前测量用户，再上传本机多出来的；日常同步只上传不删除
 *    云端多出的行（:19），注销才删云端（:20）
 *  - 推送顺序：profile → devices(跳过,M3) → measurements → health_logs（:127-130）
 *  - 拉取任一表失败即整体中止（:185-196）；坏行逐条跳过不拖垮整表（optXxx 默认值语义）
 *  - 并发：旧库 single-flight+again 合并语义收敛为 Mutex 串行（合并语义偏差已声明：
 *    重复触发按序执行而非折叠，结果等价、推送次数可能多一）
 *
 * 范围声明（M2a）：measurements/health_logs/profile 三表同步；devices 同步随 M3
 * 设备域落地（本地无设备模型）；列映射按 spec v2（云端 snake ↔ 本地 camelCase，
 * 快照四列随记录上云）。拉取上限沿旧库（measurements 180/health_logs 300，
 * 云端更早记录不自动拉取，见 CloudSync:190-196）。
 */
class SyncService(
    private val session: CloudSessionPort,
    private val cloud: CloudDataPort,
    private val auth: AuthPort,
    private val devices: DeviceRepository,
    private val goals: GoalRepository? = null,
    private val measurements: MeasurementRepository,
    private val healthLogs: HealthLogRepository,
    private val profileReader: MemberProfileReader,
    private val profileWriter: MemberProfileWriter,
    /** 拉回的数据归属哪个成员（云端按 owner 隔离拉取，落库时必须写回同一归属，组 D）。 */
    private val memberId: () -> String,
) {
    private val mutex = Mutex()

    /** 日常推送（只上传本机）。false=未登录/网络失败（吞异常同旧库:54）。 */
    suspend fun push(): Boolean = mutex.withLock {
        if (!session.isFormalAccount()) return@withLock false
        runCatching { pushNow() }.isSuccess
    }

    /** 恢复：拉云端合并本机 → 回推本机增量（旧库 restore:74-93）。 */
    suspend fun restore(): SyncOutcome {
        if (!session.isFormalAccount()) return SyncOutcome(false, "请先使用手机号登录")
        val pulled = runCatching { restoreNow() }.getOrDefault(false)
        if (!pulled) return SyncOutcome(false, "恢复失败，请检查网络后重试")
        runCatching { pushNow() }
        return SyncOutcome(true, "已从云端恢复当前测量用户的记录")
    }

    /** 注销：验码删云端身份 → 清云端四表（devices 项随 M3）→ 登出；本机保留（:263-284）。 */
    suspend fun deleteAccount(code: String): SyncOutcome {
        if (!session.isFormalAccount()) return SyncOutcome(false, "请先使用手机号登录")
        val digits = code.filter { it.isDigit() }
        if (digits.length < 4) return SyncOutcome(false, "请输入短信验证码")
        val token = session.accessToken() ?: return SyncOutcome(false, "登录已失效，请重新登录")
        val userId = session.subject(token) ?: return SyncOutcome(false, "登录已失效，请重新登录")
        val authError = try {
            auth.deleteCurrentUser(digits)
        } catch (e: Exception) {
            "当前网络不可用，请联网后再试"
        }
        if (authError != null) return SyncOutcome(false, authError)
        // 云端清表尽力而为（旧库逐表只记日志:276-281）
        runCatching {
            cloud.delete("measurements", "owner_id=eq.$userId")
            cloud.delete("devices", "owner_id=eq.$userId")
            cloud.delete("health_logs", "owner_id=eq.$userId")
            cloud.delete("profiles", "id=eq.$userId")
        }
        session.signOut()
        return SyncOutcome(true, "账号已注销")
    }

    // ---- 内部编排 ----

    private suspend fun pushNow() {
        val token = session.accessToken() ?: return
        val userId = session.subject(token) ?: return
        upsertProfile(userId)
        syncDevices(userId)
        syncMeasurements(userId)
        syncHealthLogs(userId)
    }

    /** 条件字段随旧库（:292-309）：空/默认值不上传；conflict=id。 */
    private suspend fun upsertProfile(userId: String) {
        val reader = profileReader
        val fields = mutableMapOf<String, Any?>("id" to userId)
        val height = reader.heightCm()
        val birthYear = reader.birthYear()
        if (height > 0 || birthYear > 0) fields["male"] = reader.male()
        if (birthYear > 0) fields["birth_year"] = birthYear
        if (reader.birthMonth() > 0) fields["birth_month"] = reader.birthMonth()
        if (reader.birthDay() > 0) fields["birth_day"] = reader.birthDay()
        if (height > 0) fields["height_cm"] = height
        val nickname = reader.nickname()
        if (nickname.isNotBlank() && nickname != "游客用户") fields["nickname"] = nickname
        // 目标随 profile 推送（旧库 :305-310：非空且非默认值才上传）
        goals?.let { g ->
            if (g.calorieGoal > 0 && g.calorieGoal != 2049) fields["calorie_goal"] = g.calorieGoal
            if (g.waterGoal > 0 && g.waterGoal != 2000) fields["water_goal"] = g.waterGoal
        }
        cloud.upsert("profiles", "id", listOf(CloudRow(fields)))
    }

    /** devices 上云沿旧库五列（:305-325）：协议/算法不随云走。 */
    private suspend fun syncDevices(userId: String) {
        val rows = devices.devices().map { device ->
            CloudRow(
                buildMap {
                    put("owner_id", userId)
                    put("mac", device.mac)
                    put("name", device.name)
                    put("bound_at", device.boundAt)
                    put("active", device.active)
                },
            )
        }
        if (rows.isNotEmpty()) cloud.upsert("devices", "owner_id,mac", rows)
    }

    private suspend fun syncMeasurements(userId: String) {
        val all = measurements.between(0L, Long.MAX_VALUE)
        if (all.isEmpty()) return
        val rows = all.map { r ->
            CloudRow(
                buildMap {
                    put("owner_id", userId)
                    put("measured_at", r.time)
                    put("weight", r.weight)
                    put("impedance", r.impedance)
                    put("fat", r.fat)
                    put("mac", r.mac)
                    r.algorithm.takeIf { it.isNotEmpty() }?.let { put("algorithm_id", it) }
                    r.male?.let { put("male", it) }
                    r.ageYears?.let { put("age_years", it) }
                    r.heightCm?.let { put("height_cm", it) }
                },
            )
        }
        cloud.upsert("measurements", "owner_id,measured_at", rows)
    }

    private suspend fun syncHealthLogs(userId: String) {
        // 沿旧库全量上云（日志量小；旧库 logs() 全量 :355-372）
        val rows = healthLogRowsForPush(userId)
        if (rows.isNotEmpty()) cloud.upsert("health_logs", "owner_id,logged_at,type", rows)
    }

    private suspend fun healthLogRowsForPush(userId: String): List<CloudRow> =
        healthLogs.recent(Int.MAX_VALUE).map { log ->
            CloudRow(
                buildMap {
                    put("owner_id", userId)
                    put("logged_at", log.time)
                    put("type", log.type)
                    put("value", log.value)
                    put("value2", log.value2)
                    put("label", log.label)
                    put("note", log.note)
                },
            )
        }

    /** 拉取合并（旧库 restoreNow:182-260）。 */
    private suspend fun restoreNow(): Boolean {
        val token = session.accessToken() ?: return false
        val userId = session.subject(token) ?: return false
        val profileRows = cloud.rows("profiles", "id=eq.$userId&select=*") ?: return false
        val deviceRows = cloud.rows("devices", "owner_id=eq.$userId&select=*") ?: return false
        val measurementRows = cloud.rows(
            "measurements", "owner_id=eq.$userId&select=*&order=measured_at.desc&limit=180",
        ) ?: return false
        val logRows = cloud.rows(
            "health_logs", "owner_id=eq.$userId&select=*&order=logged_at.desc&limit=300",
        ) ?: return false

        mergeDevices(deviceRows)
        mergeMeasurements(measurementRows)
        mergeHealthLogs(logRows)
        mergeProfile(profileRows.firstOrNull())
        return true
    }

    /**
     * 设备合并沿旧库 mergeDevices（AppStore:614-635）：本机优先；云端多出的补为非当前；
     * 本机为空时云端整集写入。云端行无协议列，恢复设备默认 okok-1（旧库口径）。
     */
    private fun mergeDevices(rows: List<CloudRow>) {
        if (rows.isEmpty()) return
        val cloudList = rows.mapNotNull { row ->
            val mac = MacFormat.normalize(row.fields["mac"] as? String)
            if (mac.isEmpty()) return@mapNotNull null
            val name = (row.fields["name"] as? String)?.ifBlank { "智能体脂秤" } ?: "智能体脂秤"
            val boundAt = row.fields["bound_at"] as? String ?: ""
            val active = when (val value = row.fields["active"]) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                else -> false
            }
            ScaleDevice(name, mac, boundAt, active)
        }
        if (cloudList.isEmpty()) return
        val local = devices.devices()
        if (local.isEmpty()) {
            devices.restoreAll(cloudList)
            return
        }
        val macs = local.map { it.mac }.toMutableSet()
        val added = cloudList.filter { macs.add(it.mac) }.map { it.withActive(false) }
        if (added.isNotEmpty()) devices.restoreAll(local + added)
    }

    private suspend fun mergeMeasurements(rows: List<CloudRow>) {
        val existing = measurements.between(0L, Long.MAX_VALUE).map { it.time }.toSet()
        for (row in rows) {
            val time = (row.fields["measured_at"] as? Number)?.toLong() ?: continue
            if (time <= 0 || time in existing) continue    // 只补本机没有的（不删除云端多出）
            runCatching {
                measurements.save(
                    MeasureRecord(
                        time,
                        (row.fields["weight"] as? Number)?.toDouble() ?: return@runCatching,
                        (row.fields["impedance"] as? Number)?.toDouble() ?: 0.0,
                        (row.fields["fat"] as? Number)?.toDouble() ?: -1.0,
                        row.fields["mac"]?.toString() ?: "",
                        row.fields["algorithm_id"]?.toString() ?: "",
                        row.fields["male"] as? Boolean,
                        (row.fields["age_years"] as? Number)?.toInt(),
                        (row.fields["height_cm"] as? Number)?.toInt(),
                    ),
                    memberId(),
                )
            }   // 坏行跳过（守卫 fail-fast 即过滤器）
        }
    }

    private suspend fun mergeHealthLogs(rows: List<CloudRow>) {
        val existing = healthLogs.recent(Int.MAX_VALUE).map { it.time to it.type }.toSet()
        for (row in rows) {
            val time = (row.fields["logged_at"] as? Number)?.toLong() ?: continue
            val type = row.fields["type"]?.toString().orEmpty()
            if (time <= 0 || type.isEmpty() || (time to type) in existing) continue
            runCatching {
                healthLogs.save(
                    HealthLog(
                        time, type,
                        (row.fields["value"] as? Number)?.toDouble() ?: 0.0,
                        (row.fields["value2"] as? Number)?.toDouble() ?: 0.0,
                        row.fields["label"]?.toString() ?: "",
                        row.fields["note"]?.toString() ?: "",
                    ),
                    memberId(),
                )
            }
        }
    }

    /** 云端档案合并：仅当字段组通过 MemberProfile 守卫才落本地（不完整行跳过）。 */
    private suspend fun mergeProfile(row: CloudRow?) {
        if (row == null) return
        val f = row.fields
        val height = (f["height_cm"] as? Number)?.toInt() ?: 0
        val birthYear = (f["birth_year"] as? Number)?.toInt() ?: 0
        if (height <= 0 && birthYear <= 0) return   // 空档案不覆盖（旧库 emptyBody 护栏:218-224）
        val candidate = MemberProfile(
            male = f["male"] as? Boolean ?: true,
            birthYear = birthYear,
            birthMonth = (f["birth_month"] as? Number)?.toInt() ?: 0,
            birthDay = (f["birth_day"] as? Number)?.toInt() ?: 0,
            heightCm = height,
            nickname = f["nickname"]?.toString() ?: "",
        )
        // 守卫失败（云端半行：月日缺/身高越界等）→ 跳过档案合并，本机为准
        runCatching { profileWriter.save(candidate) }
    }
}
