package com.xcwk.scalehub.application.support

/**
 * 问题反馈端口（M3）—— 抽象反馈提交与诊断信息收集。
 *
 * 实现者负责上报到后端或第三方客服系统；应用层只编排语义。
 */
interface FeedbackPort {
    /**
     * 提交用户反馈。
     * @return 提交结果，ok=true 表示已提交
     */
    suspend fun submitFeedback(
        userId: String,
        content: String,
        category: String,
        contact: String,
        attachment: String?,
    ): FeedbackSubmitResult

    /**
     * 收集本地诊断日志（设备信息、App 版本、日志片段）。
     */
    suspend fun collectDiagnostics(): DiagnosticInfo

    /**
     * 查询工单状态。
     * @param ticketId 工单号（如 #FB-20261022）
     * @return 工单状态
     */
    suspend fun getTicketStatus(ticketId: String): TicketStatus

    /**
     * 获取工单对话记录（客服与用户的往来消息）。
     * @param ticketId 工单号
     * @return 对话记录列表，按时间升序
     */
    suspend fun getTicketReplies(ticketId: String): List<TicketReply>

    /**
     * 添加回复到工单。
     * @param ticketId 工单号
     * @param content 回复内容
     * @param author 回复人（"user" 或 "support"）
     * @return 是否添加成功
     */
    suspend fun addTicketReply(ticketId: String, content: String, author: String): Boolean
}

/** 工单状态。 */
enum class TicketStatus {
    PENDING,      // 待处理
    IN_PROGRESS,  // 处理中
    REPLIED,      // 已回复
    CLOSED,       // 已关闭
    ;

    companion object {
        /** 解析字符串状态为枚举。未知状态返回 PENDING。 */
        fun fromString(status: String): TicketStatus = when (status.lowercase()) {
            "pending" -> PENDING
            "in_progress" -> IN_PROGRESS
            "replied" -> REPLIED
            "closed" -> CLOSED
            else -> PENDING
        }
    }
}

/** 工单回复。 */
data class TicketReply(
    val author: String,       // "user" | "support"
    val content: String,
    val timestamp: Long,
)

/** 反馈提交结果。 */
data class FeedbackSubmitResult(
    val ok: Boolean,
    val message: String,
    val issueId: String? = null,
)

/** 诊断信息。 */
data class DiagnosticInfo(
    val deviceInfo: Map<String, String>,
    val appVersion: String,
    val logs: List<String>,
    val timestamp: Long,
)

/** 反馈分类。 */
enum class FeedbackCategory {
    BUG,        // Bug 反馈
    FEATURE,    // 功能建议
    QUESTION,   // 使用疑问
    OTHER,      // 其他
}
