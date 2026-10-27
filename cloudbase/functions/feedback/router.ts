/**
 * Feedback router cloud function (T-1012, T-1014).
 *
 * 路由规则：
 *  - POST /feedback        → 提交反馈 (submit)
 *  - POST /feedback/diag   → 上传诊断日志 (uploadDiagnostics)
 *  - GET  /feedback/types  → 获取反馈类型列表 (getTypes)
 *  - GET  /feedback/:id   → 获取单条反馈状态 (getStatus)
 *  - GET  /feedback/ticket/:ticketNo   → 获取工单状态+回复 (getTicketInfo) [T-1014]
 *  - POST /feedback/ticket/:ticketNo/reply → 添加工单回复 (addReply) [T-1014]
 *
 * 权限模型：
 *  - 提交反馈：需要登录 (formal account)
 *  - 上传诊断：需要登录 + 同意诊断授权
 *  - 查询状态：需要登录 + 归属检查
 *
 * 数据模型（CloudBase RDB，表名 feedbacks）：
 *  - id          string  主键 (自增)
 *  - user_id     string  用户 ID (owner_id)
 *  - type        string  反馈类型 (Measure/Connect/Other)
 *  - content     text    描述内容
 *  - contact     string  联系方式
 *  - status      string  处理状态 (pending/replied/closed)
 *  - ticket_no   string  工单号 (#FB-XXXXXX)
 *  - created_at  bigint  创建时间
 *  - updated_at  bigint  更新时间
 *  - diag_log    json    诊断日志快照 (可空)
 */

const functions = require('@cloudbase/nodeunit');
const app = require('tdefs')();

// 反馈类型枚举
const FeedbackType = {
    MEASURE: 'Measure',
    CONNECT: 'Connect',
    OTHER: 'Other',
};

// 反馈状态枚举
const FeedbackStatus = {
    PENDING: 'pending',
    IN_PROGRESS: 'in_progress',
    REPLIED: 'replied',
    CLOSED: 'closed',
};

/**
 * POST /feedback — 提交反馈
 *
 * 请求体：
 *   { "type": "Measure", "content": "...", "contact": "..." }
 *
 * 响应：
 *   { "ok": true, "ticketNo": "#FB-20261022", "issueId": "fb_123" }
 */
async function submit(ctx, event) {
    const { type, content, contact } = event;
    const userId = ctx.userId;

    if (!userId) {
        return { ok: false, message: '请先登录' };
    }
    if (!content || content.trim().length > 500) {
        return { ok: false, message: '反馈内容不能为空，最多 500 字' };
    }

    const feedbackType = type || FeedbackType.OTHER;
    const ticketNo = generateTicketNo();
    const now = Date.now();

    // 插入到 feedbacks 表
    const result = await app.database.collection('feedbacks').add({
        user_id: userId,
        owner_id: userId,
        type: feedbackType,
        content: content.trim(),
        contact: contact || '',
        status: FeedbackStatus.PENDING,
        ticket_no: ticketNo,
        created_at: now,
        updated_at: now,
    });

    const issueId = result.id || `fb_${now}`;

    // 上报监控埋点
    try {
        await app.database.collection('feedback_metrics').add({
            user_id: userId,
            event: 'submit',
            type: feedbackType,
            success: true,
            timestamp: now,
        });
    } catch (e) {
        console.error('metrics report failed:', e.message);
    }

    return { ok: true, ticketNo, issueId };
}

/**
 * POST /feedback/diag — 上传诊断日志
 *
 * 请求体：
 *   { "diagnostics": { "deviceInfo": {...}, "logs": [...], "appVersion": "..." } }
 */
async function uploadDiagnostics(ctx, event) {
    const { diagnostics } = event;
    const userId = ctx.userId;

    if (!userId) {
        return { ok: false, message: '请先登录' };
    }

    const now = Date.now();

    const result = await app.database.collection('diagnostics').add({
        user_id: userId,
        owner_id: userId,
        app_version: diagnostics.appVersion || 'unknown',
        device_info: JSON.stringify(diagnostics.deviceInfo || {}),
        log_count: diagnostics.logs ? diagnostics.logs.length : 0,
        logs: JSON.stringify(diagnostics.logs || []).slice(0, 50000), // 限制长度
        created_at: now,
    });

    return { ok: true, id: result.id || `diag_${now}` };
}

/**
 * GET /feedback/types — 获取反馈类型
 */
async function getTypes(ctx, event) {
    return {
        ok: true,
        types: [
            { value: FeedbackType.MEASURE, label: '测量问题' },
            { value: FeedbackType.CONNECT, label: '连接问题' },
            { value: FeedbackType.OTHER, label: '其他' },
        ],
    };
}

/**
 * GET /feedback/:id — 获取反馈状态
 */
async function getStatus(ctx, event) {
    const { id } = event;
    const userId = ctx.userId;

    if (!userId) {
        return { ok: false, message: '请先登录' };
    }

    const result = await app.database.collection('feedbacks')
        .where({ _id: app.databaseoid(id), user_id: userId })
        .getOne();

    if (!result.data) {
        return { ok: false, message: '反馈不存在' };
    }

    return {
        ok: true,
        status: result.data.status,
        ticketNo: result.data.ticket_no,
        createdAt: result.data.created_at,
    };
}

/**
 * GET /feedback/ticket/:ticketNo — 获取工单状态与回复列表 [T-1014]
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
 * POST /feedback/ticket/:ticketNo/reply — 添加工单回复 [T-1014]
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

/**
 * 生成工单号。
 */
function generateTicketNo() {
    const date = new Date();
    const yymmdd = `${String(date.getMonth() + 1).padStart(2, '0')}${String(date.getDate()).padStart(2, '0')}`;
    const random = Math.floor(Math.random() * 10000).toString().padStart(4, '0');
    return `#FB-${yymmdd}${random}`;
}

// 路由表
const router = {
    '/feedback': { POST: submit },
    '/feedback/diag': { POST: uploadDiagnostics },
    '/feedback/types': { GET: getTypes },
    '/feedback/:id': { GET: getStatus },
    '/feedback/ticket/:ticketNo': { GET: getTicketInfo },
    '/feedback/ticket/:ticketNo/reply': { POST: addReply },
};

// TCB 云函数入口
exports.main = async (event, context) => {
    const { path, method } = event;
    const route = router[path];

    if (!route || !route[method.toUpperCase()]) {
        return { ok: false, message: '接口不存在' };
    }

    try {
        return await route[method.toUpperCase()](context, event);
    } catch (e) {
        console.error('feedback router error:', e);
        return { ok: false, message: '服务器错误，请稍后重试' };
    }
};

module.exports = { router, FeedbackType, FeedbackStatus, generateTicketNo, getTicketInfo, addReply };
