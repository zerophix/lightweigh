/**
 * Ticket system integration (T-1014).
 *
 * 客服工单系统对接 — 处理工单查询与回复。
 * 依赖 feedback router.ts 的上下文与数据库实例。
 *
 * 表结构：
 *  - feedbacks: ticket_no, status, user_id, content, ...
 *  - ticket_replies: ticket_no, author, content, created_at, ...
 */
const FeedbackStatus = {
    PENDING: 'pending',
    IN_PROGRESS: 'in_progress',
    REPLIED: 'replied',
    CLOSED: 'closed',
};

/**
 * GET /feedback/ticket/:ticketNo — 获取工单状态与回复列表
 *
 * 响应：
 *   { ok: true, status: "replied", ticketNo: "#FB-20261022", replies: [...] }
 */
async function getTicketInfo(ctx, event) {
    const { ticketNo } = event;
    const userId = ctx.userId;

    if (!userId) {
        return { ok: false, message: '请先登录' };
    }
    if (!ticketNo) {
        return { ok: false, message: '工单号不能为空' };
    }

    const app = ctx.app || require('tdefs')();

    // 查询工单状态
    const feedbackResult = await app.database.collection('feedbacks')
        .where({ ticket_no: ticketNo, user_id: userId })
        .getOne();

    if (!feedbackResult.data) {
        return { ok: false, message: '工单不存在或无权限访问' };
    }

    // 查询回复列表
    const repliesResult = await app.database.collection('ticket_replies')
        .where({ ticket_no: ticketNo })
        .orderBy('created_at', 'asc')
        .get();

    const replies = (repliesResult.data || []).map(item => ({
        author: item.author || 'unknown',
        content: item.content || '',
        timestamp: item.created_at || 0,
    }));

    return {
        ok: true,
        status: feedbackResult.data.status,
        ticketNo: feedbackResult.data.ticket_no,
        createdAt: feedbackResult.data.created_at,
        replies: replies,
    };
}

/**
 * POST /feedback/ticket/:ticketNo/reply — 添加工单回复
 *
 * 请求体：
 *   { "ticketNo": "#FB-20261022", "content": "...", "author": "user"|"support" }
 *
 * 响应：
 *   { ok: true, replyId: "reply_123" }
 */
async function addReply(ctx, event) {
    const { ticketNo, content, author } = event;
    const userId = ctx.userId;

    if (!userId) {
        return { ok: false, message: '请先登录' };
    }
    if (!ticketNo) {
        return { ok: false, message: '工单号不能为空' };
    }
    if (!content || content.trim().length === 0) {
        return { ok: false, message: '回复内容不能为空' };
    }
    if (content.trim().length > 2000) {
        return { ok: false, message: '回复内容过长（最多 2000 字）' };
    }

    const replyAuthor = author || 'user';
    const now = Date.now();
    const app = ctx.app || require('tdefs')();

    // 验证工单归属
    const feedbackResult = await app.database.collection('feedbacks')
        .where({ ticket_no: ticketNo, user_id: userId })
        .getOne();

    if (!feedbackResult.data) {
        return { ok: false, message: '工单不存在或无权限访问' };
    }

    // 插入回复
    const result = await app.database.collection('ticket_replies').add({
        ticket_no: ticketNo,
        author: replyAuthor,
        content: content.trim(),
        created_at: now,
        updated_at: now,
    });

    // 更新工单状态为已回复（如果是客服回复）
    if (replyAuthor === 'support') {
        await app.database.collection('feedbacks')
            .where({ ticket_no: ticketNo })
            .update({ status: FeedbackStatus.REPLIED, updated_at: now });
    }

    // 上报监控埋点
    try {
        await app.database.collection('feedback_metrics').add({
            user_id: userId,
            event: 'reply',
            ticket_no: ticketNo,
            author: replyAuthor,
            timestamp: now,
        });
    } catch (e) {
        console.error('metrics report failed:', e.message);
    }

    return { ok: true, replyId: result.id || `reply_${now}` };
}

/** 工单状态枚举。 */
const TicketStatus = {
    PENDING: 'pending',
    IN_PROGRESS: 'in_progress',
    REPLIED: 'replied',
    CLOSED: 'closed',
};

module.exports = {
    getTicketInfo,
    addReply,
    TicketStatus,
    FeedbackStatus,
};
