package com.xcwk.scalehub.infrastructure.member

import android.content.Context
import com.xcwk.scalehub.domain.model.LengthUnit
import com.xcwk.scalehub.domain.model.Member
import com.xcwk.scalehub.domain.model.MemberProfile
import com.xcwk.scalehub.domain.model.UserUnitPreferences
import com.xcwk.scalehub.domain.model.WeightUnit
import com.xcwk.scalehub.domain.repository.MemberRepository
import com.xcwk.scalehub.infrastructure.persistent.AppDatabase
import com.xcwk.scalehub.infrastructure.persistent.po.MemberPo
import com.xcwk.scalehub.infrastructure.profile.LegacyMemberProfile
import com.xcwk.scalehub.infrastructure.profile.ProfileStore

/**
 * 本机测量用户。权威是 Room `members`（DEC-S04）。
 * 打开时若表空，从 scalehub_members JSON 导入；写入仍镜像一份 JSON（过渡一版）。
 * 档案与单位委托 ProfileStore（DEC-S08 / S09），不再双写 male_/unit_ 键。
 * 删除成员不物理删除测量/日志/习惯，也不清档案键（孤儿可留，除非清本机/注销）。
 */
class MemberStore(
    context: Context,
    database: AppDatabase,
) : MemberRepository {

    private val prefs = context.applicationContext.getSharedPreferences("scalehub_members", Context.MODE_PRIVATE)
    private val dao = database.memberDao()
    private var profileAuthority: ProfileStore? = null

    init {
        if (dao.count() == 0) {
            val parsed = MemberCodec.parse(prefs.getString(KEY_MEMBERS, null))
            val list = parsed.ifEmpty { listOf(Member("1", 1, "我")) }
            val active = prefs.getString(KEY_ACTIVE, null)?.takeIf { id -> list.any { it.id == id } }
                ?: list.first().id
            val now = System.currentTimeMillis()
            list.forEach { member ->
                dao.upsert(
                    MemberPo(
                        id = member.id,
                        slot = member.slot,
                        nickname = member.nickname,
                        createdAt = now,
                        active = member.id == active,
                        updatedAt = now,
                    ),
                )
            }
            mirror(list, active)
        }
    }

    /** 绑定档案权威并一次性导入旧键。重复绑定同一实例无操作。 */
    fun bindProfileAuthority(store: ProfileStore) {
        if (profileAuthority === store) return
        profileAuthority = store
        members().forEach { member -> store.importLegacyIfAbsent(member.id, legacySnapshot(member.id)) }
    }

    override fun members(): List<Member> {
        val list = dao.all().map { Member(it.id, it.slot, it.nickname) }
        if (list.isEmpty()) return listOf(Member("1", 1, "我"))
        return list
    }

    override fun activeId(): String {
        val rows = dao.all()
        return rows.firstOrNull { it.active }?.id ?: rows.firstOrNull()?.id ?: "1"
    }

    override fun activeSlot(): Int {
        val id = activeId()
        return members().firstOrNull { it.id == id }?.slot ?: 1
    }

    override fun add(nickname: String): Member? {
        val list = members().toMutableList()
        if (list.size >= Member.MAX) return null
        val id = Member.nextId(list)
        val member = Member(id, Member.nextSlot(list), Member.normalizeNickname(nickname, list.size + 1))
        val now = System.currentTimeMillis()
        dao.upsert(MemberPo(member.id, member.slot, member.nickname, now, true, now))
        dao.setActive(member.id, now)
        mirror(members(), member.id)
        return member
    }

    override fun switch(id: String) {
        if (members().none { it.id == id }) return
        val now = System.currentTimeMillis()
        dao.setActive(id, now)
        mirror(members(), id)
    }

    override fun rename(id: String, nickname: String) {
        val row = dao.all().firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        dao.upsert(row.copy(nickname = nickname.trim().take(20), updatedAt = now))
        mirror(members(), activeId())
    }

    override fun remove(id: String): Boolean {
        val list = members().toMutableList()
        if (list.size <= 1 || list.none { it.id == id }) return false
        dao.delete(id)
        val next = if (activeId() == id) members().first().id else activeId()
        dao.setActive(next, System.currentTimeMillis())
        mirror(members(), next)
        return true
    }

    /** 清本机 / 注销：成员表重置为默认「我」（DEC-S05）。不删其他业务表，由调用方硬删。 */
    fun resetToDefault() {
        dao.deleteAll()
        val now = System.currentTimeMillis()
        dao.upsert(MemberPo("1", 1, "我", now, true, now))
        mirror(listOf(Member("1", 1, "我")), "1")
    }

    override fun unitPreferences(): UserUnitPreferences {
        profileAuthority?.let { return it.unitPreferences() }
        return legacyUnits()
    }

    override suspend fun saveUnitPreferences(prefs: UserUnitPreferences) {
        val authority = profileAuthority
        if (authority != null) {
            authority.saveUnitPreferences(prefs)
            return
        }
        val id = activeId()
        this.prefs.edit()
            .putString("unit_weight_$id", prefs.weightUnit.name)
            .putString("unit_length_$id", prefs.lengthUnit.name)
            .putBoolean("unit_apply_derived_$id", prefs.applyToDerived)
            .apply()
    }

    override fun male(): Boolean = profileAuthority?.male() ?: prefs.getBoolean("male_${activeId()}", true)
    override fun ageYears(): Int = profileAuthority?.ageYears() ?: prefs.getInt("age_${activeId()}", 0)
    override fun heightCm(): Int = profileAuthority?.heightCm() ?: prefs.getInt("height_${activeId()}", 0)
    override fun birthYear(): Int = profileAuthority?.birthYear() ?: prefs.getInt("birth_year_${activeId()}", 0)
    override fun birthMonth(): Int = profileAuthority?.birthMonth() ?: prefs.getInt("birth_month_${activeId()}", 0)
    override fun birthDay(): Int = profileAuthority?.birthDay() ?: prefs.getInt("birth_day_${activeId()}", 0)
    override fun nickname(): String =
        profileAuthority?.nickname() ?: (prefs.getString("nickname_${activeId()}", "") ?: "")

    override suspend fun save(profile: MemberProfile) {
        val authority = profileAuthority
        if (authority != null) {
            authority.save(profile)
            return
        }
        val id = activeId()
        prefs.edit()
            .putBoolean("male_$id", profile.male)
            .putInt("age_$id", profile.ageYears())
            .putInt("height_$id", profile.heightCm)
            .putInt("birth_year_$id", profile.birthYear)
            .putInt("birth_month_$id", profile.birthMonth)
            .putInt("birth_day_$id", profile.birthDay)
            .putString("nickname_$id", profile.nickname)
            .apply()
    }

    private fun legacyUnits(): UserUnitPreferences {
        val id = activeId()
        val weightUnitStr = prefs.getString("unit_weight_$id", "KG") ?: "KG"
        val lengthUnitStr = prefs.getString("unit_length_$id", "CM") ?: "CM"
        val applyToDerived = prefs.getBoolean("unit_apply_derived_$id", true)
        return UserUnitPreferences(
            weightUnit = runCatching { WeightUnit.valueOf(weightUnitStr) }.getOrDefault(WeightUnit.KG),
            lengthUnit = runCatching { LengthUnit.valueOf(lengthUnitStr) }.getOrDefault(LengthUnit.CM),
            applyToDerived = applyToDerived,
        )
    }

    private fun legacySnapshot(id: String): LegacyMemberProfile = LegacyMemberProfile(
        male = prefs.getBooleanOrNull("male_$id"),
        heightCm = prefs.getIntOrNull("height_$id"),
        birthYear = prefs.getIntOrNull("birth_year_$id"),
        birthMonth = prefs.getIntOrNull("birth_month_$id"),
        birthDay = prefs.getIntOrNull("birth_day_$id"),
        nickname = if (prefs.contains("nickname_$id")) prefs.getString("nickname_$id", "") else null,
        weightUnit = prefs.getString("unit_weight_$id", null),
        lengthUnit = prefs.getString("unit_length_$id", null),
        applyToDerived = prefs.getBooleanOrNull("unit_apply_derived_$id"),
    )

    private fun mirror(list: List<Member>, activeId: String) {
        prefs.edit()
            .putString(KEY_MEMBERS, MemberCodec.toJson(list))
            .putString(KEY_ACTIVE, activeId)
            .apply()
    }

    companion object {
        private const val KEY_MEMBERS = "members"
        private const val KEY_ACTIVE = "activeMember"
    }
}

private fun android.content.SharedPreferences.getIntOrNull(key: String): Int? =
    if (contains(key)) getInt(key, 0) else null

private fun android.content.SharedPreferences.getBooleanOrNull(key: String): Boolean? =
    if (contains(key)) getBoolean(key, false) else null
