#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-board-audit.sh
# 任务板缺口审计（DEC-075）：先让板子反映真实工作，再谈角色化。
#
# 背景：83 条任务里 23 条没有 target_files，59 条是 code，而实际提交中
# 纯代码只占 5% —— 板子与工作脱节，给这种板子做角色化是在给虚构建模。
#
# 用法:
#   wf-board-audit.sh              # 摘要 + 缺口清单
#   wf-board-audit.sh --json       # 机器可读
#   wf-board-audit.sh --strict     # 有缺口即退出 1（CI 用）
#
# 退出码: 0 无缺口或非 strict / 1 strict 下存在缺口
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${WF_BOARD_ROOT:-$(cd "$DIR/../.." && pwd)}"
BOARD="$ROOT/.ai/tasks.json"

[ -f "$BOARD" ] || { echo "找不到任务板: $BOARD" >&2; exit 2; }

MODE="text"
[ "${1:-}" = "--json" ] && MODE="json"
STRICT=0
[ "${1:-}" = "--strict" ] && STRICT=1

BOARD="$BOARD" MODE="$MODE" python3 - <<'PY'
import json, os, sys
from collections import Counter

board = json.load(open(os.environ["BOARD"], encoding="utf-8"))
tasks = board.get("tasks", [])

def kind(t):
    files = t.get("target_files") or []
    import re
    if not files:
        return "unclassified"
    kinds = set()
    for p in files:
        if p.startswith(("docs/", "design/")) or p.endswith(".md"):
            kinds.add("docs")
        elif p.startswith(("scripts/", ".github/")):
            kinds.add("gov")
        elif p.startswith("tests/") or "/src/test/" in p:
            kinds.add("test")
        elif re.match(r"^(app|domain|application|infrastructure)/", p):
            kinds.add("code")
        else:
            kinds.add("other")
    return kinds.pop() if len(kinds) == 1 else "mixed"

missing_files = [t["id"] for t in tasks if not (t.get("target_files") or [])]
missing_ac = [t["id"] for t in tasks if not t.get("acceptance")]
draft_ac = [t["id"] for t in tasks
            if t.get("status") == "pending"
            and (t.get("acceptance") or {}).get("status") == "draft"]
kinds = Counter(kind(t) for t in tasks)

if os.environ.get("MODE") == "json":
    print(json.dumps({
        "total": len(tasks),
        "kinds": dict(kinds),
        "missing_target_files": missing_files,
        "missing_acceptance": missing_ac,
        "pending_with_draft_ac": draft_ac,
    }, ensure_ascii=False, indent=2))
else:
    print(f"任务总数: {len(tasks)}")
    print("按 target_files 分类: " + ", ".join(f"{k}={v}" for k, v in kinds.most_common()))
    print(f"缺 target_files: {len(missing_files)}")
    if missing_files:
        print("  " + ", ".join(missing_files[:20]) + (" ..." if len(missing_files) > 20 else ""))
    print(f"缺 acceptance: {len(missing_ac)}")
    if missing_ac:
        print("  " + ", ".join(missing_ac[:20]) + (" ..." if len(missing_ac) > 20 else ""))
    print(f"pending 但 AC 仍为 draft（按规则不可开工）: {len(draft_ac)}")
    if draft_ac:
        print("  " + ", ".join(draft_ac))

sys.exit(1 if (missing_files or draft_ac) else 0)
PY
RC=$?

if [ "$STRICT" = "1" ] && [ "$RC" = "1" ]; then
  echo ""
  echo "STRICT: 任务板存在缺口（缺 target_files / pending 仍 draft）"
  exit 1
fi
exit 0
