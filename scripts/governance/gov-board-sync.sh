#!/usr/bin/env bash
# scripts/governance/gov-board-sync.sh
# 合并后的一键派生物同步（DEC-063/065）：gov-validate --fix + 注册表同步 + 自动提交
#
# 用法:
#   bash scripts/governance/gov-board-sync.sh          # 修复计数并在有变更时提交
#   bash scripts/governance/gov-board-sync.sh --dry    # 只修复不提交
#
# 背景: 多分支各自 take 任务时, .ai/tasks.json 计数副本在合并后必然脱节
# （GOV-DOCS-004）。此前每个分支手动重算 + 重提交, DEC-063 起:
# 特性分支上 004 降级 WARN, 合并后用本脚本在主干一次同步。
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
cd "$ROOT"

bash "$DIR/gov-validate.sh" --fix
# 注册表是派生数据：磁盘测试为真源，合并后统一补齐/清理（DEC-065）
python3 "$DIR/gov-registry-sync.py" --root "$ROOT"

DRY=0
if [ "${1:-}" = "--dry" ]; then
  DRY=1
fi

SCOPE=(
  docs/ai/lifecycle/03_TaskGraph.md
  docs/ai/lifecycle/06_Metrics.md
  docs/index.md
  tests/android-test-registry.yaml
)

if git diff --quiet -- "${SCOPE[@]}"; then
  echo "gov-board-sync: 计数已是最新，无需提交"
  exit 0
fi

git add -f "${SCOPE[@]}"
if [ "$DRY" -eq 1 ]; then
  echo "gov-board-sync(--dry): 已修复但未提交, 变更如下:"
  git diff --cached --stat
  exit 0
fi

if git commit -m "chore(lifecycle): auto-sync board counts after merge (gov-board-sync)" > /dev/null 2>&1; then
  echo "gov-board-sync: 已提交计数同步"
else
  echo "gov-board-sync: 修复完成但提交被钩子拦截（可能需 ses-claim-staged.sh 声明归属）" >&2
  exit 1
fi
