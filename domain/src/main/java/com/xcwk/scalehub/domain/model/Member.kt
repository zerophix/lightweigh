package com.xcwk.scalehub.domain.model

/**
 * 本机测量用户（旧库 model/Member.kt + MemberStore 槽位语义移植）。
 * 体脂秤共用，档案/测量/健康记录按成员分开（member_id 维）。
 * slot 是下发给秤的用户编号（userPacket 第 2-5 字节），1..MAX。
 */
data class Member(
    val id: String,
    val slot: Int,
    val nickname: String,
) {
    companion object {
        const val MAX = 8

        /** 新成员 id = 现有数字 id 最大值 +1（旧库 MemberStore:80-81）。 */
        fun nextId(existing: List<Member>): String =
            ((existing.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0) + 1).toString()

        /** 槽位 = 1..MAX 中第一个未占用（旧库 :82-83）。 */
        fun nextSlot(existing: List<Member>): Int {
            val used = existing.map { it.slot }.toSet()
            return (1..MAX).firstOrNull { it !in used } ?: MAX
        }

        /** 昵称：去首尾空白、截 20 字、空则回退"用户N"（旧库 :84）。 */
        fun normalizeNickname(raw: String, fallbackIndex: Int): String =
            raw.trim().take(20).ifBlank { "用户$fallbackIndex" }
    }
}
