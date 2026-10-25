#!/usr/bin/env bash
# scripts/dev/who-is-working.sh
# 看当前所有 worktree 在干什么：分支、最近提交、脏文件数、改到的模块、治理开关状态。
# 目的：多 AI 并发时，先看清楚别人占了哪些模块，再挑不重叠的活。
set -uo pipefail

cd "$(git rev-parse --show-toplevel)" || exit 2
git fetch origin --quiet 2>/dev/null || true

SHARED_PATTERNS="AGENTS.md .ai/tasks.json docs .github tests/android-test-registry.yaml"

printf '%-28s %-10s %-8s %s\n' "WORKTREE" "BRANCH" "DIRTY" "TOUCHED / MODE"

git worktree list --porcelain | awk '
  /^worktree /{wt=$2}
  /^branch /{print wt"\t"$2}
  /^detached/{print wt"\t(detached)"}
' | while IFS=$'\t' read -r wt br; do
  [ -n "$wt" ] || continue
  name="$(basename "$wt")"
  branch="${br#refs/heads/}"
  dirty="$(git -C "$wt" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
  # 相对 origin/master 改到的模块（前两段）
  touched="$(git -C "$wt" diff --name-only "origin/master...HEAD" 2>/dev/null \
    | awk -F/ 'NF>1{print $1"/"$2} NF==1{print $1}' | sort -u | paste -sd, -)"
  [ -n "$touched" ] || touched="-"
  # 是否动到共享文件
  shared=""
  if git -C "$wt" diff --name-only "origin/master...HEAD" 2>/dev/null | grep -qE '^(AGENTS\.md|\.ai/tasks\.json|docs/|\.github/|tests/android-test-registry\.yaml)'; then
    shared=" ⚠共享文件"
  fi
  # 该 worktree 自己的治理开关
  mode="on"
  f="$wt/.ai/gov-mode.json"
  if [ -f "$f" ]; then
    m="$(sed -n 's/.*"mode"[[:space:]]*:[[:space:]]*"\([a-z]*\)".*/\1/p' "$f" | head -1)"
    u="$(sed -n 's/.*"until_epoch"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p' "$f" | head -1)"
    if [ "$m" = "off" ] && [ -n "$u" ] && [ "$(date +%s)" -lt "$u" ]; then mode="off"; fi
  fi
  printf '%-28s %-10s %-8s %s [gov:%s]%s\n' \
    "$name" "${branch:0:10}" "$dirty" "${touched:0:48}" "$mode" "$shared"
done

echo
echo "提示：TOUCHED 有重叠就容易冲突；⚠共享文件 应只由一个人改。"
