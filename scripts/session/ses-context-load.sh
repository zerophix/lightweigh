#!/usr/bin/env bash
# scripts/session/ses-context-load.sh
# Session 启动时加载上下文缓存，输出关键上下文让 AI 感知
# 被 .claude/hooks/ 调用

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CACHE_SCRIPT="$ROOT/scripts/session/ses-context-cache.sh"

echo ""
echo "=== 上下文缓存 ==="
echo ""

# 交接上下文（handoff）
$CACHE_SCRIPT get handoff --limit 1 2>/dev/null | python3 -c "
import json, sys
data = sys.stdin.read()
entries = []
for block in data.split('---'):
    block = block.strip()
    if not block: continue
    try: entries.append(json.loads(block))
    except: pass
if entries:
    print('上一个 AI 的交接:')
    e = entries[0]
    from_a = e.get('from', '?')
    context = str(e.get('context', ''))[:120]
    next_step = e.get('next', '?')
    key_files = e.get('key_files', [])
    if isinstance(key_files, str): key_files = key_files.split(',')
    print(f' 来自: {from_a}')
    print(f' 上下文: {context}')
    print(f' 下一步: {next_step}')
    if key_files:
        print(f' 关键文件: {\", \".join(str(f) for f in key_files[:3])}')
" 2>/dev/null

echo ""

# 用户偏好
$CACHE_SCRIPT get user-preference --limit 10 2>/dev/null | python3 -c "
import json, sys
data = sys.stdin.read()
entries = []
for block in data.split('---'):
    block = block.strip()
    if not block: continue
    try:
        entries.append(json.loads(block))
    except: pass
if entries:
    print('用户偏好:')
    for e in entries:
        key = e.get('key', '?')
        val = e.get('value', '')[:80]
        print(f'  - {key}: {val}')
" 2>/dev/null

echo ""

# 当前任务
$CACHE_SCRIPT get current-task --limit 3 2>/dev/null | python3 -c "
import json, sys
data = sys.stdin.read()
entries = []
for block in data.split('---'):
    block = block.strip()
    if not block: continue
    try:
        entries.append(json.loads(block))
    except: pass
if entries:
    print('当前任务:')
    for e in entries:
        dec = e.get('dec', '?')
        files = e.get('files', '')[:80]
        print(f'  - {dec}: {files}')
" 2>/dev/null

echo ""

# Session 摘要
$CACHE_SCRIPT get session-summary --limit 3 2>/dev/null | python3 -c "
import json, sys
data = sys.stdin.read()
entries = []
for block in data.split('---'):
    block = block.strip()
    if not block: continue
    try:
        entries.append(json.loads(block))
    except: pass
if entries:
    e = entries[0]
    print('最后 session 摘要:')
    for k, v in e.items():
        if k in ('type', 'ts', 'agent', 'id'): continue
        print(f'  {k}: {str(v)[:100]}')
" 2>/dev/null

echo ""

# AI 收件箱 — 未读消息
INBOX_DIR="$ROOT/docs/ai/inbox"
if [ -d "$INBOX_DIR" ]; then
  UNREAD=$(find "$INBOX_DIR" -maxdepth 1 -name "*.md" -not -name ".gitkeep" -exec grep -l "^status: unread" {} \; 2>/dev/null | wc -l | tr -d ' ')
  if [ "$UNREAD" -gt 0 ]; then
    echo "AI 收件箱: ${UNREAD} 条未读消息"
    echo "  读取: bash scripts/workflow/wf-inbox.sh list"
  fi
fi

# 任务看板
TASKS_FILE="$ROOT/.ai/tasks.json"
if [ -f "$TASKS_FILE" ]; then
  python3 -c "
import json
with open('$TASKS_FILE') as f:
    d = json.load(f)
tasks = d.get('tasks', [])
prog = [t for t in tasks if t.get('status') == 'in_progress']
pend = [t for t in tasks if t.get('status') == 'pending']
blkd = [t for t in tasks if t.get('status') == 'blocked']
parts = []
if prog: parts.append(f'{len(prog)} 进行中')
if pend: parts.append(f'{len(pend)} 待开始')
if blkd: parts.append(f'{len(blkd)} 阻塞')
if parts:
    nxt = d.get('next_action', '')
    print(f'任务看板: {\" | \".join(parts)}')
    if nxt:
        print(f'  下一步: {nxt[:120]}')
" 2>/dev/null
fi

echo ""
echo "=== 缓存加载完毕 ==="
echo "  查看全部: bash scripts/session/ses-context-cache.sh status"
echo "  搜索:      bash scripts/session/ses-context-cache.sh search <keyword>"
