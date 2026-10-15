#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-task-board.sh
# 任务看板 CLI — AI 接力协作的核心状态追踪
#
# 用法:
#   bash scripts/workflow/wf-task-board.sh list                    # 列出所有任务
#   bash scripts/workflow/wf-task-board.sh add "描述" ["理由"]     # 新增任务
#   bash scripts/workflow/wf-task-board.sh done <id>               # 标记完成
#   bash scripts/workflow/wf-task-board.sh block <id> "原因"       # 标记阻塞
#   bash scripts/workflow/wf-task-board.sh take <id>               # 认领任务
#   bash scripts/workflow/wf-task-board.sh next "行动" ["理由"]    # 设置下一步
#   bash scripts/workflow/wf-task-board.sh risk "风险说明"         # 添加已知风险
#   bash scripts/workflow/wf-task-board.sh confirm                 # 人类确认下一步方向
#   bash scripts/workflow/wf-task-board.sh status                  # 任务看板摘要
# ============================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TASKS_FILE="$ROOT/.ai/tasks.json"

# 确保文件存在
if [ ! -f "$TASKS_FILE" ]; then
  mkdir -p "$ROOT/.ai"
  init_json='{"version":1,"updated_at":"","next_action":"","next_action_why":"","next_action_confirmed":false,"known_risks":[],"tasks":[]}'
  echo "$init_json" > "$TASKS_FILE"
fi

now() {
  date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo "unknown"
}

NOW=$(now)

case "${1:-}" in
  list)
    python3 << PYEOF
import json, sys
with open('$TASKS_FILE') as f:
    d = json.load(f)
print('=== 任务看板 ===')
print()
nxt = d.get('next_action', '—')
nxt_why = d.get('next_action_why', '')
if nxt_why:
    nxt = nxt + '  (理由: ' + nxt_why[:100] + ')'
confirmed = d.get('next_action_confirmed', False)
confirm_mark = '  人类已确认' if confirmed else '  待人类确认'
print('下一步: ' + nxt)
print('状态:' + confirm_mark)
risks = d.get('known_risks', [])
if risks:
    print('已知风险: ' + ' | '.join(risks))
print()
tasks = d.get('tasks', [])
if not tasks:
    print('  无任务')
else:
    for status in ['in_progress', 'pending', 'blocked', 'completed']:
        group = [t for t in tasks if t.get('status') == status]
        if not group:
            continue
        labels = {'in_progress':'>> 进行中','pending':'o 待开始','blocked':'x 阻塞','completed':'v 已完成'}
        print('  ' + labels[status] + ':')
        for t in group:
            parts = '    ' + t['id']
            if t.get('assigned_to'):
                parts += ' @' + t['assigned_to']
            parts += ': ' + t['description'][:80]
            if t.get('why'):
                parts += ' -- ' + t['why'][:60]
            if t.get('dec'):
                parts += ' [' + t['dec'] + ']'
            print(parts)
        print()
PYEOF
    ;;

  add)
    DESC="${2:-}"
    WHY="${3:-}"
    if [ -z "$DESC" ]; then echo "用法: task-board.sh add \"描述\" [\"理由\"]" >&2; exit 1; fi
    python3 << PYEOF
import json
with open('$TASKS_FILE') as f:
    d = json.load(f)
tasks = d.get('tasks', [])
max_num = 0
for t in tasks:
    tid = t.get('id', 'T-000')
    try: n = int(tid.split('-')[1]); max_num = max(max_num, n)
    except: pass
nid = 'T-{:03d}'.format(max_num + 1)
tasks.append({
    'id': nid,
    'description': '$DESC',
    'why': '$WHY',
    'status': 'pending',
    'assigned_to': '',
    'dec': '',
    'created_at': '$NOW',
    'updated_at': '$NOW',
    'notes': ''
})
d['tasks'] = tasks
d['updated_at'] = '$NOW'
with open('$TASKS_FILE', 'w') as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
print('创建: ' + nid)
PYEOF
    ;;

  done)
    ID="${2:-}"
    if [ -z "$ID" ]; then echo "用法: task-board.sh done <id>" >&2; exit 1; fi
    python3 << PYEOF
import json
with open('$TASKS_FILE') as f:
    d = json.load(f)
found = False
for t in d.get('tasks', []):
    if t['id'] == '$ID':
        t['status'] = 'completed'
        t['updated_at'] = '$NOW'
        found = True
        break
if not found:
    print('未找到: $ID', file=__import__('sys').stderr)
    exit(1)
d['updated_at'] = '$NOW'
with open('$TASKS_FILE', 'w') as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
print('完成: $ID')
PYEOF
    ;;

  block)
    ID="${2:-}"; REASON="${3:-}"
    if [ -z "$ID" ] || [ -z "$REASON" ]; then echo "用法: task-board.sh block <id> \"原因\"" >&2; exit 1; fi
    python3 << PYEOF
