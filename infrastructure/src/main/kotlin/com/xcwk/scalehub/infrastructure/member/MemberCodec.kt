package com.xcwk.scalehub.infrastructure.member

import com.xcwk.scalehub.domain.model.Member
import org.json.JSONArray
import org.json.JSONObject

/** scalehub_members.members JSON 与领域成员列表的互转（DEC-S04 导入）。 */
internal object MemberCodec {
    fun parse(raw: String?): List<Member> {
        if (raw.isNullOrBlank()) return emptyList()
        val list = mutableListOf<Member>()
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            list.add(Member(id, item.optInt("slot", i + 1), item.optString("nickname")))
        }
        return list
    }

    fun toJson(list: List<Member>): String {
        val array = JSONArray()
        list.forEach { member ->
            array.put(
                JSONObject()
                    .put("id", member.id)
                    .put("slot", member.slot)
                    .put("nickname", member.nickname),
            )
        }
        return array.toString()
    }
}
