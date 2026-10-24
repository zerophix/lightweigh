#!/usr/bin/env bash
# scripts/governance/gov-pr-hygiene.sh
# PR 卫生规则引擎（DEC-067）：外部/陈旧 PR 的关闭决策不再靠人拍板。
#
# 决策依据（全部满足才关闭，宁放过不误杀）:
#   1) 不是本地在途分支（没有对应 worktree）—— 我们自己的 PR 永不在此处关闭
#   2) 不是 dependabot/renovate 自动 PR
#   3) PR 已 CONFLICTING 或 创建超过 N 天（默认 3）
#   4) 未关联任务板中 pending/in_progress 的任务（T-xxx）
#
# 关闭理由固定模板：说明"基于哪个旧 master、为何不能直接合、如何重开"。
#
# 用法:
#   gov-pr-hygiene.sh [--dry] [--days N] [--json]
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${WF_AUTOPILOT_ROOT:-$(cd "$DIR/../.." && pwd)}"
cd "$ROOT" || exit 1

DAYS=3; DRY=0; JSON=0
while [ $# -gt 0 ]; do
  case "$1" in
    --dry) DRY=1; shift ;;
    --days) DAYS="${2:-3}"; shift 2 ;;
    --json) JSON=1; shift ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

# 本地在途分支（有 worktree 的）
live_branches="$(git worktree list --porcelain | awk '/^branch refs\/heads\//{sub("refs/heads/","",$2); print $2}')"
# 任务板中未完成的任务号
open_tasks="$(python3 -c "
import json
d=json.load(open('.ai/tasks.json'))
print(' '.join(t['id'] for t in d['tasks'] if t.get('status') in ('pending','in_progress')))
" 2>/dev/null || true)"

PRS="$(gh pr list --state open --json number,title,body,headRefName,author,createdAt,mergeable 2>/dev/null)"
LIVE="$live_branches" OPEN_TASKS="$open_tasks" DAYS="$DAYS" DRY="$DRY" JSON="$JSON" PRS="$PRS" \
python3 - <<'PY'
import datetime, json, os, subprocess, sys

prs = json.loads(os.environ.get("PRS") or "[]")
live = set((os.environ.get("LIVE") or "").split())
open_tasks = set((os.environ.get("OPEN_TASKS") or "").split())
days = int(os.environ.get("DAYS") or 3)
dry = os.environ.get("DRY") == "1"
as_json = os.environ.get("JSON") == "1"

now = datetime.datetime.now(datetime.timezone.utc)
close, keep = [], []

for p in prs:
    num, br = p["number"], p["headRefName"]
    author = (p.get("author") or {}).get("login", "")
    title, body = p.get("title", ""), p.get("body", "")
    text = f"{title}\n{body}"
    reasons = []

    if br in live:
        keep.append((num, br, "本地在途分支，不在本次卫生范围"))
        continue
    if "dependabot" in author or "renovate" in author:
        keep.append((num, br, "依赖机器人 PR，交给它自己维护"))
        continue

    try:
        created = datetime.datetime.fromisoformat(p["createdAt"].replace("Z", "+00:00"))
        age = (now - created).days
    except Exception:
        age = 0

    conflicting = p.get("mergeable") == "CONFLICTING"
    behind = 0
    try:
        base = subprocess.run(["git", "merge-base", f"origin/{br}", "origin/master"],
                              capture_output=True, text=True).stdout.strip()
        if base:
            behind = int(subprocess.run(
                ["git", "rev-list", "--count", f"{base}..origin/master"],
                capture_output=True, text=True).stdout.strip() or 0)
    except Exception:
        behind = 0
    stale_by_divergence = behind > 30
    if not conflicting and not stale_by_divergence and age <= days:
        keep.append((num, br, "近期、无冲突、与 master 未分叉"))
        continue

    # 关联任务只作信息记录：关闭 PR 不删分支，工作不会丢失，可在新分支继续
    hit_tasks = sorted({w for w in open_tasks if w in text})

    if conflicting:
        reasons.append("与当前 master 冲突")
    if age > days:
        reasons.append(f"创建已 {age} 天未推进")
    if stale_by_divergence:
        reasons.append(f"已落后 master {behind} 个提交（基线与当前结构分叉）")

    comment_lines = [
        "关闭原因（自动卫生规则，DEC-067）：",
        f"- {'；'.join(reasons)}",
        "- 该 PR 基于旧 master 构建，与当前治理与代码结构已分叉；直接合入需要大范围冲突解决，"
        "且可能覆盖已有决策",
        "- 若内容仍有价值：请基于最新 master 重开分支"
        "（bash scripts/workflow/wf-autopilot.sh start <T-xxx>），"
        "或先开 NOTE/DEC 说明背景，再走合并队列",
    ]
    if hit_tasks:
        comment_lines.append(f"- 关联任务仍在任务板：{', '.join(hit_tasks)}（请在新分支继续）")
    comment_lines.append("- 本操作可逆：分支保留在远端，可随时重新开 PR")

    close.append({
        "pr": num, "branch": br, "title": title,
        "reasons": reasons,
        "hit_tasks": hit_tasks,
        "comment": "\n".join(comment_lines),
    })

if as_json:
    print(json.dumps({"close": [{k: v for k, v in c.items() if k != "comment"} for c in close],
                      "keep": [{"pr": n, "branch": b, "why": w} for n, b, w in keep]},
                     ensure_ascii=False, indent=2))
else:
    for n, b, w in keep:
        print(f"[保留] #{n} {b} — {w}")
    for c in close:
        print(f"[{'预览' if dry else '关闭'}] #{c['pr']} {c['branch']} — {c['title'][:50]}（{'；'.join(c['reasons'])}）")

if dry:
    print(f"\n--dry：将关闭 {len(close)} 个，保留 {len(keep)} 个")
    sys.exit(0)

failed = 0
for c in close:
    r = subprocess.run(
        ["gh", "pr", "close", str(c["pr"]), "--comment", c["comment"], "--delete-branch=false"],
        capture_output=True, text=True,
    )
    if r.returncode != 0:
        failed += 1
        print(f"[失败] #{c['pr']}: {r.stderr.strip()[:120]}", file=sys.stderr)
print(f"\n已处理：关闭 {len(close) - failed} 个，失败 {failed} 个，保留 {len(keep)} 个")
sys.exit(1 if failed else 0)
PY
