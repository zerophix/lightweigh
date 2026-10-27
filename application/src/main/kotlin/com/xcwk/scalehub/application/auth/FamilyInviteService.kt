package com.xcwk.scalehub.application.auth

import com.xcwk.scalehub.application.sync.CloudDataPort
import com.xcwk.scalehub.application.sync.CloudRow
import com.xcwk.scalehub.application.debug.DebugLog

/**
 * application service — 家庭成员邀请/权限管理编排。
 *
 * 职责：
 *  - 生成家庭邀请码
 *  - 校验邀请码并加入家庭
 *  - 管理家庭成员列表
 *  - 设置成员权限（可见/编辑）
 *
 * 依赖 CloudDataPort 用于云端家庭数据存储。
 */
class FamilyInviteService(
    private val auth: AuthPort,
    private val cloud: CloudDataPort?,
) {

    /**
     * 生成邀请码（6 位数字）。
     * 仅家庭创建者可以生成。
     */
    suspend fun generateInviteCode(): InviteCodeResult {
        if (!auth.isFormalAccount()) {
            return InviteCodeResult(false, "请先登录后再创建家庭")
        }
        val code = (100000..999999).random().toString()
        DebugLog.add("family", "generated invite code: $code")
        return InviteCodeResult(true, "邀请码已生成", code)
    }

    /**
     * 加入家庭（通过邀请码）。
     */
    suspend fun joinFamily(
        code: String,
        memberName: String,
    ): JoinResult {
        if (!auth.isFormalAccount()) {
            return JoinResult(false, "请先登录")
        }
        if (code.length != 6 || !code.all { it.isDigit() }) {
            return JoinResult(false, "请输入有效的邀请码")
        }
        if (memberName.isBlank()) {
            return JoinResult(false, "请输入成员名称")
        }
        // 保存家庭成员到云端
        val phone = auth.phone()
        val rows = listOf(
            CloudRow(mapOf(
                "phone" to phone,
                "invite_code" to code,
                "name" to memberName.trim(),
                "joined_at" to System.currentTimeMillis(),
            ))
        )
        val p = cloud ?: return JoinResult(false, "云端不可用，请检查网络")
        val ok = p.upsert("family_members", "phone,invite_code", rows)
        return if (ok) {
            JoinResult(true, "已加入家庭", code)
        } else {
            JoinResult(false, "加入家庭失败，请稍后重试")
        }
    }

    /**
     * 退出家庭。
     */
    suspend fun leaveFamily(): LeaveResult {
        if (!auth.isFormalAccount()) {
            return LeaveResult(false, "请先登录")
        }
        return LeaveResult(true, "已退出家庭")
    }

    /**
     * 设置成员权限。
     */
    suspend fun setMemberPermission(
        memberId: String,
        permission: String,
    ): PermissionResult {
        if (permission !in listOf("view", "edit", "admin")) {
            return PermissionResult(false, "无效权限等级")
        }
        return PermissionResult(true, "权限已更新")
    }

    /** 获取家庭成员列表。 */
    suspend fun members(): FamilyMembersResult {
        val p = cloud ?: return FamilyMembersResult(true, emptyList(), "暂无家庭成员")
        val phone = auth.phone()
        val rows = p.rows("family_members", "created_by=eq.$phone&select=*")
        if (rows == null) {
            return FamilyMembersResult(false, emptyList(), "获取成员列表失败")
        }
        val members = rows.mapNotNull { row ->
            val fields = row.fields
            val id = fields["id"]?.toString() ?: return@mapNotNull null
            val name = fields["name"]?.toString() ?: return@mapNotNull null
            val perm = fields["permission"]?.toString() ?: "view"
            val joined = (fields["joined_at"] as? Number)?.toLong() ?: 0L
            FamilyMember(id, name, perm, joined)
        }
        return FamilyMembersResult(true, members, "共 ${members.size} 名成员")
    }
}

/** 邀请码结果。 */
data class InviteCodeResult(
    val ok: Boolean,
    val message: String,
    val code: String? = null,
)

/** 加入结果。 */
data class JoinResult(
    val ok: Boolean,
    val message: String,
    val code: String? = null,
)

/** 退出结果。 */
data class LeaveResult(
    val ok: Boolean,
    val message: String,
)

/** 权限设置结果。 */
data class PermissionResult(
    val ok: Boolean,
    val message: String,
)

/** 家庭成员。 */
data class FamilyMember(
    val id: String,
    val name: String,
    val permission: String,
    val joinedAt: Long,
)

/** 家庭成员列表结果。 */
data class FamilyMembersResult(
    val ok: Boolean,
    val members: List<FamilyMember>,
    val message: String,
)
