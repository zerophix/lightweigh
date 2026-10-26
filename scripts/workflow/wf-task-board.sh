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
    DESC=""; WHY=""; GROUP=""; DISTINCT=""; SUPERSEDES=""; DEPENDS=""; PARENT=""; PRIORITY="P2"; SOURCE=""; SOURCE_REF=""; BATCH=""; FILES=""; ROLE=""
    shift || true
    while [ $# -gt 0 ]; do
      case "$1" in
        --group) GROUP="${2:-}"; shift 2 ;;
        --distinct) DISTINCT="${2:-}"; shift 2 ;;
        --supersedes) SUPERSEDES="${2:-}"; shift 2 ;;
        --depends-on) DEPENDS="${2:-}"; shift 2 ;;
        --parent) PARENT="${2:-}"; shift 2 ;;
        --priority) PRIORITY="${2:-P2}"; shift 2 ;;
        --source) SOURCE="${2:-}"; shift 2 ;;
        --source-ref) SOURCE_REF="${2:-}"; shift 2 ;;
        --batch) BATCH="${2:-}"; shift 2 ;;
        --files) FILES="${2:-}"; shift 2 ;;
        --role) ROLE="${2:-}"; shift 2 ;;
        -*) echo "未知参数: $1" >&2; exit 2 ;;
        *)
          if [ -z "$DESC" ]; then DESC="$1"
          elif [ -z "$WHY" ]; then WHY="$1"
          else echo "多余参数: $1" >&2; exit 2; fi
          shift ;;
      esac
    done
    if [ -z "$DESC" ] || [ -z "$GROUP" ] || [ -z "$SOURCE" ] || [ -z "$BATCH" ]; then
      echo '用法: task-board.sh add "描述" ["理由"] --group <id> --source <dec|prototype|incident|gap|review> --batch <B-xxx>' >&2
      echo '       [--source-ref DEC-xxx] [--files a.kt,b.kt] [--role dev|test|docs|gov|review]' >&2
      echo '       [--depends-on T-xxx] [--parent T-xxx] [--priority P0/P1/P2]' >&2
      echo '       [--distinct "与既有任务的差异" | --supersedes T-xxx]' >&2
      echo '组见 .ai/tasks.json 的 groups；重复建单会被拒绝。' >&2
      exit 1
    fi
    TASKS_FILE="$TASKS_FILE" NOW="$NOW" ADD_DESC="$DESC" ADD_WHY="$WHY" \
    ADD_GROUP="$GROUP" ADD_DISTINCT="$DISTINCT" ADD_SUPERSEDES="$SUPERSEDES" \
    ADD_DEPENDS="$DEPENDS" ADD_PARENT="$PARENT" ADD_PRIORITY="$PRIORITY" \
    ADD_SOURCE="$SOURCE" ADD_SOURCE_REF="$SOURCE_REF" ADD_BATCH="$BATCH" \
    ADD_FILES="$FILES" ADD_ROLE="$ROLE" \
    python3 - <<'PYEOF'
import json, os, re, sys

path = os.environ["TASKS_FILE"]
with open(path, encoding="utf-8") as f:
    d = json.load(f)

groups = {g["id"]: g for g in d.get("groups", [])}
batches = {b["id"] for b in d.get("batches", [])}
SOURCE_SET = {"dec", "prototype", "incident", "gap", "review"}
ROLE_SET = {"dev", "test", "docs", "gov", "review"}
source = os.environ.get("ADD_SOURCE", "")
source_ref = os.environ.get("ADD_SOURCE_REF", "")
batch = os.environ.get("ADD_BATCH", "")
files = [x.strip() for x in os.environ.get("ADD_FILES", "").split(",") if x.strip()]
explicit_role = os.environ.get("ADD_ROLE", "")

if source not in SOURCE_SET:
    print(f"拒绝: --source 必须是 {sorted(SOURCE_SET)}", file=sys.stderr)
    sys.exit(1)
if source in ("dec", "review") and not source_ref:
    print(f"拒绝: source={source} 必须提供 --source-ref", file=sys.stderr)
    sys.exit(1)
if batch not in batches:
    print(f"拒绝: 未知批次 {batch}（先 wf-batch.sh create {batch}）", file=sys.stderr)
    sys.exit(1)
if explicit_role and explicit_role not in ROLE_SET:
    print(f"拒绝: --role 必须是 {sorted(ROLE_SET)}", file=sys.stderr)
    sys.exit(1)

def infer_role(paths):
    if not paths:
        return "dev"
    kinds = set()
    for f in paths:
        if f.startswith("scripts/") or f.startswith(".github/"):
            kinds.add("gov")
        elif f.startswith("tests/") or "/src/test/" in f:
            kinds.add("test")
        elif f.startswith("docs/") or f.endswith(".md"):
            kinds.add("docs")
        else:
            kinds.add("dev")
    if kinds == {"gov"}:
        return "gov"
    if kinds == {"docs"}:
        return "docs"
    if kinds == {"test"}:
        return "test"
    return "dev"

role = explicit_role or infer_role(files)
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

depends = [x.strip() for x in os.environ.get("ADD_DEPENDS", "").split(",") if x.strip()]
for dep in depends:
    if dep not in {t["id"] for t in tasks}:
        print(f"拒绝: --depends-on {dep} 不存在", file=sys.stderr)
        sys.exit(1)
parent = os.environ.get("ADD_PARENT", "").strip()
if parent and parent not in {t["id"] for t in tasks}:
    print(f"拒绝: --parent {parent} 不存在", file=sys.stderr)
    sys.exit(1)
