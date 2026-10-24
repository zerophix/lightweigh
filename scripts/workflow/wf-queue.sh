#!/usr/bin/env bash
# scripts/workflow/wf-queue.sh
# 本地合并队列（DEC-068）：N 个分支 → 1 个候选 → 1 次全量验证 → 1 次推送。
#
# 为什么不是"每个分支跑一遍 CI"：
#   - 每个 worktree 是独立构建目录，冷构建 5~10 分钟；N 个分支 = N 次冷构建
#   - 分支之间的冲突几乎全部来自共享状态（已由 pre-commit 拒绝 + strip 兜底）
#   - 集成本身才是风险点：在**合并后的候选**上验证一次，比 N 个孤立分支各验一次更可信
#
# 用法:
#   wf-queue.sh run [--dry] [--keep] [--lanes auto|full] [--pr N,N | branch...]
#     默认：所有"有本地 worktree 的 open PR"分支
#   wf-queue.sh status
#
# 失败处理:
#   - 合并冲突：报告冲突分支，中止本次（已合的分支不受影响，候选保留在 --keep 时）
#   - 验证失败：报告失败步骤；候选保留（便于排查），不推送
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${WF_QUEUE_ROOT:-$(cd "$DIR/../.." && pwd)}"
cd "$ROOT" || exit 1

CMD="${1:-status}"
[ $# -gt 0 ] && shift
DRY=0; KEEP=0; LANES="auto"; ONLY=""; EXPLICIT=()
while [ $# -gt 0 ]; do
  case "$1" in
    --dry) DRY=1; shift ;;
    --keep) KEEP=1; shift ;;
    --lanes) LANES="${2:-auto}"; shift 2 ;;
    --pr) ONLY="${2:-}"; shift 2 ;;
    *) EXPLICIT+=("$1"); shift ;;
  esac
done

say() { echo "$*"; }

collect_branches() {
  if [ "${#EXPLICIT[@]}" -gt 0 ]; then
    printf '%s\n' "${EXPLICIT[@]}"
    return
  fi
  if [ -n "$ONLY" ]; then
    IFS=',' read -ra nums <<< "$ONLY"
    for n in "${nums[@]}"; do
      gh pr view "$n" --json headRefName -q .headRefName 2>/dev/null
    done
    return
  fi
  gh pr list --state open --json headRefName -q '.[].headRefName' 2>/dev/null
}

worktree_of() {
  git worktree list --porcelain | awk -v b="$1" '/^worktree /{w=$2} /^branch /{if($2=="refs/heads/"b) print w}'
}

cmd_status() {
  local n=0
  while read -r br; do
    [ -z "$br" ] && continue
    local wt; wt="$(worktree_of "$br")"
    [ -z "$wt" ] && continue
    n=$((n+1))
    local nr; nr="$(gh pr list --head "$br" --json number -q '.[0].number' 2>/dev/null)"
    echo "  候选 #$nr $br → $wt"
  done < <(collect_branches)
  echo "共 $n 个可进入队列"
}

