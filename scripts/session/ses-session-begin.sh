#!/usr/bin/env bash
# ============================================================
# scripts/session/ses-session-begin.sh
# Session 启动钩子 — 闭环接力入口
# 被 .claude/settings.json 的 SessionStart hook 调用
#
# 职责:
#   1. 加载上下文缓存
#   2. 读取收件箱 — 强制展示未读消息
#   3. 读取任务板 — 展示待确认的下一步 + 进行中任务
#   4. 输出结构化接力提示 → AI 启动时必读
# ============================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$ROOT/scripts/lib/lib-platform.sh" 2>/dev/null || true

CACHE_SCRIPT="$ROOT/scripts/session/ses-context-cache.sh"
INBOX_DIR="$ROOT/docs/ai/inbox"
TASKS_FILE="$ROOT/.ai/tasks.json"
STATUS_FILE="$ROOT/STATUS.md"

# 静默模式：所有失败吞掉，不阻塞 Claude Code 启动
# 但接力提示输出到 stdout，AI 无法忽视

# ---------- 1. 加载上下文缓存 ----------
if [ -f "$CACHE_SCRIPT" ]; then
    bash "$CACHE_SCRIPT" get handoff --limit 1 2>/dev/null | python3 -c "
import json, sys
data = sys.stdin.read()
entries = []
for block in data.split('---'):
    block = block.strip()
    if not block: continue
    try: entries.append(json.loads(block))
    except: pass
if entries:
    e = entries[0]
    ctx = str(e.get('context', ''))[:150]
    nxt = e.get('next', '?')
    print('  上一个 AI 的交接:')
    print('  上下文: ' + ctx)
    print('  下一步: ' + nxt)
" 2>/dev/null
fi

# ---------- 2. 收件箱 — 未读消息 ----------
if [ -d "$INBOX_DIR" ]; then
    unread_count=$(find "$INBOX_DIR" -maxdepth 1 -name "*.md" -not -name ".gitkeep" -exec grep -l "^status: unread" {} \; 2>/dev/null | wc -l | tr -d ' ')
    if [ "$unread_count" -gt 0 ]; then
        echo ""
        echo "    AI 收件箱有 $unread_count 条未读消息"
      echo "  必须执行: bash scripts/workflow/wf-inbox.sh list"
      echo "  然后逐条读取: bash scripts/workflow/wf-inbox.sh read <id>"
    fi
fi

# ---------- 3. 任务板 — 接力状态 ----------
if [ -f "$TASKS_FILE" ]; then
    python3 -c "
import json
with open('$TASKS_FILE') as f:
    d = json.load(f)

nxt = d.get('next_action', '')
why = d.get('next_action_why', '')
confirmed = d.get('next_action_confirmed', False)

tasks = d.get('tasks', [])
prog = [t for t in tasks if t.get('status') == 'in_progress']
pend = [t for t in tasks if t.get('status') == 'pending']

if nxt or prog:
    print()
    print('  === AI 接力状态 ===')

    if nxt and not confirmed:
        print('    下一步待人类确认: ' + nxt)
        if why:
            print('     理由: ' + why[:120])
        print('     请向人类确认: \"上一个 AI 计划 [行动]，要继续吗?\"')
        print('     确认后执行: bash scripts/workflow/wf-task-board.sh confirm')

    if nxt and confirmed:
        print('   下一步（已确认）: ' + nxt)

    if prog:
        for t in prog:
            desc = t.get('description', '')
            print('   进行中任务: ' + t['id'] + ' ' + desc[:80])
    if pend:
        print('  ○ 待开始任务: ' + str(len(pend)) + ' 个')
" 2>/dev/null
fi

# ---------- 4. 检查 STATUS.md ----------
if [ -f "$STATUS_FILE" ]; then
        REFLECTION=$(grep -c "反思" "$STATUS_FILE" 2>/dev/null || true)
        if [ "${REFLECTION:-0}" -eq 0 ]; then
        echo "    STATUS.md 缺少反思节，请运行: bash scripts/governance/gov-update-status.sh"
    fi
else
        echo "    STATUS.md 不存在，请运行: bash scripts/governance/gov-update-status.sh"
fi

# ---------- 5. 写入 session-start 缓存 ----------
if [ -f "$CACHE_SCRIPT" ]; then
    START_TIME=$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo "")
    NEXT_TASK=""
    if [ -f "$TASKS_FILE" ]; then
        NEXT_TASK=$(python3 -c "
import json
with open('$TASKS_FILE') as f:
    d = json.load(f)
print(d.get('next_action', ''))
" 2>/dev/null)
    fi
    bash "$CACHE_SCRIPT" save session-start \
        dec="startup" \
        next_task="${NEXT_TASK:-读取 STATUS.md}" \
        2>/dev/null || true
fi

# ---------- 6. 快速脚本完整性检查 ----------
MISSING=""
for f in \
    "$ROOT/scripts/governance/gov-validate.sh" \
    "$ROOT/scripts/governance/gov-update-status.sh" \
    "$ROOT/scripts/governance/gov-next-decision.sh" \
    "$ROOT/scripts/workflow/wf-task-board.sh" \
    "$ROOT/scripts/workflow/wf-inbox.sh"; do
    if [ ! -f "$f" ]; then
        MISSING="$MISSING $(basename "$f")"
    fi
done
if [ -n "$MISSING" ]; then
    echo "    关键脚本缺失:$MISSING"
    echo "  请重新安装: bash scripts/bootstrap/bs-install-hooks.sh"
fi

exit 0