import json
with open('$TASKS_FILE') as f:
    d = json.load(f)
found = False
for t in d.get('tasks', []):
    if t['id'] == '$ID':
        t['status'] = 'blocked'
        t['notes'] = (t.get('notes','') + ' | 阻塞: $REASON').strip()
        t['updated_at'] = '$NOW'
        found = True
        break
if not found:
    print('未找到: $ID', file=__import__('sys').stderr)
    exit(1)
d['updated_at'] = '$NOW'
with open('$TASKS_FILE', 'w') as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
print('阻塞: $ID -- $REASON')
PYEOF
    ;;

  take)
    ID="${2:-}"
    if [ -z "$ID" ]; then echo "用法: task-board.sh take <id>" >&2; exit 1; fi
    python3 << PYEOF
import json
with open('$TASKS_FILE') as f:
    d = json.load(f)
found = False
for t in d.get('tasks', []):
    if t['id'] == '$ID':
        t['status'] = 'in_progress'
        t['assigned_to'] = 'ai'
        t['updated_at'] = '$NOW'
        found = True
        break
if not found:
    print('未找到: $ID', file=__import__('sys').stderr)
    exit(1)
d['updated_at'] = '$NOW'
with open('$TASKS_FILE', 'w') as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
print('认领: $ID (进行中)')
PYEOF
    ;;

  next)
    ACTION="${2:-}"
    WHY="${3:-}"
    if [ -z "$ACTION" ]; then echo "用法: task-board.sh next \"行动\" [\"理由\"]" >&2; exit 1; fi
    python3 << PYEOF
import json, sys
with open('$TASKS_FILE') as f:
    d = json.load(f)
d['next_action'] = '$ACTION'
d['next_action_why'] = '$WHY'
d['next_action_confirmed'] = False
d['updated_at'] = '$NOW'
with open('$TASKS_FILE', 'w') as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
msg = '下一步: $ACTION'
why = '$WHY'
if why:
    msg = msg + ' (理由: ' + why + ')'
msg = msg + ' (待人类确认)'
print(msg)
PYEOF
    ;;

  confirm)
    python3 << PYEOF
import json
with open('$TASKS_FILE') as f:
    d = json.load(f)
if not d.get('next_action'):
    print('没有待确认的下一步行动', file=__import__('sys').stderr)
    exit(1)
d['next_action_confirmed'] = True
d['updated_at'] = '$NOW'
with open('$TASKS_FILE', 'w') as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
print('已确认: ' + d['next_action'])
PYEOF
    ;;

  risk)
    RISK="${2:-}"
    if [ -z "$RISK" ]; then echo "用法: task-board.sh risk \"风险说明\"" >&2; exit 1; fi
    python3 << PYEOF
import json
with open('$TASKS_FILE') as f:
    d = json.load(f)
risks = d.get('known_risks', [])
risks.append('$RISK')
d['known_risks'] = risks
d['updated_at'] = '$NOW'
with open('$TASKS_FILE', 'w') as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
print('添加风险: $RISK')
PYEOF
    ;;

  status)
    python3 << PYEOF
import json, sys
with open('$TASKS_FILE') as f:
    d = json.load(f)
tasks = d.get('tasks', [])
total = len(tasks)
done = len([t for t in tasks if t.get('status') == 'completed'])
prog = len([t for t in tasks if t.get('status') == 'in_progress'])
pend = len([t for t in tasks if t.get('status') == 'pending'])
blkd = len([t for t in tasks if t.get('status') == 'blocked'])
confirmed = d.get('next_action_confirmed', False)
mark = ' [已确认]' if confirmed else ' [待确认]'
print('任务: {} 总 | {} 完成 | {} 进行中 | {} 待开始 | {} 阻塞'.format(total, done, prog, pend, blkd))
print('下一步: {}{}'.format(d.get('next_action', '--'), mark))
if d.get('known_risks'):
    print('已知风险: ' + ' | '.join(d['known_risks']))
PYEOF
    ;;

  *)
    echo "用法: bash scripts/workflow/wf-task-board.sh <命令> [参数]"
    echo ""
    echo "命令:"
    echo "  list                列出所有任务"
    echo "  add \"描述\" [\"理由\"]  新增任务（带原因）"
    echo "  done <id>           标记完成"
    echo "  block <id> \"原因\"   标记阻塞"
    echo "  take <id>           认领任务"
    echo "  next \"行动\" [\"理由\"] 设置下一步（重置确认状态）"
    echo "  confirm             人类确认下一步方向"
    echo "  risk \"风险\"         添加已知风险"
    echo "  status              看板摘要"
    ;;
esac
