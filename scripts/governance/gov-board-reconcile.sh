#!/usr/bin/env bash
# scripts/governance/gov-board-reconcile.sh
# 任务板回填（DEC-067）：已合入的 PR 对应任务自动置 completed。
#
# 背景：合并后没人回填任务板是"隐形漂移"：任务仍停在 in_progress/pending，
# wf-parallel 可能把已交付的任务再次派发 → 重复实现（2026-10-24 实际发生 4 次）。
# 本脚本以 GitHub 的合并事实为准回填，是**单一写者**的一段。
#
# 用法:
#   gov-board-reconcile.sh [--since-days N] [--dry] [--json]
#
# 规则:
#   - 读取最近合并的 PR（默认 30 天），从 title/body 提取 T-xxx
#   - 仅当任务当前状态为 pending/in_progress 且 acceptance.status=ready 时置 completed
#   - 附带 notes 记录 PR 号与合入日期；不改其它字段，不删除任务
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${WF_AUTOPILOT_ROOT:-$(cd "$DIR/../.." && pwd)}"
cd "$ROOT" || exit 1

DAYS=30; DRY=0; JSON=0; BRANCHES=""
while [ $# -gt 0 ]; do
  case "$1" in
    --branches) BRANCHES="${2:-}"; shift 2 ;;
    --since-days) DAYS="${2:-30}"; shift 2 ;;
    --dry) DRY=1; shift ;;
    --json) JSON=1; shift ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

# 拉取已合并 PR（title + body + number + mergedAt）
PRS_JSON="$(
  gh pr list --state merged --limit 100 \
    --json number,title,body,mergedAt 2>/dev/null \
    | python3 -c "
import json,sys,datetime
days=int(sys.argv[1]) if len(sys.argv)>1 else 30
raw=sys.stdin.read()
try: data=json.loads(raw) if raw.strip() else []
except Exception: data=[]
cut=datetime.datetime.now(datetime.timezone.utc)-datetime.timedelta(days=days)
out=[]
for p in data:
    ts=p.get('mergedAt') or ''
    try: dt=datetime.datetime.fromisoformat(ts.replace('Z','+00:00'))
    except Exception: dt=None
    if dt and dt < cut: continue
    out.append(p)
print(json.dumps(out))
" "$DAYS"
)"

DRY="$DRY" JSON="$JSON" PRS_JSON="$PRS_JSON" BRANCHES="$BRANCHES" python3 - <<'PY'
import json, os, re, sys
from pathlib import Path

root = Path.cwd()
tasks_path = root / ".ai" / "tasks.json"
prs = json.loads(os.environ.get("PRS_JSON") or "[]")
dry = os.environ.get("DRY") == "1"
as_json = os.environ.get("JSON") == "1"

data = json.loads(tasks_path.read_text(encoding="utf-8"))
tasks = {t["id"]: t for t in data.get("tasks", [])}

# PR → 任务号（title/body 里的 T-xxx）
# 分支名里带任务号（feat/t085-xxx）→ 队列合并的唯一可靠交付依据
branch_ids = set()
for b in (os.environ.get("BRANCHES") or "").split():
    branch_ids.update(re.findall(r"[tT]-?(\d{3})", b))
    branch_ids.update("T-" + m for m in re.findall(r"[tT]-?(\d{3})", b))

pr_task = {}
for p in prs:
    title = p.get('title', '') or ''
    body = p.get('body', '') or ''
    # 只认"交付声明"：标题里的任务号，或正文中明确写"关联任务"的那一行。
    # 曾经的 bug：整段正文任意提及 T-xxx（如"与并行任务 T-093/T-094 无交集"）
    # 都会被当成已交付 → 任务被误标 completed（2026-10-24 实测）。
    ids = set(re.findall(r"\bT-\d{3}\b", title))
    for line in body.splitlines():
        if ('关联任务' in line or 'related task' in line.lower()):
            # 排除范围/泛指写法：T-072~T-105、"各 T-xxx" 之类不是交付声明
            if any(mark in line for mark in ('~', '～', '—', '各')):
                continue
            ids.update(re.findall(r"\bT-\d{3}\b", line))
    for tid in ids:
        pr_task.setdefault(tid, p)

updated = []
for tid in sorted(branch_ids):
    t = tasks.get(tid)
    if not t or t.get("status") not in ("pending", "in_progress"):
        continue
    acc = t.get("acceptance") or {}
    if acc.get("status") != "ready":
        continue
    t["status"] = "completed"
    t["notes"] = (t.get("notes") or "") + "\n已由合并队列按分支名回填（DEC-071）"
    updated.append((tid, 0, "queue"))

for tid, p in sorted(pr_task.items()):
    t = tasks.get(tid)
    if not t:
        continue
    if t.get("status") not in ("pending", "in_progress"):
        continue
    acc = t.get("acceptance") or {}
    if acc.get("status") != "ready":
        continue
    ts = (p.get("mergedAt") or "")[:10]
    t["status"] = "completed"
    t["updated_at"] = p.get("mergedAt") or t.get("updated_at")
    note = f"已合入 master（PR #{p.get('number')}，{ts}）；由 gov-board-reconcile 依据合并事实回填"
    t["notes"] = note if not t.get("notes") else f"{t['notes']}\n{note}"
    updated.append((tid, p.get("number"), ts))

if not updated:
    print("[board-reconcile] 无需回填（任务板与合并事实一致）")
    sys.exit(0)

if as_json:
    print(json.dumps({"updated": [{"task": x[0], "pr": x[1], "merged": x[2]} for x in updated]},
                     ensure_ascii=False, indent=2))
else:
    for tid, n, ts in updated:
        print(f"[board-reconcile] {tid} ← PR #{n} ({ts}) 置 completed")

if dry:
    print("[board-reconcile] --dry：未写文件")
    sys.exit(0)

tasks_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"[board-reconcile] 已写入 .ai/tasks.json（{len(updated)} 条）")
PY
