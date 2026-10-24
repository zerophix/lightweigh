#!/usr/bin/env bash
# scripts/governance/gov-post-merge-check.sh
# 批次合入后的主干复核（DEC-067）
#
# 背景：autopilot 逐个 PR 合入时都在**分支上**跑 local-ci；合并后没有人验证
# "master 这个组合"是否仍然健康。多个分支各自绿、合在一起红（编译/测试冲突、
# 注册表/计数漂移）是典型隐形故障，且没有 CI 兜底（账单停摆）。
#
# 做法：为 origin/master 建一个临时 worktree（不污染主树），跑全量 local-ci，结束后删除。
# 用法:
#   gov-post-merge-check.sh [--quick] [--keep] [--lane A|B|C|full]
#     --quick  只跑 gov-validate（秒级）
#     --keep   保留临时 worktree 供排查
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
cd "$ROOT" || exit 1

QUICK=0; KEEP=0; LANE="full"
while [ $# -gt 0 ]; do
  case "$1" in
    --quick) QUICK=1; shift ;;
    --keep) KEEP=1; shift ;;
    --lane) LANE="${2:-full}"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

git fetch -q origin 2>/dev/null || true
sha="$(git rev-parse origin/master)"
short="$(git rev-parse --short origin/master)"
tmp="$ROOT/../$(basename "$ROOT")-postmerge-$$"

echo "=== 主干复核 origin/master @ $short ==="
git worktree add --detach "$tmp" "$sha" >/dev/null 2>&1 || { echo "无法创建临时 worktree" >&2; exit 2; }

cleanup() {
  if [ "$KEEP" -eq 1 ]; then
    echo "保留临时 worktree: $tmp"
  else
    cd "$ROOT" 2>/dev/null || true
    git worktree remove --force "$tmp" >/dev/null 2>&1
    rm -rf "$tmp"
    git worktree prune
  fi
}
trap cleanup EXIT

# local.properties 是 gitignore 的本机文件，临时树需要它
if [ -f "$ROOT/local.properties" ] && [ ! -f "$tmp/local.properties" ]; then
  cp "$ROOT/local.properties" "$tmp/local.properties"
fi

cd "$tmp" || exit 2

if [ "$QUICK" -eq 1 ]; then
  echo "--- quick: gov-validate ---"
  bash scripts/governance/gov-validate.sh
  rc=$?
else
  echo "--- full local-ci (GOV_LANE=$LANE) ---"
  GOV_LANE="$LANE" bash scripts/governance/gov-local-ci.sh
  rc=$?
fi

if [ "$rc" -eq 0 ]; then
  echo "主干复核通过（origin/master @ ${short}）"
else
  echo "主干复核失败（origin/master @ ${short}）—— 建议: gh pr revert <出问题的PR> 或回滚后重跑" >&2
fi
exit "$rc"
