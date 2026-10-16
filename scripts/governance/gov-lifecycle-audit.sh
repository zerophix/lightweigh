#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-lifecycle-audit.sh
# 生命周期审计 — "源码变更必须含 DEC + DevNotes"
#
# 复刻 gov-pre-commit 的源码变更检测逻辑, 供本地与 CI 共用同一份实现。
# CI 中 lifecycle-audit job 以此脚本退出码判定, 不可 --no-verify 绕过。
#
# 用法:
#   bash scripts/governance/gov-lifecycle-audit.sh [--check]   # 审计工作区/HEAD 改动
#   bash scripts/governance/gov-lifecycle-audit.sh --base <ref> # 相对某 ref 审计（CI 用, 默认 HEAD~1）
# ============================================================
set -euo pipefail

_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$_DIR/../.." && pwd)"
cd "$ROOT"

BASE="HEAD~1"
if [ "${1:-}" = "--base" ] && [ -n "${2:-}" ]; then
  BASE="$2"
fi

# 源码目录单一事实源: gov-config.sh 的 SRC_DIRS
SRC_DIRS=""
if [ -f "$ROOT/scripts/governance/gov-config.sh" ]; then
  SRC_DIRS=$(grep -oE '^SRC_DIRS="[^"]*"' "$ROOT/scripts/governance/gov-config.sh" | sed 's/^SRC_DIRS="//;s/"$//')
fi
SRC_DIRS="${SRC_DIRS:-domain application infrastructure app}"

CHANGED=$(git diff --name-only --diff-filter=ACMR "$BASE" 2>/dev/null || true)

CODE_CHANGED=0
for d in $SRC_DIRS; do
  if echo "$CHANGED" | grep -q "^$d/"; then
    CODE_CHANGED=1
  fi
done

if [ "$CODE_CHANGED" -eq 0 ]; then
  echo "  [PASS] 无源码变更（${SRC_DIRS}）, 无需生命周期审计"
  exit 0
fi

echo "  [审计] 检测到源码变更, 校验 DEC + DevNotes 是否随行..."

FAIL=0

DEVNOTES=$(echo "$CHANGED" | grep 'docs/ai/lifecycle/04_DevNotes.md' || true)
if [ -z "$DEVNOTES" ]; then
  echo "  [FAIL] 源码变更但 04_DevNotes.md 未变更（生命周期 M 步骤缺失）"
  FAIL=1
fi

DEC=$(echo "$CHANGED" | grep 'docs/ai/decisions/DEC-' || true)
if [ -z "$DEC" ]; then
  echo "  [FAIL] 源码变更但无 DEC（生命周期 L 步骤缺失）"
  FAIL=1
fi

if [ "$FAIL" -ne 0 ]; then
  echo "  [FAIL] lifecycle-audit 未通过: 源码变更必须同时包含 DEC + DevNotes"
  exit 1
fi

echo "  [PASS] lifecycle-audit 通过（DEC + DevNotes 均已包含）"
exit 0