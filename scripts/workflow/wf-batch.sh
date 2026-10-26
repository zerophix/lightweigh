#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-batch.sh
# 批次执行入口：一次处理同一批任务，而不是一条一条来。
#
# 模型（三件事互相决定）：
#   来源 source  →  决定任务类型与角色 role
#     dec        决策拆出来的执行项        dev / gov
#     prototype  原型缺口                 dev
#     incident   故障复盘                dev / gov
#     gap        对照缺口                dev
#     review     评审结论                gov / docs
#   角色 role    →  决定开发期开关与测试策略
#     dev   关钩子，跑受影响单测 + 增量编译
#     test  关钩子，跑受影响 + 全量 pytest
#     docs  关钩子，只跑 gov-validate
#     gov   开钩子，跑全量 pytest / local-ci
#     review 只读，不跑测试
#   批次 batch   →  一次开工/收工的单位
#
# 用法:
#   wf-batch.sh list                 # 所有批次进度
#   wf-batch.sh status <batch>       # 批次内明细 + 下一步
#   wf-batch.sh start <batch>        # 按角色切换模式并打印测试计划
#   wf-batch.sh finish <batch>       # 校验全部完成后跑对应验证并恢复 full
#
# 环境:
#   WF_BATCH_ROOT  覆盖仓库根（测试用）
#   WF_BATCH_DRY=1 只打印、不切换 git 模式
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${WF_BATCH_ROOT:-$(cd "$DIR/../.." && pwd)}"
BOARD="$ROOT/.ai/tasks.json"
cd "$ROOT" || exit 2

[ -f "$BOARD" ] || { echo "找不到任务板: $BOARD" >&2; exit 2; }

BOARD="$BOARD" python3 - "$@" <<'PY'
import json, os, sys
from collections import Counter, defaultdict

board = json.load(open(os.environ["BOARD"], encoding="utf-8"))
tasks = [t for t in board.get("tasks", []) if not t.get("merged_into") and not t.get("archived")]
by_batch = defaultdict(list)
for t in tasks:
    by_batch[t.get("batch") or "B-untracked"].append(t)

cmd = sys.argv[1] if len(sys.argv) > 1 else "list"
batches = {b["id"]: b for b in board.get("batches", [])}

TEST_PLAN = {
    "dev": ["./gradlew :app:testDebugUnitTest", "bash scripts/governance/gov-affected.sh --run"],
    "test": ["bash scripts/testing/gov-pytest.sh --all"],
    "docs": ["bash scripts/governance/gov-validate.sh"],
    "gov": ["bash scripts/governance/gov-local-ci.sh"],
    "review": [],
}

def summarize(items):
    total = len(items)
    done = sum(1 for t in items if t.get("status") in ("completed", "done"))
    roles = Counter(t.get("role", "dev") for t in items)
    return total, done, roles

if cmd == "create":
    if len(sys.argv) < 3:
        print("用法: wf-batch.sh create <batch-id> [标题]", file=sys.stderr)
        sys.exit(2)
    bid = sys.argv[2]
    title = sys.argv[3] if len(sys.argv) > 3 else bid
    if bid in batches:
        print(f"已存在: {bid}", file=sys.stderr)
        sys.exit(1)
    board.setdefault("batches", []).append({"id": bid, "title": title, "tasks": []})
    json.dump(board, open(os.environ["BOARD"], "w", encoding="utf-8"), indent=2, ensure_ascii=False)
    print(f"创建批次 {bid}（{title}）")
    sys.exit(0)

if cmd == "list":
    for bid in sorted(by_batch):
        total, done, roles = summarize(by_batch[bid])
        role_txt = ",".join(f"{k}:{v}" for k, v in roles.most_common())
        print(f"{bid:18s} {done:2d}/{total:2d}  角色[{role_txt}]")
    sys.exit(0)

if len(sys.argv) < 3:
    print("用法: wf-batch.sh {list|status|start|finish} <batch>", file=sys.stderr)
    sys.exit(2)

bid = sys.argv[2]
items = by_batch.get(bid)
if not items:
    print(f"未找到批次 {bid}（可用: {', '.join(sorted(by_batch))}）", file=sys.stderr)
    sys.exit(2)

total, done, roles = summarize(items)

if cmd == "status":
    print(f"批次 {bid}: {done}/{total} 完成")
    for t in sorted(items, key=lambda x: x["id"]):
        mark = "✓" if t.get("status") in ("completed", "done") else "·"
        print(f"  {mark} {t['id']} [{t.get('role','dev')}] {(t.get('title') or t.get('description') or '')[:50]}")
    pending = [t for t in items if t.get("status") not in ("completed", "done")]
    if not pending:
        print("  批次已全部完成")
    sys.exit(0)

if cmd == "start":
    print(f"批次 {bid}: {done}/{total} 完成，角色 {dict(roles)}")
    plan = []
    for role in roles:
        for step in TEST_PLAN.get(role, []):
            if step not in plan:
                plan.append(step)
    print("测试计划（按角色合并去重）:")
    for step in plan:
        print("  - " + step)
    mode = "full" if "gov" in roles else "dev"
    if os.environ.get("WF_BATCH_DRY") == "1":
        print(f"[dry] 将切换到 mode={mode}")
    else:
        os.system(f"bash scripts/dev/mode.sh {mode} >/dev/null")
        print(f"已切换 mode={mode}")
    sys.exit(0)

if cmd == "finish":
    pending = [t for t in items if t.get("status") not in ("completed", "done")]
    if pending:
        print(f"拒绝收工: {len(pending)} 条未完成 -> {', '.join(t['id'] for t in pending[:8])}", file=sys.stderr)
        sys.exit(1)
    plan = []
    for role in roles:
        for step in TEST_PLAN.get(role, []):
            if step not in plan:
                plan.append(step)
    print(f"批次 {bid} 全部完成，验证 {len(plan)} 步")
    if os.environ.get("WF_BATCH_DRY") == "1":
        for step in plan:
            print("  [dry] " + step)
        print("[dry] 将跑 gov-workspace-doctor --fix 并恢复 mode=full")
        sys.exit(0)
    failed = 0
    for step in plan:
        print(f"  → {step}")
        rc = os.system(step)
        if rc != 0:
            print(f"  ✗ 失败: {step}", file=sys.stderr)
            failed = 1
            break
    if failed:
        print("验证未通过，保持当前模式（不恢复 full）", file=sys.stderr)
        sys.exit(1)
    # 收工即清理：worktree / 已合入分支 / 孤儿目录
    os.system("bash scripts/governance/gov-workspace-doctor.sh --fix >/dev/null 2>&1")
    os.system("bash scripts/dev/mode.sh full >/dev/null")
    print("验证通过，工作区已清理，已恢复 mode=full")
    sys.exit(0)

print(f"未知命令: {cmd}", file=sys.stderr)
sys.exit(2)
PY
