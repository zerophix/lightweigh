package com.xcwk.scalehub.infrastructure.member

import android.content.Context
import com.xcwk.scalehub.domain.model.Member
import com.xcwk.scalehub.domain.repository.MemberRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * repository 实现 — 本机测量用户（旧库 MemberStore.kt 全量移植，行号随注释）。
 * SP 单键存 JSON 列表 + activeMember；ensure 首次落默认成员（id=1, slot=1）。
 * 偏差声明：删除成员不清理其档案键（旧库逐键 remove；v2 档案键随成员 id 隔离后
 * 属无害孤儿，随下一次同 id 分配复用——id 递增不复用，实际不复现）。
 */
class MemberStore(context: Context) : MemberRepository {

    private val prefs = context.applicationContext.getSharedPreferences("scalehub_members", Context.MODE_PRIVATE)

    override fun members(): List<Member> {
        ensure()
        val raw = prefs.getString(KEY_MEMBERS, "[]") ?: "[]"
        val list = mutableListOf<Member>()
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.optString("id")
            if (id.isBlank()) continue
            list.add(Member(id, item.optInt("slot", i + 1), item.optString("nickname")))
        }
        if (list.isEmpty()) return listOf(Member("1", 1, "我"))
        return list
    }

    override fun activeId(): String {
        ensure()
        return prefs.getString(KEY_ACTIVE, null) ?: members().first().id
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
        list.add(member)
        save(list, id)
        return member
    }

    override fun switch(id: String) {
        if (members().none { it.id == id }) return
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    override fun rename(id: String, nickname: String) {
        val list = members().map { if (it.id == id) it.copy(nickname = nickname.trim().take(20)) else it }
        save(list, activeId())
    }

    override fun remove(id: String): Boolean {
        val list = members().toMutableList()
        if (list.size <= 1 || list.none { it.id == id }) return false
        list.removeAll { it.id == id }
        val next = if (activeId() == id) list.first().id else activeId()
        save(list, next)
        return true
    }

    private fun ensure() {
        if (prefs.contains(KEY_MEMBERS)) return
        save(listOf(Member("1", 1, "我")), "1")
    }

    private fun save(list: List<Member>, activeId: String) {
        val array = JSONArray()
        list.forEach { member ->
            array.put(
                JSONObject()
                    .put("id", member.id)
                    .put("slot", member.slot)
                    .put("nickname", member.nickname),
            )
        }
        prefs.edit()
            .putString(KEY_MEMBERS, array.toString())
            .putString(KEY_ACTIVE, activeId)
            .apply()
    }

    companion object {
        private const val KEY_MEMBERS = "members"
        private const val KEY_ACTIVE = "activeMember"
    }
}
