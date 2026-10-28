#!/usr/bin/env bash
# scripts/governance/gov-merge.sh
# 合并队列一条命令（DEC-064）：PR 拉平主干 + 派生物同步 + local-ci + 合入 + 清理
#
# 用法:
#   bash scripts/governance/gov-merge.sh <PR号> [--dry-run] [--keep-worktree]
#   bash scripts/governance/gov-merge.sh --help
#
# 步骤（任一失败即停止并给出下一步）:
#   1. gh pr view 前置检查（非 OPEN 直接退出）
#   2. 为该 PR 分支准备 worktree（已存在则复用）
#   3. git merge origin/master（冲突不自动解决，打印清单后退出）
#   4. gov-board-sync 同步派生物（--dry，只改不提交）
#   5. 有变更则提交（固定 commit message）
#   6. git push    7. gov-local-ci（合入权威）    8. gh pr merge
#   9. 清理：fetch --prune、更新 master、删本地分支、移除 worktree、worktree prune
#
# 背景: 并发多分支合入时，每个 PR 都要手工"拉 master → 解冲突 → 重算计数 →
# 重跑 local-ci → gh pr merge → 删分支删 worktree"。DEC-064 把这条链收成一条命令。
set -euo pipefail

SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "${ROOT:-}" ] || [ ! -f "$ROOT/scripts/governance/gov-board-sync.sh" ]; then
  ROOT="$(cd "$SELF_DIR/../.." && pwd)"
fi
cd "$ROOT"

REPO_NAME="$(basename "$ROOT")"

# 主干探测：master 优先，其次 main（与 ses-worktree.sh 保持一致）
TRUNK=""
for c in master main; do
  if git show-ref --verify --quiet "refs/heads/$c" || git show-ref --verify --quiet "refs/remotes/origin/$c"; then
    TRUNK="$c"
    break
  fi
done
TRUNK="${TRUNK:-master}"

SYNC_COMMIT_MSG="chore(lifecycle): sync derived docs after merging master"
DRY=0
KEEP_WT=0
PR=""

usage() {
  sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

log() { echo "[gov-merge] $*"; }

die() {
  echo "" >&2
  echo "❌ $*" >&2
  exit 1
}

# ---------------------------------------------------------------- 参数
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run|--dry|-n) DRY=1; shift ;;
    --keep-worktree) KEEP_WT=1; shift ;;
    -h|--help) usage 0 ;;
    -*)
      echo "未知选项: $1" >&2
      usage 1
      ;;
    *)
      if [ -z "$PR" ]; then PR="$1"; shift; else echo "多余参数: $1" >&2; usage 1; fi
      ;;
  esac
done

if [ -z "$PR" ]; then
  echo "用法: gov-merge.sh <PR号> [--dry-run] [--keep-worktree]" >&2
  exit 1
fi
if ! echo "$PR" | grep -qE '^[0-9]+$'; then
  echo "PR 号必须是数字: $PR" >&2
  exit 1
fi

# ---------------------------------------------------------------- 步骤 1: 前置检查
log "步骤 1/9: 读取 PR #$PR"
command -v gh >/dev/null 2>&1 || die "未安装 gh（GitHub CLI）。
   安装后: brew install gh && gh auth login"

GH_ERR="$(mktemp)"
if ! PR_JSON="$(gh pr view "$PR" --json headRefName,state,mergeable 2>"$GH_ERR")"; then
  gh_msg="$(cat "$GH_ERR" 2>/dev/null || true)"
  rm -f "$GH_ERR"
  die "无法读取 PR #$PR${gh_msg:+：$gh_msg}
   检查: gh auth status / PR 号是否存在 / 网络
   只读排查: gh pr view $PR --json state,headRefName"
fi
rm -f "$GH_ERR"

read -r BRANCH PR_STATE MERGEABLE <<<"$(printf '%s' "$PR_JSON" | python3 -c '
import json, sys
d = json.load(sys.stdin)
print(d.get("headRefName") or "-", d.get("state") or "UNKNOWN", d.get("mergeable") or "UNKNOWN")
')"

if [ -z "${BRANCH:-}" ] || [ "$BRANCH" = "-" ]; then
  die "PR #$PR 没有可用分支名（headRefName 为空）"
fi
if [ "$PR_STATE" != "OPEN" ]; then
  die "PR #$PR 状态为 ${PR_STATE}（非 OPEN），无需合入。
   若已合并且只是要清理残留: bash scripts/session/ses-worktree.sh clean"