if parent and parent in depends:
    print("拒绝: 任务不能依赖自己的父任务", file=sys.stderr)
    sys.exit(1)

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
    "priority": os.environ.get("ADD_PRIORITY", "P2"),
    "source": source,
    **({"source_ref": source_ref} if source_ref else {}),
    "batch": batch,
    "role": role,
    "target_files": files,
    "depends_on": depends,
    **({"parent": parent} if parent else {}),
})

d["tasks"] = tasks
d["updated_at"] = os.environ["NOW"]
with open(path, "w", encoding="utf-8") as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
print(f"创建: {nid}（组 {group}）")
PYEOF
    ;;

  ready)
    TASKS_FILE="$TASKS_FILE" python3 - <<'PY'
import json, os, sys
d = json.load(open(os.environ["TASKS_FILE"], encoding="utf-8"))
tasks = {t["id"]: t for t in d.get("tasks", [])}
done_status = {"completed", "done"}
def open_blockers(t):
    return [x for x in (t.get("depends_on") or [])
            if x in tasks and tasks[x].get("status") not in done_status]
ready = [t for t in tasks.values()
         if t.get("status") in ("pending", "in_progress")
         and not t.get("merged_into") and not t.get("archived")
         and not open_blockers(t)]
if not ready:
    print("没有可开工任务（全部在跑或被阻塞）")
    sys.exit(0)
order = {"P0": 0, "P1": 1, "P2": 2}
for t in sorted(ready, key=lambda x: (order.get(x.get("priority", "P2"), 9), x["id"])):
    print(f"{t['id']}  {(t.get('title') or t.get('description') or '')[:60]}")
PY
    ;;

  blocked)
    TASKS_FILE="$TASKS_FILE" python3 - <<'PY'
import json, os
d = json.load(open(os.environ["TASKS_FILE"], encoding="utf-8"))
tasks = {t["id"]: t for t in d.get("tasks", [])}
done_status = {"completed", "done"}
for t in sorted(tasks.values(), key=lambda x: x["id"]):
    if t.get("merged_into") or t.get("archived"):
        continue
    blockers = [x for x in (t.get("depends_on") or [])
                if x in tasks and tasks[x].get("status") not in done_status]
    if blockers and t.get("status") in ("pending", "in_progress"):
        print(f"{t['id']}  等待 {', '.join(blockers)}")
PY
    ;;

  dep)
    ACTION="${2:-}"
    case "$ACTION" in
      add)
        ID="${3:-}"; ON="${4:-}"
        TASKS_FILE="$TASKS_FILE" DEP_ID="$ID" DEP_ON="$ON" python3 - <<'PY'
import json, os, sys
path = os.environ["TASKS_FILE"]
d = json.load(open(path, encoding="utf-8"))
tasks = {t["id"]: t for t in d.get("tasks", [])}
tid, dep = os.environ["DEP_ID"], os.environ["DEP_ON"]
if tid not in tasks or dep not in tasks:
    print("拒绝: 任务不存在", file=sys.stderr); sys.exit(1)
if tid == dep:
    print("拒绝: 不能依赖自己", file=sys.stderr); sys.exit(1)
tasks[tid].setdefault("depends_on", [])
if dep not in tasks[tid]["depends_on"]:
    tasks[tid]["depends_on"].append(dep)
json.dump(d, open(path, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
print(f"{tid} 现在依赖 {dep}")
PY
        ;;
      cycles)
        TASKS_FILE="$TASKS_FILE" python3 - <<'PY'
import json, os, sys
d = json.load(open(os.environ["TASKS_FILE"], encoding="utf-8"))
tasks = {t["id"]: (t.get("depends_on") or []) for t in d.get("tasks", [])}
state, stack = {}, []
def visit(n):
    state[n] = 1; stack.append(n)
    for m in tasks.get(n, []):
        if state.get(m) == 1:
            print("环: " + " → ".join(stack[stack.index(m):] + [m]))
            return True
        if state.get(m, 0) == 0 and visit(m):
            return True
    stack.pop(); state[n] = 2
    return False
bad = any(visit(n) for n in tasks if state.get(n, 0) == 0)
sys.exit(1 if bad else 0)
PY
        ;;
      *)
        echo "用法: task-board.sh dep add <id> <depends-on> | dep cycles" >&2; exit 2 ;;
    esac
    ;;

  checkpoint)
    ID="${2:-}"; NOTE="${3:-}"
    if [ -z "$ID" ] || [ -z "$NOTE" ]; then
      echo "用法: task-board.sh checkpoint <id> \"进度/决定/下一步\"" >&2; exit 1
    fi
    TASKS_FILE="$TASKS_FILE" NOW="$NOW" CP_ID="$ID" CP_NOTE="$NOTE" python3 - <<'PY'
import json, os, sys
path = os.environ["TASKS_FILE"]
d = json.load(open(path, encoding="utf-8"))
tid = os.environ["CP_ID"]
task = next((t for t in d.get("tasks", []) if t["id"] == tid), None)
if task is None:
    print(f"未找到: {tid}", file=sys.stderr); sys.exit(1)
task.setdefault("progress", []).append({"at": os.environ["NOW"], "note": os.environ["CP_NOTE"]})
json.dump(d, open(path, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
print(f"{tid} 检查点 #{len(task['progress'])}: {os.environ['CP_NOTE']}")
PY
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
