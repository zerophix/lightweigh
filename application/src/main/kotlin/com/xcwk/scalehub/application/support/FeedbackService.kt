package com.xcwk.scalehub.application.support

/**
 * application service — 问题反馈编排。
 *
 * 职责：
 *  - 提交用户反馈到后端
 *  - 收集诊断信息
 *  - 管理反馈提交状态
 *  - 跟踪工单状态与回复（客服工单系统对接）
 *
 * 依赖 FeedbackPort（实现负责上报）。
 */
class FeedbackService(
    private val port: FeedbackPort?,
) {

    /** 提交反馈。 */
    suspend fun submit(
        userId: String,
        content: String,
        category: String,
        contact: String,
    ): FeedbackSubmitOutcome {
        // 校验输入
        if (content.isBlank()) {
            return FeedbackSubmitOutcome(false, "请输入反馈内容", null, false)
        }
        if (content.length > 2000) {
            return FeedbackSubmitOutcome(false, "反馈内容过长（最多 2000 字）", null, false)
        }
        val p = port ?: return FeedbackSubmitOutcome(false, "反馈服务不可用", null, false)
        return try {
            val result = p.submitFeedback(userId, content, category, contact, null)
            FeedbackSubmitOutcome(
                ok = result.ok,
                message = result.message,
                issueId = result.issueId,
                canRetry = !result.ok,
            )
        } catch (e: Exception) {
            FeedbackSubmitOutcome(false, "提交失败：${e.message ?: "请稍后重试"}", null, true)
        }
    }

    /** 收集诊断信息。 */
    suspend fun collectDiagnostics(): DiagnosticInfo? {
        return port?.collectDiagnostics()
    }

    /** 提交带附件的反馈。 */
    suspend fun submitWithAttachment(
        userId: String,
        content: String,
        category: String,
        contact: String,
        attachment: String,
    ): FeedbackSubmitOutcome {
        if (content.isBlank()) {
            return FeedbackSubmitOutcome(false, "请输入反馈内容", null, false)
        }
        val p = port ?: return FeedbackSubmitOutcome(false, "反馈服务不可用", null, false)
        return try {
            val result = p.submitFeedback(userId, content, category, contact, attachment)
            FeedbackSubmitOutcome(
                ok = result.ok,
                message = result.message,
                issueId = result.issueId,
                canRetry = !result.ok,
            )
        } catch (e: Exception) {
            FeedbackSubmitOutcome(false, "提交失败：${e.message ?: "请稍后重试"}", null, true)
        }
    }

    /**
     * 查询工单状态。
     * @param ticketId 工单号（如 #FB-20261022）
     * @return 工单状态，若查询失败返回 null
     */
    suspend fun trackTicket(ticketId: String): TicketStatus? {
        val p = port ?: return null
        return try {
            p.getTicketStatus(ticketId)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取工单对话记录。
     * @param ticketId 工单号
     * @return 对话记录列表，按时间升序
     */
    suspend fun getTicketConversation(ticketId: String): List<TicketReply> {
        val p = port ?: return emptyList()
        return try {
            p.getTicketReplies(ticketId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 添加回复到工单。
     * @param ticketId 工单号
     * @param content 回复内容
     * @param author 回复人（"user" 或 "support"）
     * @return 是否添加成功
     */
    suspend fun addReply(ticketId: String, content: String, author: String): Boolean {
        if (content.isBlank()) return false
        val p = port ?: return false
        return try {
            p.addTicketReply(ticketId, content, author)
        } catch (e: Exception) {
            false
        }
    }
}

/** 反馈提交结果。 */
data class FeedbackSubmitOutcome(
    val ok: Boolean,
    val message: String,
    val issueId: String?,
    val canRetry: Boolean,
)