cmd_run() {
  git fetch -q origin || { say "fetch 失败"; return 2; }

  # 主树必须干净（队列在临时 worktree 操作，但 master 落后会误导）
  if ! git diff --quiet || [ -n "$(git ls-files --others --exclude-standard | grep -v '^local.properties$' | head -1)" ]; then
    say "⚠️ 主树有未提交改动（队列只用 origin/master，不影响本次；但建议先处理）"
  fi

  local branches=() merged=() skipped=()
  while read -r br; do
    [ -z "$br" ] && continue
    if [ -z "$(worktree_of "$br")" ]; then
      skipped+=("$br")
      continue
    fi
    branches+=("$br")
  done < <(collect_branches)

  if [ "${#branches[@]}" -eq 0 ]; then say "没有可进入队列的分支"; return 0; fi
  say "队列：${#branches[@]} 个分支（跳过无 worktree：${#skipped[@]}）"

  local stamp candidate branch name
  stamp="$(date +%Y%m%d-%H%M%S)"
  name="chore/integration-$stamp"
  candidate="$ROOT/../$(basename "$ROOT")-queue-$stamp"

  if [ "$DRY" -eq 1 ]; then
    for branch in "${branches[@]}"; do say "  [dry] 将合并 $branch"; done
    say "  [dry] 候选验证: GOV_LANE=${LANES}，通过后 push origin HEAD:master"
    return 0
  fi

  git worktree add -q --detach "$candidate" origin/master || { say "无法创建候选 worktree"; return 2; }
  cd "$candidate" || return 2
  git checkout -q -b "$name"

  # 1) 依次合并（分支间应无交集；冲突即停并指名）
  for branch in "${branches[@]}"; do
    say "  [合并] $branch"
    if ! git merge --no-ff -q -m "merge($branch): via wf-queue $stamp" "origin/$branch" 2>/tmp/wf-queue-merge.log; then
      say "  ❌ 合并冲突：$branch"
      sed -n '1,8p' /tmp/wf-queue-merge.log | sed 's/^/      /'
      git merge --abort 2>/dev/null
      say "  已合并并保留：${merged[*]:-（无）}"
      git checkout -q origin/master 2>/dev/null
      cd "$ROOT"; git worktree remove --force "$candidate" 2>/dev/null; rm -rf "$candidate"
      return 3
    fi
    merged+=("$branch")
  done

  # 2) 候选上验证一次（这是唯一的重检查）
  say "  [验证] 候选上跑 local-ci（GOV_LANE=${LANES}）…"
  if ! GOV_LANE="$LANES" bash scripts/governance/gov-local-ci.sh > /tmp/wf-queue-ci.log 2>&1; then
    say "  ❌ 候选验证失败："
    grep -E "FAIL|通过:|失败:|PASS gov-local-ci" /tmp/wf-queue-ci.log | tail -8 | sed 's/^/      /'
    say "  候选保留：${candidate}（解决后重跑；--keep 可强制保留）"
    if [ "$KEEP" -eq 0 ]; then cd "$ROOT"; git worktree remove --force "$candidate" 2>/dev/null; rm -rf "$candidate"; fi
    return 4
  fi
  say "  ✅ 候选验证通过（$(grep -c . /dev/null 2>/dev/null; tail -2 /tmp/wf-queue-ci.log | tr '\n' ' ')）"

  # 3) 推送 master
  cd "$candidate" || return 2
  if ! git push -q origin HEAD:master; then
    say "  ❌ 推送 master 失败"; return 5
  fi
  local sha; sha="$(git rev-parse --short HEAD)"
  say "  ✅ 已推送 master @ ${sha}（本次合入 ${#merged[@]} 个分支）"

  # 4) 清理：分支/worktree/PR
  cd "$ROOT" || return 2
  for branch in "${merged[@]}"; do
    local wt nr
    wt="$(worktree_of "$branch")"
    nr="$(gh pr list --head "$branch" --json number -q '.[0].number' 2>/dev/null)"
    [ -n "$nr" ] && gh pr close "$nr" --comment "已由本地合并队列合入 master（${sha}，DEC-068：批次单次验证）" --delete-branch=false >/dev/null 2>&1
    [ -n "$wt" ] && { git worktree remove --force "$wt" 2>/dev/null; rm -rf "$wt"; }
    git branch -D "$branch" >/dev/null 2>&1
    git push -q origin --delete "$branch" >/dev/null 2>&1
    say "  [清理] $branch"
  done
  git worktree remove --force "$candidate" 2>/dev/null; rm -rf "$candidate"
  git worktree prune

  # 5) 任务板回填 + 主干快检
  git pull -q --ff-only origin master 2>/dev/null || true
  say "  [回填] 任务板…"
  bash scripts/governance/gov-board-reconcile.sh 2>&1 | sed 's/^/      /'
  say "  [快检] 主干 gov-validate…"
  bash scripts/governance/gov-validate.sh > /tmp/wf-queue-post.log 2>&1 && say "      ✅ 主干校验通过" || { say "      ❌ 主干校验失败（见 /tmp/wf-queue-post.log）"; return 6; }
  return 0
}

case "$CMD" in
  status) cmd_status ;;
  run) cmd_run ;;
  -h|--help) sed -n '1,22p' "$0" | sed 's/^# \{0,1\}//' ;;
  *) echo "用法: wf-queue.sh {run|status} [--dry] [--keep] [--lanes auto|full] [--pr N,N]" >&2; exit 1 ;;
esac
