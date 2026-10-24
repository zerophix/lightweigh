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

# 派生/共享状态：冲突时一律取候选侧（HEAD）。分支携带副本是历史行为，
# 队列负责把它们中和掉；真正的代码冲突仍然中止并指名分支。
SHARED_PATHS=(
  AGENTS.md
  .gitignore
  .ai/tasks.json
  STATUS.md
  tests/android-test-registry.yaml
  docs/ai/lifecycle/03_TaskGraph.md
  docs/ai/lifecycle/06_Metrics.md
  docs/index.md
)

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
  local batch_start batch_ts
  # 运行锁：清理工具据此跳过候选（DEC-069 补）
  mkdir -p "$ROOT/.cache" 2>/dev/null || true
  echo "$$" > "$ROOT/.cache/queue.lock" 2>/dev/null || true
  trap 'rm -f "$ROOT/.cache/queue.lock"' EXIT INT TERM
  batch_start="$(date +%s)"
  batch_ts="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  git fetch -q origin || { say "fetch 失败"; return 2; }

  # 主树必须干净（队列在临时 worktree 操作，但 master 落后会误导）
  if ! git diff --quiet || [ -n "$(git ls-files --others --exclude-standard | grep -v '^local.properties$' | head -1)" ]; then
    say "⚠️ 主树有未提交改动（队列只用 origin/master，不影响本次；但建议先处理）"
  fi

  # 启动前先回收已合入的 worktree/分支（--dry 不清理，保证只读语义）
  if [ "$DRY" -eq 0 ] && [ -x "$ROOT/scripts/session/ses-worktree.sh" ]; then
    bash "$ROOT/scripts/session/ses-worktree.sh" clean >/dev/null 2>&1 || true
  fi

  local branches=() merged=() skipped=()
  local explicit_mode=0
  [ "${#EXPLICIT[@]}" -gt 0 ] && explicit_mode=1
  while read -r br; do
    [ -z "$br" ] && continue
    if [ -z "$(worktree_of "$br")" ] && [ "$explicit_mode" -eq 0 ]; then
      skipped+=("$br")
      continue
    fi
    # 显式模式下也要求远端分支存在
    if ! git rev-parse -q --verify "origin/$br" >/dev/null; then
      say "  [跳过] origin/$br 不存在"; continue
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
  # 无论成功/失败/被 kill，退出时都删除候选目录（避免再次堆积）
  QUEUE_CANDIDATE="$candidate"
  cleanup_candidate() {
    [ -z "${QUEUE_CANDIDATE:-}" ] && return 0
    if [ "${KEEP:-0}" -eq 1 ]; then say "  保留候选: ${QUEUE_CANDIDATE}"; return 0; fi
    cd "$ROOT" 2>/dev/null || true
    git worktree remove --force "$QUEUE_CANDIDATE" 2>/dev/null
    rm -rf "$QUEUE_CANDIDATE"
    git worktree prune 2>/dev/null
    QUEUE_CANDIDATE=""
  }
  trap cleanup_candidate EXIT INT TERM
  # local.properties 是 gitignore 的本机文件；缺它 gradle 直接报 SDK location not found
  if [ -f "$ROOT/local.properties" ] && [ ! -f "$candidate/local.properties" ]; then
    cp "$ROOT/local.properties" "$candidate/local.properties"
  fi
  cd "$candidate" || return 2
  git checkout -q -b "$name"

  # 1) 依次合并（分支间应无交集；冲突即停并指名）
  for branch in "${branches[@]}"; do
    say "  [合并] $branch"
    if ! git merge --no-ff -q -m "chore(merge): $branch via queue $stamp" "origin/$branch" 2>/tmp/wf-queue-merge.log; then
      # 先尝试中和共享状态冲突（取候选侧）
      local shared_resolved=1
      for f in "${SHARED_PATHS[@]}"; do
        if git ls-files -u -- "$f" | grep -q .; then
          git checkout --ours -- "$f" 2>/dev/null && git add "$f" 2>/dev/null || shared_resolved=0
        fi
      done
      if git ls-files -u | grep -q "staged-claims"; then git rm -qf .ai/staged-claims.json 2>/dev/null; fi
      if [ "$shared_resolved" -eq 1 ] && ! git ls-files -u | grep -q .; then
        git add -A
        git commit -q --no-edit 2>/dev/null || git commit -q -m "chore(merge): $branch via queue $stamp"
        say "  [合并] ${branch}（共享状态已取候选侧）"
        merged+=("$branch")
        continue
      fi
      say "  ❌ 合并冲突：$branch"
      git ls-files -u | awk '{print $4}' | sort -u | sed 's/^/      /'
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

  # 2b) 回填任务板（在候选上完成，随候选一起推送；避免主树留脏改动）
  say "  [回填] 任务板（候选侧）…"
  bash scripts/governance/gov-board-reconcile.sh --branches "${merged[*]}" 2>&1 | sed 's/^/      /' || true
  if ! git diff --quiet; then
    git add -A && git commit -q -m "chore(board): reconcile tasks from merged PRs" || true
  fi
  # 回填会改任务状态 → 计数文档必须同步，否则主干 GOV-DOCS-004 红
  bash scripts/session/ses-claim-staged.sh >/dev/null 2>&1 || true
  bash scripts/governance/gov-board-sync.sh >/dev/null 2>&1 || true
  python3 scripts/governance/gov-registry-sync.py >/dev/null 2>&1 || true
  if ! git diff --quiet; then
    git add -A && git commit -q -m "chore(lifecycle): sync derived docs after board reconcile" || true
  fi

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

  # 计时记账（用于度量：流程成本是否失控）
  mkdir -p "$ROOT/.cache" 2>/dev/null || true
  # 产品:治理 文件比例（DEC-071 防本末倒置）
  local prod gov
  prod=$(git -C "$ROOT" log --name-only --pretty=format: "origin/master~${#merged[@]}..origin/master" 2>/dev/null \
    | grep -cE '^(app|domain|application|infrastructure)/src/' || true)
  gov=$(git -C "$ROOT" log --name-only --pretty=format: "origin/master~${#merged[@]}..origin/master" 2>/dev/null \
    | grep -cE '^(scripts|docs|\.ai|\.github)/' || true)
  printf '{"ts":"%s","branches":%d,"seconds":%d,"product":%s,"governance":%s,"result":"ok"}\n' \
    "$batch_ts" "${#merged[@]}" "$(( $(date +%s) - batch_start ))" \
    "${prod:-0}" "${gov:-0}" \
    >> "$ROOT/.cache/queue-runs.jsonl" 2>/dev/null || true

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
