package com.xcwk.scalehub.application.support

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * T-1014 客服工单系统对接 — FeedbackService 工单跟踪行为测试。
 *
 * 覆盖：
 * 1. trackTicket — 查询工单状态
 * 2. trackTicket — 端口为 null 时返回 null
 * 3. getTicketConversation — 获取对话记录
 * 4. addReply — 空内容不可添加
 * 5. addReply — 端口为 null 时返回 false
 * 6. TicketStatus.fromString — 状态解析
 */
class FeedbackTicketServiceTest {

    private class FakeFeedbackPort : FeedbackPort {
        var ticketStatus: TicketStatus = TicketStatus.PENDING
        var replies: List<TicketReply> = emptyList()
        var replyAdded = false
        var lastReplyContent: String? = null
        var lastReplyAuthor: String? = null

        override suspend fun submitFeedback(
            userId: String,
            content: String,
            category: String,
            contact: String,
            attachment: String?,
        ): FeedbackSubmitResult {
            return FeedbackSubmitResult(ok = true, message = "ok", issueId = "fb_1")
        }

        override suspend fun collectDiagnostics(): DiagnosticInfo {
            return DiagnosticInfo(
                deviceInfo = emptyMap(),
                appVersion = "1.0",
                logs = emptyList(),
                timestamp = System.currentTimeMillis(),
            )
        }

        override suspend fun getTicketStatus(ticketId: String): TicketStatus = ticketStatus

        override suspend fun getTicketReplies(ticketId: String): List<TicketReply> = replies

        override suspend fun addTicketReply(
            ticketId: String,
            content: String,
            author: String,
        ): Boolean {
            lastReplyContent = content
            lastReplyAuthor = author
            replyAdded = true
            return true
        }
    }

    @Test
    fun `trackTicket returns status from port`() = runTest {
        val port = FakeFeedbackPort().apply { ticketStatus = TicketStatus.REPLIED }
        val service = FeedbackService(port)

        val status = service.trackTicket("#FB-20261022")

        assertThat(status).isEqualTo(TicketStatus.REPLIED)
    }

    @Test
    fun `trackTicket returns null when port is null`() = runTest {
        val service = FeedbackService(port = null)

        val status = service.trackTicket("#FB-20261022")

        assertThat(status).isNull()
    }

    @Test
    fun `getTicketConversation returns replies from port`() = runTest {
        val now = System.currentTimeMillis()
        val port = FakeFeedbackPort().apply {
            replies = listOf(
                TicketReply(author = "support", content = "收到反馈", timestamp = now + 1000),
                TicketReply(author = "user", content = "问题依然存在", timestamp = now + 2000),
            )
        }
        val service = FeedbackService(port)

        val conversation = service.getTicketConversation("#FB-20261022")

        assertThat(conversation).hasSize(2)
        assertThat(conversation[0].author).isEqualTo("support")
        assertThat(conversation[1].author).isEqualTo("user")
    }

    @Test
    fun `getTicketConversation returns empty list when port is null`() = runTest {
        val service = FeedbackService(port = null)

        val conversation = service.getTicketConversation("#FB-20261022")

        assertThat(conversation).isEmpty()
    }

    @Test
    fun `addReply returns false for empty content`() = runTest {
        val port = FakeFeedbackPort()
        val service = FeedbackService(port)

        val result = service.addReply("#FB-20261022", "", "user")

        assertThat(result).isFalse()
        assertThat(port.replyAdded).isFalse()
    }

    @Test
    fun `addReply delegates to port`() = runTest {
        val port = FakeFeedbackPort()
        val service = FeedbackService(port)

        val result = service.addReply("#FB-20261022", "问题已解决", "support")

        assertThat(result).isTrue()
        assertThat(port.replyAdded).isTrue()
        assertThat(port.lastReplyContent).isEqualTo("问题已解决")
        assertThat(port.lastReplyAuthor).isEqualTo("support")
    }

    @Test
    fun `addReply returns false when port is null`() = runTest {
        val service = FeedbackService(port = null)

        val result = service.addReply("#FB-20261022", "test", "user")

        assertThat(result).isFalse()
    }

    @Test
    fun `TicketStatus fromString handles known statuses`() {
        assertThat(TicketStatus.fromString("pending")).isEqualTo(TicketStatus.PENDING)
        assertThat(TicketStatus.fromString("in_progress")).isEqualTo(TicketStatus.IN_PROGRESS)
        assertThat(TicketStatus.fromString("replied")).isEqualTo(TicketStatus.REPLIED)
        assertThat(TicketStatus.fromString("closed")).isEqualTo(TicketStatus.CLOSED)
    }

    @Test
    fun `TicketStatus fromString falls back to PENDING for unknown`() {
        assertThat(TicketStatus.fromString("unknown")).isEqualTo(TicketStatus.PENDING)
        assertThat(TicketStatus.fromString("")).isEqualTo(TicketStatus.PENDING)
    }
}
