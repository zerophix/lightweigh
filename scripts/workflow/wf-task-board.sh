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

ROOT="${WF_BOARD_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
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
    DESC=""; WHY=""; GROUP=""; DISTINCT=""; SUPERSEDES=""
    shift || true
    while [ $# -gt 0 ]; do
      case "$1" in
        --group) GROUP="${2:-}"; shift 2 ;;
        --distinct) DISTINCT="${2:-}"; shift 2 ;;
        --supersedes) SUPERSEDES="${2:-}"; shift 2 ;;
        -*) echo "未知参数: $1" >&2; exit 2 ;;
        *)
          if [ -z "$DESC" ]; then DESC="$1"
          elif [ -z "$WHY" ]; then WHY="$1"
          else echo "多余参数: $1" >&2; exit 2; fi
          shift ;;
      esac
    done
    if [ -z "$DESC" ] || [ -z "$GROUP" ]; then
      echo '用法: task-board.sh add "描述" ["理由"] --group <id> [--distinct "与既有任务的差异" | --supersedes T-xxx]' >&2
      echo '组见 .ai/tasks.json 的 groups；重复建单会被拒绝。' >&2
      exit 1
    fi
    TASKS_FILE="$TASKS_FILE" NOW="$NOW" ADD_DESC="$DESC" ADD_WHY="$WHY" \
    ADD_GROUP="$GROUP" ADD_DISTINCT="$DISTINCT" ADD_SUPERSEDES="$SUPERSEDES" \
    python3 - <<'PYEOF'
import json, os, re, sys

path = os.environ["TASKS_FILE"]
with open(path, encoding="utf-8") as f:
    d = json.load(f)

groups = {g["id"]: g for g in d.get("groups", [])}
group = os.environ["ADD_GROUP"]
if group not in groups:
    print(f"拒绝: 未知分组 {group}（可用: {', '.join(sorted(groups))}）", file=sys.stderr)
    sys.exit(1)

def grams(text):
    s = "".join(re.findall(r"[\u4e00-\u9fffA-Za-z0-9]", text))
    return {s[i:i + 2] for i in range(len(s) - 1)}

desc = os.environ["ADD_DESC"]
distinct = os.environ["ADD_DISTINCT"]
supersedes = os.environ["ADD_SUPERSEDES"]
g_new = grams(desc)

best = (0.0, None)
for t in d.get("tasks", []):
    if t.get("merged_into") or t.get("archived"):
        continue
    g_old = grams((t.get("title") or "") + " " + (t.get("description") or ""))
    if not g_old or not g_new:
        continue
    j = len(g_new & g_old) / len(g_new | g_old)
    if j > best[0]:
        best = (j, t)

if best[1] is not None and best[0] >= 0.6 and not distinct and not supersedes:
    print(f"拒绝: 与 {best[1]['id']} 相似度 {best[0]:.0%}", file=sys.stderr)
    print(f"  既有: {(best[1].get('title') or best[1].get('description') or '')[:60]}", file=sys.stderr)
    print("  如确是不同交付物: 加 --distinct \"差异是什么\"", file=sys.stderr)
    print("  如替代旧任务: 加 --supersedes " + best[1]["id"], file=sys.stderr)
    sys.exit(1)

tasks = d.get("tasks", [])
max_num = 0
for t in tasks:
    try:
        max_num = max(max_num, int(t.get("id", "T-000").split("-")[1]))
    except Exception:
        pass
nid = "T-{:03d}".format(max_num + 1)

if supersedes:
    old = next((t for t in tasks if t["id"] == supersedes), None)
    if old is None:
        print(f"拒绝: --supersedes {supersedes} 不存在", file=sys.stderr)
        sys.exit(1)
    if old.get("merged_into"):
        print(f"拒绝: {supersedes} 已经是重复项", file=sys.stderr)
        sys.exit(1)
    old["merged_into"] = nid
    old["notes"] = (old.get("notes") or "") + f"；被 {nid} 取代"

tasks.append({
    "id": nid,
    "description": desc,
    "why": os.environ["ADD_WHY"],
    "status": "pending",
    "group_id": group,
    "assigned_to": "",
    "dec": "",
    "created_at": os.environ["NOW"],
    "updated_at": os.environ["NOW"],
    "notes": ("差异: " + distinct) if distinct else "",
})

d["tasks"] = tasks
d["updated_at"] = os.environ["NOW"]
with open(path, "w", encoding="utf-8") as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
print(f"创建: {nid}（组 {group}）")
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
