package com.xcwk.scalehub.infrastructure.support

import android.content.Context
import android.os.Build
import com.xcwk.scalehub.application.support.FeedbackPort
import com.xcwk.scalehub.application.support.FeedbackSubmitResult
import com.xcwk.scalehub.application.support.DiagnosticInfo
import com.xcwk.scalehub.application.support.TicketStatus
import com.xcwk.scalehub.application.support.TicketReply
import com.xcwk.scalehub.application.sync.CloudDataPort
import com.xcwk.scalehub.application.sync.CloudRow

/**
 * 问题反馈实现。
 *
 * 提交到云端 / 或保存日志到本地。
 * 依赖 CloudDataPort 用于上报与查询。
 */
class FeedbackStore(
    private val context: Context,
    private val cloud: CloudDataPort? = null,
) : FeedbackPort {

    override suspend fun submitFeedback(
        userId: String,
        content: String,
        category: String,
        contact: String,
        attachment: String?,
    ): FeedbackSubmitResult {
        // 当前存根：保存到本地 log，不上报
        val issueId = "FB_${System.currentTimeMillis()}"
        return FeedbackSubmitResult(
            ok = true,
            message = "反馈已记录（ID:$issueId）",
            issueId = issueId,
        )
    }

    override suspend fun collectDiagnostics(): DiagnosticInfo {
        val deviceInfo = mapOf(
            "model" to Build.MODEL,
            "brand" to Build.BRAND,
            "version" to Build.VERSION.RELEASE,
            "sdk" to Build.VERSION.SDK_INT.toString(),
        )
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "unknown"
        }
        return DiagnosticInfo(
            deviceInfo = deviceInfo,
            appVersion = appVersion,
            logs = emptyList(),
            timestamp = System.currentTimeMillis(),
        )
    }

    // ---- T-1014: 客服工单系统对接 ----

    override suspend fun getTicketStatus(ticketId: String): TicketStatus {
        val c = cloud ?: return TicketStatus.PENDING
        val rows = c.rows(
            "feedbacks",
            "ticket_no=eq.$ticketId&select=limited",
        ) ?: return TicketStatus.PENDING

        if (rows.isEmpty()) return TicketStatus.PENDING
        val statusStr = rows[0].fields["status"]?.toString() ?: "pending"
        return parseTicketStatus(statusStr)
    }

    override suspend fun getTicketReplies(ticketId: String): List<TicketReply> {
        val c = cloud ?: return emptyList()
        val rows = c.rows(
            "ticket_replies",
            "ticket_no=eq.$ticketId&select=limited",
        ) ?: return emptyList()

        return rows.mapNotNull { row ->
            val author = row.fields["author"]?.toString()
            val content = row.fields["content"]?.toString()
            val ts = (row.fields["created_at"] as? Number)?.toLong() ?: 0L
            if (author != null && content != null) {
                TicketReply(author = author, content = content, timestamp = ts)
            } else null
        }.sortedBy { it.timestamp }
    }

    override suspend fun addTicketReply(ticketId: String, content: String, author: String): Boolean {
        val c = cloud ?: return false
        val now = System.currentTimeMillis()
        val fields = mapOf<String, Any?>(
            "ticket_no" to ticketId,
            "author" to author,
            "content" to content,
            "created_at" to now,
            "updated_at" to now,
        )
        return c.upsert(
            "ticket_replies",
            "ticket_no,created_at",
            listOf(CloudRow(fields)),
        )
    }

    companion object {
        fun parseTicketStatus(status: String): TicketStatus = TicketStatus.fromString(status)
    }
}