fi
log "  分支: $BRANCH  状态: $PR_STATE  mergeable: $MERGEABLE"

# worktree 路径与 ses-worktree.sh 默认规则一致: 与主仓同级 <repo>-<slug>
slug="$(echo "${BRANCH#*/}" | tr '/' '-' | tr '[:upper:]' '[:lower:]')"
WT="$(dirname "$ROOT")/${REPO_NAME}-${slug}"

# ---------------------------------------------------------------- --dry-run
if [ "$DRY" = "1" ]; then
  cat <<PLAN

[dry-run] 合并计划（PR #${PR}）
  分支:     $BRANCH
  状态:     $PR_STATE / $MERGEABLE
  worktree: ${WT}（已存在则复用）
  主干:     $TRUNK

  将执行:
    1. git fetch origin $TRUNK && cd "$WT" && git merge origin/$TRUNK --no-edit
    2. bash scripts/governance/gov-board-sync.sh --dry
    3. 有派生物变更则提交 "$SYNC_COMMIT_MSG"
    4. git push -u origin $BRANCH
    5. bash scripts/governance/gov-local-ci.sh
    6. gh pr merge $PR --merge --delete-branch=true
    7. 清理: fetch --prune / 更新 $TRUNK / 删本地分支 $BRANCH / 移除 worktree $WT
       （--keep-worktree 可跳过本步）

  未执行任何变更。
PLAN
  exit 0
fi

# ---------------------------------------------------------------- 步骤 2: worktree
log "步骤 2/9: 准备 worktree"
existing_wt="$(git worktree list --porcelain | awk -v b="refs/heads/$BRANCH" '
  /^worktree / { p=$2 }
  /^branch / && $2==b { print p; exit }
')"
if [ -n "$existing_wt" ]; then
  WT="$existing_wt"
  log "  复用已有 worktree: $WT"
else
  git fetch origin "$BRANCH" --quiet || die "拉取分支失败: origin/$BRANCH
   检查: git ls-remote --heads origin $BRANCH"
  git fetch origin "$TRUNK" --quiet || true
  if [ -e "$WT" ]; then
    die "路径已存在但不是该分支的 worktree: $WT
   处理: bash scripts/session/ses-worktree.sh remove \"$WT\" 或手工确认后删除"
  fi
  if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
    git worktree add "$WT" "$BRANCH" >/dev/null
  else
    git worktree add -b "$BRANCH" "$WT" "origin/$BRANCH" >/dev/null
  fi
  # local.properties 被 gitignore，不随 checkout 带出；gov-local-ci 需要 gradle
  if [ -f "$ROOT/local.properties" ] && [ ! -f "$WT/local.properties" ]; then
    cp "$ROOT/local.properties" "$WT/local.properties"
  fi
  log "  已创建 worktree: $WT"
fi

# 在 worktree 内声明暂存文件归属（pre-commit 要求；无会话文件时静默跳过）
claim_staged() {
  if [ -f "$WT/.ai/current-session.json" ] && [ -f "$WT/.ai/staged-claims.json" ]; then
    ( cd "$WT" && bash scripts/session/ses-claim-staged.sh >/dev/null 2>&1 ) || true
  fi
}

print_conflicts_and_exit() {
  local files
  files="$(git -C "$WT" diff --name-only --diff-filter=U 2>/dev/null || true)"
  echo "" >&2
  echo "❌ 合并 origin/$TRUNK 存在冲突，未自动解决。" >&2
  if [ -n "$files" ]; then
    echo "   冲突文件:" >&2
    echo "$files" | sed 's/^/     - /' >&2
  else
    echo "   worktree 内有未提交改动或索引不干净（无冲突标记文件）。" >&2
    echo "   查看: cd \"$WT\" && git status" >&2
  fi
  cat >&2 <<HINT

   下一步:
     cd "$WT"
     git status                 # 解决冲突 / 提交本地改动
     git add <files>
     bash scripts/session/ses-claim-staged.sh
     # 解决完回到主仓重跑:
     bash scripts/governance/gov-merge.sh $PR
HINT
  exit 2
}

# ---------------------------------------------------------------- 步骤 3: merge
log "步骤 3/9: 合并 origin/$TRUNK 到 $BRANCH"
git fetch origin "$TRUNK" --quiet || die "拉取主干失败: origin/$TRUNK"

if [ -n "$(git -C "$WT" diff --name-only --diff-filter=U 2>/dev/null || true)" ]; then
  # 上次合并冲突未解决完
  print_conflicts_and_exit
fi

WT_GIT_DIR="$(git -C "$WT" rev-parse --absolute-git-dir)"
if [ -f "$WT_GIT_DIR/MERGE_HEAD" ]; then
  log "  检测到已解决但未提交的合并，补提交"
  claim_staged
  if ! git -C "$WT" commit --no-edit --quiet; then
    die "合并提交失败（可能被 pre-commit 拦截：会话归属 / 编译 / custom-checks）。
   排查: cd \"$WT\" && git commit --no-edit
   解决后重跑本命令。"
  fi
else
  # --no-commit: 先把合并结果放暂存区，声明会话归属后再提交，避免钩子误拦
  if ! git -C "$WT" merge --no-commit --no-edit "origin/$TRUNK"; then
    print_conflicts_and_exit
  fi
  if [ -f "$WT_GIT_DIR/MERGE_HEAD" ]; then
    claim_staged
    if ! GOV_MERGE_QUEUE=1 git -C "$WT" commit --no-edit --quiet; then
      die "合并提交失败（可能被 pre-commit 拦截：会话归属 / 编译 / custom-checks）。
   排查: cd \"$WT\" && GOV_MERGE_QUEUE=1 git commit --no-edit
   解决后重跑本命令。"
    fi
  fi
fi

# ---------------------------------------------------------------- 步骤 4: 派生物同步
log "步骤 4/9: 同步派生物（gov-board-sync --dry）"
if ! ( cd "$WT" && bash scripts/governance/gov-board-sync.sh --dry ); then
  die "gov-board-sync 失败（派生物计数无法同步）。
   排查: cd \"$WT\" && bash scripts/governance/gov-board-sync.sh --dry
   修好治理校验后再重跑本命令。"
fi

# ---------------------------------------------------------------- 步骤 5: 提交
log "步骤 5/9: 提交派生物变更"
STAGED_FILES="$(git -C "$WT" diff --cached --name-only || true)"
if [ -n "$STAGED_FILES" ]; then
  claim_staged
  # 允许合并队列提交派生数据 (DEC-067)
  if ! GOV_MERGE_QUEUE=1 git -C "$WT" commit -m "$SYNC_COMMIT_MSG" --quiet; then
    die "派生物同步提交失败（可能被 pre-commit 拦截）。
   排查: cd \"$WT\" && GOV_MERGE_QUEUE=1 git commit -m \"$SYNC_COMMIT_MSG\""
  fi
  log "  已提交: $(echo "$STAGED_FILES" | tr '\n' ' ')"
else
  log "  派生物无变更，跳过提交"
fi

# ---------------------------------------------------------------- 步骤 6: push
log "步骤 6/9: 推送 $BRANCH"
if ! git -C "$WT" push -u origin "$BRANCH"; then
  die "push 失败。
   排查: cd \"$WT\" && git push -u origin $BRANCH
   （远端有新提交时先 git pull --rebase）"
fi

# ---------------------------------------------------------------- 步骤 6.5: 审计检查 (DEC-077 P1)
# 检查 bypass.jsonl：如果 explore/docs 模式下跳过了编译, 合并前必须额外跑一次全量 CI
log "步骤 6.5/9: 审计 explore/docs 的 bypass 记录"
AUDIT_FILE="$WT/.ai/audit/bypass.jsonl"
# source 审计检查函数 (DRY: 与 wf-autopilot.sh 共享)
if [ -f "$WT/scripts/governance/gov-mode-lib.sh" ]; then
  . "$WT/scripts/governance/gov-mode-lib.sh"
fi
AUDIT_CHECK="$(gov_mode_check_audit "$AUDIT_FILE" "$BRANCH" 2>/dev/null || echo "OK")"
if [ "$AUDIT_CHECK" = "FORCE_LANE_C" ]; then
  log "  [审计] 发现 explore/docs 模式跳过编译的记录，合并前强制全量 CI (Lane C)..."
  if ! ( cd "$WT" && GOV_MODE=dev GOV_LANE=C GOV_CI_FULL=1 bash scripts/governance/gov-local-ci.sh >/tmp/gov-merge-audit-ci.log 2>&1 ); then
    die "审计 CI 未通过（explore/docs bypass 提交必须通过全量 CI）。
   修复后重跑: bash scripts/governance/gov-merge.sh $PR"
  fi
  log "  [审计] 审计 CI 通过"
else
  log "  [审计] 无 explore/docs bypass 记录，跳过额外审计 CI"
fi

# ---------------------------------------------------------------- 步骤 7: local-ci
# DEC-077: 合并时强制 dev 模式 — 探险模式提交必须通过完整 CI 才能合入
log "步骤 7/9: gov-local-ci（合入权威，必须全绿）"
log "  强制 GOV_MODE=dev + GOV_LANE=C (忽略 explore/docs 模式豁免)"

# 清除工作区的模式文件 + 环境变量, 防止 explore bypass 滲透到合入
rm -f "$WT/.ai/current-mode.json" 2>/dev/null || true
if ! ( cd "$WT" && GOV_MODE=dev GOV_LANE=C GOV_CI_FULL=1 bash scripts/governance/gov-local-ci.sh ); then
  die "gov-local-ci 未全绿，已停止（未执行 gh pr merge）。
   修复后重跑: bash scripts/governance/gov-merge.sh $PR"
fi

# ---------------------------------------------------------------- 步骤 8: gh pr merge
log "步骤 8/9: gh pr merge $PR"
GH_MERGE_ERR="$(mktemp)"
if ! gh pr merge "$PR" --merge --delete-branch=true 2>"$GH_MERGE_ERR"; then
  merge_msg="$(cat "$GH_MERGE_ERR" 2>/dev/null || true)"
  rm -f "$GH_MERGE_ERR"
  post_state="$(gh pr view "$PR" --json state --jq '.state' 2>/dev/null || echo UNKNOWN)"
  if [ "$post_state" = "MERGED" ]; then
    log "  gh 返回非零但 PR 已是 MERGED（多为本地分支删除失败），继续清理"
  else
    die "gh pr merge 失败${merge_msg:+：$merge_msg}
   排查: gh pr view $PR --json state,mergeable,statusCheckRollup
   确认 checks 全绿 / 无冲突后重跑本命令。"
  fi
fi
rm -f "$GH_MERGE_ERR" 2>/dev/null || true

# ---------------------------------------------------------------- 步骤 9: 清理
DELETED_BRANCH=""
if [ "$KEEP_WT" = "1" ]; then
  log "步骤 9/9: 跳过清理（--keep-worktree），worktree 保留在: $WT"
else
  log "步骤 9/9: 清理"
  git -C "$ROOT" fetch --prune origin --quiet || true

  cur_branch="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
  if [ "$cur_branch" = "$TRUNK" ]; then
    if git -C "$ROOT" merge --ff-only "origin/$TRUNK" --quiet 2>/dev/null; then
      log "  $TRUNK 已快进到 origin/$TRUNK"
    else
      log "  $TRUNK 无法快进（本地有未推送提交？），请自行确认后 git pull --ff-only"
    fi
  else
    log "  主树当前在 ${cur_branch}，未切换；稍后自行: git -C \"$ROOT\" checkout $TRUNK && git pull --ff-only"
  fi

  # 移除该 PR 的 worktree（含残留目录）
  if [ "$WT" != "$ROOT" ] && git -C "$ROOT" worktree list --porcelain | grep -Fqx "worktree $WT"; then
    git -C "$ROOT" worktree remove --force "$WT" >/dev/null 2>&1 || true
  fi
  if [ "$WT" != "$ROOT" ] && [ -d "$WT" ]; then
    rm -rf "$WT"
  fi
  git -C "$ROOT" worktree prune

  # 删除本地分支（已合入，-D 安全）
  if git -C "$ROOT" show-ref --verify --quiet "refs/heads/$BRANCH"; then
    if git -C "$ROOT" branch -D "$BRANCH" >/dev/null 2>&1; then
      DELETED_BRANCH="$BRANCH"
    else
      log "  本地分支 $BRANCH 删除失败（可能仍被其它 worktree 占用）"
    fi
  fi
fi

# ---------------------------------------------------------------- 摘要
NEW_SHA="$(git -C "$ROOT" rev-parse --short "$TRUNK" 2>/dev/null || echo unknown)"
echo ""
echo "✅ PR #$PR 已合并: $BRANCH → ${TRUNK}（${NEW_SHA}）"
if [ "$KEEP_WT" = "1" ]; then
  echo "   保留 worktree: $WT"
else
  echo "   清理: worktree $WT${DELETED_BRANCH:+ / 本地分支 $DELETED_BRANCH}"
fi
