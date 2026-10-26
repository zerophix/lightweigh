#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-board-audit.sh
# 任务板审计：分组完整性、重复任务、元数据缺口、合并链一致性。
#
# 用法:
#   wf-board-audit.sh              # 摘要 + 问题清单
#   wf-board-audit.sh --json       # 机器可读
#   wf-board-audit.sh --strict     # 有硬问题即退出 1（CI/合入前用）
#
# 硬问题（strict 下失败）:
#   - merged_into 指向不存在的任务，或形成链
#   - 有效任务缺少 target_files
#   - 有效任务 target_files 高度重叠（Jaccard >= 0.5）且未声明 overlaps_with
#   - 任务没有 group_id 或 group_id 不在 groups 索引里
#
# 软债务（只报告）:
#   - 历史任务缺 acceptance
#   - 没有 verify_commands
#
# 退出码: 0 / 1（strict 且有硬问题）
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${WF_BOARD_ROOT:-$(cd "$DIR/../.." && pwd)}"
BOARD="$ROOT/.ai/tasks.json"

[ -f "$BOARD" ] || { echo "找不到任务板: $BOARD" >&2; exit 2; }

MODE="text"
STRICT=0
for arg in "$@"; do
  case "$arg" in
    --json) MODE="json" ;;
    --strict) STRICT=1 ;;
    -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
  esac
done

BOARD="$BOARD" MODE="$MODE" python3 - <<'PY'
import json, os, sys
from collections import Counter, defaultdict

board = json.load(open(os.environ["BOARD"], encoding="utf-8"))
tasks = board.get("tasks", [])
groups = {g["id"]: g for g in board.get("groups", [])}
by_id = {t["id"]: t for t in tasks}
active = [t for t in tasks if not t.get("merged_into") and not t.get("archived")]

errors, debts = [], []

# 1) 合并链
for t in tasks:
    target = t.get("merged_into")
    if not target:
        continue
    if target not in by_id:
        errors.append(f"{t['id']} merged_into 指向不存在的任务 {target}")
    elif by_id[target].get("merged_into"):
        errors.append(f"{t['id']} → {target} → 又指向别的任务（链）")

# 2) 分组
for t in active:
    gid = t.get("group_id")
    if not gid:
        errors.append(f"{t['id']} 缺 group_id")
    elif gid not in groups:
        errors.append(f"{t['id']} 的 group_id={gid} 不在 groups 索引")

# 3) 元数据
for t in active:
    if not t.get("target_files"):
        errors.append(f"{t['id']} 缺 target_files")
    if not t.get("acceptance"):
        debts.append(f"{t['id']} 缺 acceptance（历史批次）")
    elif not (t.get("acceptance") or {}).get("verify_commands"):
        debts.append(f"{t['id']} 无 verify_commands")

# 4) 重复：有效任务之间的 target_files 重叠
byfile = defaultdict(set)
for t in active:
    for f in (t.get("target_files") or []):
        byfile[f].add(t["id"])
overlap_pairs = []
for f, ids in byfile.items():
    ids = sorted(ids)
    for i in range(len(ids)):
        for j in range(i + 1, len(ids)):
            overlap_pairs.append((ids[i], ids[j], f))
seen = set()
for a, b, f in overlap_pairs:
    key = tuple(sorted((a, b)))
    if key in seen:
        continue
    fa = set(by_id[a].get("target_files") or [])
    fb = set(by_id[b].get("target_files") or [])
    jac = len(fa & fb) / len(fa | fb) if (fa | fb) else 0
    if jac < 0.5:
        continue
    seen.add(key)
    declared_a = set(by_id[a].get("overlaps_with") or [])
    declared_b = set(by_id[b].get("overlaps_with") or [])
    if b in declared_a or a in declared_b:
        continue
    errors.append(f"{a} 与 {b} 的 target_files 重叠 {jac:.0%}（共享 {f.split('/')[-1]}）且未声明 overlaps_with")

summary = {
    "total": len(tasks),
    "active": len(active),
    "merged": sum(1 for t in tasks if t.get("merged_into")),
    "archived": sum(1 for t in tasks if t.get("archived")),
    "status": dict(Counter(t.get("status") for t in tasks)),
    "groups": {gid: len(g.get("tasks", [])) for gid, g in groups.items()},
    "errors": errors,
    "debts": debts,
    "ok": not errors,
}

if os.environ.get("MODE") == "json":
    print(json.dumps(summary, ensure_ascii=False, indent=2))
else:
    print(f"任务 {summary['total']} 条：有效 {summary['active']}，重复合并 {summary['merged']}，归档 {summary['archived']}")
    print("状态: " + ", ".join(f"{k}={v}" for k, v in summary["status"].items()))
    print(f"分组 {len(groups)} 个: " + ", ".join(f"{k}({v})" for k, v in sorted(summary["groups"].items())))
    print(f"\n硬问题: {len(errors)}")
    for e in errors:
        print("  ✗ " + e)
    print(f"软债务: {len(debts)}")
    for d in debts[:12]:
        print("  · " + d)
    if len(debts) > 12:
        print(f"  · …共 {len(debts)} 条")
    print("\n结论: " + ("PASS" if not errors else "FAIL"))

sys.exit(0 if not errors else 1)
PY
RC=$?

if [ "$STRICT" = "1" ] && [ "$RC" = "1" ]; then
  exit 1
fi
exit 0
